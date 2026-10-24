package io.fleetbase.navigator.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.facebook.react.bridge.LifecycleEventListener
import com.facebook.react.common.LifecycleState
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.UiThreadUtil
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import io.fleetbase.navigator.BuildConfig
import java.net.URI

class DriverLocationModule(private val context: ReactApplicationContext) : NativeDriverLocationSpec(context), LifecycleEventListener {
    private val pending = mutableMapOf<CancellationTokenSource, Promise>()
    init { context.addLifecycleEventListener(this) }
    override fun getName() = NAME

    override fun start(endpoint: String, token: String, promise: Promise) {
        UiThreadUtil.runOnUiThread {
            try {
                val uri = URI(endpoint)
                require(uri.host != null && uri.userInfo == null && uri.fragment == null)
                require(uri.scheme == "https" || (BuildConfig.DEBUG && uri.scheme == "http"))
                require(token.isNotBlank() && !token.contains('\n') && !token.contains('\r'))
                require(context.lifecycleState == LifecycleState.RESUMED) { "Open the app before starting tracking" }
                require(ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED)
                require(GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS)
                val current = TrackingState.session.current()
                if (current?.endpoint == endpoint && current.token == token) { promise.resolve(null); return@runOnUiThread }
                TrackingState.clear()
                TrackingState.session.start(endpoint, token)
                TrackingState.status = "starting"
                ContextCompat.startForegroundService(context, Intent(context, DriverLocationService::class.java))
                promise.resolve(null)
            } catch (_: Exception) {
                TrackingState.clear("start_rejected")
                context.stopService(Intent(context, DriverLocationService::class.java))
                promise.reject("START_REJECTED", "Tracking requires an active Android app, precise location, Google Play Services and a valid endpoint (HTTPS in release).")
            }
        }
    }

    override fun stop(promise: Promise) {
        UiThreadUtil.runOnUiThread {
            TrackingState.clear()
            context.stopService(Intent(context, DriverLocationService::class.java))
            cancelPositions()
            promise.resolve(null)
        }
    }

    override fun getStatus(promise: Promise) {
        UiThreadUtil.runOnUiThread { promise.resolve(TrackingState.snapshot()) }
    }

    @SuppressLint("MissingPermission")
    override fun getCurrentPosition(promise: Promise) {
        UiThreadUtil.runOnUiThread {
            if (context.lifecycleState != LifecycleState.RESUMED || ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                promise.reject("PERMISSION_REQUIRED", "Open the app and allow precise location.")
                return@runOnUiThread
            }
            val cancellation = CancellationTokenSource()
            pending[cancellation] = promise
            try {
                val request = CurrentLocationRequest.Builder().setPriority(Priority.PRIORITY_HIGH_ACCURACY)
                    .setMaxUpdateAgeMillis(5_000L).setDurationMillis(15_000L).build()
                LocationServices.getFusedLocationProviderClient(context).getCurrentLocation(request, cancellation.token)
                    .addOnSuccessListener { location ->
                        pending.remove(cancellation)?.let {
                            if (location == null) it.reject("LOCATION_TIMEOUT", "No current location available.")
                            else it.resolve(TrackingState.encode(location))
                        }
                    }.addOnFailureListener {
                        pending.remove(cancellation)?.reject("LOCATION_UNAVAILABLE", "Unable to obtain location.")
                    }
            } catch (_: Exception) {
                pending.remove(cancellation)?.reject("LOCATION_UNAVAILABLE", "Unable to obtain location.")
            }
        }
    }

    private fun cancelPositions() {
        val requests = pending.toMap()
        pending.clear()
        requests.forEach { (token, promise) -> token.cancel(); promise.reject("LOCATION_CANCELLED", "Location request cancelled.") }
    }

    override fun onHostResume() {}
    override fun onHostPause() { cancelPositions() }
    override fun onHostDestroy() { cancelPositions() }
    override fun invalidate() {
        context.removeLifecycleEventListener(this)
        UiThreadUtil.runOnUiThread {
            cancelPositions()
            TrackingState.clear()
            context.stopService(Intent(context, DriverLocationService::class.java))
        }
        super.invalidate()
    }

    companion object { const val NAME = "NativeDriverLocation" }
}
