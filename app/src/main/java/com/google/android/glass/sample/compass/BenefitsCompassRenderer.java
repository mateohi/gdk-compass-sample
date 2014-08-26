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

public class BenefitsCompassRenderer implements DirectRenderingCallback {

    private static final String TAG = BenefitsCompassRenderer.class.getSimpleName();

    private static final float TOO_STEEP_PITCH_DEGREES = 50.0f;

    private static final int REFRESH_RATE_FPS = 45;

    private static final long FRAME_TIME_MILLIS = TimeUnit.SECONDS.toMillis(1) / REFRESH_RATE_FPS;

    private final TextView benefitNameView;
    private final TextView benefitDescrView;

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
    private final RelativeLayout benefitssContainer;
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
        benefitssContainer = (RelativeLayout) frameLayout.findViewById(R.id.benefit_container);
        tipsView = (TextView) frameLayout.findViewById(R.id.tips_view);
        benefitNameView = (TextView) frameLayout.findViewById(R.id.benefits_name);
        benefitDescrView = (TextView) frameLayout.findViewById(R.id.benefits_description);

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
            updateFrontBenefits();
            canvas.drawColor(Color.BLACK);
            frameLayout.draw(canvas);

            try {
                surfaceHolder.unlockCanvasAndPost(canvas);
            } catch (RuntimeException e) {
                Log.d(TAG, "unlockCanvasAndPost failed", e);
            }
        }
    }

    private void updateFrontBenefits() {
        Place frontBenefit = getFrontBenefit();

        if (frontBenefit != null) {
            benefitNameView.setText(frontBenefit.getName());
            benefitDescrView.setText(frontBenefit.getDescription());
        }
    }

    /**
     * Shows or hides the tip view with an appropriate message based on the current accuracy of the
     * compass.
     */
    private void updateTipsView() {
        float tipsAlpha = 1.0f;
        float benefitsAlpha = 0.0f;

        if (isTooSteep) {
            tipsView.setText(R.string.pitch_too_steep);
            doLayout();
        } else if (hasMagneticInterference) {
            tipsView.setText(R.string.magnetic_interference);
            doLayout();
        } else {
            tipsAlpha = 0.0f;
            benefitsAlpha = 1.0f;
        }

        if (tipsContainer.getAnimation() == null) {
            tipsContainer.animate().alpha(tipsAlpha).start();
        }
        if (benefitssContainer.getAnimation() == null) {
            benefitssContainer.animate().alpha(benefitsAlpha).start();
        }
    }

    private class RenderThread extends Thread {
        private boolean shouldRun;

        public RenderThread() {
            shouldRun = true;
        }

        private synchronized boolean shouldRun() {
            return shouldRun;
        }

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

    public Place getFrontBenefit() {
        return this.benefitsCompassView.getFrontBenefit();
    }
}
