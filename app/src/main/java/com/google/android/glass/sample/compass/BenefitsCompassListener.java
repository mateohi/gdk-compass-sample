package com.google.android.glass.sample.compass;

/**
 * Classes should implement this interface if they want to be notified of changes in the user's
 * location, orientation, or the accuracy of the compass.
 */
public interface BenefitsCompassListener {
    /**
     * Called when the user's orientation changes.
     *
     * @param orientationManager the orientation manager that detected the change
     */
    void onOrientationChanged(OrientationManager orientationManager);

    /**
     * Called when the user's location changes.
     *
     * @param orientationManager the orientation manager that detected the change
     */
    void onLocationChanged(OrientationManager orientationManager);

    /**
     * Called when the accuracy of the compass changes.
     *
     * @param orientationManager the orientation manager that detected the change
     */
    void onAccuracyChanged(OrientationManager orientationManager);
}

