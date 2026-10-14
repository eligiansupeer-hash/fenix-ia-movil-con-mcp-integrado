package com.fenix.mobilemcp.services.channel.geofence

import com.fenix.mobilemcp.data.model.GeofenceZone

interface GeofenceManager {
    suspend fun addGeofence(zone: GeofenceZone): Result<Unit>

    suspend fun removeGeofence(zoneId: String): Result<Unit>

    suspend fun removeAllGeofences(): Result<Unit>

    suspend fun syncGeofences(zones: List<GeofenceZone>): Result<Unit>
}
