package com.khahdihdz.locktimer

import android.Manifest
import android.app.Activity
import android.app.AlarmManager
import android.app.DownloadManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    private var pendingStartMinutes: Int? = null

    private val viewModel: TimerViewModel by viewModels()
    private var updateInfo by mutableStateOf<UpdateInfo?>(null)

    private val adminLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val minutes = pendingStartMinutes
        if (result.resultCode == Activity.RESULT_OK && minutes != null) {
            startCountdown(minutes)
        }
        pendingStartMinutes = null
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* chấp nhận cả từ chối: đếm giờ vẫn chạy, chỉ thiếu notification */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycleScope.launch(Dispatchers.IO) {
            val latest = UpdateChecker.fetchLatest()
            withContext(Dispatchers.Main) { updateInfo = latest }
        }
        UpdateWorkScheduler.schedule(this)
        setContent {
            LockTimerTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        updateInfo?.let { info ->
                            UpdateBanner(info = info, onInstall = { installUpdate(info) })
                        }
                        val state by viewModel.state.collectAsState()
                        Box(modifier = Modifier.weight(1f)) {
                            LockTimerScreen(
                                state = state,
                                onStart = ::onStartRequested,
                                onCancel = viewModel::cancel
                            )
                        }
                    }
                }
            }
        }
        requestNotificationPermissionIfNeeded()
    }

    private fun installUpdate(info: UpdateInfo) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !packageManager.canRequestPackageInstalls()) {
            Toast.makeText(this, getString(R.string.update_allow_unknown_sources), Toast.LENGTH_LONG).show()
            runCatching {
                startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:$packageName")))
            }
            return
        }
        lifecycleScope.launch {
            try {
                Toast.makeText(this@MainActivity, getString(R.string.update_downloading), Toast.LENGTH_SHORT).show()
                val apkUri = withContext(Dispatchers.IO) { downloadUpdate(info) }
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(apkUri, "application/vnd.android.package-archive")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(intent)
            } catch (error: Exception) {
                Toast.makeText(this@MainActivity, getString(R.string.update_failed, error.message ?: ""), Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun downloadUpdate(info: UpdateInfo): Uri {
        val manager = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val request = DownloadManager.Request(Uri.parse(info.downloadUrl))
            .setTitle("LockTimer ${info.versionName}")
            .setDescription(getString(R.string.update_download_description))
            .setMimeType("application/vnd.android.package-archive")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(this, Environment.DIRECTORY_DOWNLOADS, info.assetName)
        val id = manager.enqueue(request)
        val started = System.currentTimeMillis()
        while (System.currentTimeMillis() - started < 10 * 60 * 1000L) {
            val query = DownloadManager.Query().setFilterById(id)
            manager.query(query).use { cursor ->
                if (cursor != null && cursor.moveToFirst()) {
                    when (cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))) {
                        DownloadManager.STATUS_SUCCESSFUL -> return manager.getUriForDownloadedFile(id)
                            ?: error(getString(R.string.update_download_file_missing))
                        DownloadManager.STATUS_FAILED -> {
                            val reason = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
                            error(getString(R.string.update_download_failed_code, reason))
                        }
                    }
                }
            }
            Thread.sleep(1000)
        }
        error(getString(R.string.update_download_timeout))
    }

    /** Nhấn Bắt đầu: chưa có Device Admin -> xin quyền rồi mới đếm. */
    private fun onStartRequested(minutes: Int) {
        if (viewModel.isAdminActive()) {
            startCountdown(minutes)
        } else {
            pendingStartMinutes = minutes
            requestDeviceAdmin()
        }
    }

    private fun requestDeviceAdmin() {
        val component = ComponentName(this, LockDeviceAdminReceiver::class.java)
        val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
            .putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, component)
            .putExtra(
                DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                getString(R.string.device_admin_explanation)
            )
        runCatching { adminLauncher.launch(intent) }
            .onFailure {
                Toast.makeText(this, R.string.admin_error, Toast.LENGTH_LONG).show()
            }
    }

    private fun startCountdown(minutes: Int) {
        requestNotificationPermissionIfNeeded()
        promptExactAlarmIfNeeded()
        promptBatteryOptimization()
        viewModel.start(minutes)
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun promptExactAlarmIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
            if (!alarmManager.canScheduleExactAlarms()) {
                runCatching {
                    startActivity(
                        Intent(
                            Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                            Uri.parse("package:$packageName")
                        )
                    )
                }
            }
        }
    }

    private fun promptBatteryOptimization() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
            val intent = Intent().apply {
                action = Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
                data = Uri.parse("package:$packageName")
            }
            runCatching { startActivity(intent) }
        }
    }
}

@Composable
fun LockTimerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Color(0xFF24D5EF),
            secondary = Color(0xFF7C9DFF),
            tertiary = Color(0xFF8A7CFF),
            background = Color(0xFF071326),
            surface = Color(0xFF10213D),
            surfaceVariant = Color(0xFF172B4B),
            onPrimary = Color(0xFF04172B),
            onBackground = Color(0xFFF1F7FF),
            onSurface = Color(0xFFF1F7FF),
            onSurfaceVariant = Color(0xFFB6C7E0)
        ),
        content = content
    )
}

@Composable
fun LockTimerScreen(
    state: TimerUiState,
    onStart: (Int) -> Unit,
    onCancel: () -> Unit
) {
    var minutesText by remember { mutableStateOf("15") }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Image(
                painter = painterResource(R.drawable.ic_timer),
                contentDescription = stringResource(R.string.app_name),
                modifier = Modifier.size(52.dp)
            )
            Spacer(modifier = Modifier.size(12.dp))
            Column {
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = stringResource(R.string.ui_tagline),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 3.dp,
            shadowElevation = 6.dp
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = stringResource(R.string.ui_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                if (!state.running) {
                    IdleContent(
                        minutesText = minutesText,
                        onMinutesChange = { minutesText = it },
                        onStart = onStart
                    )
                } else {
                    RunningContent(state = state, onCancel = onCancel)
                }
            }
        }

        AboutFooter()
    }
}

@Composable
private fun AboutFooter() {
    val context = LocalContext.current
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Text(
            text = stringResource(R.string.about_website),
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.clickable {
                runCatching {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse("https://khahdihdz.github.io/LockTimer/"))
                    )
                }
            }
        )
        Text(
            text = stringResource(R.string.about_author),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.clickable {
                runCatching {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/khahdihdz"))
                    )
                }
            }
        )
        Text(
            text = stringResource(R.string.ui_version_label, BuildConfig.VERSION_NAME),
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
            style = MaterialTheme.typography.labelSmall
        )
    }
}

@Composable
private fun IdleContent(
    minutesText: String,
    onMinutesChange: (String) -> Unit,
    onStart: (Int) -> Unit
) {
    val presets = listOf(5, 15, 30, 60, 120)
    val minutes = minutesText.toIntOrNull() ?: 0

    Box(contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(184.dp)) {
            val stroke = 12.dp.toPx()
            val inset = stroke / 2f
            val arcSize = Size(size.width - stroke, size.height - stroke)
            drawArc(
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )
            drawArc(
                color = MaterialTheme.colorScheme.primary,
                startAngle = -90f,
                sweepAngle = 285f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = stringResource(R.string.ui_choose_duration),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = formatRemaining(minutes.coerceAtLeast(0) * 60_000L),
                fontSize = 34.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = stringResource(R.string.ui_remaining),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = stringResource(R.string.ui_choose_duration),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            presets.forEach { preset ->
                val label = if (preset >= 60) {
                    stringResource(R.string.chip_hours, preset / 60)
                } else {
                    stringResource(R.string.chip_minutes, preset)
                }
                FilterChip(
                    selected = minutesText == preset.toString(),
                    onClick = { onMinutesChange(preset.toString()) },
                    label = { Text(label, fontWeight = FontWeight.SemiBold) }
                )
            }
        }
    }

    OutlinedTextField(
        value = minutesText,
        onValueChange = { value ->
            onMinutesChange(value.filter { it.isDigit() }.take(5))
        },
        label = { Text(stringResource(R.string.ui_minutes_label)) },
        singleLine = true,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    )

    Text(
        text = stringResource(R.string.ui_idle_hint),
        style = MaterialTheme.typography.bodySmall,
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    Button(
        onClick = { onStart(minutes) },
        enabled = minutes > 0,
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth().height(56.dp)
    ) {
        Text(
            text = stringResource(R.string.ui_start),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun RunningContent(
    state: TimerUiState,
    onCancel: () -> Unit
) {
    val progress = if (state.totalMillis > 0) {
        (state.remainingMillis.toFloat() / state.totalMillis.toFloat()).coerceIn(0f, 1f)
    } else 0f
    val trackColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.12f)
    val progressColor = MaterialTheme.colorScheme.primary

    Text(
        text = stringResource(R.string.ui_running_hint),
        style = MaterialTheme.typography.bodyMedium,
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Box(contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(220.dp)) {
            val stroke = 15.dp.toPx()
            val topLeft = Offset(stroke / 2f, stroke / 2f)
            val arcSize = Size(size.width - stroke, size.height - stroke)
            drawArc(
                color = trackColor,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )
            drawArc(
                color = progressColor,
                startAngle = -90f,
                sweepAngle = 360f * progress,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = formatRemaining(state.remainingMillis),
                fontSize = 34.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = stringResource(R.string.ui_remaining),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
    OutlinedButton(
        onClick = onCancel,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().height(52.dp)
    ) {
        Text(text = stringResource(R.string.ui_cancel), fontWeight = FontWeight.SemiBold)
    }
}
