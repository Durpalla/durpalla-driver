package com.durpalla.driver.call

import org.junit.Assert.*
import org.junit.Test

class AudioQualityTest {
    private val offer = listOf(
        "v=0", "o=- 1 2 IN IP4 127.0.0.1", "s=-", "t=0 0",
        "m=audio 9 UDP/TLS/RTP/SAVPF 111 63 9 0 8 13 110 126",
        "c=IN IP4 0.0.0.0", "a=mid:0", "a=sendrecv",
        "a=rtpmap:111 opus/48000/2", "a=rtcp-fb:111 transport-cc", "a=fmtp:111 minptime=10;useinbandfec=1",
        "a=rtpmap:63 red/48000/2", "a=fmtp:63 111/111",
        "a=rtpmap:9 G722/8000", "a=rtpmap:0 PCMU/8000", "a=rtpmap:8 PCMA/8000",
        "a=rtpmap:13 CN/8000", "a=rtpmap:110 telephone-event/48000", "a=rtpmap:126 telephone-event/8000",
        "a=ssrc:1 cname:x",
    ).joinToString("\r\n") + "\r\n"

    @Test fun opusOnlyMonoFmtp() {
        val out = SdpMunger.opusOnly(offer)
        val lines = out.split("\r\n")
        assertTrue(lines.contains("m=audio 9 UDP/TLS/RTP/SAVPF 111"))
        assertTrue(lines.contains("a=fmtp:111 useinbandfec=1;usedtx=1;stereo=0;maxaveragebitrate=24000;maxplaybackrate=16000;ptime=20"))
        assertTrue(lines.contains("a=ptime:20"))
        assertTrue(lines.contains("a=rtcp-fb:111 transport-cc"))
        listOf("red/", "G722", "PCMU", "PCMA", "CN/", "telephone-event", "a=fmtp:63").forEach { assertFalse(it, out.contains(it)) }
        assertTrue(lines.contains("a=ssrc:1 cname:x")); assertTrue(out.endsWith("\r\n"))
        assertEquals(1, lines.count { it.startsWith("a=fmtp:") })
    }

    @Test fun addsFmtpWhenMissingAndIsIdempotent() {
        val bare = "v=0\nm=audio 9 RTP/SAVPF 111 0\na=rtpmap:111 opus/48000/2\na=rtpmap:0 PCMU/8000\nm=video 0 RTP/SAVPF 96\na=rtpmap:96 VP8/90000\n"
        val out = SdpMunger.opusOnly(bare)
        assertTrue(out.contains("a=fmtp:111 ${AudioQuality.OPUS_FMTP}\na=ptime:20\nm=video"))
        assertTrue("non-audio sections untouched", out.contains("a=rtpmap:96 VP8/90000"))
        assertEquals(out, SdpMunger.opusOnly(out))
    }

    @Test fun noOpusLeavesSdpAlone() {
        val s = "v=0\nm=audio 9 RTP/SAVPF 0\na=rtpmap:0 PCMU/8000\n"
        assertEquals(s, SdpMunger.opusOnly(s))
    }

    @Test fun bitrateStepsDownAndRecovers() {
        val c = BitrateController()
        assertEquals(24_000, c.bitrateBps); assertFalse(c.weak)
        assertEquals(12_000, c.onSample(0.06, 100.0)); assertTrue(c.weak)
        assertEquals(8_000, c.onSample(0.0, 450.0))
        assertEquals(8_000, c.onSample(0.2, 900.0)) // floor
        assertEquals(8_000, c.onSample(0.0, 50.0)); assertEquals(8_000, c.onSample(0.0, 50.0))
        assertEquals(12_000, c.onSample(0.0, 50.0)) // 3 good samples → one step up
        assertEquals(12_000, c.onSample(0.03, 300.0)) // in-between resets the good streak
        repeat(2) { c.onSample(0.01, 100.0) }; assertEquals(12_000, c.bitrateBps)
        assertEquals(24_000, c.onSample(0.01, 100.0)); assertFalse(c.weak)
        assertEquals(24_000, c.onSample(null, null))
    }

    @Test fun thresholdsAreStrict() {
        val c = BitrateController()
        assertEquals(24_000, c.onSample(0.05, 400.0)) // exactly 5 % / 400 ms is not bad
        assertEquals(12_000, c.onSample(0.0501, 0.0))
    }
}
