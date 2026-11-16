package com.khahdihdz.locktimer

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.libraries.ads.mobile.sdk.MobileAds
import com.google.android.libraries.ads.mobile.sdk.initialization.InitializationConfig
import com.google.android.libraries.ads.mobile.sdk.banner.AdSize
import com.google.android.libraries.ads.mobile.sdk.banner.AdView
import com.google.android.libraries.ads.mobile.sdk.banner.BannerAd
import com.google.android.libraries.ads.mobile.sdk.banner.BannerAdRequest
import com.google.android.libraries.ads.mobile.sdk.common.AdLoadCallback
import com.google.android.libraries.ads.mobile.sdk.common.LoadAdError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

private const val ADS_TAG = "LockTimerAds"
private const val MAX_LOAD_RETRIES = 4

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
            Log.i(ADS_TAG, "GMA Next-Gen initialized successfully")
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
    val activity = context as? Activity ?: run {
        Log.e(ADS_TAG, "Cannot load banner: current context is not an Activity")
        return
    }
    val appId = BuildConfig.ADMOB_APP_ID
    val adUnitId = BuildConfig.ADMOB_BANNER_UNIT_ID
    val adView = remember(activity) { AdView(activity) }
    val coroutineScope = rememberCoroutineScope()

    // Keep the Android view in the Compose hierarchy before starting the request.
    AndroidView(
        factory = { adView },
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 50.dp)
            .wrapContentHeight()
    )

    LaunchedEffect(adView, appId, adUnitId) {
        if (appId.isBlank() || adUnitId.isBlank()) {
            Log.e(
                ADS_TAG,
                "Ads disabled: blank AdMob IDs (appIdBlank=${appId.isBlank()}, unitIdBlank=${adUnitId.isBlank()})"
            )
            return@LaunchedEffect
        }

        Log.i(
            ADS_TAG,
            "Preparing Free banner (appIdSuffix=${appId.takeLast(6)}, unitIdSuffix=${adUnitId.takeLast(6)})"
        )

        try {
            withContext(Dispatchers.IO) {
                LockTimerAdsSdk.initialize(context, appId)
            }

            // Let AndroidView attach and receive its first measurement before loading.
            kotlinx.coroutines.yield()
            val density = context.resources.displayMetrics.density
            val widthDp = (context.resources.displayMetrics.widthPixels / density).toInt()
            if (widthDp <= 0) {
                Log.e(ADS_TAG, "Cannot load banner: calculated width is $widthDp dp")
                return@LaunchedEffect
            }

            val adSize = AdSize.getLargeAnchoredAdaptiveBannerAdSize(activity, widthDp)
            val request = BannerAdRequest.Builder(adUnitId, adSize).build()

            lateinit var loadBanner: (Int) -> Unit
            loadBanner = { attempt ->
                if (!adView.isAttachedToWindow) {
                    Log.w(ADS_TAG, "Banner view is not attached; waiting before request (attempt=$attempt)")
                    adView.post { loadBanner(attempt) }
                } else {
                    Log.i(ADS_TAG, "Requesting banner (attempt=${attempt + 1}/${MAX_LOAD_RETRIES + 1}, widthDp=$widthDp)")
                    try {
                        adView.loadAd(
                            request,
                            object : AdLoadCallback<BannerAd> {
                                override fun onAdLoaded(ad: BannerAd) {
                                    Log.i(ADS_TAG, "Free banner loaded successfully")
                                }

                                override fun onAdFailedToLoad(adError: LoadAdError) {
                                    Log.e(ADS_TAG, "Free banner failed: $adError")
                                    if (attempt < MAX_LOAD_RETRIES) {
                                        val waitMs = (30_000L * (attempt + 1)).coerceAtMost(300_000L)
                                        coroutineScope.launch {
                                            Log.w(ADS_TAG, "Retrying banner in ${waitMs / 1000}s")
                                            delay(waitMs)
                                            if (adView.isAttachedToWindow) {
                                                loadBanner(attempt + 1)
                                            } else {
                                                Log.w(ADS_TAG, "Banner view detached; retry skipped")
                                            }
                                        }
                                    } else {
                                        Log.e(ADS_TAG, "Banner retries exhausted")
                                    }
                                }
                            }
                        )
                    } catch (error: Exception) {
                        Log.e(ADS_TAG, "Banner request threw an exception", error)
                    }
                }
            }

            loadBanner(0)
        } catch (error: Exception) {
            Log.e(ADS_TAG, "Unable to initialize or load ads", error)
        }
    }

    DisposableEffect(adView) {
        onDispose {
            adView.destroy()
            Log.d(ADS_TAG, "Banner view destroyed")
        }
    }
}
