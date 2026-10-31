package org.futo.inputmethod.keyboard;

import static org.junit.Assert.assertEquals;

import android.graphics.Rect;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class KeyDetectorGapTests {
    // A key whose hitbox covers x 100..199 and y 200..311 (Rect excludes right and bottom)
    private static final Rect BOX = new Rect(100, 200, 200, 312);

    @Test
    public void insideIsZero() {
        assertEquals(0f, KeyDetector.distanceToHitBox(BOX, 100, 200), 0f);
        assertEquals(0f, KeyDetector.distanceToHitBox(BOX, 199, 311), 0f);
    }

    @Test
    public void rightAndBottomEdgesAreOutsideByOnePixel() {
        // y=312 is the 1px strip below the row: it belongs to no hitbox
        assertEquals(1f, KeyDetector.distanceToHitBox(BOX, 150, 312), 0f);
        assertEquals(1f, KeyDetector.distanceToHitBox(BOX, 200, 250), 0f);
    }

    @Test
    public void leftAndTopEdgesCountFromTheEdge() {
        assertEquals(1f, KeyDetector.distanceToHitBox(BOX, 99, 250), 0f);
        assertEquals(2f, KeyDetector.distanceToHitBox(BOX, 150, 198), 0f);
    }

    @Test
    public void cornerIsEuclidean() {
        assertEquals(5f, KeyDetector.distanceToHitBox(BOX, 96, 197), 1e-6f);
    }
}
