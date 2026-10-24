package io.fleetbase.navigator.location

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import io.fleetbase.navigator.R
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/** GPS-001.1: live positions only. No historical replay until FleetOps supports it. */
class DriverLocationService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private val client by lazy { LocationServices.getFusedLocationProviderClient(this) }
    private val executor = Executors.newSingleThreadExecutor()
    private var callback: LocationCallback? = null
    private var active: TrackingSession.Credentials? = null
    private var sending = false
    private var lastAttempt = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    @SuppressLint("MissingPermission")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val session = TrackingState.session.current()
        if (session == null) { stopSelf(); return START_NOT_STICKY }
        if (active == session && callback != null) return START_NOT_STICKY
        active = session
        callback?.let { client.removeLocationUpdates(it) }
        if (!hasPermission()) { fail(session, "permission_required"); return START_NOT_STICKY }

        try {
            val manager = getSystemService(NotificationManager::class.java)
            if (Build.VERSION.SDK_INT >= 26) {
                manager.createNotificationChannel(NotificationChannel(CHANNEL, getString(R.string.tracking_channel), NotificationManager.IMPORTANCE_LOW))
            }
            val launch = packageManager.getLaunchIntentForPackage(packageName)
            val contentIntent = launch?.let { PendingIntent.getActivity(this, 0, it, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE) }
            val notification = NotificationCompat.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_tracking_location)
                .setContentTitle(getString(R.string.tracking_title))
                .setContentText(getString(R.string.tracking_body))
                .setContentIntent(contentIntent)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .build()
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification,
                if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0)

            val listener = object : LocationCallback() {
                override fun onLocationResult(result: LocationResult) {
                    if (!TrackingState.session.owns(session)) return
                    result.lastLocation?.let { onLocation(session, it) }
                }
            }
            callback = listener
            val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 10_000L)
                .setMinUpdateIntervalMillis(5_000L)
                .setMinUpdateDistanceMeters(10f)
                .setMaxUpdateAgeMillis(0L)
                .build()
            client.requestLocationUpdates(request, listener, Looper.getMainLooper())
                .addOnSuccessListener {
                    if (TrackingState.session.owns(session)) TrackingState.status = "tracking"
                    else client.removeLocationUpdates(listener)
                }
                .addOnFailureListener { fail(session, "location_unavailable") }
            handler.removeCallbacks(permissionCheck)
            handler.postDelayed(permissionCheck, 5_000L)
        } catch (_: Exception) { fail(session, "service_start_failed") }
        // Never resurrect a session after reboot/process death using a persisted bearer token.
        return START_NOT_STICKY
    }

    private fun hasPermission() = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private val permissionCheck = object : Runnable {
        override fun run() {
            val session = active ?: return
            if (!TrackingState.session.owns(session)) { stopSelf(); return }
            if (!hasPermission()) { fail(session, "permission_required"); return }
            handler.postDelayed(this, 5_000L)
        }
    }

    private fun onLocation(session: TrackingSession.Credentials, point: Location) {
        if (!point.latitude.isFinite() || !point.longitude.isFinite() ||
            point.latitude !in -90.0..90.0 || point.longitude !in -180.0..180.0 ||
            !point.hasAccuracy() || !point.accuracy.isFinite() || point.accuracy > 100f) return
        // Do not replay stale fused-provider fixes into the current-position endpoint.
        if (SystemClock.elapsedRealtimeNanos() - point.elapsedRealtimeNanos > 30_000_000_000L) return
        TrackingState.location = TrackingState.encode(point)
        val now = SystemClock.elapsedRealtime()
        if (sending || now - lastAttempt < 5_000L) return
        lastAttempt = now
        sending = true
        val payload = JSONObject(TrackingState.location!!).getJSONObject("coords").toString()
        executor.execute {
            var connection: HttpURLConnection? = null
            var failure: String? = null
            var delivered = false
            try {
                if (TrackingState.session.owns(session)) {
                    val http = URL(session.endpoint).openConnection() as HttpURLConnection
                    connection = http
                    http.apply {
                        instanceFollowRedirects = false // Never forward the bearer to another URL.
                        requestMethod = "POST"
                        connectTimeout = 10_000
                        readTimeout = 10_000
                        doOutput = true
                        setRequestProperty("Authorization", "Bearer ${session.token}")
                        setRequestProperty("Content-Type", "application/json")
                        setRequestProperty("Accept", "application/json")
                        setRequestProperty("User-Agent", "@fleetbase/navigator-app")
                    }
                    TrackingState.connection = http
                    if (TrackingState.session.owns(session)) {
                        http.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }
                        val code = http.responseCode
                        delivered = code in 200..299
                        failure = when {
                            code == 401 || code == 403 -> "session_expired"
                            code in 300..499 && code != 408 && code != 429 -> "endpoint_rejected"
                            else -> null
                        }
                    }
                }
            } catch (_: Exception) {
                // Live-only milestone: report delivery failure; send a fresh fix on the next update.
            } finally {
                connection?.disconnect()
                handler.post {
                    sending = false
                    if (TrackingState.connection === connection) TrackingState.connection = null
                    if (TrackingState.session.owns(session)) {
                        TrackingState.delivery = if (delivered) "sent" else "unavailable"
                        if (delivered) TrackingState.lastSentAt = System.currentTimeMillis()
                        failure?.let { fail(session, it) }
                    }
                }
            }
        }
    }

    private fun fail(session: TrackingSession.Credentials, reason: String) {
        if (!TrackingState.session.owns(session)) return
        TrackingState.clear(reason)
        stopSelf()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        callback?.let { client.removeLocationUpdates(it) }
        active?.let { if (TrackingState.session.owns(it)) TrackingState.clear() }
        executor.shutdownNow()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL = "driver_location"
        private const val NOTIFICATION_ID = 7310
    }
}
