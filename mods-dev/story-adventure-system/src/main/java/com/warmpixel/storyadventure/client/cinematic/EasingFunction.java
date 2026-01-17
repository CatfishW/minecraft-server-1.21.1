package com.warmpixel.storyadventure.client.cinematic;

/**
 * Easing functions for smooth camera interpolation.
 * Inspired by Unity's animation curves and CSS easing functions.
 * 
 * All functions map input t (0.0 to 1.0) to output (typically 0.0 to 1.0).
 */
public enum EasingFunction {
    LINEAR,
    EASE_IN,
    EASE_OUT,
    EASE_IN_OUT,
    CUBIC_IN,
    CUBIC_OUT,
    CUBIC_IN_OUT,
    QUART_IN,
    QUART_OUT,
    QUART_IN_OUT,
    QUINT_IN,
    QUINT_OUT,
    QUINT_IN_OUT,
    SINE_IN,
    SINE_OUT,
    SINE_IN_OUT,
    EXPO_IN,
    EXPO_OUT,
    EXPO_IN_OUT,
    CIRC_IN,
    CIRC_OUT,
    CIRC_IN_OUT,
    SMOOTH_STEP,
    SMOOTHER_STEP,
    BOUNCE_OUT,
    BOUNCE_IN,
    BOUNCE_IN_OUT,
    ELASTIC_OUT,
    ELASTIC_IN,
    ELASTIC_IN_OUT,
    BACK_IN,
    BACK_OUT,
    BACK_IN_OUT;
    
    // Constants for elastic and back easing
    private static final double PI = Math.PI;
    private static final double C1 = 1.70158;
    private static final double C2 = C1 * 1.525;
    private static final double C3 = C1 + 1;
    private static final double C4 = (2 * PI) / 3;
    private static final double C5 = (2 * PI) / 4.5;
    
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
            
            // Quadratic
            case EASE_IN -> t * t;
            case EASE_OUT -> 1.0 - (1.0 - t) * (1.0 - t);
            case EASE_IN_OUT -> t < 0.5 
                ? 2.0 * t * t 
                : 1.0 - Math.pow(-2.0 * t + 2.0, 2) / 2.0;
            
            // Cubic
            case CUBIC_IN -> t * t * t;
            case CUBIC_OUT -> 1.0 - Math.pow(1.0 - t, 3);
            case CUBIC_IN_OUT -> t < 0.5 
                ? 4.0 * t * t * t 
                : 1.0 - Math.pow(-2.0 * t + 2.0, 3) / 2.0;
            
            // Quartic
            case QUART_IN -> t * t * t * t;
            case QUART_OUT -> 1.0 - Math.pow(1.0 - t, 4);
            case QUART_IN_OUT -> t < 0.5
                ? 8.0 * t * t * t * t
                : 1.0 - Math.pow(-2.0 * t + 2.0, 4) / 2.0;
            
            // Quintic
            case QUINT_IN -> t * t * t * t * t;
            case QUINT_OUT -> 1.0 - Math.pow(1.0 - t, 5);
            case QUINT_IN_OUT -> t < 0.5
                ? 16.0 * t * t * t * t * t
                : 1.0 - Math.pow(-2.0 * t + 2.0, 5) / 2.0;
            
            // Sine
            case SINE_IN -> 1.0 - Math.cos((t * PI) / 2.0);
            case SINE_OUT -> Math.sin((t * PI) / 2.0);
            case SINE_IN_OUT -> -(Math.cos(PI * t) - 1.0) / 2.0;
            
            // Exponential
            case EXPO_IN -> t == 0 ? 0 : Math.pow(2.0, 10.0 * t - 10.0);
            case EXPO_OUT -> t == 1 ? 1 : 1.0 - Math.pow(2.0, -10.0 * t);
            case EXPO_IN_OUT -> {
                if (t == 0) yield 0;
                if (t == 1) yield 1;
                yield t < 0.5
                    ? Math.pow(2.0, 20.0 * t - 10.0) / 2.0
                    : (2.0 - Math.pow(2.0, -20.0 * t + 10.0)) / 2.0;
            }
            
            // Circular
            case CIRC_IN -> 1.0 - Math.sqrt(1.0 - Math.pow(t, 2));
            case CIRC_OUT -> Math.sqrt(1.0 - Math.pow(t - 1.0, 2));
            case CIRC_IN_OUT -> t < 0.5
                ? (1.0 - Math.sqrt(1.0 - Math.pow(2.0 * t, 2))) / 2.0
                : (Math.sqrt(1.0 - Math.pow(-2.0 * t + 2.0, 2)) + 1.0) / 2.0;
            
            // Smooth step functions
            case SMOOTH_STEP -> t * t * (3.0 - 2.0 * t);
            case SMOOTHER_STEP -> t * t * t * (t * (t * 6.0 - 15.0) + 10.0);
            
            // Bounce
            case BOUNCE_OUT -> bounceOut(t);
            case BOUNCE_IN -> 1.0 - bounceOut(1.0 - t);
            case BOUNCE_IN_OUT -> t < 0.5
                ? (1.0 - bounceOut(1.0 - 2.0 * t)) / 2.0
                : (1.0 + bounceOut(2.0 * t - 1.0)) / 2.0;
            
            // Elastic
            case ELASTIC_OUT -> {
                if (t == 0 || t == 1) yield t;
                yield Math.pow(2.0, -10.0 * t) * Math.sin((t * 10.0 - 0.75) * C4) + 1.0;
            }
            case ELASTIC_IN -> {
                if (t == 0 || t == 1) yield t;
                yield -Math.pow(2.0, 10.0 * t - 10.0) * Math.sin((t * 10.0 - 10.75) * C4);
            }
            case ELASTIC_IN_OUT -> {
                if (t == 0 || t == 1) yield t;
                yield t < 0.5
                    ? -(Math.pow(2.0, 20.0 * t - 10.0) * Math.sin((20.0 * t - 11.125) * C5)) / 2.0
                    : (Math.pow(2.0, -20.0 * t + 10.0) * Math.sin((20.0 * t - 11.125) * C5)) / 2.0 + 1.0;
            }
            
            // Back (overshoot)
            case BACK_IN -> C3 * t * t * t - C1 * t * t;
            case BACK_OUT -> 1.0 + C3 * Math.pow(t - 1.0, 3) + C1 * Math.pow(t - 1.0, 2);
            case BACK_IN_OUT -> t < 0.5
                ? (Math.pow(2.0 * t, 2) * ((C2 + 1.0) * 2.0 * t - C2)) / 2.0
                : (Math.pow(2.0 * t - 2.0, 2) * ((C2 + 1.0) * (t * 2.0 - 2.0) + C2) + 2.0) / 2.0;
        };
    }
    
    /**
     * Helper function for bounce easing.
     */
    private static double bounceOut(double t) {
        double n1 = 7.5625;
        double d1 = 2.75;
        
        if (t < 1.0 / d1) {
            return n1 * t * t;
        } else if (t < 2.0 / d1) {
            t -= 1.5 / d1;
            return n1 * t * t + 0.75;
        } else if (t < 2.5 / d1) {
            t -= 2.25 / d1;
            return n1 * t * t + 0.9375;
        } else {
            t -= 2.625 / d1;
            return n1 * t * t + 0.984375;
        }
    }
    
    /**
     * Parse an easing function from string.
     * Supports various naming conventions (kebab-case, snake_case, etc.)
     * @param name The easing function name
     * @return The easing function, defaults to LINEAR if unknown
     */
    public static EasingFunction fromString(String name) {
        if (name == null || name.isEmpty()) {
            return LINEAR;
        }
        
        // Normalize the name
        String normalized = name.toUpperCase()
            .replace("-", "_")
            .replace(" ", "_");
        
        try {
            return valueOf(normalized);
        } catch (IllegalArgumentException e) {
            // Try common aliases
            return switch (normalized) {
                case "QUAD_IN" -> EASE_IN;
                case "QUAD_OUT" -> EASE_OUT;
                case "QUAD_IN_OUT" -> EASE_IN_OUT;
                case "EASE" -> EASE_IN_OUT;
                case "SMOOTH" -> SMOOTH_STEP;
                case "SMOOTHER" -> SMOOTHER_STEP;
                default -> LINEAR;
            };
        }
    }
    
    /**
     * Get a user-friendly display name for the easing function.
     */
    public String getDisplayName() {
        String name = name().toLowerCase().replace("_", " ");
        // Capitalize first letter of each word
        StringBuilder result = new StringBuilder();
        boolean capitalizeNext = true;
        for (char c : name.toCharArray()) {
            if (c == ' ') {
                result.append(c);
                capitalizeNext = true;
            } else if (capitalizeNext) {
                result.append(Character.toUpperCase(c));
                capitalizeNext = false;
            } else {
                result.append(c);
            }
        }
        return result.toString();
    }
    
    /**
     * Get all easing functions suitable for camera movement.
     * Excludes extreme effects like bounce and elastic.
     */
    public static EasingFunction[] getSmoothFunctions() {
        return new EasingFunction[] {
            LINEAR,
            EASE_IN, EASE_OUT, EASE_IN_OUT,
            CUBIC_IN, CUBIC_OUT, CUBIC_IN_OUT,
            QUART_IN, QUART_OUT, QUART_IN_OUT,
            SINE_IN, SINE_OUT, SINE_IN_OUT,
            SMOOTH_STEP, SMOOTHER_STEP
        };
    }
}