/**
 * BackgroundLocationService used for tracking user location in the background.
 *
 * @author rohit.g
 */
package com.example.saksham.lazymoney;

import android.Manifest;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.app.Service;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.os.PowerManager;
import android.support.v4.app.ActivityCompat;
import android.util.Log;
import android.widget.Toast;

import java.text.DateFormat;
import java.util.Date;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.common.api.GoogleApiClient.*;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.*;


public class BackgroundLocationService extends Service implements ConnectionCallbacks, OnConnectionFailedListener {
    IBinder mBinder = new LocalBinder();
    private String TAG = "BgLocService";
    private GoogleApiClient mGoogleApiClient;
    private LocationRequest mLocationRequest;
    private PowerManager.WakeLock mWakeLock;
    // Flag that indicates if a request is underway.
    private boolean mInProgress;

    private Boolean servicesAvailable = false;

    public class LocalBinder extends Binder {
        public BackgroundLocationService getServerInstance() {
            return BackgroundLocationService.this;
        }
    }

    @Override
    public void onConnectionSuspended(int i) {
        // Turn off the request flag
        mInProgress = false;
        // Destroy the current location client
        mGoogleApiClient = null;
        // Display the connection status
        // Toast.makeText(this, DateFormat.getDateTimeInstance().format(new Date()) + ": Disconnected. Please re-connect.", Toast.LENGTH_SHORT).show();
        Log.d(TAG,DateFormat.getDateTimeInstance().format(new Date()) + ": Disconnected");
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "Service Created");
        mInProgress = false;
        // Create the LocationRequest object
        mLocationRequest = LocationRequest.create();
        // Use high accuracy
        mLocationRequest.setPriority(LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY);
        // Set the update interval to 5 seconds
        mLocationRequest.setInterval(Constants.UPDATE_INTERVAL);
        // Set the fastest update interval to 1 second
        mLocationRequest.setFastestInterval(Constants.FASTEST_INTERVAL);

        servicesAvailable = servicesConnected();

        buildGoogleApiClient();
    }

    private boolean servicesConnected() {
        // Check that Google Play services is available
        int resultCode = GooglePlayServicesUtil.isGooglePlayServicesAvailable(this);

        // If Google Play services is available return true
        return ConnectionResult.SUCCESS == resultCode;
    }

    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);
        Log.d(TAG, "On Start");
        PowerManager mgr = (PowerManager)getSystemService(Context.POWER_SERVICE);
        /*
        WakeLock is reference counted so we don't want to create multiple WakeLocks. So do a check before initializing and acquiring.
        This will fix the "java.lang.Exception: WakeLock finalized while still held: MyWakeLock" error that you may find.
        */
        if (this.mWakeLock == null) {
            this.mWakeLock = mgr.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MyWakeLock");
        }
        if (!this.mWakeLock.isHeld()) {
            this.mWakeLock.acquire();
        }
        if (!servicesAvailable || mGoogleApiClient.isConnected() || mInProgress)
            return START_STICKY;

        buildGoogleApiClient();

        if (!mGoogleApiClient.isConnected() || !mGoogleApiClient.isConnecting() && !mInProgress) {
            Log.d(TAG, DateFormat.getDateTimeInstance().format(new Date()) + ": Started");
            mInProgress = true;
            mGoogleApiClient.connect();
        }

        return START_STICKY;
    }

	/*
     * Create a new location client, using the enclosing class to
     * handle callbacks.
     */

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public void onDestroy() {
        // Turn off the request flag
        this.mInProgress = false;
        if (this.servicesAvailable && this.mGoogleApiClient != null) {
            this.mGoogleApiClient.unregisterConnectionCallbacks(this);
            this.mGoogleApiClient.unregisterConnectionFailedListener(this);
            this.mGoogleApiClient.disconnect();
            this.mGoogleApiClient = null;
        }

        // Display the connection status
        Toast.makeText(this, DateFormat.getDateTimeInstance().format(new Date()) + ": Disconnected. Please re-connect.", Toast.LENGTH_SHORT).show();
        Log.d(TAG, DateFormat.getDateTimeInstance().format(new Date()) + ": Stopped");

        if (this.mWakeLock != null) {
            this.mWakeLock.release();
            this.mWakeLock = null;
        }
        super.onDestroy();
    }

    /*
     * Called by Location Services when the request to connect the
     * client finishes successfully. At this point, you can
     * request the current location or start periodic updates
     */
    @Override
    public void onConnected(Bundle bundle) {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return;
        }
        Intent intent = new Intent(this, LocationReceiver.class);
        PendingIntent pendingIntent = PendingIntent
                .getBroadcast(this, 54321, intent, PendingIntent.FLAG_CANCEL_CURRENT);
        LocationServices.FusedLocationApi.requestLocationUpdates(this.mGoogleApiClient, mLocationRequest, pendingIntent);
        Log.d(TAG, DateFormat.getDateTimeInstance().format(new Date()) + ": Connected");
    }

    /*
     * Called by Location Services if the attempt to
     * Location Services fails.
     */
    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {
    	mInProgress = false;
    	Log.e(TAG, "Connection Failed");
        /*
         * Google Play services can resolve some errors it detects.
         * If the error has a resolution, try sending an Intent to
         * start a Google Play services activity that can resolve
         * error.
         */
        if (connectionResult.hasResolution()) {

        // If no resolution is available, display an error dialog
        } else {

        }
    }

    protected synchronized void buildGoogleApiClient() {
        if (this.mGoogleApiClient == null) {
            Log.i("BgLoc", "Building GoogleApiClient");
            this.mGoogleApiClient = new GoogleApiClient.Builder(this)
                    .addConnectionCallbacks(this)
                    .addOnConnectionFailedListener(this)
                    .addApi(LocationServices.API)
                    .addApi(ActivityRecognition.API)
                    .build();
        }
    }
    
}
