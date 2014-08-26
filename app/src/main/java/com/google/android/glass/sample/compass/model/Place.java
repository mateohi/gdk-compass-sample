
package com.google.android.glass.sample.compass.model;

/**
 * This class represents a point of interest that has geographical coordinates (latitude and
 * longitude) and a name that is displayed to the user.
 */
public class Place {

    private final double mLatitude;
    private final double mLongitude;
    private final String mName;
    private final String description;

    public Place(double latitude, double longitude, String name, String description) {
        mLatitude = latitude;
        mLongitude = longitude;
        mName = name;
        this.description = description;
    }

    public double getLatitude() {
        return mLatitude;
    }

    public double getLongitude() {
        return mLongitude;
    }

    public String getName() {
        return mName;
    }

    public String getDescription() {
        return description;
    }
}
