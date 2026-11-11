package com.khahdihdz.locktimer

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val LICENSE_KEY_PATTERN = Regex("^LT(?:-[0-9A-F]{4}){8}$")

@Composable
fun LicenseScreen(
    onTrial: suspend () -> LicenseStatus,
    onActivate: suspend (String) -> LicenseStatus,
    onRetry: suspend () -> LicenseStatus,
    onSuccess: () -> Unit
) {
    var key by remember { mutableStateOf("") }
    var message by remember {
        mutableStateOf("Nhập License Key để kích hoạt hoặc dùng thử miễn phí 7 ngày.")
    }
    var busy by remember { mutableStateOf(false) }
    var showKey by remember { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    fun run(action: suspend () -> LicenseStatus) {
        if (busy) return
        scope.launch {
            busy = true
            try {
                val result = withContext(Dispatchers.IO) { action() }
                message = if (result.active) {
                    val expiry = result.expiresAt?.let {
                        SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                            .format(Date(it * 1000L))
                    }
                    buildString {
                        append(if (result.trial) "Dùng thử đã được kích hoạt." else "License Key đã kích hoạt thành công.")
                        if (expiry != null) append(" Hết hạn: $expiry.")
                        else if (!result.trial) append(" Thời hạn: Vĩnh viễn.")
                    }
                } else {
                    result.message
                }
                if (result.active) {
                    delay(1400)
                    onSuccess()
                }
            } finally {
                busy = false
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text("LockTimer", style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(12.dp))
        Text("Ứng dụng yêu cầu kích hoạt. Bạn có thể bắt đầu dùng thử miễn phí 7 ngày.")
        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = key,
            onValueChange = { key = it.uppercase(Locale.ROOT).filterNot { ch -> ch.isWhitespace() } },
            label = { Text("License Key") },
            placeholder = { Text("LT-XXXX-XXXX-...") },
            singleLine = true,
            visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                TextButton(onClick = { showKey = !showKey }) {
                    Text(if (showKey) "Ẩn" else "Hiện")
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !busy
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(
                onClick = {
                    val pasted = clipboard.getText()?.text.orEmpty()
                        .trim()
                        .uppercase(Locale.ROOT)
                        .filterNot { it.isWhitespace() }
                    if (pasted.isNotEmpty()) {
                        key = pasted
                        message = "Đã dán License Key. Hãy nhấn Kích hoạt để xác minh."
                    } else {
                        message = "Clipboard đang trống."
                    }
                },
                enabled = !busy
            ) {
                Text("Dán key")
            }
            TextButton(
                onClick = {
                    key = ""
                    message = "Đã xóa nội dung key."
                },
                enabled = !busy && key.isNotEmpty()
            ) {
                Text("Xóa")
            }
        }

        Spacer(Modifier.height(4.dp))
        Text(message, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(12.dp))

        Button(
            onClick = {
                val normalized = key.trim().uppercase(Locale.ROOT)
                if (!LICENSE_KEY_PATTERN.matches(normalized)) {
                    message = "Định dạng key không hợp lệ. Key phải có dạng LT-XXXX-XXXX-XXXX-XXXX-XXXX-XXXX-XXXX-XXXX."
                } else {
                    run { onActivate(normalized) }
                }
            },
            enabled = !busy && key.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (busy) "Đang xử lý…" else "Kích hoạt License Key")
        }

        Button(
            onClick = { run { onTrial() } },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Dùng thử miễn phí 7 ngày")
        }

        OutlinedButton(
            onClick = { run { onRetry() } },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Kiểm tra lại")
        }
    }
}
