package com.google.android.glass.sample.compass;

import com.google.android.glass.sample.compass.util.MathUtils;

import android.hardware.GeomagneticField;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Looper;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Collects and communicates information about the user's current orientation and location.
 */
public class OrientationManager {

    /**
     * The minimum distance desired between location notifications.
     */
    private static final long METERS_BETWEEN_LOCATIONS = 2;

    /**
     * The minimum elapsed time desired between location notifications.
     */
    private static final long MILLIS_BETWEEN_LOCATIONS = TimeUnit.SECONDS.toMillis(3);

    /**
     * The maximum age of a location retrieved from the passive location provider before it is
     * considered too old to use when the compass first starts up.
     */
    private static final long MAX_LOCATION_AGE_MILLIS = TimeUnit.MINUTES.toMillis(30);

    /**
     * The sensors used by the compass are mounted in the movable arm on Glass. Depending on how
     * this arm is rotated, it may produce a displacement ranging anywhere from 0 to about 12
     * degrees. Since there is no way to know exactly how far the arm is rotated, we just split the
     * difference.
     */
    private static final int ARM_DISPLACEMENT_DEGREES = 6;

    private final SensorManager sensorManager;
    private final LocationManager locationManager;
    private final float[] rotationMatrix;
    private final float[] orientation;

    private boolean tracking;
    private float heading;
    private float pitch;
    private Location location;
    private BenefitsCompassListener listener;
    private GeomagneticField geomagneticField;
    private boolean hasInterference;

    /**
     * The sensor listener used by the orientation manager.
     */
    private SensorEventListener mSensorListener = new SensorEventListener() {

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {
            if (sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD) {
                hasInterference = (accuracy < SensorManager.SENSOR_STATUS_ACCURACY_HIGH);
                listener.onAccuracyChanged(OrientationManager.this);
            }
        }

        @Override
        public void onSensorChanged(SensorEvent event) {
            if (event.sensor.getType() == Sensor.TYPE_ROTATION_VECTOR) {
                // Get the current heading from the sensor, then notify the listeners of the
                // change.
                SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values);
                SensorManager.remapCoordinateSystem(rotationMatrix, SensorManager.AXIS_X,
                        SensorManager.AXIS_Z, rotationMatrix);
                SensorManager.getOrientation(rotationMatrix, orientation);

                // Store the pitch (used to display a message indicating that the user's head
                // angle is too steep to produce reliable results.
                pitch = (float) Math.toDegrees(orientation[1]);

                // Convert the heading (which is relative to magnetic north) to one that is
                // relative to true north, using the user's current location to compute this.
                float magneticHeading = (float) Math.toDegrees(orientation[0]);
                heading = MathUtils.mod(computeTrueNorth(magneticHeading), 360.0f)
                        - ARM_DISPLACEMENT_DEGREES;

                listener.onOrientationChanged(OrientationManager.this);
            }
        }
    };

    /**
     * The location listener used by the orientation manager.
     */
    private LocationListener mLocationListener = new LocationListener() {
        @Override
        public void onLocationChanged(Location location) {
            OrientationManager.this.location = location;
            updateGeomagneticField();
            listener.onLocationChanged(OrientationManager.this);
        }

        @Override
        public void onProviderDisabled(String provider) {
            // Don't need to do anything here.
        }

        @Override
        public void onProviderEnabled(String provider) {
            // Don't need to do anything here.
        }

        @Override
        public void onStatusChanged(String provider, int status, Bundle extras) {
            // Don't need to do anything here.
        }
    };

    /**
     * Initializes a new instance of {@code OrientationManager}, using the specified context to
     * access system services.
     */
    public OrientationManager(SensorManager sensorManager, LocationManager locationManager) {
        rotationMatrix = new float[16];
        orientation = new float[9];
        this.sensorManager = sensorManager;
        this.locationManager = locationManager;
    }

    /**
     * Starts tracking the user's location and orientation.
     */
    public void start() {
        if (!tracking) {
            sensorManager.registerListener(mSensorListener,
                    sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR),
                    SensorManager.SENSOR_DELAY_UI);

            // The rotation vector sensor doesn't give us accuracy updates, so we observe the
            // magnetic field sensor solely for those.
            sensorManager.registerListener(mSensorListener,
                    sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD),
                    SensorManager.SENSOR_DELAY_UI);

            Location lastLocation = locationManager
                    .getLastKnownLocation(LocationManager.PASSIVE_PROVIDER);
            if (lastLocation != null) {
                long locationAge = lastLocation.getTime() - System.currentTimeMillis();
                if (locationAge < MAX_LOCATION_AGE_MILLIS) {
                    location = lastLocation;
                    updateGeomagneticField();
                }
            }

            Criteria criteria = new Criteria();
            criteria.setAccuracy(Criteria.ACCURACY_FINE);
            criteria.setBearingRequired(false);
            criteria.setSpeedRequired(false);

            List<String> providers =
                    locationManager.getProviders(criteria, true /* enabledOnly */);
            for (String provider : providers) {
                locationManager.requestLocationUpdates(provider,
                        MILLIS_BETWEEN_LOCATIONS, METERS_BETWEEN_LOCATIONS, mLocationListener,
                        Looper.getMainLooper());
            }

            tracking = true;
        }
    }

    /**
     * Stops tracking the user's location and orientation. Listeners will no longer be notified of
     * these events.
     */
    public void stop() {
        if (tracking) {
            sensorManager.unregisterListener(mSensorListener);
            locationManager.removeUpdates(mLocationListener);
            tracking = false;
        }
    }

    public boolean hasInterference() {
        return hasInterference;
    }

    public boolean hasLocation() {
        return location != null;
    }

    public float getHeading() {
        return heading;
    }

    public float getPitch() {
        return pitch;
    }

    public Location getLocation() {
        return location;
    }

    public void setBenefitsCompassListener(BenefitsCompassListener benefitsCompassListener) {
        this.listener = benefitsCompassListener;
    }

    /**
     * Updates the cached instance of the geomagnetic field after a location change.
     */
    private void updateGeomagneticField() {
        geomagneticField = new GeomagneticField((float) location.getLatitude(),
                (float) location.getLongitude(), (float) location.getAltitude(),
                location.getTime());
    }

    /**
     * Use the magnetic field to compute true (geographic) north from the specified heading
     * relative to magnetic north.
     *
     * @param heading the heading (in degrees) relative to magnetic north
     * @return the heading (in degrees) relative to true north
     */
    private float computeTrueNorth(float heading) {
        if (geomagneticField != null) {
            return heading + geomagneticField.getDeclination();
        } else {
            return heading;
        }
    }
}
