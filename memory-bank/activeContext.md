# Active context

- 2026-11-07: Debug install via `scripts/run_debug.sh` + `.env` (`API_HOST`, Pusher, reCAPTCHA) → syncs `local.properties` then `installDebug` on paired device.
- 2026-10-20: Added Cursor token-saving rules, ignores and this memory bank.
- Next: wire real API client (currently mock/fake data).

- 2026-10-20: Calls are routed per call by `CallProviderRouter` (server `provider`: agora default, webrtc; missing = webrtc). `AgoraCallProvider` (io.agora.rtc:voice-sdk 4.6.4, token from GET /calls/{id}/agora-token) + `WebRtcCallProvider`; Fake when Firebase is unconfigured. Contract: docs/calling-contract.md §0.
