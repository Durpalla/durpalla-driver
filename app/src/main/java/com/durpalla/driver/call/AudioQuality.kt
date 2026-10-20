package com.durpalla.driver.call

/**
 * Audio tuning shared with the customer apps (docs/calling-contract.md, "Audio quality").
 * Pure Kotlin so it is unit-tested on the JVM.
 */
object AudioQuality {
    const val OPUS_FMTP = "useinbandfec=1;usedtx=1;stereo=0;maxaveragebitrate=24000;maxplaybackrate=16000;ptime=20"
    const val PTIME_MS = 20
    const val STATS_INTERVAL_MS = 2_000L
}

/** Rewrites an SDP so the audio m-line offers Opus only (mono, FEC, DTX, 24 kbps cap, 16 kHz playback, 20 ms). */
object SdpMunger {
    fun opusOnly(sdp: String): String {
        val eol = if (sdp.contains("\r\n")) "\r\n" else "\n"
        val lines = sdp.split(eol).let { if (it.lastOrNull().isNullOrEmpty()) it.dropLast(1) else it }
        val out = mutableListOf<String>()
        var inAudio = false
        var opus: String? = null
        // Opus payload per audio section is found first so we can drop the other codecs' attributes.
        val opusByLine = mutableMapOf<Int, String?>()
        run {
            var start = -1
            lines.forEachIndexed { i, l ->
                if (l.startsWith("m=")) start = if (l.startsWith("m=audio")) i else -1
                if (start >= 0 && opusByLine[start] == null) Regex("^a=rtpmap:(\\d+) opus/48000", RegexOption.IGNORE_CASE).find(l)?.let { opusByLine[start] = it.groupValues[1] }
            }
        }
        var wroteFmtp = false; var hadPtime = false
        fun closeAudio() {
            val pt = opus ?: return
            if (!wroteFmtp) out += "a=fmtp:$pt ${AudioQuality.OPUS_FMTP}"
            if (!hadPtime) out += "a=ptime:${AudioQuality.PTIME_MS}"
        }
        lines.forEachIndexed { i, l ->
            if (l.startsWith("m=")) {
                if (inAudio) closeAudio()
                inAudio = l.startsWith("m=audio"); opus = if (inAudio) opusByLine[i] else null; wroteFmtp = false; hadPtime = false
                if (inAudio && opus != null) {
                    val p = l.split(" "); out += (p.take(3) + opus!!).joinToString(" "); return@forEachIndexed
                }
            }
            val pt = opus
            if (inAudio && pt != null) {
                val m = Regex("^a=(rtpmap|fmtp|rtcp-fb):(\\d+)").find(l)
                if (m != null && m.groupValues[2] != pt) return@forEachIndexed
                if (m != null && m.groupValues[1] == "fmtp") { out += "a=fmtp:$pt ${AudioQuality.OPUS_FMTP}"; wroteFmtp = true; return@forEachIndexed }
                if (l.startsWith("a=ptime:")) { out += "a=ptime:${AudioQuality.PTIME_MS}"; hadPtime = true; return@forEachIndexed }
                if (l.startsWith("a=maxptime:")) return@forEachIndexed
            }
            out += l
        }
        if (inAudio) closeAudio()
        return out.joinToString(eol) + eol
    }
}

/**
 * Adaptive Opus bitrate from getStats every 2 s: 24 → 12 → 8 kbps one step per bad sample
 * (loss > 5 % or RTT > 400 ms); one step back up after 3 consecutive good samples (loss < 2 % and RTT < 250 ms).
 */
class BitrateController {
    companion object {
        val LEVELS = intArrayOf(24_000, 12_000, 8_000)
        const val BAD_LOSS = 0.05; const val BAD_RTT_MS = 400.0
        const val GOOD_LOSS = 0.02; const val GOOD_RTT_MS = 250.0
        const val GOOD_SAMPLES_TO_RECOVER = 3
    }
    var level = 0; private set
    private var good = 0
    val bitrateBps get() = LEVELS[level]
    /** Shown as the "Weak connection" hint. */
    val weak get() = level > 0

    /** Returns the bitrate to apply after this sample. [lossFraction] 0..1, null = unknown (treated as 0). */
    fun onSample(lossFraction: Double?, rttMs: Double?): Int {
        val loss = lossFraction ?: 0.0; val rtt = rttMs ?: 0.0
        when {
            loss > BAD_LOSS || rtt > BAD_RTT_MS -> { good = 0; if (level < LEVELS.lastIndex) level++ }
            loss < GOOD_LOSS && rtt < GOOD_RTT_MS -> { if (level > 0 && ++good >= GOOD_SAMPLES_TO_RECOVER) { level--; good = 0 } }
            else -> good = 0
        }
        return bitrateBps
    }
    fun reset() { level = 0; good = 0 }
}
