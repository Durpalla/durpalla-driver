# Active context

- 2026-11-07: Driver display name + 100×100 avatar — blank OTP name gates `DisplayNameScreen` (`PATCH me`); Profile Change photo crops JPEG via `AvatarCrop` → `POST me/avatar`; `displayName` falls back to phone.
- 2026-11-07: First-register `driver.welcome` notification (feed + FCM title/body; localized in Notifications list).
- 2026-11-07: KYC view/re-upload (GET `kyc/documents/{type}`), rejected status mapping; request feed refresh after service-area save; home shows RFQs while offline.
- 2026-11-07: OTP SMS auto-read via Google SMS User Consent (`SmsOtpUserConsent` + `SmsOtpParser`); fills boxes and auto-verifies (same pattern as customer Flutter app).
- 2026-11-07: Debug install via `scripts/run_debug.sh` + `.env` (`API_HOST`, Pusher, reCAPTCHA) → syncs `local.properties` then `adb install` on paired device.
- 2026-10-20: Added Cursor token-saving rules, ignores and this memory bank.
- Next: wire real API client (currently mock/fake data).

- 2026-10-20: Calls are routed per call by `CallProviderRouter` (server `provider`: agora default, webrtc; missing = webrtc). `AgoraCallProvider` (io.agora.rtc:voice-sdk 4.6.4, token from GET /calls/{id}/agora-token) + `WebRtcCallProvider`; Fake when Firebase is unconfigured. Contract: docs/calling-contract.md §0.
