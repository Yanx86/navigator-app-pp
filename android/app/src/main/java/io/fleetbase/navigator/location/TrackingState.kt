package io.fleetbase.navigator.location

import android.location.Location
import org.json.JSONObject
import java.net.HttpURLConnection

internal object TrackingState {
    val session = TrackingSession()
    @Volatile var status = "stopped"
    @Volatile var error: String? = null
    @Volatile var delivery = "idle"
    @Volatile var lastSentAt: Long? = null
    @Volatile var location: String? = null
    @Volatile var connection: HttpURLConnection? = null

    fun clear(reason: String? = null) {
        session.stop()
        connection?.disconnect()
        connection = null
        status = if (reason == null) "stopped" else "error"
        error = reason
        delivery = "idle"
        lastSentAt = null
        location = null
    }

    fun snapshot(): String = JSONObject().apply {
        put("status", status)
        put("error", error ?: JSONObject.NULL)
        put("delivery", delivery)
        put("lastSentAt", lastSentAt ?: JSONObject.NULL)
        put("location", location?.let { JSONObject(it) } ?: JSONObject.NULL)
    }.toString()

    fun encode(location: Location): String = JSONObject().apply {
        put("timestamp", location.time.toDouble())
        put("coords", JSONObject().apply {
            put("latitude", location.latitude)
            put("longitude", location.longitude)
            put("accuracy", location.accuracy.toDouble())
            put("altitude", if (location.hasAltitude()) location.altitude else JSONObject.NULL)
            put("heading", if (location.hasBearing()) location.bearing.toDouble() else JSONObject.NULL)
            put("speed", if (location.hasSpeed()) location.speed.toDouble() else JSONObject.NULL)
        })
    }.toString()
}
