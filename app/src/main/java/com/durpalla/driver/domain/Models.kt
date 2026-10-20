package com.durpalla.driver.domain

import java.time.LocalDate
import java.time.LocalDateTime

enum class VehicleType(val label: String, val emoji: String) {
    SPEEDBOAT("Speedboat", "🚤"), BOAT("Boat", "⛴️"), TAXI("Taxi", "🚕"), MICROBUS("Microbus", "🚐")
}

/** A time window a driver/vehicle is committed for. End is exclusive. */
data class TimeSlot(val start: LocalDateTime, val end: LocalDateTime) {
    init { require(end.isAfter(start)) { "Slot end must be after start" } }
    fun overlaps(other: TimeSlot): Boolean = start.isBefore(other.end) && other.start.isBefore(end)
}

data class Customer(val name: String, val rating: Double, val trips: Int)

enum class RequestStatus { OPEN, BIDDING, ASSIGNED, EXPIRED, CANCELLED }

data class TripRequest(
    val id: String,
    val vehicleType: VehicleType,
    val from: String,
    val to: String,
    val slot: TimeSlot,
    val passengers: Int,
    val customer: Customer,
    val suggestedMin: Int,
    val suggestedMax: Int,
    val bidsCount: Int,
    val expiresInSec: Int,
    val note: String = "",
    val distanceKm: Double = 0.0,
    val status: RequestStatus = RequestStatus.OPEN,
)

enum class BidStatus(val label: String) { PENDING("Pending"), WON("Won"), LOST("Lost"), EXPIRED("Expired") }

data class Bid(
    val id: String,
    val requestId: String,
    val request: TripRequest,
    val amount: Int,
    val note: String,
    val status: BidStatus,
)

enum class TripStatus { UPCOMING, ON_THE_WAY, ARRIVED, IN_PROGRESS, COMPLETED, CANCELLED }

enum class PaymentMethod(val label: String) { CASH("Cash"), BKASH("bKash"), NAGAD("Nagad"), BANK("Bank") }

data class Trip(
    val id: String,
    val code: String,
    val vehicleType: VehicleType,
    val from: String,
    val to: String,
    val slot: TimeSlot,
    val passengers: Int,
    val customer: Customer,
    val fare: Int,
    val advancePaid: Int,
    val status: TripStatus,
    val paymentMethod: PaymentMethod? = null,
    val ratingGiven: Int? = null,
    val fromPredefined: Boolean = false,
    /** trip.callLog from the API (see docs/calling-contract.md). */
    val callLog: List<com.durpalla.driver.call.CallLogEntry> = emptyList(),
)

data class PredefinedTrip(
    val id: String,
    val vehicleType: VehicleType,
    val from: String,
    val to: String,
    val departure: String,
    val days: Set<String>,
    val seats: Int,
    val rent: Int,
    val active: Boolean,
)

data class Rating(val customer: String, val stars: Int, val comment: String, val date: LocalDate, val tripCode: String)

data class Vehicle(val name: String, val type: VehicleType, val reg: String, val seats: Int, val verified: Boolean)

data class DocumentItem(val name: String, val status: DocStatus)
enum class DocStatus(val label: String) { VERIFIED("Verified"), UNDER_REVIEW("Under review"), MISSING("Upload"), EXPIRING("Expiring soon") }

data class Driver(
    val name: String,
    val phone: String,
    val rating: Double,
    val totalTrips: Int,
    val area: String,
    val vehicles: List<Vehicle>,
    val documents: List<DocumentItem>,
)

enum class TxType { EARNING, COMMISSION, WITHDRAWAL, ADJUSTMENT }
data class WalletTx(val id: String, val title: String, val date: LocalDate, val amount: Int, val type: TxType)

data class WithdrawAccount(val method: PaymentMethod, val account: String)

data class EarningsSummary(
    val walletBalance: Int,
    val pendingSettlement: Int,
    val nextPayout: String,
    val today: Int,
    val todayTrips: Int,
    val daily: List<Pair<String, Int>>,
    val weekTotal: Int,
    val weekChangePct: Int,
    val weekTrips: Int,
    val weekCommission: Int,
)

data class TripLine(val date: String, val code: String, val method: PaymentMethod, val gross: Int, val commission: Int)

/** Reconciliation for a period. All money in BDT (৳). */
data class Reconciliation(
    val period: String,
    val completedTrips: Int,
    val gross: Int,
    val commission: Int,
    val paidOut: Int,
    val withdrawals: Int,
    val cashCollected: Int,
    val lines: List<TripLine>,
) {
    val net: Int get() = gross - commission
    /** Platform still owes driver after cash already held by the driver and payouts. */
    val pendingSettlement: Int get() = net - paidOut - cashCollected
    val commissionDueOnCash: Int get() = Commission.of(cashCollected)
}

data class AppNotification(val title: String, val body: String, val time: String, val unread: Boolean)
