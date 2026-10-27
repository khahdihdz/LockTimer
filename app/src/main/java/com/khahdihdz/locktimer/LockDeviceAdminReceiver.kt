package com.khahdihdz.locktimer

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent

/** Device Admin của app — chỉ khai báo policy force-lock, dùng cho lockNow(). */
class LockDeviceAdminReceiver : DeviceAdminReceiver() {
    override fun onEnabled(context: Context, intent: Intent) {
        // Không cần hành động gì thêm
    }
}
