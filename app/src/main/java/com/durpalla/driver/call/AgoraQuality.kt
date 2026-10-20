package com.durpalla.driver.call

/**
 * Weak-network hint for Agora calls from onNetworkQuality (local uid 0, tx/rx 0..6) and onRtcStats
 * (packet loss %, lastmile RTT). Same thresholds as the WebRTC [BitrateController].
 */
class AgoraQuality {
    companion object {
        /** Agora Quality: 0 unknown, 1 excellent, 2 good, 3 poor, 4 bad, 5 very bad, 6 down. */
        const val BAD_QUALITY = 4
        const val GOOD_QUALITY = 2
    }
    private var level = 0
    private var good = 0
    val weak get() = level > 0

    fun onNetworkQuality(tx: Int, rx: Int) {
        val worst = maxOf(tx, rx)
        if (worst == 0) return
        sample(bad = worst >= BAD_QUALITY, ok = worst <= GOOD_QUALITY)
    }

    /** loss in percent (0..100) as Agora reports it. */
    fun onRtcStats(txLossPct: Int, rxLossPct: Int, rttMs: Int) {
        val loss = maxOf(txLossPct, rxLossPct) / 100.0
        sample(bad = loss >= BitrateController.BAD_LOSS || rttMs >= BitrateController.BAD_RTT_MS,
            ok = loss <= BitrateController.GOOD_LOSS && rttMs <= BitrateController.GOOD_RTT_MS)
    }

    private fun sample(bad: Boolean, ok: Boolean) {
        when {
            bad -> { level = 1; good = 0 }
            ok && level > 0 -> if (++good >= BitrateController.GOOD_SAMPLES_TO_RECOVER) { level = 0; good = 0 }
            else -> good = 0
        }
    }

    fun reset() { level = 0; good = 0 }
}
