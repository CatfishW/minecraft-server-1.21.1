package com.warmpixel.storyadventure.client.cinematic;

/**
 * Easing functions for smooth camera interpolation.
 * Inspired by Unity's animation curves and CSS easing functions.
 */
public enum EasingFunction {
    LINEAR,
    EASE_IN,
    EASE_OUT,
    EASE_IN_OUT,
    CUBIC_IN,
    CUBIC_OUT,
    CUBIC_IN_OUT,
    SMOOTH_STEP,
    SMOOTHER_STEP,
    BOUNCE_OUT,
    ELASTIC_OUT;
    
    /**
     * Apply the easing function to a normalized time value (0.0 to 1.0).
     * @param t Normalized time (0.0 = start, 1.0 = end)
     * @return Eased value
     */
    public double apply(double t) {
        // Clamp t to [0, 1]
        t = Math.max(0.0, Math.min(1.0, t));
        
        return switch (this) {
            case LINEAR -> t;
            case EASE_IN -> t * t;
            case EASE_OUT -> 1.0 - (1.0 - t) * (1.0 - t);
            case EASE_IN_OUT -> t < 0.5 
                ? 2.0 * t * t 
                : 1.0 - Math.pow(-2.0 * t + 2.0, 2) / 2.0;
            case CUBIC_IN -> t * t * t;
            case CUBIC_OUT -> 1.0 - Math.pow(1.0 - t, 3);
            case CUBIC_IN_OUT -> t < 0.5 
                ? 4.0 * t * t * t 
                : 1.0 - Math.pow(-2.0 * t + 2.0, 3) / 2.0;
            case SMOOTH_STEP -> t * t * (3.0 - 2.0 * t);
            case SMOOTHER_STEP -> t * t * t * (t * (t * 6.0 - 15.0) + 10.0);
            case BOUNCE_OUT -> {
                double n1 = 7.5625;
                double d1 = 2.75;
                if (t < 1 / d1) {
                    yield n1 * t * t;
                } else if (t < 2 / d1) {
                    t -= 1.5 / d1;
                    yield n1 * t * t + 0.75;
                } else if (t < 2.5 / d1) {
                    t -= 2.25 / d1;
                    yield n1 * t * t + 0.9375;
                } else {
                    t -= 2.625 / d1;
                    yield n1 * t * t + 0.984375;
                }
            }
            case ELASTIC_OUT -> {
                if (t == 0 || t == 1) yield t;
                double c4 = (2.0 * Math.PI) / 3.0;
                yield Math.pow(2.0, -10.0 * t) * Math.sin((t * 10.0 - 0.75) * c4) + 1.0;
            }
        };
    }
    
    /**
     * Parse an easing function from string.
     * @param name The easing function name
     * @return The easing function, defaults to LINEAR if unknown
     */
    public static EasingFunction fromString(String name) {
        if (name == null || name.isEmpty()) {
            return LINEAR;
        }
        try {
            return valueOf(name.toUpperCase().replace("-", "_"));
        } catch (IllegalArgumentException e) {
            return LINEAR;
        }
    }
}
