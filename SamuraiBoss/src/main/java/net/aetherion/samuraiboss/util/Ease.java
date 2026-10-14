package net.aetherion.samuraiboss.util;

/**
 * Easing-Kurven (0..1 -> 0..1). Werden für Keyframes und Effekte benutzt.
 * Die Client-Interpolation von Displays ist immer linear – wir "backen" die Kurve,
 * indem wir die Animation jeden (zweiten) Tick abtasten.
 */
public enum Ease {
    LINEAR {
        public float apply(float t) {
            return t;
        }
    },
    IN_QUAD {
        public float apply(float t) {
            return t * t;
        }
    },
    OUT_QUAD {
        public float apply(float t) {
            return 1f - (1f - t) * (1f - t);
        }
    },
    IN_OUT_QUAD {
        public float apply(float t) {
            return t < 0.5f ? 2f * t * t : 1f - (float) Math.pow(-2f * t + 2f, 2) / 2f;
        }
    },
    IN_CUBIC {
        public float apply(float t) {
            return t * t * t;
        }
    },
    OUT_CUBIC {
        public float apply(float t) {
            float u = 1f - t;
            return 1f - u * u * u;
        }
    },
    IN_OUT_CUBIC {
        public float apply(float t) {
            return t < 0.5f ? 4f * t * t * t : 1f - (float) Math.pow(-2f * t + 2f, 3) / 2f;
        }
    },
    OUT_EXPO {
        public float apply(float t) {
            return t >= 1f ? 1f : 1f - (float) Math.pow(2, -10 * t);
        }
    },
    IN_EXPO {
        public float apply(float t) {
            return t <= 0f ? 0f : (float) Math.pow(2, 10 * t - 10);
        }
    },
    OUT_BACK {
        public float apply(float t) {
            float c1 = 1.70158f, c3 = c1 + 1f;
            float u = t - 1f;
            return 1f + c3 * u * u * u + c1 * u * u;
        }
    },
    IN_BACK {
        public float apply(float t) {
            float c1 = 1.70158f, c3 = c1 + 1f;
            return c3 * t * t * t - c1 * t * t;
        }
    },
    OUT_ELASTIC {
        public float apply(float t) {
            if (t <= 0f) return 0f;
            if (t >= 1f) return 1f;
            float c4 = (float) (2 * Math.PI / 3);
            return (float) (Math.pow(2, -10 * t) * Math.sin((t * 10 - 0.75) * c4) + 1);
        }
    },
    /** Hält den alten Wert und springt am Ende (für "Schnitt"-Momente). */
    STEP {
        public float apply(float t) {
            return t >= 1f ? 1f : 0f;
        }
    },
    /** Sanftes Ein- und Ausblenden (Smoothstep). */
    SMOOTH {
        public float apply(float t) {
            return t * t * (3f - 2f * t);
        }
    };

    public abstract float apply(float t);

    public static float clamp01(float t) {
        return t < 0f ? 0f : (t > 1f ? 1f : t);
    }
}
