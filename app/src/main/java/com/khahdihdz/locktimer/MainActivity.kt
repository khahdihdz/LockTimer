package com.khahdihdz.locktimer

import android.Manifest
import android.app.Activity
import android.app.AlarmManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel

class MainActivity : ComponentActivity() {

    private var pendingStartMinutes: Int? = null

    private val viewModel: TimerViewModel by viewModels()

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
        VersionUpdateChecker.check(this)
        setContent {
            LockTimerTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val state by viewModel.state.collectAsState()
                    LockTimerScreen(
                        state = state,
                        onStart = ::onStartRequested,
                        onCancel = viewModel::cancel
                    )
                }
            }
        }
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
            primary = Color(0xFF8AB4F8),
            background = Color(0xFF101418),
            surface = Color(0xFF161B21),
            onPrimary = Color(0xFF0B1020),
            onBackground = Color(0xFFE6EAF0),
            onSurface = Color(0xFFE6EAF0)
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
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = stringResource(R.string.ui_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(24.dp))

        if (!state.running) {
            IdleContent(
                minutesText = minutesText,
                onMinutesChange = { minutesText = it },
                onStart = onStart
            )
        } else {
            RunningContent(state = state, onCancel = onCancel)
        }

        Spacer(modifier = Modifier.height(24.dp))
        val uriHandler = LocalUriHandler.current
        Text(
            text = stringResource(R.string.ui_credit),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
            modifier = Modifier.clickable {
                runCatching { uriHandler.openUri("https://khahdihdz.github.io") }
            }
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
                label = { Text(label) }
            )
        }
    }

    Spacer(modifier = Modifier.height(16.dp))

    OutlinedTextField(
        value = minutesText,
        onValueChange = { value ->
            onMinutesChange(value.filter { it.isDigit() }.take(5))
        },
        label = { Text(stringResource(R.string.ui_minutes_label)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )

    Spacer(modifier = Modifier.height(16.dp))

    Text(
        text = stringResource(R.string.ui_idle_hint),
        style = MaterialTheme.typography.bodyMedium,
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
    )

    Spacer(modifier = Modifier.height(16.dp))

    val minutes = minutesText.toIntOrNull() ?: 0
    Button(
        onClick = { onStart(minutes) },
        enabled = minutes > 0,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(text = stringResource(R.string.ui_start))
    }
}

@Composable
private fun RunningContent(
    state: TimerUiState,
    onCancel: () -> Unit
) {
    val progress = if (state.totalMillis > 0) {
        (state.remainingMillis.toFloat() / state.totalMillis.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    val trackColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.12f)
    val progressColor = MaterialTheme.colorScheme.primary
    val textColor = MaterialTheme.colorScheme.onBackground

    Text(
        text = stringResource(R.string.ui_running_hint),
        style = MaterialTheme.typography.bodyMedium,
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
    )

    Spacer(modifier = Modifier.height(24.dp))

    Box(contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(220.dp)) {
            val stroke = 14.dp.toPx()
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
        Text(
            text = formatRemaining(state.remainingMillis),
            fontSize = 36.sp,
            fontWeight = FontWeight.Bold,
            color = textColor
        )
    }

    Spacer(modifier = Modifier.height(24.dp))

    OutlinedButton(
        onClick = onCancel,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(text = stringResource(R.string.ui_cancel))
    }
}
