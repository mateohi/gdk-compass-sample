package com.google.android.glass.sample.compass;

import com.google.android.glass.sample.compass.model.Place;
import com.google.android.glass.sample.compass.util.MathUtils;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.animation.ValueAnimator.AnimatorUpdateListener;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.location.Location;
import android.text.TextPaint;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.LinearInterpolator;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;

public class BenefitsCompassView extends View {

    /** Various dimensions and other drawing-related constants. */
    private static final float NEEDLE_WIDTH = 6;
    private static final float NEEDLE_HEIGHT = 125;
    private static final int NEEDLE_COLOR = Color.RED;
    private static final float TICK_WIDTH = 2;
    private static final float TICK_HEIGHT = 10;
    private static final float DIRECTION_TEXT_HEIGHT = 84.0f;
    private static final float PLACE_TEXT_HEIGHT = 22.0f;
    private static final float PLACE_PIN_WIDTH = 14.0f;
    private static final float PLACE_TEXT_LEADING = 4.0f;
    private static final float PLACE_TEXT_MARGIN = 8.0f;

    /**
     * The maximum number of places names to allow to stack vertically underneath the compass
     * direction labels.
     */
    private static final int MAX_OVERLAPPING_PLACE_NAMES = 4;

    /**
     * If the difference between two consecutive headings is less than this value, the canvas will
     * be redrawn immediately rather than animated.
     */
    private static final float MIN_DISTANCE_TO_ANIMATE = 15.0f;

    /** The actual heading that represents the direction that the user is facing. */
    private float heading;

    /**
     * Represents the heading that is currently being displayed when the view is drawn. This is
     * used during animations, to keep track of the heading that should be drawn on the current
     * frame, which may be different than the desired end point.
     */
    private float animatedHeading;

    private OrientationManager orientationManager;
    private List<Place> nearbyBenefits;

    private final Paint paint;
    private final Paint tickPaint;
    private final Path path;
    private final TextPaint benefitPaint;
    private final Bitmap placeBitmap;
    private final Rect textBounds;
    private final List<Rect> allBounds;
    private final NumberFormat distanceFormat;
    private final String[] directions;
    private final ValueAnimator valueAnimator;

    public BenefitsCompassView(Context context) {
        this(context, null, 0);
    }

    public BenefitsCompassView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public BenefitsCompassView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        paint = new Paint();
        paint.setStyle(Paint.Style.FILL);
        paint.setAntiAlias(true);
        paint.setTextSize(DIRECTION_TEXT_HEIGHT);
        paint.setTypeface(Typeface.create("sans-serif-thin", Typeface.NORMAL));

        tickPaint = new Paint();
        tickPaint.setStyle(Paint.Style.STROKE);
        tickPaint.setStrokeWidth(TICK_WIDTH);
        tickPaint.setAntiAlias(true);
        tickPaint.setColor(Color.WHITE);

        benefitPaint = new TextPaint();
        benefitPaint.setStyle(Paint.Style.FILL);
        benefitPaint.setAntiAlias(true);
        benefitPaint.setColor(Color.WHITE);
        benefitPaint.setTextSize(PLACE_TEXT_HEIGHT);
        benefitPaint.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));

        path = new Path();
        textBounds = new Rect();
        allBounds = new ArrayList<Rect>();

        distanceFormat = NumberFormat.getNumberInstance();
        distanceFormat.setMinimumFractionDigits(0);
        distanceFormat.setMaximumFractionDigits(1);

        placeBitmap = BitmapFactory.decodeResource(context.getResources(), R.drawable.place_mark);

        // We use NaN to indicate that the compass is being drawn for the first
        // time, so that we can jump directly to the starting orientation
        // instead of spinning from a default value of 0.
        animatedHeading = Float.NaN;

        directions = context.getResources().getStringArray(R.array.direction_abbreviations);

        valueAnimator = new ValueAnimator();
        setupAnimator();
    }

    /**
     * Sets the instance of {@link OrientationManager} that this view will use to get the current
     * heading and location.
     *
     * @param orientationManager the instance of {@code OrientationManager} that this view will use
     */
    public void setOrientationManager(OrientationManager orientationManager) {
        this.orientationManager = orientationManager;
    }

    /**
     * Gets the current heading in degrees.
     *
     * @return the current heading.
     */
    public float getHeading() {
        return heading;
    }

    /**
     * Sets the current heading in degrees and redraws the compass. If the angle is not between 0
     * and 360, it is shifted to be in that range.
     *
     * @param degrees the current heading
     */
    public void setHeading(float degrees) {
        heading = MathUtils.mod(degrees, 360.0f);
        animateTo(heading);
    }

    /**
     * Sets the list of nearby places that the compass should display. This list is recalculated
     * whenever the user's location changes, so that only locations within a certain distance will
     * be displayed.
     *
     * @param places the list of {@code Place}s that should be displayed
     */
    public void setNearbyPlaces(List<Place> places) {
        nearbyBenefits = places;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        // The view displays 90 degrees across its width so that one 90 degree head rotation is
        // equal to one full view cycle.
        float pixelsPerDegree = getWidth() / 90.0f;
        float centerX = getWidth() / 2.0f;
        float centerY = getHeight() / 2.0f;

        canvas.save();
        canvas.translate(-animatedHeading * pixelsPerDegree + centerX, centerY);

        // In order to ensure that places on a boundary close to 0 or 360 get drawn correctly, we
        // draw them three times; once to the left, once at the "true" bearing, and once to the
        // right.
        for (int i = -1; i <= 1; i++) {
            drawPlaces(canvas, pixelsPerDegree, i * pixelsPerDegree * 360);
        }

        drawCompassDirections(canvas, pixelsPerDegree);

        canvas.restore();

        paint.setColor(NEEDLE_COLOR);
        drawNeedle(canvas, false);
        drawNeedle(canvas, true);
    }

    /**
     * Draws the compass direction strings (N, NW, W, etc.).
     *
     * @param canvas the {@link android.graphics.Canvas} upon which to draw
     * @param pixelsPerDegree the size, in pixels, of one degree step
     */
    private void drawCompassDirections(Canvas canvas, float pixelsPerDegree) {
        float degreesPerTick = 360.0f / directions.length;

        paint.setColor(Color.WHITE);

        // We draw two extra ticks/labels on each side of the view so that the
        // full range is visible even when the heading is approximately 0.
        for (int i = -2; i <= directions.length + 2; i++) {
            if (MathUtils.mod(i, 2) == 0) {
                // Draw a text label for the even indices.
                String direction = directions[MathUtils.mod(i, directions.length)];
                paint.getTextBounds(direction, 0, direction.length(), textBounds);

                canvas.drawText(direction,
                        i * degreesPerTick * pixelsPerDegree - textBounds.width() / 2,
                        textBounds.height() / 2, paint);
            } else {
                // Draw a tick mark for the odd indices.
                canvas.drawLine(i * degreesPerTick * pixelsPerDegree, -TICK_HEIGHT / 2, i
                        * degreesPerTick * pixelsPerDegree, TICK_HEIGHT / 2, tickPaint);
            }
        }
    }

    /**
     * Draws the pins and text labels for the nearby list of places.
     *
     * @param canvas the {@link android.graphics.Canvas} upon which to draw
     * @param pixelsPerDegree the size, in pixels, of one degree step
     * @param offset the number of pixels to translate the drawing operations by in the horizontal
     *         direction; used because place names are drawn three times to get proper wraparound
     */
    private void drawPlaces(Canvas canvas, float pixelsPerDegree, float offset) {
        if (orientationManager.hasLocation() && nearbyBenefits != null) {
            synchronized (nearbyBenefits) {
                Location userLocation = orientationManager.getLocation();
                double latitude1 = userLocation.getLatitude();
                double longitude1 = userLocation.getLongitude();

                allBounds.clear();

                // Loop over the list of nearby places (those within 10 km of the user's current
                // location), and compute the relative bearing from the user's location to the
                // place's location. This determines the position on the compass view where the
                // pin will be drawn.
                for (Place place : nearbyBenefits) {
                    double latitude2 = place.getLatitude();
                    double longitude2 = place.getLongitude();
                    float bearing = MathUtils.getBearing(latitude1, longitude1, latitude2,
                            longitude2);

                    String name = place.getName();
                    double distanceKm = MathUtils.getDistance(latitude1, longitude1, latitude2,
                            longitude2);
                    String text = getContext().getResources().getString(
                            R.string.place_text_format, name, distanceFormat.format(distanceKm));

                    // Measure the text and offset the text bounds to the location where the text
                    // will finally be drawn.
                    Rect textBounds = new Rect();
                    benefitPaint.getTextBounds(text, 0, text.length(), textBounds);
                    textBounds.offsetTo((int) (offset + bearing * pixelsPerDegree
                            + PLACE_PIN_WIDTH / 2 + PLACE_TEXT_MARGIN), canvas.getHeight() / 2
                            - (int) PLACE_TEXT_HEIGHT);

                    // Extend the bounds rectangle to include the pin icon and a small margin
                    // to the right of the text, for the overlap calculations below.
                    textBounds.left -= PLACE_PIN_WIDTH + PLACE_TEXT_MARGIN;
                    textBounds.right += PLACE_TEXT_MARGIN;

                    // This loop attempts to find the best vertical position for the string by
                    // starting at the bottom of the display and checking to see if it overlaps
                    // with any other labels that were already drawn. If there is an overlap, we
                    // move up and check again, repeating this process until we find a vertical
                    // position where there is no overlap, or when we reach the limit on
                    // overlapping place names.
                    boolean intersects;
                    int numberOfTries = 0;
                    do {
                        intersects = false;
                        numberOfTries++;
                        textBounds.offset(0, (int) -(PLACE_TEXT_HEIGHT + PLACE_TEXT_LEADING));

                        for (Rect existing : allBounds) {
                            if (Rect.intersects(existing, textBounds)) {
                                intersects = true;
                                break;
                            }
                        }
                    } while (intersects && numberOfTries <= MAX_OVERLAPPING_PLACE_NAMES);

                    // Only draw the string if it would not go high enough to overlap the compass
                    // directions. This means some places may not be drawn, even if they're nearby.
                    if (numberOfTries <= MAX_OVERLAPPING_PLACE_NAMES) {
                        allBounds.add(textBounds);

                        canvas.drawBitmap(placeBitmap, offset + bearing * pixelsPerDegree
                                - PLACE_PIN_WIDTH / 2, textBounds.top + 2, paint);
                        canvas.drawText(text,
                                offset + bearing * pixelsPerDegree + PLACE_PIN_WIDTH / 2
                                + PLACE_TEXT_MARGIN, textBounds.top + PLACE_TEXT_HEIGHT,
                                benefitPaint);
                    }
                }
            }
        }
    }

    /**
     * Draws a needle that is centered at the top or bottom of the compass.
     *
     * @param canvas the {@link android.graphics.Canvas} upon which to draw
     * @param bottom true to draw the bottom needle, or false to draw the top needle
     */
    private void drawNeedle(Canvas canvas, boolean bottom) {
        float centerX = getWidth() / 2.0f;
        float origin;
        float sign;

        // Flip the vertical coordinates if we're drawing the bottom needle.
        if (bottom) {
            origin = getHeight();
            sign = -1;
        } else {
            origin = 0;
            sign = 1;
        }

        float needleHalfWidth = NEEDLE_WIDTH / 2;

        path.reset();
        path.moveTo(centerX - needleHalfWidth, origin);
        path.lineTo(centerX - needleHalfWidth, origin + sign * (NEEDLE_HEIGHT - 4));
        path.lineTo(centerX, origin + sign * NEEDLE_HEIGHT);
        path.lineTo(centerX + needleHalfWidth, origin + sign * (NEEDLE_HEIGHT - 4));
        path.lineTo(centerX + needleHalfWidth, origin);
        path.close();

        canvas.drawPath(path, paint);
    }

    /**
     * Sets up a {@link android.animation.ValueAnimator} that will be used to animate the compass
     * when the distance between two sensor events is large.
     */
    private void setupAnimator() {
        valueAnimator.setInterpolator(new LinearInterpolator());
        valueAnimator.setDuration(250);

        // Notifies us at each frame of the animation so we can redraw the view.
        valueAnimator.addUpdateListener(new AnimatorUpdateListener() {

            @Override
            public void onAnimationUpdate(ValueAnimator animator) {
                animatedHeading = MathUtils.mod((Float) valueAnimator.getAnimatedValue(), 360.0f);
                invalidate();
            }
        });

        // Notifies us when the animation is over. During an animation, the user's head may have
        // continued to move to a different orientation than the original destination angle of the
        // animation. Since we can't easily change the animation goal while it is running, we call
        // animateTo() again, which will either redraw at the new orientation (if the difference is
        // small enough), or start another animation to the new heading. This seems to produce
        // fluid results.
        valueAnimator.addListener(new AnimatorListenerAdapter() {

            @Override
            public void onAnimationEnd(Animator animator) {
                animateTo(heading);
            }
        });
    }

    /**
     * Animates the view to the specified heading, or simply redraws it immediately if the
     * difference between the current heading and new heading are small enough that it wouldn't be
     * noticeable.
     *
     * @param end the desired heading
     */
    private void animateTo(float end) {
        // Only act if the animator is not currently running. If the user's orientation changes
        // while the animator is running, we wait until the end of the animation to update the
        // display again, to prevent jerkiness.
        if (!valueAnimator.isRunning()) {
            float start = animatedHeading;
            float distance = Math.abs(end - start);
            float reverseDistance = 360.0f - distance;
            float shortest = Math.min(distance, reverseDistance);

            if (Float.isNaN(animatedHeading) || shortest < MIN_DISTANCE_TO_ANIMATE) {
                // If the distance to the destination angle is small enough (or if this is the
                // first time the compass is being displayed), it will be more fluid to just redraw
                // immediately instead of doing an animation.
                animatedHeading = end;
                invalidate();
            } else {
                // For larger distances (i.e., if the compass "jumps" because of sensor calibration
                // issues), we animate the effect to provide a more fluid user experience. The
                // calculation below finds the shortest distance between the two angles, which may
                // involve crossing 0/360 degrees.
                float goal;

                if (distance < reverseDistance) {
                    goal = end;
                } else if (end < start) {
                    goal = end + 360.0f;
                } else {
                    goal = end - 360.0f;
                }

                valueAnimator.setFloatValues(start, goal);
                valueAnimator.start();
            }
        }
    }
}
