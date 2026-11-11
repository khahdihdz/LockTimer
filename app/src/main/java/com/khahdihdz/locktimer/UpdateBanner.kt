package com.khahdihdz.locktimer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun UpdateBanner(
    info: UpdateInfo,
    onInstall: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = "Đã có LockTimer ${info.versionName}",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = "Có phiên bản mới. Bạn có thể tải và cài đặt ngay trong ứng dụng.",
            style = MaterialTheme.typography.bodySmall
        )
        Row(modifier = Modifier.fillMaxWidth()) {
            Button(onClick = onInstall) {
                Text("Tải và cập nhật")
            }
        }
    }
}
