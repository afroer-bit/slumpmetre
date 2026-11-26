package com.example.slumpmetre

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class SlumpMathTest {

    @Test
    fun olcek_dogru_hesaplaniyor() {
        val m = Marks(
            r1 = Offset(0.10f, 0.90f), r2 = Offset(0.10f, 0.60f),
            apex = Offset(0.50f, 0.50f),
            left = Offset(0.40f, 0.80f), right = Offset(0.60f, 0.80f)
        )
        val r = SlumpMath.measure(m, refMm = 300.0, tiltDeg = 0.0, aspect = 1.0)
        assertNotNull(r)
        assertEquals(300.0, r!!.heightMm, 0.5)
        assertEquals(200.0, r.baseMm, 0.5)
    }

    @Test
    fun taban_cok_kucukse_null() {
        val m = Marks(left = Offset(0.5f, 0.8f), right = Offset(0.5f, 0.8f))
        assertEquals(null, SlumpMath.measure(m, 300.0, 0.0, 1.0))
    }
}
