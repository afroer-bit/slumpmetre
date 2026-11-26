package com.example.slumpmetre

import androidx.compose.ui.geometry.Offset
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max

/** Noktalar 0..1 normalize koordinatta tutulur; ekran boyutu degisse de olcum bozulmaz. */
data class Marks(
    val r1: Offset = Offset(0.80f, 0.86f),
    val r2: Offset = Offset(0.80f, 0.52f),
    val apex: Offset = Offset(0.52f, 0.52f),
    val left: Offset = Offset(0.36f, 0.82f),
    val right: Offset = Offset(0.68f, 0.82f)
)

data class SlumpResult(
    val slumpMm: Int,
    val heightMm: Double,
    val baseMm: Double,
    val angleDeg: Double,
    val ratio: Double,
    val hasRef: Boolean
)

object SlumpMath {

    private const val VIRTUAL = 1000.0

    private val anchors = listOf(
        36.0 to 15.0, 33.0 to 22.0, 30.0 to 30.0, 27.0 to 40.0, 24.0 to 55.0,
        21.0 to 72.0, 18.0 to 92.0, 15.0 to 118.0, 12.0 to 145.0, 9.0 to 180.0,
        6.0 to 215.0, 3.0 to 250.0
    )

    private fun toPx(p: Offset, aspect: Double) =
        Offset((p.x * VIRTUAL).toFloat(), (p.y * VIRTUAL / aspect).toFloat())

    private fun dist(a: Offset, b: Offset) = hypot((a.x - b.x).toDouble(), (a.y - b.y).toDouble())

    private fun foot(a: Offset, b: Offset, c: Offset): Offset {
        val dx = (c.x - b.x).toDouble()
        val dy = (c.y - b.y).toDouble()
        val l2 = max(dx * dx + dy * dy, 1e-6)
        val t = ((a.x - b.x) * dx + (a.y - b.y) * dy) / l2
        return Offset((b.x + t * dx).toFloat(), (b.y + t * dy).toFloat())
    }

    private fun interp(angle: Double): Double {
        if (angle >= anchors.first().first) return anchors.first().second
        if (angle <= anchors.last().first) return anchors.last().second
        for (i in 0 until anchors.size - 1) {
            val (a1, s1) = anchors[i]
            val (a2, s2) = anchors[i + 1]
            if (angle <= a1 && angle >= a2) {
                val t = (angle - a2) / (a1 - a2)
                return s2 + t * (s1 - s2)
            }
        }
        return 100.0
    }

    /** aspect = genislik / yukseklik. Gecersiz geometride null. */
    fun measure(marks: Marks, refMm: Double, tiltDeg: Double, aspect: Double): SlumpResult? {
        if (aspect <= 0.0 || refMm <= 0.0) return null

        val r1 = toPx(marks.r1, aspect)
        val r2 = toPx(marks.r2, aspect)
        val apex = toPx(marks.apex, aspect)
        val left = toPx(marks.left, aspect)
        val right = toPx(marks.right, aspect)

        val refPx = dist(r1, r2)
        val hasRef = refPx > 8.0
        val mmPerPx = if (hasRef) refMm / refPx else 1500.0 / VIRTUAL

        val f = foot(apex, left, right)
        val hPx = dist(apex, f)
        val wPx = dist(left, right)
        if (wPx < 1.0) return null

        val corr = 1.0 / max(0.5, cos(Math.toRadians(tiltDeg)))
        val h = hPx * mmPerPx * corr
        val w = wPx * mmPerPx
        val angle = Math.toDegrees(atan2(2 * h, max(w, 1.0)))

        val slump = interp(angle).coerceIn(5.0, 260.0)
        val rounded = (Math.round(slump / 5.0) * 5).toInt()

        return SlumpResult(
            slumpMm = rounded,
            heightMm = h,
            baseMm = w,
            angleDeg = angle,
            ratio = if (h > 1) w / h else 0.0,
            hasRef = hasRef
        )
    }
}
