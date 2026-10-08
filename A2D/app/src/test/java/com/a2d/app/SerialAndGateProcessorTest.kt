package com.a2d.app

import com.a2d.app.domain.engine.SerialAndGateProcessor
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class SerialAndGateProcessorTest {

    private lateinit var processor: SerialAndGateProcessor

    @Before
    fun setup() {
        processor = SerialAndGateProcessor()
    }

    @Test
    fun testHighPulsesLatchUntilArityMet() {
        val pulse1 = processor.processPulse("2", "HIGH")
        assertEquals(0, pulse1.output)
        assertEquals(listOf(1), pulse1.latchedInputs)
        assertFalse(pulse1.reset)

        val pulse2 = processor.processPulse("2", "HIGH")
        assertEquals(1, pulse2.output)
        assertEquals(listOf(1, 1), pulse2.latchedInputs)
        assertFalse(pulse2.reset)
    }

    @Test
    fun testLowPulseResetsPin() {
        processor.processPulse("3", "HIGH")
        val resetResult = processor.processPulse("3", "LOW")

        assertEquals(0, resetResult.output)
        assertTrue(resetResult.latchedInputs.isEmpty())
        assertTrue(resetResult.reset)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testInvalidPulseValueThrowsException() {
        processor.processPulse("2", "INVALID")
    }

    @Test(expected = IllegalArgumentException::class)
    fun testNonIntegerPinThrowsException() {
        processor.processPulse("ABC", "HIGH")
    }
}
