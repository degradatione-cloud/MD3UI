package dev.md3ui.core;

import dev.md3ui.core.motion.Motion;
import dev.md3ui.core.motion.Spring;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Guards the animation core.
 *
 * <p>The frame-rate independence test is the important one: it is the whole
 * reason the spring is solved analytically instead of stepped. If it regresses,
 * animations would run at different speeds depending on FPS, which is the kind of
 * bug that is nearly impossible to spot by eye but obvious to players.
 */
class MotionTest {

    @Test
    @DisplayName("a spring converges on its target")
    void springSettles() {
        Spring s = Motion.SPATIAL_DEFAULT.instance(0);
        s.target(1);
        for (int i = 0; i < 600 && !s.settled(); i++) {
            s.advance(1.0 / 60.0);
        }
        assertTrue(s.settled(), "spring never settled");
        assertEquals(1.0, s.value(), 1e-3);
    }

    @Test
    @DisplayName("timing is frame-rate independent")
    void frameRateIndependent() {
        // Same wall-clock duration, three very different frame rates.
        double duration = 0.25;
        double at30 = simulate(duration, 1.0 / 30.0);
        double at60 = simulate(duration, 1.0 / 60.0);
        double at240 = simulate(duration, 1.0 / 240.0);

        assertEquals(at60, at30, 0.02,
                "30 vs 60 FPS diverged: " + at30 + " vs " + at60);
        assertEquals(at60, at240, 0.02,
                "240 vs 60 FPS diverged: " + at240 + " vs " + at60);
    }

    private static double simulate(double totalSeconds, double dt) {
        Spring s = Motion.SPATIAL_DEFAULT.instance(0);
        s.target(1);
        double elapsed = 0;
        while (elapsed < totalSeconds - 1e-9) {
            double step = Math.min(dt, totalSeconds - elapsed);
            s.advance(step);
            elapsed += step;
        }
        return s.value();
    }

    @Test
    @DisplayName("an underdamped spring overshoots, a critically damped one does not")
    void dampingBehaviour() {
        Spring bouncy = Motion.EXPRESSIVE_DEFAULT.instance(0);
        bouncy.target(1);
        double maxSeen = 0;
        for (int i = 0; i < 240; i++) {
            bouncy.advance(1.0 / 60.0);
            maxSeen = Math.max(maxSeen, bouncy.value());
        }
        assertTrue(maxSeen > 1.0,
                "expressive spring should overshoot, peaked at " + maxSeen);

        Spring firm = Motion.EFFECT_DEFAULT.instance(0);
        firm.target(1);
        double firmMax = 0;
        for (int i = 0; i < 240; i++) {
            firm.advance(1.0 / 60.0);
            firmMax = Math.max(firmMax, firm.value());
        }
        assertTrue(firmMax <= 1.0001,
                "effect spring must not overshoot, peaked at " + firmMax);
    }

    @Test
    @DisplayName("retargeting mid-flight preserves velocity and stays bounded")
    void retargetIsStable() {
        Spring s = Motion.SPATIAL_DEFAULT.instance(0);
        s.target(1);
        for (int i = 0; i < 6; i++) s.advance(1.0 / 60.0);
        double mid = s.value();
        assertTrue(mid > 0 && mid < 1, "should be in flight, at " + mid);

        // Flip the target repeatedly, as a user hammering a switch would.
        for (int flip = 0; flip < 20; flip++) {
            s.target(flip % 2 == 0 ? 0 : 1);
            for (int i = 0; i < 3; i++) s.advance(1.0 / 60.0);
            assertTrue(s.value() > -0.6 && s.value() < 1.6,
                    "value escaped sane bounds during retargeting: " + s.value());
            assertFalse(Double.isNaN(s.value()), "spring produced NaN");
        }
    }

    @Test
    @DisplayName("a huge frame gap cannot explode the spring")
    void survivesStalls() {
        Spring s = Motion.EXPRESSIVE_DEFAULT.instance(0);
        s.target(1);
        // Simulates alt-tab or a world-load hitch.
        s.advance(10.0);
        assertFalse(Double.isNaN(s.value()));
        assertTrue(s.value() > -1 && s.value() < 2,
                "value after a stall: " + s.value());
    }

    @Test
    @DisplayName("easing curves are monotone and hit their endpoints")
    void easingEndpoints() {
        assertEquals(0f, Motion.emphasized(0f), 1e-4);
        assertEquals(1f, Motion.emphasized(1f), 1e-4);
        assertEquals(0f, Motion.standard(0f), 1e-4);
        assertEquals(1f, Motion.standard(1f), 1e-4);

        float prev = -1;
        for (float t = 0; t <= 1.0001f; t += 0.02f) {
            float v = Motion.emphasized(Math.min(t, 1f));
            assertTrue(v >= prev - 1e-3f,
                    "emphasized must not go backwards at t=" + t);
            prev = v;
        }
    }

    @Test
    @DisplayName("clamped inputs never produce out-of-range output")
    void clamping() {
        assertEquals(0f, Motion.standard(-5f), 1e-6);
        assertEquals(1f, Motion.standard(5f), 1e-6);
        assertEquals(0f, Motion.lerp(0f, 10f, -1f), 1e-6);
        assertEquals(10f, Motion.lerp(0f, 10f, 2f), 1e-6);
    }

    @Test
    @DisplayName("snapTo kills velocity")
    void snapResets() {
        Spring s = Motion.EXPRESSIVE_DEFAULT.instance(0);
        s.target(1);
        for (int i = 0; i < 10; i++) s.advance(1.0 / 60.0);
        s.snapTo(0.5);
        assertEquals(0.5, s.value(), 1e-9);
        s.advance(1.0 / 60.0);
        assertEquals(0.5, s.value(), 1e-9, "snapped spring must stay put");
        assertTrue(s.settled());
    }
}
