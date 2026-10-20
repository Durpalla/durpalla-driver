package com.durpalla.driver.data

import com.durpalla.driver.domain.*
import kotlinx.coroutines.flow.StateFlow

/*
 * Repository contracts. The app only talks to these interfaces; swap the Fake* implementations
 * in [ServiceLocator] for Retrofit/Ktor-backed ones to plug in the real Durpalla REST API.
 */

interface AuthRepository {
    /** [captchaToken] = reCAPTCHA Enterprise token for action LOGIN (null when bot protection is off). */
    suspend fun requestOtp(phone: String, captchaToken: String?): Result<Unit>
    /** FCM registration token -> backend, so it can push to this device. */
    suspend fun registerDeviceToken(token: String): Result<Unit>
    suspend fun verifyOtp(phone: String, code: String): Result<Boolean> // true = KYC complete
    suspend fun submitKyc(type: VehicleType, docs: List<String>): Result<Unit>
}

interface RequestRepository {
    val requests: StateFlow<List<TripRequest>>
    val bids: StateFlow<List<Bid>>
    suspend fun placeBid(requestId: String, amount: Int, note: String): Result<Bid>
    suspend fun skip(requestId: String)
    /** Simulates the customer choosing this driver's bid. */
    suspend fun simulateWin(bidId: String): Result<Trip>
    // Realtime (Pusher/FCM) updates
    suspend fun upsertRequest(r: TripRequest)
    suspend fun updateCompetition(requestId: String, bidCount: Int)
    /** Customer accepted this bid: mark won, expire overlapping bids, create the trip. */
    suspend fun accepted(bidId: String, tripId: String, tripCode: String, advancePaid: Int): Result<Trip>
    suspend fun rejected(bidId: String)
}

interface TripRepository {
    val trips: StateFlow<List<Trip>>
    val predefined: StateFlow<List<PredefinedTrip>>
    val blockedDays: StateFlow<Set<java.time.LocalDate>>
    suspend fun addTrip(trip: Trip)
    suspend fun updateStatus(tripId: String, status: TripStatus, method: PaymentMethod? = null)
    suspend fun savePredefined(trip: PredefinedTrip)
    suspend fun toggleBlocked(day: java.time.LocalDate)
}

interface EarningsRepository {
    val summary: StateFlow<EarningsSummary>
    val transactions: StateFlow<List<WalletTx>>
    val accounts: List<WithdrawAccount>
    suspend fun reconciliation(period: String): Reconciliation
    suspend fun withdraw(account: WithdrawAccount, amount: Int, pin: String): Result<WalletTx>
}

interface ProfileRepository {
    val driver: StateFlow<Driver>
    val ratings: StateFlow<List<Rating>>
    val notifications: StateFlow<List<AppNotification>>
}
