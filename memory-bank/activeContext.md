# Active context

- 2026-11-10: Agora calling — apigw holds App ID/Certificate; driver joins via server token. Needs Firebase (`google-services.json`) for Firestore signaling + FCM. Local `.env`/`local.properties` seeded with `API_HOST=https://dev-apigw.durpalla.com`.
- 2026-11-08: Multi-boat — Add Vehicle registers solo boat via `POST vehicles`; fleet drivers get admin assignment; bid picker when >1 boat; `me.vehicles[]`.
- 2026-11-08: Vehicles “+ Add” opens Add Vehicle form (solo) / KYC still for docs.
- 2026-11-07: Release signing — `scripts/generate_keystore.sh` + `scripts/run_release.sh` (`.env` STORE_* / API); `assembleRelease` uses `keystore/durpalla-driver-release.jks`.
- 2026-11-07: Override R8 `9.1.29` in `settings.gradle.kts` so Kotlin 2.4 + AGP 8.7.3 release builds stop D8 kotlin-metadata warnings.
- 2026-11-07: Create trip — two departure modes: fixed `departure` (HH:MM) or `openAnyTime` (skip time); API + migration `open_any_time` on `driver_predefined_trips`.
- 2026-11-07: Fingerprint login — skippable after set-password register (`BiometricSetupScreen`); Profile toggle; password screen unlock via BiometricPrompt + EncryptedSharedPreferences.
- 2026-11-07: Avatar EXIF orientation — `AvatarCrop.decodeOriented` + CameraX `targetRotation` so profile selfies stay upright across devices.
- 2026-11-07: Profile photo = front-camera selfie only (no gallery) + ML Kit live face presence before capture; crop 100×100 then `POST me/avatar`.
- 2026-11-07: KYC docs upload to server on pick (`POST kyc/documents/{type}`); submit finalizes; admin **Drivers / KYC** reviews approve/reject.
- 2026-11-07: Auth like customer — `POST auth/check` → OTP (first) or password login; after OTP `needsPassword` → `SetPasswordScreen` (`POST auth/password`); later `POST auth/login`.
- 2026-11-07: Driver display name + 100×100 avatar — blank OTP name gates `DisplayNameScreen` (`PATCH me`); Profile Change photo crops JPEG via `AvatarCrop` → `POST me/avatar`; `displayName` falls back to phone.
- 2026-11-07: First-register `driver.welcome` notification (feed + FCM title/body; localized in Notifications list).
- 2026-11-07: KYC view/re-upload (GET `kyc/documents/{type}`), rejected status mapping; request feed refresh after service-area save; home shows RFQs while offline.
- 2026-11-07: OTP SMS auto-read via Google SMS User Consent (`SmsOtpUserConsent` + `SmsOtpParser`); fills boxes and auto-verifies (same pattern as customer Flutter app).
- 2026-11-07: Debug install via `scripts/run_debug.sh` + `.env` (`API_HOST`, Pusher, reCAPTCHA) → syncs `local.properties` then `adb install` on paired device.
- 2026-10-20: Added Cursor token-saving rules, ignores and this memory bank.
- Next: wire real API client (currently mock/fake data).

- 2026-10-20: Calls are routed per call by `CallProviderRouter` (server `provider`: agora default, webrtc; missing = webrtc). `AgoraCallProvider` (io.agora.rtc:voice-sdk 4.6.4, token from GET /calls/{id}/agora-token) + `WebRtcCallProvider`; Fake when Firebase is unconfigured. Contract: docs/calling-contract.md §0.
