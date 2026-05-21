package com.glorycam.app.camera

import androidx.compose.ui.graphics.ColorMatrix

enum class FilterPreset(val displayName: String, val description: String) {
    ORIGINAL("Original", "Natural camera output"),
    NEO_NOIR("Noir", "Cinematic high-contrast monochrome"),
    GOLDEN_HOUR("Golden Hour", "Soft warm amber and amber sunset tones"),
    CYBERPUNK("Cyberpunk", "Vibrant neon-cyan and pink Tokyo tones"),
    COOL_MIST("Cool Sky", "Dreamy arctic blue highlights"),
    FOREST("Emerald", "Rich organic forestry green depth"),
    NIGHT_NEON("Night Neon ⚡", "Vibrant warm copper roads and sapphire blues"),
    AURORA_GREEN("Aurora Nights 🌌", "Dreamy mystic boreal emerald and teal skies"),
    ASTRO_DEEP("Astro Deep 🌟", "Deep royal cosmic indigo and solar stardust");

    fun getColorMatrix(): ColorMatrix {
        return ColorMatrix(getMatrixArray())
    }

    fun getMatrixArray(): FloatArray {
        return when (this) {
            ORIGINAL -> floatArrayOf(
                1f, 0f, 0f, 0f, 0f,
                0f, 1f, 0f, 0f, 0f,
                0f, 0f, 1f, 0f, 0f,
                0f, 0f, 0f, 1f, 0f
            )
            NEO_NOIR -> {
                // High contrast desaturated luma with minor dark offset
                val r = 0.33f
                val g = 0.59f
                val b = 0.11f
                floatArrayOf(
                    r * 1.2f, g * 1.2f, b * 1.2f, 0f, -10f,
                    r * 1.2f, g * 1.2f, b * 1.2f, 0f, -10f,
                    r * 1.2f, g * 1.2f, b * 1.2f, 0f, -10f,
                    0f,       0f,       0f,       1f, 0f
                )
            }
            GOLDEN_HOUR -> floatArrayOf(
                // Warm, rich yellows and reds, slight green drop
                1.15f, 0.2f,   0.0f,   0f,  15f,
                0.05f, 1.05f,  0.0f,   0f,  10f,
                0.0f,  0.0f,   0.85f,  0f, -15f,
                0f,    0f,     0f,     1f,   0f
            )
            CYBERPUNK -> floatArrayOf(
                // Amplify Blues/Magentas, shift Greens to Blues, expand Reds
                1.1f,  0.1f,   0.2f,   0f,  20f,
                0.0f,  0.8f,   0.3f,   0f, -10f,
                0.2f,  0.2f,   1.3f,   0f,  30f,
                0f,    0f,     0f,     1f,   0f
            )
            COOL_MIST -> floatArrayOf(
                // High Blue-Green highlights, lower Reds
                0.78f, 0.0f,   0.1f,   0f, -10f,
                0.0f,  1.05f,  0.15f,  0f,  10f,
                0.0f,  0.15f,  1.18f,  0f,  25f,
                0f,    0f,     0f,     1f,   0f
            )
            FOREST -> floatArrayOf(
                // Deepen reds/blues, amplify green tones
                0.85f, 0.05f,  0.0f,   0f, -15f,
                0.1f,  1.15f,  0.1f,   0f,  10f,
                0.0f,  0.05f,  0.85f,  0f, -10f,
                0f,    0f,     0f,     1f,   0f
            )
            NIGHT_NEON -> floatArrayOf(
                // High neon contrast (copper warm reds, glowing blues)
                1.35f, 0.00f,  0.10f,  0f,  18f,
                0.00f, 0.80f,  0.10f,  0f,  -5f,
                0.10f, 0.10f,  1.42f,  0f,  25f,
                0f,    0f,     0f,     1f,   0f
            )
            AURORA_GREEN -> floatArrayOf(
                // Emerald-boreal green / cyan sky boost
                0.65f, 0.15f,  0.00f,  0f, -12f,
                0.05f, 1.45f,  0.15f,  0f,  32f,
                0.00f, 0.30f,  1.15f,  0f,  15f,
                0f,    0f,     0f,     1f,   0f
            )
            ASTRO_DEEP -> floatArrayOf(
                // Ultra navy/violet atmosphere deep cosmic boost
                0.72f, 0.05f,  0.00f,  0f,  -8f,
                0.02f, 0.88f,  0.12f,  0f,  -5f,
                0.15f, 0.15f,  1.55f,  0f,  42f,
                0f,    0f,     0f,     1f,   0f
            )
        }
    }
}
