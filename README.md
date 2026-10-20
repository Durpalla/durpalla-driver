# Durpalla Driver App (Android)

Driver app for **Durpalla Limited**: Speedboat/Boat, Taxi and Microbus drivers in Bangladesh receive customer trip requests, bid a rent, and run confirmed trips.
Kotlin · Jetpack Compose · Material 3 · Navigation Compose · MVVM (ViewModel + StateFlow) · minSdk 24 / target 35.

## Setup
1. Android Studio Ladybug (2024.2) or newer, JDK 17.
2. Open this folder → let Gradle sync (AGP 8.7.3, Kotlin 2.0.21, Gradle 8.10.2 wrapper).
3. Run the `app` configuration, or from CLI:
   ```
   ./gradlew assembleDebug          # app/build/outputs/apk/debug/app-debug.apk
   ./gradlew testDebugUnitTest      # slot-lock / bid / commission rules
   ```
Demo login: any 11-digit number, any 6-digit OTP. KYC needs 3+ documents "uploaded" (tap to toggle).

## Business rules (domain layer)
- Customer opens a trip request (vehicle, route, slot, passengers) → nearby drivers are notified → drivers bid → customer picks one.
- **Slot lock** (`domain/Rules.kt › SlotLockPolicy`): a confirmed trip (UPCOMING/ON_THE_WAY/ARRIVED/IN_PROGRESS) locks its time window (+30 min buffer). Overlapping requests show **Slot booked**, bidding is disabled, and on winning, other overlapping pending bids are auto-expired.
- `BidRules` validates amount and conflicts. `Commission` = 10% of rent. `Reconciliation` computes gross, commission, net, paid out, cash collected, and pending settlement.
- Unit tests: `app/src/test/.../SlotLockPolicyTest.kt`.

## Structure
```
app/src/main/java/com/durpalla/driver/
  MainActivity.kt            single activity, edge-to-edge
  domain/                    Models, Rules (slot lock, bids, commission), Format (৳, slots)
  data/Repositories.kt       repository interfaces (Auth, Request, Trip, Earnings, Profile)
  data/FakeData.kt           in-memory fakes + sample data + ServiceLocator (manual DI)
  ui/DriverViewModel.kt      StateFlow state + actions
  ui/nav/AppNav.kt           NavHost, routes, bottom nav (Home/Requests/My Trips/Earnings/Profile)
  ui/theme/Theme.kt          Durpalla driver palette (primary #0E5F7E, accent #F28C28, ...)
  ui/components/             cards, buttons, sticky CTA, chips, tabs, map placeholder, ...
  ui/screens/                Auth, Request, Trip, Earnings, Profile screens (each has @Preview)
```

## Screens (34)
Splash · Phone login · OTP · KYC vehicle type · KYC documents · KYC under review ·
Home · Requests (New) · My Bids (Pending/Won/Lost/Expired) · Incoming request (map, countdown) · Place bid (+ submitted sheet) · Bid won · Slot conflict ·
My Trips (Upcoming/Pre-defined/History) · Trip detail · Live trip · Collect payment · Trip completed (+ rate customer) · Pre-defined list · Create/Edit pre-defined trip · Availability calendar · Trip history ·
Earnings (Canvas bar chart) · Withdraw (+ PIN sheet) · Withdraw success · Reconciliation (PDF/CSV) · Wallet · Ratings ·
Profile · Vehicles & documents · Notifications · Support.
Demo: in My Bids tap a *Pending* bid → "Demo: customer chooses me" to see Bid won + slot lock.

## Plugging in the REST API
1. Add Retrofit/Ktor + kotlinx-serialization to `gradle/libs.versions.toml`.
2. Implement the interfaces in `data/Repositories.kt`, e.g. `class ApiRequestRepository(api: DriverApi) : RequestRepository`, mapping DTOs to `domain` models. Base URL: `BuildConfig.API_BASE_URL` (app/build.gradle.kts).
3. Swap the bindings in `ServiceLocator` (or move to Hilt). UI and ViewModel need no changes.
4. Real-time requests/bid results: push via FCM or WebSocket into the `requests`/`bids` StateFlows.
5. Keep the slot-lock check on the **server** too (authoritative); the client rule is for UX.
6. Map: replace `MapPlaceholder` with Google Maps Compose / Mapbox.

## Realtime, push & bot protection

### Configuration
Put keys in `local.properties` (not committed), `~/.gradle/gradle.properties`, `-P` flags or env vars:
```
API_BASE_URL=https://api.durpalla.com/driver/v1/
PUSHER_KEY=xxxxxxxxxxxxxxxxxxxx
PUSHER_CLUSTER=ap2
PUSHER_AUTH_URL=https://api.durpalla.com/driver/v1/realtime/auth
RECAPTCHA_SITE_KEY=6Lxxxxxxxxxxxxxxxxxxxxxx
```
All are optional. With an empty `PUSHER_KEY` the app uses `FakeRealtimeClient`, which simulates competitor bids,
a new request and the customer's accept/reject about 8 s after you bid. With an empty `RECAPTCHA_SITE_KEY` bot protection is a no-op.

### Firebase Cloud Messaging
1. Firebase console → add Android app `com.durpalla.driver` → download `google-services.json` into `app/`
   (see `app/google-services.json.example`). The Google Services plugin is applied **only if that file exists**, so CI builds without it.
2. `DurpallaMessagingService` handles `onNewToken` (→ `AuthRepository.registerDeviceToken`) and data messages.
3. Channels: `trip_requests` (high, sound + vibration, full-screen intent), `bids` (high), `payments`, `general`.
4. Android 13+: `POST_NOTIFICATIONS` is requested when the driver first reaches Home.
5. Notification taps deep-link via the `durpalla_route` extra or FCM `route` key: `request/{id}`, `won/{tripId}`, `trip/{id}`, `requests?tab=1`, `wallet`.

### Pusher Channels
`RealtimeClient` (`realtime/`) → `PusherRealtimeClient` subscribes to `private-driver.{driverId}`, plus `private-trip.{tripId}` for confirmed trips.
Events flow into `DriverViewModel` → `BidReducer` (live lowest bid / bid count / my status) and the repositories.
UI: `LiveIndicator` (Live / Reconnecting / Offline), `LiveBidPanel` on Trip request and Place bid, and `LiveBannerCard`
(high-priority in-app banner, e.g. "Customer accepted your bid!", which opens Bid won). Events received while the app is in the background become system notifications.

### Bot protection (reCAPTCHA)
reCAPTCHA v3 is web-only. On Android we use **reCAPTCHA Enterprise for mobile** (`com.google.android.recaptcha`),
which is likewise invisible and score-based. `BotProtection.token("LOGIN")` runs before *Send OTP*, and the token is sent with the OTP request.
Create an **Android** key in Google Cloud → reCAPTCHA Enterprise (package `com.durpalla.driver`) and set `RECAPTCHA_SITE_KEY`.

**Firebase App Check:** `DriverApplication` installs the Play Integrity provider when Firebase is configured.
Enable App Check → Play Integrity in the Firebase console (register your SHA-256), and enforce it for your backend/Firebase APIs.
Use the debug provider for emulators.

### What the backend must implement
| Area | Contract |
|---|---|
| Device tokens | `POST /devices {fcmToken, platform:"android", driverId}`. Upsert on every `onNewToken`. Delete on logout. |
| FCM sending | **Data** messages (high priority for `trip.requested` and `bid.accepted`): `{event, payload: "<JSON string>", route?, channel?, title?, body?}`. Same `payload` schema as Pusher. Collapse key = `requestId`/`tripId`. |
| Pusher auth | `POST PUSHER_AUTH_URL` (form: `socket_id`, `channel_name`, header `Authorization: Bearer <driver JWT>`). Allow only `private-driver.{ownId}` and `private-trip.{tripId}` where the driver is assigned. Reply with Pusher's signed `{auth}`. |
| Pusher triggers | Server SDK `trigger("private-driver.{id}", event, payload)` to every eligible driver (nearby, right vehicle type, **slot free**). |
| OTP + reCAPTCHA | `POST /auth/otp {phone, recaptchaToken}` → `projects.assessments.create` (siteKey, token, expectedAction `LOGIN`). Reject if `tokenProperties.valid=false`, action mismatch, or `riskAnalysis.score < 0.5` (tune). Rate-limit per phone/IP. |
| Slot lock | Authoritative on the server: on accept, lock the driver's slot, auto-withdraw their overlapping pending bids, send `slot.locked`. |

**Event payloads** (times are ISO-8601 local; amounts are integer BDT):
```jsonc
trip.requested   {"request":{"id","vehicleType":"SPEEDBOAT|BOAT|TAXI|MICROBUS","from","to","start","end","passengers",
                  "customer":{"name","rating","trips"},"suggestedMin","suggestedMax","bidsCount","expiresInSec","note","distanceKm"}}
bid.placed       {"requestId","bidCount","lowestBid"}              // competitors, anonymised
bid.updated      {"bidId","requestId","amount","bidCount","lowestBid"}
bid.accepted     {"bidId","requestId","tripId","tripCode","advancePaid"}
bid.rejected     {"bidId","requestId","reason"}
trip.cancelled   {"tripId","reason"}
slot.locked      {"tripId","start","end"}      slot.released {"tripId"}
payment.received {"tripId","amount","method":"CASH|BKASH|NAGAD|BANK"}
```
Deliver every event over both Pusher and FCM. The app de-duplicates (`accepted()` is idempotent by `tripId`).

### Brand buttons
`ui/components/Buttons.kt`: `DurpallaButton(variant = Primary | Accent | Secondary | Text | Danger | OnDark, size = Large | Medium | Small, loading, icon)`,
plus `DurpallaIconButton` and `DurpallaFab`. Use **Accent** (#F28C28) only for each screen's key CTA (Place bid, Send OTP, Withdraw, Start trip).
Chips and switches use `brandChipColors()` / `brandSwitchColors()`.
