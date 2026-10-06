package com.tuttochiaro.offline.data

import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class OcrCropTest {
    @Test fun roundsEveryEdgeInward() {
        assertEquals(PixelCrop(3, 5, 17, 18),
            boundedOcrCrop(2.01f, 4.9f, 17.99f, 18.01f, 20, 20))
        assertEquals(PixelCrop(0, 0, 20, 20),
            boundedOcrCrop(-4f, -3f, 25f, 30f, 20, 20))
    }

    @Test fun clampsToImageAndKeepsTheMinimumUsefulArea() {
        assertEquals(PixelCrop(18, 17, 20, 20),
            boundedOcrCrop(17.1f, 16.2f, 99f, 99f, 20, 20))
        rejects { boundedOcrCrop(19f, 1f, 99f, 10f, 20, 20) }
        rejects { boundedOcrCrop(-9f, -8f, 1.9f, 10f, 20, 20) }
    }

    @Test fun rejectsNonFiniteInvertedAndEmptyCrops() {
        listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY).forEach { bad ->
            rejects { boundedOcrCrop(bad, 0f, 10f, 10f, 20, 20) }
            rejects { boundedOcrCrop(0f, bad, 10f, 10f, 20, 20) }
            rejects { boundedOcrCrop(0f, 0f, bad, 10f, 20, 20) }
            rejects { boundedOcrCrop(0f, 0f, 10f, bad, 20, 20) }
        }
        rejects { boundedOcrCrop(5f, 5f, 4f, 10f, 20, 20) }
        rejects { boundedOcrCrop(5f, 5f, 6.9f, 10f, 20, 20) }
        rejects { boundedOcrCrop(0f, 0f, 10f, 10f, 0, 20) }
        rejects { boundedOcrCrop(0f, 0f, 10f, 10f, 20, -1) }
    }

    private fun rejects(block: () -> Unit) {
        try {
            block()
            fail("Expected invalid crop to be rejected")
        } catch (_: IllegalArgumentException) {
            // Expected.
        }
    }
}
