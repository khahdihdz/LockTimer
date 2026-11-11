package com.khahdihdz.locktimer

import android.content.Context
import android.provider.Settings
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class LicenseStatus(val active:Boolean,val message:String,val expiresAt:Long?=null,val trial:Boolean=false)
class LicenseManager(private val context:Context) {
 private val deviceId get()=Settings.Secure.getString(context.contentResolver,Settings.Secure.ANDROID_ID) ?: "unknown"
 fun check()=post("/api/validate",JSONObject().put("deviceId",deviceId))
 fun trial()=post("/api/trial",JSONObject().put("deviceId",deviceId))
 fun activate(key:String)=post("/api/activate",JSONObject().put("deviceId",deviceId).put("key",key))
 private fun post(path:String,body:JSONObject):LicenseStatus {
  val base=BuildConfig.LICENSE_API_URL.trimEnd('/')
  if(base.isBlank())return LicenseStatus(false,"Chưa cấu hình License API URL trong app/build.gradle.kts.")
  return try {
   val c=(URL(base+path).openConnection() as HttpURLConnection).apply { requestMethod="POST";connectTimeout=12000;readTimeout=12000;doOutput=true;setRequestProperty("Content-Type","application/json") }
   c.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
   val stream=if(c.responseCode in 200..299)c.inputStream else c.errorStream
   val data=JSONObject(stream.bufferedReader().use{it.readText()});c.disconnect()
   val active=data.optBoolean("active");val exp=if(data.isNull("expiresAt"))null else data.optLong("expiresAt")
   val msg=if(active)if(data.optBoolean("trial"))"Dùng thử 7 ngày" else "License Key đã kích hoạt" else when(data.optString("code")){"trial_expired"->"Đã hết 7 ngày dùng thử.";"device_limit"->"License Key đã đạt giới hạn thiết bị.";"invalid_or_expired"->"Key không hợp lệ, hết hạn hoặc đã bị thu hồi.";else->data.optString("message","Chưa kích hoạt.")}
   LicenseStatus(active,msg,exp,data.optBoolean("trial"))
  }catch(e:Exception){LicenseStatus(false,"Không kết nối được máy chủ: ${e.message ?: "lỗi mạng"}")}
 }
}
