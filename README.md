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
