package com.khahdihdz.locktimer

import android.app.Application
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Trạng thái UI của đếm ngược. */
data class TimerUiState(
    val running: Boolean = false,
    val remainingMillis: Long = 0L,
    val totalMillis: Long = 0L,
)

class TimerViewModel(app: Application) : AndroidViewModel(app) {

    private val _state = MutableStateFlow(TimerUiState())
    val state: StateFlow<TimerUiState> = _state.asStateFlow()

    init {
        val context = getApplication<Application>()
        // Ứng dụng có thể bị kill và mở lại trong lúc đếm: khôi phục trạng thái.
        if (TimerRepository.isRunning(context)) {
            syncState()
        }
        viewModelScope.launch {
            while (true) {
                delay(1_000L)
                if (TimerRepository.isRunning(getApplication())) {
                    syncState()
                } else if (_state.value.running) {
                    _state.update { it.copy(running = false, remainingMillis = 0L) }
                }
            }
        }
    }

    private fun syncState() {
        val context = getApplication<Application>()
        val remaining =
            (TimerRepository.endTimeRtc(context) - System.currentTimeMillis()).coerceAtLeast(0L)
        _state.update {
            it.copy(
                running = true,
                remainingMillis = remaining,
                totalMillis = TimerRepository.durationMillis(context)
            )
        }
    }

    fun isAdminActive(): Boolean {
        val context = getApplication<Application>()
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        return dpm.isAdminActive(ComponentName(context, LockDeviceAdminReceiver::class.java))
    }

    fun start(durationMinutes: Int) {
        val durationMillis = durationMinutes * 60_000L
        if (durationMillis <= 0L) return

        val context = getApplication<Application>()
        val endRtc = System.currentTimeMillis() + durationMillis
        TimerRepository.start(context, durationMillis)
        // Đặt alarm ngay lập tức để an toàn nếu service start thất bại.
        AlarmScheduler.scheduleLockAlarm(context, endRtc)

        val intent = Intent(context, CountdownService::class.java)
            .setAction(CountdownService.ACTION_START)
            .putExtra(CountdownService.EXTRA_DURATION_MILLIS, durationMillis)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
        syncState()
    }

    fun cancel() {
        val context = getApplication<Application>()
        AlarmScheduler.cancelLockAlarm(context)
        TimerRepository.stop(context)
        val intent = Intent(context, CountdownService::class.java)
            .setAction(CountdownService.ACTION_CANCEL)
        runCatching { context.startService(intent) }
        _state.update { it.copy(running = false, remainingMillis = 0L) }
    }
}
