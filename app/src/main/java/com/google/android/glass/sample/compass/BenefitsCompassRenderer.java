package com.google.android.glass.sample.compass;

import com.google.android.glass.sample.compass.model.Landmarks;
import com.google.android.glass.sample.compass.model.Place;
import com.google.android.glass.timeline.DirectRenderingCallback;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.location.Location;
import android.os.SystemClock;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.SurfaceHolder;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * The surface callback that provides the rendering logic for the compass live card. This callback
 * also manages the lifetime of the sensor and location event listeners (through
 * {@link OrientationManager}) so that tracking only occurs when the card is visible.
 */
public class BenefitsCompassRenderer implements DirectRenderingCallback {

    private static final String TAG = BenefitsCompassRenderer.class.getSimpleName();

    /**
     * The (absolute) pitch angle beyond which the compass will display a message telling the user
     * that his or her head is at too steep an angle to be reliable.
     */
    private static final float TOO_STEEP_PITCH_DEGREES = 70.0f;

    /** The refresh rate, in frames per second, of the compass. */
    private static final int REFRESH_RATE_FPS = 45;

    /** The duration, in milliseconds, of one frame. */
    private static final long FRAME_TIME_MILLIS = TimeUnit.SECONDS.toMillis(1) / REFRESH_RATE_FPS;

    private SurfaceHolder surfaceHolder;
    private boolean isTooSteep;
    private boolean hasMagneticInterference;
    private RenderThread renderThread;
    private int surfaceWidth;
    private int surfaceHeight;

    private boolean renderingPaused;

    private final FrameLayout frameLayout;
    private final BenefitsCompassView benefitsCompassView;
    private final RelativeLayout tipsContainer;
    private final TextView tipsView;
    private final OrientationManager orientationManager;
    private final Landmarks landmarks;

    private final BenefitsCompassListener benefitsCompassListener = new BenefitsCompassListener() {

        @Override
        public void onOrientationChanged(OrientationManager orientationManager) {
            benefitsCompassView.setHeading(orientationManager.getHeading());

            boolean oldTooSteep = isTooSteep;
            isTooSteep = (Math.abs(orientationManager.getPitch()) > TOO_STEEP_PITCH_DEGREES);
            if (isTooSteep != oldTooSteep) {
                updateTipsView();
            }
        }

        @Override
        public void onLocationChanged(OrientationManager orientationManager) {
            Location location = orientationManager.getLocation();
            List<Place> places = landmarks.getNearbyLandmarks(
                    location.getLatitude(), location.getLongitude());
            benefitsCompassView.setNearbyPlaces(places);
        }

        @Override
        public void onAccuracyChanged(OrientationManager orientationManager) {
            hasMagneticInterference = orientationManager.hasInterference();
            updateTipsView();
        }
    };

    /**
     * Creates a new instance of the {@code CompassRenderer} with the specified context,
     * orientation manager, and landmark collection.
     */
    public BenefitsCompassRenderer(Context context, OrientationManager orientationManager,
                                   Landmarks landmarks) {
        LayoutInflater inflater = LayoutInflater.from(context);
        frameLayout = (FrameLayout) inflater.inflate(R.layout.compass, null);
        frameLayout.setWillNotDraw(false);

        benefitsCompassView = (BenefitsCompassView) frameLayout.findViewById(R.id.compass);
        tipsContainer = (RelativeLayout) frameLayout.findViewById(R.id.tips_container);
        tipsView = (TextView) frameLayout.findViewById(R.id.tips_view);

        this.orientationManager = orientationManager;
        this.landmarks = landmarks;

        benefitsCompassView.setOrientationManager(this.orientationManager);
        this.orientationManager.setBenefitsCompassListener(benefitsCompassListener);
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        surfaceWidth = width;
        surfaceHeight = height;
        doLayout();
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        // The creation of a new Surface implicitly resumes the rendering.
        renderingPaused = false;
        surfaceHolder = holder;
        updateRenderingState();
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        surfaceHolder = null;
        updateRenderingState();
    }

    @Override
    public void renderingPaused(SurfaceHolder holder, boolean paused) {
        renderingPaused = paused;
        updateRenderingState();
    }

    private void updateRenderingState() {
        boolean shouldRender = (surfaceHolder != null) && !renderingPaused;
        boolean isRendering = (renderThread != null);

        if (shouldRender != isRendering) {
            if (shouldRender) {
                orientationManager.start();

                if (orientationManager.hasLocation()) {
                    Location location = orientationManager.getLocation();
                    List<Place> nearbyPlaces = landmarks.getNearbyLandmarks(
                        location.getLatitude(), location.getLongitude());
                    benefitsCompassView.setNearbyPlaces(nearbyPlaces);
                }

                renderThread = new RenderThread();
                renderThread.start();
            } else {
                renderThread.quit();
                renderThread = null;

                orientationManager.stop();

            }
        }
    }

    /**
     * Requests that the views redo their layout. This must be called manually every time the
     * tips view's text is updated because this layout doesn't exist in a GUI thread where those
     * requests will be enqueued automatically.
     */
    private void doLayout() {
        // Measure and update the layout so that it will take up the entire surface space
        // when it is drawn.
        int measuredWidth = View.MeasureSpec.makeMeasureSpec(surfaceWidth, View.MeasureSpec.EXACTLY);
        int measuredHeight = View.MeasureSpec.makeMeasureSpec(surfaceHeight, View.MeasureSpec.EXACTLY);

        frameLayout.measure(measuredWidth, measuredHeight);
        frameLayout.layout(0, 0, frameLayout.getMeasuredWidth(), frameLayout.getMeasuredHeight());
    }

    /**
     * Repaints the compass.
     */
    private synchronized void repaint() {
        Canvas canvas = null;

        try {
            canvas = surfaceHolder.lockCanvas();
        } catch (RuntimeException e) {
            Log.d(TAG, "lockCanvas failed", e);
        }

        if (canvas != null) {
            canvas.drawColor(Color.BLACK);
            frameLayout.draw(canvas);

            try {
                surfaceHolder.unlockCanvasAndPost(canvas);
            } catch (RuntimeException e) {
                Log.d(TAG, "unlockCanvasAndPost failed", e);
            }
        }
    }

    /**
     * Shows or hides the tip view with an appropriate message based on the current accuracy of the
     * compass.
     */
    private void updateTipsView() {
        float newAlpha = 1.0f;

        if (hasMagneticInterference) {
            tipsView.setText(R.string.magnetic_interference);
            doLayout();
        } else if (isTooSteep) {
            tipsView.setText(R.string.pitch_too_steep);
            doLayout();
        } else {
            newAlpha = 0.0f;
        }

        if (tipsContainer.getAnimation() == null) {
            tipsContainer.animate().alpha(newAlpha).start();
        }
    }

    /**
     * Redraws the compass in the background.
     */
    private class RenderThread extends Thread {
        private boolean shouldRun;

        /**
         * Initializes the background rendering thread.
         */
        public RenderThread() {
            shouldRun = true;
        }

        /**
         * Returns true if the rendering thread should continue to run.
         *
         * @return true if the rendering thread should continue to run
         */
        private synchronized boolean shouldRun() {
            return shouldRun;
        }

        /**
         * Requests that the rendering thread exit at the next opportunity.
         */
        public synchronized void quit() {
            shouldRun = false;
        }

        @Override
        public void run() {
            while (shouldRun()) {
                long frameStart = SystemClock.elapsedRealtime();
                repaint();
                long frameLength = SystemClock.elapsedRealtime() - frameStart;

                long sleepTime = FRAME_TIME_MILLIS - frameLength;
                if (sleepTime > 0) {
                    SystemClock.sleep(sleepTime);
                }
            }
        }
    }
}
