package com.khahdihdz.locktimer

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun LicenseScreen(
 onTrial:suspend ()->LicenseStatus,
 onActivate:suspend (String)->LicenseStatus,
 onRetry:suspend ()->LicenseStatus,
 onSuccess:()->Unit
) {
 var key by remember { mutableStateOf("") };var message by remember { mutableStateOf("Kích hoạt để tiếp tục hoặc dùng thử miễn phí 7 ngày.") }
 var busy by remember { mutableStateOf(false) };val scope=rememberCoroutineScope()
 fun run(action:suspend ()->LicenseStatus){if(busy)return;scope.launch{busy=true;val result=withContext(Dispatchers.IO){action()};message=result.message;busy=false;if(result.active)onSuccess()}}
 Column(Modifier.fillMaxSize().padding(24.dp),verticalArrangement=Arrangement.Center){
  Text("LockTimer",style=MaterialTheme.typography.headlineLarge)
  Spacer(Modifier.height(12.dp));Text("Ứng dụng yêu cầu kích hoạt. Bạn có thể bắt đầu dùng thử miễn phí 7 ngày.")
  Spacer(Modifier.height(16.dp))
  OutlinedTextField(value=key,onValueChange={key=it.uppercase()},label={Text("License Key")},singleLine=true,modifier=Modifier.fillMaxWidth())
  Spacer(Modifier.height(8.dp));Text(message)
  Spacer(Modifier.height(12.dp))
  Button(onClick={run{onActivate(key.trim())}},enabled=!busy&&key.isNotBlank(),modifier=Modifier.fillMaxWidth()){Text(if(busy)"Đang xử lý…" else "Kích hoạt License Key")}
  Button(onClick={run{onTrial()}},enabled=!busy,modifier=Modifier.fillMaxWidth()){Text("Dùng thử miễn phí 7 ngày")}
  OutlinedButton(onClick={run{onRetry()}},enabled=!busy,modifier=Modifier.fillMaxWidth()){Text("Kiểm tra lại")}
 }
}
