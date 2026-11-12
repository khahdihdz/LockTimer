package com.khahdihdz.locktimer

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import com.google.android.libraries.ads.mobile.sdk.MobileAds
import com.google.android.libraries.ads.mobile.sdk.initialization.InitializationConfig
import com.google.android.libraries.ads.mobile.sdk.banner.AdSize
import com.google.android.libraries.ads.mobile.sdk.banner.AdView
import com.google.android.libraries.ads.mobile.sdk.banner.BannerAd
import com.google.android.libraries.ads.mobile.sdk.banner.BannerAdRequest
import com.google.android.libraries.ads.mobile.sdk.common.AdLoadCallback
import com.google.android.libraries.ads.mobile.sdk.common.LoadAdError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

private object LockTimerAdsSdk {
    private val initializationMutex = Mutex()
    private var initializedAppId: String? = null

    suspend fun initialize(context: Context, appId: String) {
        initializationMutex.withLock {
            if (initializedAppId == appId) return

            // GMA Next-Gen requires initialization on a background thread.
            // Wait for its completion callback before issuing the first ad request.
            suspendCancellableCoroutine<Unit> { continuation ->
                MobileAds.initialize(
                    context.applicationContext,
                    InitializationConfig.Builder(appId).build()
                ) {
                    if (continuation.isActive) continuation.resume(Unit)
                }
            }
            initializedAppId = appId
        }
    }
}

/**
 * Free edition only. A compact adaptive banner is shown on the idle screen;
 * it is never shown while the countdown is running.
 */
@Composable
fun EditionAdSlot() {
    val context = LocalContext.current
    val activity = context as? Activity ?: return
    val appId = BuildConfig.ADMOB_APP_ID
    val adUnitId = BuildConfig.ADMOB_BANNER_UNIT_ID

    // A release build without real AdMob IDs stays ad-free instead of sending
    // invalid production requests. Debug builds use Google's official test IDs.
    if (appId.isBlank() || adUnitId.isBlank()) {
        Log.w("LockTimerAds", "Ads disabled: AdMob App ID or banner unit ID is blank")
        return
    }

    val adView = remember(activity) { AdView(activity) }

    LaunchedEffect(adView, appId, adUnitId) {
        try {
            withContext(Dispatchers.IO) {
                LockTimerAdsSdk.initialize(context, appId)
            }

            // LaunchedEffect resumes on the main thread; AdView loading and
            // view lifecycle operations must remain on the UI thread.
            val density = context.resources.displayMetrics.density
            val widthDp = (context.resources.displayMetrics.widthPixels / density).toInt()
            val adSize = AdSize.getLargeAnchoredAdaptiveBannerAdSize(activity, widthDp)
            val request = BannerAdRequest.Builder(adUnitId, adSize).build()

            adView.loadAd(
                request,
                object : AdLoadCallback<BannerAd> {
                    override fun onAdLoaded(ad: BannerAd) {
                        Log.i("LockTimerAds", "Free banner loaded successfully")
                    }

                    override fun onAdFailedToLoad(adError: LoadAdError) {
                        Log.e("LockTimerAds", "Free banner failed to load: $adError")
                    }
                }
            )
        } catch (error: Exception) {
            Log.e("LockTimerAds", "Unable to initialize or load ads", error)
        }
    }

    AndroidView(
        factory = { adView },
        modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp)
    )

    DisposableEffect(adView) {
        onDispose { adView.destroy() }
    }
}
