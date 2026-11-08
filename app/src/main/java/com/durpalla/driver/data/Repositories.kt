package com.durpalla.driver.data

import com.durpalla.driver.domain.*
import kotlinx.coroutines.flow.StateFlow

/*
 * Repository contracts. Implemented by the Api* repositories (Durpalla apigw `/api/v1/driver/`).
 * Tests provide their own in-memory doubles.
 */

/** Post-login gates: set password (first OTP), display name, then KYC/home. */
data class AuthGate(
    val needsPassword: Boolean = false,
    val needsName: Boolean = false,
    val kycComplete: Boolean = false,
)

enum class AuthStep { OTP, LOGIN }

interface AuthRepository {
    /** Existing password → LOGIN; otherwise sends OTP → OTP (customer parity). */
    suspend fun checkPhone(phone: String, captchaToken: String?): Result<AuthStep>
    /** [captchaToken] = reCAPTCHA Enterprise token for action LOGIN (null when bot protection is off). */
    suspend fun requestOtp(phone: String, captchaToken: String?): Result<Unit>
    /** FCM registration token -> backend (POST devices), so it can push to this device. */
    suspend fun registerDeviceToken(token: String): Result<Unit>
    /** Stores the session on success. */
    suspend fun verifyOtp(phone: String, code: String): Result<AuthGate>
    /** Password login for returning drivers. */
    suspend fun loginWithPassword(phone: String, password: String): Result<AuthGate>
    /** After first OTP (authenticated). */
    suspend fun setPassword(password: String): Result<Unit>
    /** Upload one KYC photo immediately (multipart). */
    suspend fun uploadKycDocument(docType: String, uri: String): Result<Unit>
    /** Finalize KYC after documents are on the server. [files] optional extra multipart photos. */
    suspend fun submitKyc(type: VehicleType, files: Map<String, String> = emptyMap()): Result<Unit>
    suspend fun logout(): Result<Unit>
}

interface RequestRepository {
    val requests: StateFlow<List<TripRequest>>
    val bids: StateFlow<List<Bid>>
    suspend fun refresh(): Result<Unit>
    suspend fun placeBid(requestId: String, amount: Int, note: String, boatId: String? = null): Result<Bid>
    /** POST requests/{id}/skip (hidden from feed + fan-out). */
    suspend fun skip(requestId: String)
    suspend fun withdrawBid(bidId: String): Result<Unit>
    // Realtime (Pusher/FCM) updates
    suspend fun upsertRequest(r: TripRequest)
    suspend fun updateCompetition(requestId: String, bidCount: Int)
    /** Customer accepted this bid: mark won, expire overlapping bids, load the trip from the API. */
    suspend fun accepted(bidId: String, tripId: String, tripCode: String, advancePaid: Int): Result<Trip>
    suspend fun rejected(bidId: String)
    fun clear()
}

interface TripRepository {
    val trips: StateFlow<List<Trip>>
    /** GET/POST/PUT/DELETE predefined-trips. */
    val predefined: StateFlow<List<PredefinedTrip>>
    val blockedDays: StateFlow<Set<java.time.LocalDate>>
    suspend fun refresh(): Result<Unit>
    suspend fun refreshTrip(tripId: String): Result<Trip>
    /** ON_THE_WAY / ARRIVED / IN_PROGRESS → POST status; COMPLETED + [method] → POST collect. CANCELLED is local (server-driven). */
    suspend fun updateStatus(tripId: String, status: TripStatus, method: PaymentMethod? = null): Result<Unit>
    suspend fun savePredefined(trip: PredefinedTrip): Result<Unit>
    suspend fun deletePredefined(id: String): Result<Unit>
    suspend fun toggleBlocked(day: java.time.LocalDate): Result<Unit>
    fun clear()
}

interface EarningsRepository {
    val summary: StateFlow<EarningsSummary>
    val transactions: StateFlow<List<WalletTx>>
    /** Saved payout accounts (GET payout-accounts), default first. */
    val accounts: StateFlow<List<WithdrawAccount>>
    suspend fun refresh(): Result<Unit>
    suspend fun reconciliation(period: String): Result<Reconciliation>
    suspend fun withdraw(account: WithdrawAccount, amount: Int, pin: String): Result<WalletTx>
    suspend fun addAccount(method: PaymentMethod, account: String): Result<WithdrawAccount>
    fun clear()
}

interface ProfileRepository {
    val driver: StateFlow<Driver>
    val ratings: StateFlow<List<Rating>>
    /** GET notifications (server-recorded feed). */
    val notifications: StateFlow<List<AppNotification>>
    suspend fun refresh(): Result<Unit>
    suspend fun setOnline(online: Boolean): Result<Unit>
    suspend fun refreshNotifications(): Result<Unit>
    suspend fun markNotificationsRead(): Result<Unit>
    suspend fun loadServiceStoppages(): Result<List<ServiceStoppage>>
    suspend fun saveServiceStoppages(ids: List<Int>): Result<List<ServiceStoppage>>
    suspend fun updateName(name: String): Result<Driver>
    /** JPEG bytes already cropped to 100×100 (customer parity). */
    suspend fun uploadAvatarJpeg(bytes: ByteArray): Result<Driver>
    /** Solo operator: POST vehicles {title, registrationNo?, capacity?, vehicleType?}. */
    suspend fun registerVehicle(title: String, registrationNo: String, capacity: Int, vehicleType: VehicleType): Result<Driver>
    fun clear()
}
