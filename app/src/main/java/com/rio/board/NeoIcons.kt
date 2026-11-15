package com.rio.board

import android.graphics.Path

/**
 * "Neo" toolbar icons: original vector artwork on a 24x24 grid, stored as path data
 * (M L Q C Z, absolute) so they stay sharp at any size and take the theme colour.
 * Each layer is drawn in the icon's tint: outline, soft-filled outline, solid, faint or dashed.
 */
object NeoIcons {
    const val S = 0      // outline
    const val SOFT = 1   // outline + light fill
    const val SOLID = 2  // solid fill
    const val FAINT = 3  // half-transparent outline (secondary detail)
    const val DASH = 4   // dashed outline

    class Layer(val d: String, val mode: Int)

    private val data: Map<IconView.Kind, List<Layer>> = mapOf(
        IconView.Kind.THEME to listOf(
            Layer("M8.2 3.6 L3.4 6.6 Q3 6.9 3.2 7.3 L4.8 10.2 Q5 10.6 5.4 10.4 L7.6 9.4 L7.6 19.6 Q7.6 20.6 8.6 20.6 L15.4 20.6 Q16.4 20.6 16.4 19.6 L16.4 9.4 L18.6 10.4 Q19 10.6 19.2 10.2 L20.8 7.3 Q21 6.9 20.6 6.6 L15.8 3.6 Q12 7 8.2 3.6 Z", SOFT),
            Layer("M12 11.4 Q12.42 13.58 14.6 14 Q12.42 14.42 12 16.6 Q11.58 14.42 9.4 14 Q11.58 13.58 12 11.4 Z", SOLID)
        ),
        IconView.Kind.EMOJI to listOf(
            Layer("M19.5 12.5 C19.5 17.19 15.69 21 11 21 C6.31 21 2.5 17.19 2.5 12.5 C2.5 7.81 6.31 4 11 4 C15.69 4 19.5 7.81 19.5 12.5 Z", SOFT),
            Layer("M9.35 11.3 C9.35 11.94 8.84 12.45 8.2 12.45 C7.56 12.45 7.05 11.94 7.05 11.3 C7.05 10.66 7.56 10.15 8.2 10.15 C8.84 10.15 9.35 10.66 9.35 11.3 Z", SOLID),
            Layer("M12.4 11.4 Q14 9.8 15.6 11.4", S),
            Layer("M7.4 15.2 Q11 18.6 14.6 15.2", S),
            Layer("M19.4 1.6 Q19.91 4.29 22.6 4.8 Q19.91 5.31 19.4 8 Q18.89 5.31 16.2 4.8 Q18.89 4.29 19.4 1.6 Z", SOLID)
        ),
        IconView.Kind.STICKER to listOf(
            Layer("M6.5 3.5 L17.5 3.5 Q20.5 3.5 20.5 6.5 L20.5 13 L13 20.5 L6.5 20.5 Q3.5 20.5 3.5 17.5 L3.5 6.5 Q3.5 3.5 6.5 3.5 Z", SOFT),
            Layer("M20.5 13 L16 13 Q13 13 13 16 L13 20.5", S),
            Layer("M10 5.8 Q10.67 9.33 14.2 10 Q10.67 10.67 10 14.2 Q9.33 10.67 5.8 10 Q9.33 9.33 10 5.8 Z", SOLID),
            Layer("M17 6.8 C17 7.35 16.55 7.8 16 7.8 C15.45 7.8 15 7.35 15 6.8 C15 6.25 15.45 5.8 16 5.8 C16.55 5.8 17 6.25 17 6.8 Z", SOLID)
        ),
        IconView.Kind.MIC to listOf(
            Layer("M12 2.8 L12 2.8 Q15 2.8 15 5.8 L15 11.2 Q15 14.2 12 14.2 L12 14.2 Q9 14.2 9 11.2 L9 5.8 Q9 2.8 12 2.8 Z", SOFT),
            Layer("M5.8 11 Q5.8 17.2 12 17.2 Q18.2 17.2 18.2 11", S),
            Layer("M12 17.2 L12 21", S),
            Layer("M8.8 21 L15.2 21", S),
            Layer("M3.2 8.6 Q2.2 11.5 3.2 14.4", FAINT),
            Layer("M20.8 8.6 Q21.8 11.5 20.8 14.4", FAINT)
        ),
        IconView.Kind.SETTINGS to listOf(
            Layer("M19.18 10.21 L21.57 10.4 L21.57 13.6 L19.18 13.79 L18.34 15.81 L19.9 17.63 L17.63 19.9 L15.81 18.34 L13.79 19.18 L13.6 21.57 L10.4 21.57 L10.21 19.18 L8.19 18.34 L6.37 19.9 L4.1 17.63 L5.66 15.81 L4.82 13.79 L2.43 13.6 L2.43 10.4 L4.82 10.21 L5.66 8.19 L4.1 6.37 L6.37 4.1 L8.19 5.66 L10.21 4.82 L10.4 2.43 L13.6 2.43 L13.79 4.82 L15.81 5.66 L17.63 4.1 L19.9 6.37 L18.34 8.19 Z", SOFT),
            Layer("M15.1 12 C15.1 13.71 13.71 15.1 12 15.1 C10.29 15.1 8.9 13.71 8.9 12 C8.9 10.29 10.29 8.9 12 8.9 C13.71 8.9 15.1 10.29 15.1 12 Z", S)
        ),
        IconView.Kind.COLLAPSE to listOf(
            Layer("M5.8 8.2 L12 14.4 L18.2 8.2", S),
            Layer("M8.6 18.6 L15.4 18.6", FAINT)
        ),
        IconView.Kind.CLIPBOARD to listOf(
            Layer("M7.6 4.6 L16.4 4.6 Q19 4.6 19 7.2 L19 18.6 Q19 21.2 16.4 21.2 L7.6 21.2 Q5 21.2 5 18.6 L5 7.2 Q5 4.6 7.6 4.6 Z", SOFT),
            Layer("M10.2 2.4 L13.8 2.4 Q15.4 2.4 15.4 4 L15.4 5 Q15.4 6.6 13.8 6.6 L10.2 6.6 Q8.6 6.6 8.6 5 L8.6 4 Q8.6 2.4 10.2 2.4 Z", SOFT),
            Layer("M8.6 11.4 L15.4 11.4", S),
            Layer("M8.6 15.6 L12.8 15.6", S)
        ),
        IconView.Kind.HISTORY to listOf(
            Layer("M20.4 12 C20.4 16.64 16.64 20.4 12 20.4 C7.36 20.4 3.6 16.64 3.6 12 C3.6 7.36 7.36 3.6 12 3.6 C16.64 3.6 20.4 7.36 20.4 12 Z", FAINT),
            Layer("M4.39 8.45 L4.85 7.59 L5.41 6.79 L6.06 6.06 L6.79 5.41 L7.59 4.85 L8.45 4.39 L9.36 4.03 L10.3 3.77 L11.27 3.63 L12.24 3.6 L13.22 3.69 L14.17 3.89 L15.1 4.19 L15.99 4.61 L16.82 5.12 L17.58 5.72 L18.28 6.42 L18.88 7.18 L19.39 8.01 L19.81 8.9 L20.11 9.83 L20.31 10.78 L20.4 11.76 L20.37 12.73 L20.23 13.7 L19.97 14.64 L19.61 15.55 L19.15 16.41 L18.59 17.21 L17.94 17.94 L17.21 18.59 L16.41 19.15 L15.55 19.61 L14.64 19.97 L13.7 20.23 L12.73 20.37 L11.76 20.4 L10.78 20.31 L9.83 20.11 L8.9 19.81 L8.01 19.39 L7.18 18.88 L6.42 18.28 L5.72 17.58 L5.12 16.82 L4.61 15.99 L4.19 15.1 L3.89 14.17", S),
            Layer("M5.55 11.64 L4.39 8.45 L1.19 9.61", S),
            Layer("M12 7.4 L12 12 L15.4 14.2", S)
        ),
        IconView.Kind.GLOBE to listOf(
            Layer("M21 12 C21 16.97 16.97 21 12 21 C7.03 21 3 16.97 3 12 C3 7.03 7.03 3 12 3 C16.97 3 21 7.03 21 12 Z", SOFT),
            Layer("M16 12 L15.94 13.56 L15.76 15.08 L15.46 16.5 L15.06 17.79 L14.57 18.89 L14 19.79 L13.37 20.46 L12.69 20.86 L12 21 L11.31 20.86 L10.63 20.46 L10 19.79 L9.43 18.89 L8.94 17.79 L8.54 16.5 L8.24 15.08 L8.06 13.56 L8 12 L8.06 10.44 L8.24 8.92 L8.54 7.5 L8.94 6.21 L9.43 5.11 L10 4.21 L10.63 3.54 L11.31 3.14 L12 3 L12.69 3.14 L13.37 3.54 L14 4.21 L14.57 5.11 L15.06 6.21 L15.46 7.5 L15.76 8.92 L15.94 10.44 Z", S),
            Layer("M3 12 L21 12", S),
            Layer("M4.6 7.2 Q12 9.8 19.4 7.2", FAINT),
            Layer("M4.6 16.8 Q12 14.2 19.4 16.8", FAINT)
        ),
        IconView.Kind.BOLT to listOf(
            Layer("M13.8 2.6 L5.6 13.2 L11.2 13.2 L10.2 21.4 L18.4 10.4 L12.8 10.4 Z", SOFT)
        ),
        IconView.Kind.IMAGE to listOf(
            Layer("M6 4.6 L18 4.6 Q20.6 4.6 20.6 7.2 L20.6 16.8 Q20.6 19.4 18 19.4 L6 19.4 Q3.4 19.4 3.4 16.8 L3.4 7.2 Q3.4 4.6 6 4.6 Z", SOFT),
            Layer("M3.8 17 L9 11.4 L13 15.4 L15.8 12.8 L20.2 17.2", S),
            Layer("M17.9 9 C17.9 9.94 17.14 10.7 16.2 10.7 C15.26 10.7 14.5 9.94 14.5 9 C14.5 8.06 15.26 7.3 16.2 7.3 C17.14 7.3 17.9 8.06 17.9 9 Z", SOLID)
        ),
        IconView.Kind.SNOW to listOf(
            Layer("M10.2 4.2 Q11.84 10.76 18.4 12.4 Q11.84 14.04 10.2 20.6 Q8.56 14.04 2 12.4 Q8.56 10.76 10.2 4.2 Z", SOFT),
            Layer("M19 2.2 Q19.51 4.89 22.2 5.4 Q19.51 5.91 19 8.6 Q18.49 5.91 15.8 5.4 Q18.49 4.89 19 2.2 Z", SOLID),
            Layer("M19.4 16 Q19.78 18.02 21.8 18.4 Q19.78 18.78 19.4 20.8 Q19.02 18.78 17 18.4 Q19.02 18.02 19.4 16 Z", SOLID)
        ),
        IconView.Kind.GRID to listOf(
            Layer("M5.8 4 L8.8 4 Q10.6 4 10.6 5.8 L10.6 8.8 Q10.6 10.6 8.8 10.6 L5.8 10.6 Q4 10.6 4 8.8 L4 5.8 Q4 4 5.8 4 Z", SOFT),
            Layer("M15.2 4 L18.2 4 Q20 4 20 5.8 L20 8.8 Q20 10.6 18.2 10.6 L15.2 10.6 Q13.4 10.6 13.4 8.8 L13.4 5.8 Q13.4 4 15.2 4 Z", S),
            Layer("M5.8 13.4 L8.8 13.4 Q10.6 13.4 10.6 15.2 L10.6 18.2 Q10.6 20 8.8 20 L5.8 20 Q4 20 4 18.2 L4 15.2 Q4 13.4 5.8 13.4 Z", S),
            Layer("M15.2 13.4 L18.2 13.4 Q20 13.4 20 15.2 L20 18.2 Q20 20 18.2 20 L15.2 20 Q13.4 20 13.4 18.2 L13.4 15.2 Q13.4 13.4 15.2 13.4 Z", S)
        ),
        IconView.Kind.LOCK to listOf(
            Layer("M7.6 10.6 L16.4 10.6 Q19 10.6 19 13.2 L19 18 Q19 20.6 16.4 20.6 L7.6 20.6 Q5 20.6 5 18 L5 13.2 Q5 10.6 7.6 10.6 Z", SOFT),
            Layer("M8.4 10.6 L8.4 7.8 Q8.4 3.6 12 3.6 Q15.6 3.6 15.6 7.8 L15.6 10.6", S),
            Layer("M13.4 15 C13.4 15.77 12.77 16.4 12 16.4 C11.23 16.4 10.6 15.77 10.6 15 C10.6 14.23 11.23 13.6 12 13.6 C12.77 13.6 13.4 14.23 13.4 15 Z", SOLID),
            Layer("M12 16 L12 18", S)
        ),
        IconView.Kind.CALENDAR to listOf(
            Layer("M6.6 5.4 L17.4 5.4 Q20.2 5.4 20.2 8.2 L20.2 17.6 Q20.2 20.4 17.4 20.4 L6.6 20.4 Q3.8 20.4 3.8 17.6 L3.8 8.2 Q3.8 5.4 6.6 5.4 Z", SOFT),
            Layer("M3.8 10 L20.2 10", S),
            Layer("M8 3.4 L8 7", S),
            Layer("M16 3.4 L16 7", S),
            Layer("M9.5 14.2 C9.5 14.81 9.01 15.3 8.4 15.3 C7.79 15.3 7.3 14.81 7.3 14.2 C7.3 13.59 7.79 13.1 8.4 13.1 C9.01 13.1 9.5 13.59 9.5 14.2 Z", SOLID),
            Layer("M13.1 14.2 C13.1 14.81 12.61 15.3 12 15.3 C11.39 15.3 10.9 14.81 10.9 14.2 C10.9 13.59 11.39 13.1 12 13.1 C12.61 13.1 13.1 13.59 13.1 14.2 Z", SOLID),
            Layer("M16.7 14.2 C16.7 14.81 16.21 15.3 15.6 15.3 C14.99 15.3 14.5 14.81 14.5 14.2 C14.5 13.59 14.99 13.1 15.6 13.1 C16.21 13.1 16.7 13.59 16.7 14.2 Z", SOLID)
        ),
        IconView.Kind.COPY to listOf(
            Layer("M10.8 8.4 L18 8.4 Q20.4 8.4 20.4 10.8 L20.4 18.4 Q20.4 20.8 18 20.8 L10.8 20.8 Q8.4 20.8 8.4 18.4 L8.4 10.8 Q8.4 8.4 10.8 8.4 Z", SOFT),
            Layer("M8.4 15.6 L6.4 15.6 Q4.6 15.6 4.6 13.8 L4.6 5.6 Q4.6 3.8 6.4 3.8 L14.6 3.8 Q16.4 3.8 16.4 5.6 L16.4 8.4", S)
        ),
        IconView.Kind.CUT to listOf(
            Layer("M9.8 17.4 C9.8 18.95 8.55 20.2 7 20.2 C5.45 20.2 4.2 18.95 4.2 17.4 C4.2 15.85 5.45 14.6 7 14.6 C8.55 14.6 9.8 15.85 9.8 17.4 Z", SOFT),
            Layer("M19.8 17.4 C19.8 18.95 18.55 20.2 17 20.2 C15.45 20.2 14.2 18.95 14.2 17.4 C14.2 15.85 15.45 14.6 17 14.6 C18.55 14.6 19.8 15.85 19.8 17.4 Z", SOFT),
            Layer("M9.2 15.4 L17.8 3.6", S),
            Layer("M14.8 15.4 L6.2 3.6", S)
        ),
        IconView.Kind.SELECT_ALL to listOf(
            Layer("M5.9 3.5 L18.1 3.5 Q20.5 3.5 20.5 5.9 L20.5 18.1 Q20.5 20.5 18.1 20.5 L5.9 20.5 Q3.5 20.5 3.5 18.1 L3.5 5.9 Q3.5 3.5 5.9 3.5 Z", DASH),
            Layer("M9.8 8 L14.2 8 Q16 8 16 9.8 L16 14.2 Q16 16 14.2 16 L9.8 16 Q8 16 8 14.2 L8 9.8 Q8 8 9.8 8 Z", SOFT)
        ),
        IconView.Kind.TRASH to listOf(
            Layer("M4.6 6.6 L19.4 6.6", S),
            Layer("M9.4 6.6 L9.4 4.6 Q9.4 3.6 10.4 3.6 L13.6 3.6 Q14.6 3.6 14.6 4.6 L14.6 6.6", S),
            Layer("M6.4 6.6 L7.2 19.4 Q7.3 20.8 8.8 20.8 L15.2 20.8 Q16.7 20.8 16.8 19.4 L17.6 6.6", SOFT),
            Layer("M10.2 10.4 L10.2 17", FAINT),
            Layer("M13.8 10.4 L13.8 17", FAINT)
        )
    )

    private val cache = HashMap<IconView.Kind, List<Pair<Path, Int>>>()
    private val token = Regex("[MLQCZ]|-?\\d*\\.?\\d+")

    /** Parsed paths for [kind], or null when this icon has no Neo version (then the classic one is used). */
    fun layers(kind: IconView.Kind): List<Pair<Path, Int>>? {
        cache[kind]?.let { return it }
        val src = data[kind] ?: return null
        val out = src.map { parse(it.d) to it.mode }
        cache[kind] = out
        return out
    }

    private fun parse(d: String): Path {
        val path = Path()
        val t = token.findAll(d).map { it.value }.toList()
        var i = 0
        fun n(): Float = t[i++].toFloat()
        while (i < t.size) {
            when (t[i++]) {
                "M" -> path.moveTo(n(), n())
                "L" -> path.lineTo(n(), n())
                "Q" -> path.quadTo(n(), n(), n(), n())
                "C" -> path.cubicTo(n(), n(), n(), n(), n(), n())
                "Z" -> path.close()
            }
        }
        return path
    }
}
