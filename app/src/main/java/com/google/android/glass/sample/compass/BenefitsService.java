package com.google.android.glass.sample.compass;

import com.google.android.glass.sample.compass.model.Landmarks;
import com.google.android.glass.sample.compass.util.MathUtils;
import com.google.android.glass.timeline.LiveCard;
import com.google.android.glass.timeline.LiveCard.PublishMode;

import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.hardware.SensorManager;
import android.location.LocationManager;
import android.os.Binder;
import android.os.IBinder;
import android.speech.tts.TextToSpeech;
import android.util.Log;

public class BenefitsService extends Service {

    private static final String TAG = BenefitsService.class.getSimpleName();

    private final BenefitsBinder binder = new BenefitsBinder();

    private OrientationManager orientationManager;
    private Landmarks landmarks;
    private TextToSpeech speech;

    private LiveCard liveCard;
    private BenefitsCompassRenderer benefitsCompassRenderer;

    @Override
    public void onCreate() {
        super.onCreate();

        speech = new TextToSpeech(this, new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int status) {
                Log.i(TAG, "Text to speech initialized ...");
            }
        });

        SensorManager sensorManager =
                (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        LocationManager locationManager =
                (LocationManager) getSystemService(Context.LOCATION_SERVICE);

        orientationManager = new OrientationManager(sensorManager, locationManager);
        landmarks = new Landmarks(this);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (liveCard == null) {
            liveCard = new LiveCard(this, TAG);
            benefitsCompassRenderer = new BenefitsCompassRenderer(this, orientationManager, landmarks);

            liveCard.setDirectRenderingEnabled(true).getSurfaceHolder().addCallback(benefitsCompassRenderer);

            // Display the options menu when the live card is tapped.
            Intent menuIntent = new Intent(this, BenefitsMenuActivity.class);
            menuIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            liveCard.setAction(PendingIntent.getActivity(this, 0, menuIntent, 0));
            liveCard.attach(this);
            liveCard.publish(PublishMode.REVEAL);
        } else {
            liveCard.navigate();
        }

        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        if (liveCard != null && liveCard.isPublished()) {
            liveCard.unpublish();
            liveCard = null;
        }

        speech.shutdown();

        speech = null;
        orientationManager = null;
        landmarks = null;

        super.onDestroy();
    }

    /**
     * A binder that gives other components access to the speech capabilities provided by the
     * service.
     */
    public class BenefitsBinder extends Binder {
        /**
         * Read the current observed benefit description aloud.
         */
        public void readBenefitDescription() {
            float heading = orientationManager.getHeading();

            Resources res = getResources();
            String[] spokenDirections = res.getStringArray(R.array.spoken_directions);
            String directionName = spokenDirections[MathUtils.getHalfWindIndex(heading)];

            int roundedHeading = Math.round(heading);
            int headingFormat;
            if (roundedHeading == 1) {
                headingFormat = R.string.spoken_heading_format_one;
            } else {
                headingFormat = R.string.spoken_heading_format;
            }

            String headingText = res.getString(headingFormat, roundedHeading, directionName);
            speech.speak(headingText, TextToSpeech.QUEUE_FLUSH, null);
        }
    }
}
