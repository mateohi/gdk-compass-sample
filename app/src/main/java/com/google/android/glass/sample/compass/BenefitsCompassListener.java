package com.google.android.glass.sample.compass;

public interface BenefitsCompassListener {
    void onOrientationChanged(OrientationManager orientationManager);

    void onLocationChanged(OrientationManager orientationManager);

    void onAccuracyChanged(OrientationManager orientationManager);
}

