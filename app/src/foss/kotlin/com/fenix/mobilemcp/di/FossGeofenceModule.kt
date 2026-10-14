package com.fenix.mobilemcp.di

import com.fenix.mobilemcp.services.channel.GeofenceChannelController
import com.fenix.mobilemcp.services.channel.NoOpGeofenceChannelController
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** foss flavor: binds the inert geofence channel controller (no GeofenceManager exists). */
@Module
@InstallIn(SingletonComponent::class)
abstract class FossGeofenceModule {
    @Binds
    @Singleton
    abstract fun bindGeofenceChannelController(impl: NoOpGeofenceChannelController): GeofenceChannelController
}
