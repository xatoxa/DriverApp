package com.example.driverapp;

import android.location.Location;
import android.location.LocationListener;
import android.os.Bundle;

import androidx.annotation.NonNull;

public class DriverLocationListener implements LocationListener {
    private DriverLocListenerInterface driverLocLisInterface;

    @Override
    public void onLocationChanged(@NonNull Location location) {
        driverLocLisInterface.onLocationChanged(location);
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {

    }

    @Override
    public void onProviderEnabled(@NonNull String provider) {

    }

    @Override
    public void onProviderDisabled(@NonNull String provider) {

    }

    public void setDriverLocLisInterface(DriverLocListenerInterface driverLocLisInterface) {
        this.driverLocLisInterface = driverLocLisInterface;
    }
}
