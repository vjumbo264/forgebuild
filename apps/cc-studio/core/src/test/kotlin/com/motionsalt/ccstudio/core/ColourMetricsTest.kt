package com.motionsalt.ccstudio.core

import org.junit.Assert.*
import org.junit.Test

class ColourMetricsTest {
    @Test fun sharmaCiede2000PublishedReferenceCases() {
        // Sharma/Wu/Dalal supplementary test data, Color Research & Application 30:21-30.
        val cases = arrayOf(
            doubleArrayOf(50.0, 2.6772, -79.7751, 50.0, 0.0, -82.7485, 2.0425),
            doubleArrayOf(50.0, 3.1571, -77.2803, 50.0, 0.0, -82.7485, 2.8615),
            doubleArrayOf(50.0, 2.8361, -74.0200, 50.0, 0.0, -82.7485, 3.4412),
            doubleArrayOf(50.0, -1.3802, -84.2814, 50.0, 0.0, -82.7485, 1.0000),
            doubleArrayOf(50.0, -1.1848, -84.8006, 50.0, 0.0, -82.7485, 1.0000),
            doubleArrayOf(50.0, -0.9009, -85.5211, 50.0, 0.0, -82.7485, 1.0000),
            doubleArrayOf(50.0, 0.0, 0.0, 50.0, -1.0, 2.0, 2.3669),
            doubleArrayOf(50.0, -1.0, 2.0, 50.0, 0.0, 0.0, 2.3669),
            doubleArrayOf(50.0, 2.49, -0.001, 50.0, -2.49, 0.0009, 7.1792),
            doubleArrayOf(50.0, 2.49, -0.001, 50.0, -2.49, 0.0010, 7.1792),
            doubleArrayOf(50.0, 2.49, -0.001, 50.0, -2.49, 0.0011, 7.2195),
            doubleArrayOf(50.0, 2.49, -0.001, 50.0, -2.49, 0.0012, 7.2195),
            doubleArrayOf(50.0, -0.001, 2.49, 50.0, 0.0009, -2.49, 4.8045),
            doubleArrayOf(50.0, -0.001, 2.49, 50.0, 0.0010, -2.49, 4.8045),
            doubleArrayOf(50.0, -0.001, 2.49, 50.0, 0.0011, -2.49, 4.7461),
            doubleArrayOf(50.0, 2.5, 0.0, 50.0, 0.0, -2.5, 4.3065),
            doubleArrayOf(50.0, 2.5, 0.0, 73.0, 25.0, -18.0, 27.1492),
            doubleArrayOf(50.0, 2.5, 0.0, 61.0, -5.0, 29.0, 22.8977),
            doubleArrayOf(50.0, 2.5, 0.0, 56.0, -27.0, -3.0, 31.9030),
            doubleArrayOf(50.0, 2.5, 0.0, 58.0, 24.0, 15.0, 19.4535)
        )
        for (c in cases) {
            val a = ColourMetrics.Lab(c[0], c[1], c[2]); val b = ColourMetrics.Lab(c[3], c[4], c[5])
            assertEquals(c[6], ColourMetrics.deltaE2000(a, b), 0.00005)
            assertEquals(ColourMetrics.deltaE2000(a, b), ColourMetrics.deltaE2000(b, a), 1e-10)
        }
    }

    @Test fun labelsAndPercentileAreHonest() {
        assertEquals("Poor match", ColourMetrics.qualityLabel(38.26))
        assertEquals("Close match", ColourMetrics.qualityLabel(4.86))
        assertEquals("Rough match", ColourMetrics.qualityLabel(6.0))
        assertEquals("Invalid measurement", ColourMetrics.qualityLabel(Double.NaN))
        val summary = ColourMetrics.summarize(doubleArrayOf(0.0, 10.0, 20.0))
        assertEquals(10.0, summary.mean, 1e-10)
        assertEquals(19.0, summary.p95, 1e-10)
    }

    @Test fun referenceGateAndIdentityCases() {
        assertEquals(100.0, ColourMetrics.lab(1.0, 1.0, 1.0).l, 0.00001)
        assertEquals(0.0, ColourMetrics.deltaE2000(ColourMetrics.lab(0.2, 0.5, 0.6), ColourMetrics.lab(0.2, 0.5, 0.6)), 1e-12)
        val identity = ColourMetrics.Summary(10.12, 24.54, 100)
        assertTrue(ColourMetrics.meetsGojoGate(identity, ColourMetrics.Summary(4.86, 11.36, 100), ColourMetrics.Summary(3.87, 8.44, 100)))
        assertFalse(ColourMetrics.meetsGojoGate(identity, ColourMetrics.Summary(7.0, 10.0, 100), ColourMetrics.Summary(3.87, 8.44, 100)))
    }
}
