package dev.md3ui.core.motion;

/**
 * A retargetable damped-spring animator, integrated analytically.
 *
 * <p>Closed-form rather than stepped Euler on purpose: Minecraft's client tick
 * is 20&nbsp;Hz while frames run at whatever the GPU manages, so a
 * frame-stepped integrator would make animation speed depend on FPS and would
 * visibly stiffen when a chunk hitch drops a frame. Solving position from
 * elapsed time keeps identical timing at 30 or 300&nbsp;FPS.
 *
 * <p>Each instance holds its own state, so widgets keep one per animated
 * property. {@link #target(double)} can be called at any moment; velocity is
 * preserved, which is what stops a rapidly toggled switch from snapping.
 */
public final class Spring {

    private final double dampingRatio;
    private final double stiffness;

    private double value;
    private double velocity;
    private double targetValue;

    /** Displacement below which the spring is considered settled. */
    private static final double REST_DELTA = 0.0008;
    private static final double REST_VELOCITY = 0.008;

    private Spring(double dampingRatio, double stiffness) {
        this.dampingRatio = Math.max(0.05, dampingRatio);
        this.stiffness = Math.max(1, stiffness);
    }

    /**
     * @param dampingRatio 1.0 critically damped, &lt;1 bouncy, &gt;1 sluggish
     * @param stiffness    higher settles faster
     */
    public static Spring of(double dampingRatio, double stiffness) {
        return new Spring(dampingRatio, stiffness);
    }

    /** A fresh animator with this spring's constants, starting at {@code start}. */
    public Spring instance(double start) {
        Spring s = new Spring(dampingRatio, stiffness);
        s.value = start;
        s.targetValue = start;
        return s;
    }

    public void target(double t) { this.targetValue = t; }

    public void snapTo(double v) {
        this.value = v;
        this.targetValue = v;
        this.velocity = 0;
    }

    public double value() { return value; }

    public float valueF() { return (float) value; }

    public double target() { return targetValue; }

    public boolean settled() {
        return Math.abs(targetValue - value) < REST_DELTA && Math.abs(velocity) < REST_VELOCITY;
    }

    /**
     * Advance by {@code dtSeconds}. Safe to call with a large dt after a stall:
     * the analytic solution cannot explode the way an Euler step would.
     */
    public void advance(double dtSeconds) {
        if (dtSeconds <= 0) return;
        if (settled()) {
            value = targetValue;
            velocity = 0;
            return;
        }
        // Clamp pathological frame gaps (alt-tab, world load) to keep motion sane.
        double dt = Math.min(dtSeconds, 0.25);

        double omega = Math.sqrt(stiffness);
        double zeta = dampingRatio;
        double x0 = value - targetValue;
        double v0 = velocity;

        double x, v;
        if (zeta < 1) {
            double wd = omega * Math.sqrt(1 - zeta * zeta);
            double e = Math.exp(-zeta * omega * dt);
            double c1 = x0;
            double c2 = (v0 + zeta * omega * x0) / wd;
            double cos = Math.cos(wd * dt), sin = Math.sin(wd * dt);
            x = e * (c1 * cos + c2 * sin);
            v = e * ((c2 * wd - zeta * omega * c1) * cos
                    - (c1 * wd + zeta * omega * c2) * sin);
        } else if (zeta == 1) {
            double e = Math.exp(-omega * dt);
            x = e * (x0 + (v0 + omega * x0) * dt);
            v = e * (v0 - dt * omega * (v0 + omega * x0));
        } else {
            double r = omega * Math.sqrt(zeta * zeta - 1);
            double a = -zeta * omega + r;
            double b = -zeta * omega - r;
            double c2 = (v0 - a * x0) / (b - a);
            double c1 = x0 - c2;
            x = c1 * Math.exp(a * dt) + c2 * Math.exp(b * dt);
            v = c1 * a * Math.exp(a * dt) + c2 * b * Math.exp(b * dt);
        }

        value = x + targetValue;
        velocity = v;

        if (settled()) {
            value = targetValue;
            velocity = 0;
        }
    }

    /** Convenience: advance and read in one call. */
    public float step(double dtSeconds) {
        advance(dtSeconds);
        return (float) value;
    }
}
