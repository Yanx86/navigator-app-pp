package io.fleetbase.navigator.location

/** Pure session boundary: stale callbacks must never mutate a replacement session. */
internal class TrackingSession {
    data class Credentials(val generation: Long, val endpoint: String, val token: String)

    private var generation = 0L
    private var credentials: Credentials? = null

    @Synchronized fun start(endpoint: String, token: String): Credentials {
        return Credentials(++generation, endpoint, token).also { credentials = it }
    }

    @Synchronized fun current(): Credentials? = credentials
    @Synchronized fun owns(candidate: Credentials): Boolean = credentials?.generation == candidate.generation
    @Synchronized fun stop() { generation++; credentials = null }
}
