package helium314.keyboard.latin.vibevoice

import helium314.keyboard.latin.vibevoice.VibeVoiceClient.Companion.BYTES_PER_MS
import helium314.keyboard.latin.vibevoice.VibeVoiceClient.Companion.resendPlan
import helium314.keyboard.latin.vibevoice.VibeVoiceClient.Companion.trimLeadingOverlap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Where a reconnect resends audio from. Positions are bytes in the capture; the server's clock is
 * milliseconds from the first byte it received, which sits at `origin` in the capture.
 */
class StreamResumeTest {
    private fun ms(v: Long) = v * BYTES_PER_MS

    /** The 2026-10-03 12:42 case against a server with resume: 9.0 s held, 9.4 s sent. */
    @Test fun resumedResendsOnlyWhatWasInFlight() {
        val origin = ms(200) // 0.2 s of capture before the first byte reached the server
        val plan = resendPlan(resumed = true, receivedMs = 9000, streamOriginPos = origin, ackedPos = -1, lastPiecePos = -1)
        assertTrue(plan.sameSession)
        assertFalse(plan.trimOverlap)
        assertEquals(origin + ms(9000), plan.from)
    }

    @Test fun resumedIgnoresAckAndPieces() {
        val plan = resendPlan(true, 30_000, ms(100), ackedPos = ms(5000), lastPiecePos = ms(20_000))
        assertEquals(ms(100) + ms(30_000), plan.from)
        assertTrue(plan.sameSession)
    }

    /** Token unknown or expired: a new server session, resend exactly from the last ack. */
    @Test fun notResumedResendsFromAck() {
        val plan = resendPlan(false, -1, ms(100), ackedPos = ms(100) + ms(24_820), lastPiecePos = ms(30_000))
        assertFalse(plan.sameSession)
        assertTrue(plan.trimOverlap) // held-back text keeps ack_ms at the start of a delivered segment
        assertEquals(ms(100) + ms(24_820), plan.from)
    }

    /** "resumed": true without received_ms is not trusted as a resume. */
    @Test fun resumedWithoutReceivedMsFallsBack() {
        val plan = resendPlan(true, -1, ms(100), ackedPos = ms(4000), lastPiecePos = -1)
        assertFalse(plan.sameSession)
        assertEquals(ms(4000), plan.from)
    }

    /** Today's production server: no token, no ack. Guess from the last piece, trim the overlap. */
    @Test fun oldServerResendsFromLastPieceLessMargin() {
        val plan = resendPlan(false, -1, ms(100), ackedPos = -1, lastPiecePos = ms(20_000))
        assertFalse(plan.sameSession)
        assertTrue(plan.trimOverlap)
        assertEquals(ms(19_000), plan.from)
    }

    @Test fun marginNeverReachesBeforeTheStream() {
        val plan = resendPlan(false, -1, ms(800), ackedPos = -1, lastPiecePos = ms(1200))
        assertEquals(ms(800), plan.from)
    }

    /** The 12:42 case against today's server: nothing came back, so everything is sent again. */
    @Test fun nothingDeliveredResendsTheWholeStream() {
        val plan = resendPlan(false, -1, ms(150), ackedPos = -1, lastPiecePos = -1)
        assertEquals(ms(150), plan.from)
        assertFalse(plan.trimOverlap)
    }

    @Test fun socketThatNeverOpenedResendsFromCaptureStart() {
        assertEquals(0L, resendPlan(false, -1, -1, -1, -1).from)
    }

    @Test fun trimsRepeatedWordsIgnoringCaseAndPunctuation() {
        assertEquals("und dann weiter.", trimLeadingOverlap("Wir haben einen FTP-Server, oder?", "oder und dann weiter."))
        assertEquals("ist erreichbar", trimLeadingOverlap("vom Internet aus", "Internet aus ist erreichbar"))
    }

    @Test fun keepsTextWithoutOverlap() {
        assertEquals("Ganz neuer Satz.", trimLeadingOverlap("Das war der alte.", "Ganz neuer Satz."))
        assertEquals("text", trimLeadingOverlap("", "text"))
    }

    @Test fun prefersTheLongestOverlap() {
        assertEquals("c", trimLeadingOverlap("a b a b", "a b a b c"))
    }
}
