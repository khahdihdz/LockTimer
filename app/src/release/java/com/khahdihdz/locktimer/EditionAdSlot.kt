package com.khahdihdz.locktimer

import android.app.Activity
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
import kotlinx.coroutines.withContext

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
    if (appId.isBlank() || adUnitId.isBlank()) return

    val adView = remember(activity) { AdView(activity) }

    LaunchedEffect(adView, appId, adUnitId) {
        try {
            withContext(Dispatchers.IO) {
                MobileAds.initialize(
                    context,
                    InitializationConfig.Builder(appId).build()
                ) { }
            }

            val density = context.resources.displayMetrics.density
            val widthDp = (context.resources.displayMetrics.widthPixels / density).toInt()
            val adSize = AdSize.getLargeAnchoredAdaptiveBannerAdSize(activity, widthDp)
            val request = BannerAdRequest.Builder(adUnitId, adSize).build()

            adView.loadAd(
                request,
                object : AdLoadCallback<BannerAd> {
                    override fun onAdLoaded(ad: BannerAd) {
                        Log.d("LockTimerAds", "Free banner loaded")
                    }

                    override fun onAdFailedToLoad(adError: LoadAdError) {
                        Log.w("LockTimerAds", "Free banner failed: $adError")
                    }
                }
            )
        } catch (error: Exception) {
            Log.w("LockTimerAds", "Unable to initialize ads", error)
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
