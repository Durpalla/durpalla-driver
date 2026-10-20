package com.durpalla.driver.data

import com.durpalla.driver.domain.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

private fun at(dayOffset: Long, h: Int, m: Int = 0): LocalDateTime =
    LocalDateTime.of(LocalDate.now().plusDays(dayOffset), LocalTime.of(h, m))

private fun slot(day: Long, h1: Int, h2: Int, m1: Int = 0, m2: Int = 0) = TimeSlot(at(day, h1, m1), at(day, h2, m2))

object SampleData {
    val customers = listOf(
        Customer("Tanvir Ahmed", 4.7, 23), Customer("Nusrat Jahan", 4.9, 41),
        Customer("Arif Hossain", 4.5, 8), Customer("Sadia Rahman", 4.8, 15),
    )

    val requests = listOf(
        TripRequest("r1", VehicleType.SPEEDBOAT, "Sadarghat", "Narayanganj", slot(0, 16, 17, 30, 30), 8, customers[0], 2800, 3500, 3, 45, "Family trip, 2 kids", 18.5),
        TripRequest("r2", VehicleType.SPEEDBOAT, "Sadarghat", "Munshiganj", slot(1, 9, 11), 10, customers[1], 3000, 3800, 5, 180, "Life jackets needed", 26.0),
        // Overlaps the confirmed upcoming trip t1 (tomorrow 8-10) -> slot conflict demo.
        TripRequest("r3", VehicleType.SPEEDBOAT, "Sadarghat", "Chandpur", slot(1, 8, 12, 30), 6, customers[2], 8500, 9500, 2, 300, "", 62.0),
        TripRequest("r4", VehicleType.TAXI, "Gulshan", "Airport", slot(0, 20, 21), 3, customers[3], 650, 850, 4, 90, "2 large bags", 11.2),
        TripRequest("r5", VehicleType.MICROBUS, "Dhaka", "Gazipur Resort", slot(2, 7, 19), 11, customers[1], 5500, 7000, 1, 600, "Day trip, wait at resort", 42.0),
    )

    val trips = listOf(
        Trip("t1", "DP-25814", VehicleType.SPEEDBOAT, "Sadarghat", "Narayanganj", slot(1, 8, 10), 8, customers[1], 3200, 1000, TripStatus.UPCOMING),
        Trip("t2", "DP-25790", VehicleType.SPEEDBOAT, "Sadarghat", "Munshiganj", slot(-1, 10, 12), 9, customers[0], 3500, 1000, TripStatus.COMPLETED, PaymentMethod.CASH, 5),
        Trip("t3", "DP-25771", VehicleType.SPEEDBOAT, "Mawa", "Sadarghat", slot(-2, 15, 17), 6, customers[2], 4000, 1000, TripStatus.COMPLETED, PaymentMethod.BKASH, 4),
        Trip("t4", "DP-25702", VehicleType.SPEEDBOAT, "Sadarghat", "Chandpur", slot(-4, 7, 11), 12, customers[3], 9000, 2000, TripStatus.COMPLETED, PaymentMethod.NAGAD, 5, true),
        Trip("t5", "DP-25655", VehicleType.SPEEDBOAT, "Sadarghat", "Narayanganj", slot(-6, 17, 18), 5, customers[0], 2900, 0, TripStatus.CANCELLED),
    )

    val predefined = listOf(
        PredefinedTrip("p1", VehicleType.SPEEDBOAT, "Sadarghat", "Narayanganj", "8:00 AM", setOf("Fri", "Sat"), 12, 3200, true),
        PredefinedTrip("p2", VehicleType.SPEEDBOAT, "Sadarghat", "Chandpur", "7:00 AM", setOf("Thu"), 12, 9000, true),
        PredefinedTrip("p3", VehicleType.SPEEDBOAT, "Mawa", "Sadarghat", "4:00 PM", setOf("Sun", "Tue"), 10, 4000, false),
    )

    val driver = Driver(
        "Rafiqul Islam", "+880 1711-234567", 4.8, 312, "Sadarghat, Dhaka",
        listOf(
            Vehicle("MV Sonar Tori", VehicleType.SPEEDBOAT, "DHK-SB-1043", 12, true),
            Vehicle("Toyota Hiace", VehicleType.MICROBUS, "DHAKA METRO-CHA 15-2231", 11, false),
        ),
        listOf(
            DocumentItem("National ID (NID)", DocStatus.VERIFIED),
            DocumentItem("Driving / Master licence", DocStatus.VERIFIED),
            DocumentItem("Vessel registration (BIWTA)", DocStatus.EXPIRING),
            DocumentItem("Fitness certificate", DocStatus.UNDER_REVIEW),
            DocumentItem("Insurance", DocStatus.MISSING),
        ),
    )

    val ratings = listOf(
        Rating("Nusrat Jahan", 5, "Very safe ride, life jackets for everyone.", LocalDate.now().minusDays(1), "DP-25790"),
        Rating("Arif Hossain", 4, "On time, boat was clean.", LocalDate.now().minusDays(2), "DP-25771"),
        Rating("Sadia Rahman", 5, "Excellent driver, helped with luggage.", LocalDate.now().minusDays(4), "DP-25702"),
        Rating("Tanvir Ahmed", 5, "Great experience!", LocalDate.now().minusDays(9), "DP-25611"),
        Rating("Mahmud Hasan", 3, "Started 15 min late.", LocalDate.now().minusDays(12), "DP-25544"),
    )

    val summary = EarningsSummary(
        walletBalance = 18640, pendingSettlement = 4850, nextPayout = "Mon",
        today = 4850, todayTrips = 3,
        daily = listOf("Sa" to 2400, "Su" to 3100, "Mo" to 1200, "Tu" to 3900, "We" to 3300, "Th" to 4300, "Fr" to 3250),
        weekTotal = 21450, weekChangePct = 12, weekTrips = 17, weekCommission = 2383,
    )

    val transactions = listOf(
        WalletTx("w1", "Trip DP-25790 · Cash", LocalDate.now().minusDays(1), 3150, TxType.EARNING),
        WalletTx("w2", "Commission DP-25790", LocalDate.now().minusDays(1), -350, TxType.COMMISSION),
        WalletTx("w3", "Withdraw to bKash", LocalDate.now().minusDays(2), -10000, TxType.WITHDRAWAL),
        WalletTx("w4", "Trip DP-25771 · bKash", LocalDate.now().minusDays(2), 3600, TxType.EARNING),
        WalletTx("w5", "Trip DP-25702 · Nagad", LocalDate.now().minusDays(4), 8100, TxType.EARNING),
        WalletTx("w6", "Adjustment · fuel surcharge", LocalDate.now().minusDays(5), 200, TxType.ADJUSTMENT),
    )

    val notifications = listOf(
        AppNotification("New trip request", "Speedboat · Sadarghat → Narayanganj · 8 pax", "2 min ago", true),
        AppNotification("You won a bid!", "Nusrat Jahan chose your ৳3,200 bid. Slot locked.", "1 h ago", true),
        AppNotification("Payout sent", "৳10,000 sent to bKash 017•••567", "Yesterday", false),
        AppNotification("Document expiring", "Vessel registration expires in 14 days", "2 days ago", false),
    )
}

class FakeAuthRepository : AuthRepository {
    override suspend fun requestOtp(phone: String, captchaToken: String?) = run { delay(400); Result.success(Unit) }
    var lastDeviceToken: String? = null; private set
    override suspend fun registerDeviceToken(token: String) = run { lastDeviceToken = token; Result.success(Unit) }
    override suspend fun verifyOtp(phone: String, code: String): Result<Boolean> {
        delay(400)
        return if (code.length == 6) Result.success(false) else Result.failure(IllegalArgumentException("Invalid code"))
    }
    override suspend fun submitKyc(type: VehicleType, docs: List<String>) = run { delay(400); Result.success(Unit) }
}

class FakeTripRepository : TripRepository {
    private val _trips = MutableStateFlow(SampleData.trips)
    override val trips: StateFlow<List<Trip>> = _trips
    private val _pre = MutableStateFlow(SampleData.predefined)
    override val predefined: StateFlow<List<PredefinedTrip>> = _pre
    private val _blocked = MutableStateFlow(setOf(LocalDate.now().plusDays(5), LocalDate.now().plusDays(6)))
    override val blockedDays: StateFlow<Set<LocalDate>> = _blocked

    override suspend fun addTrip(trip: Trip) = _trips.update { listOf(trip) + it }
    override suspend fun updateStatus(tripId: String, status: TripStatus, method: PaymentMethod?) =
        _trips.update { l -> l.map { if (it.id == tripId) it.copy(status = status, paymentMethod = method ?: it.paymentMethod) else it } }
    override suspend fun savePredefined(trip: PredefinedTrip) = _pre.update { l ->
        if (l.any { it.id == trip.id }) l.map { if (it.id == trip.id) trip else it } else l + trip
    }
    override suspend fun toggleBlocked(day: LocalDate) = _blocked.update { if (day in it) it - day else it + day }
}

class FakeRequestRepository(
    private val tripRepo: TripRepository,
    private val policy: SlotLockPolicy,
) : RequestRepository {
    private val _requests = MutableStateFlow(SampleData.requests)
    override val requests: StateFlow<List<TripRequest>> = _requests
    private val _bids = MutableStateFlow(
        listOf(
            Bid("b1", "x1", SampleData.requests[1].copy(id = "x1", to = "Munshiganj"), 3200, "", BidStatus.PENDING),
            Bid("b2", "x2", SampleData.requests[4].copy(id = "x2", vehicleType = VehicleType.MICROBUS, from = "Dhaka", to = "Narayanganj"), 6200, "", BidStatus.PENDING),
            Bid("b3", "x3", SampleData.requests[0].copy(id = "x3", from = "Sadarghat", to = "Chandpur"), 9000, "", BidStatus.LOST),
            Bid("b4", "x4", SampleData.requests[3].copy(id = "x4"), 750, "", BidStatus.WON),
            Bid("b5", "x5", SampleData.requests[0].copy(id = "x5", from = "Mawa"), 4000, "", BidStatus.EXPIRED),
        )
    )
    override val bids: StateFlow<List<Bid>> = _bids

    override suspend fun placeBid(requestId: String, amount: Int, note: String): Result<Bid> {
        delay(300)
        val req = _requests.value.firstOrNull { it.id == requestId } ?: return Result.failure(NoSuchElementException("Request gone"))
        return when (val v = BidRules.validate(amount, req, tripRepo.trips.value, policy)) {
            is BidValidation.Error -> Result.failure(IllegalStateException(v.message))
            BidValidation.Ok -> {
                val bid = Bid("b${System.nanoTime()}", requestId, req, amount, note, BidStatus.PENDING)
                _bids.update { listOf(bid) + it }
                _requests.update { l -> l.map { if (it.id == requestId) it.copy(status = RequestStatus.BIDDING, bidsCount = it.bidsCount + 1) else it } }
                Result.success(bid)
            }
        }
    }

    override suspend fun skip(requestId: String) = _requests.update { l -> l.filterNot { it.id == requestId } }

    override suspend fun simulateWin(bidId: String): Result<Trip> =
        accepted(bidId, "t${System.nanoTime()}", "DP-${25900 + (0..99).random()}", -1)

    override suspend fun accepted(bidId: String, tripId: String, tripCode: String, advancePaid: Int): Result<Trip> {
        val bid = _bids.value.firstOrNull { it.id == bidId } ?: return Result.failure(NoSuchElementException("Unknown bid $bidId"))
        tripRepo.trips.value.firstOrNull { it.id == tripId }?.let { return Result.success(it) } // idempotent (Pusher + FCM may both deliver)
        val won = bid.copy(status = BidStatus.WON)
        _bids.update { l -> policy.resolveAfterWin(won, l.map { if (it.id == bidId) won else it }) }
        val r = bid.request
        val trip = Trip(tripId, tripCode, r.vehicleType, r.from, r.to, r.slot, r.passengers, r.customer, bid.amount,
            if (advancePaid >= 0) advancePaid else bid.amount / 4, TripStatus.UPCOMING)
        tripRepo.addTrip(trip)
        _requests.update { l -> l.map { if (it.id == r.id) it.copy(status = RequestStatus.ASSIGNED) else it } }
        return Result.success(trip)
    }

    override suspend fun rejected(bidId: String) = _bids.update { l -> l.map { if (it.id == bidId && it.status == BidStatus.PENDING) it.copy(status = BidStatus.LOST) else it } }
    override suspend fun upsertRequest(r: TripRequest) = _requests.update { l -> if (l.any { it.id == r.id }) l.map { if (it.id == r.id) r else it } else listOf(r) + l }
    override suspend fun updateCompetition(requestId: String, bidCount: Int) =
        _requests.update { l -> l.map { if (it.id == requestId) it.copy(bidsCount = maxOf(it.bidsCount, bidCount)) else it } }
}

class FakeEarningsRepository : EarningsRepository {
    private val _summary = MutableStateFlow(SampleData.summary)
    override val summary: StateFlow<EarningsSummary> = _summary
    private val _tx = MutableStateFlow(SampleData.transactions)
    override val transactions: StateFlow<List<WalletTx>> = _tx
    override val accounts = listOf(
        WithdrawAccount(PaymentMethod.BKASH, "017•••567"),
        WithdrawAccount(PaymentMethod.NAGAD, "018•••921"),
        WithdrawAccount(PaymentMethod.BANK, "DBBL ••••4410"),
    )

    override suspend fun reconciliation(period: String) = Reconciliation(
        period = period, completedTrips = 42, gross = 96400, commission = 9640, paidOut = 62000, withdrawals = 4,
        cashCollected = 0,
        lines = listOf(
            TripLine("23 Oct", "DP-25814", PaymentMethod.CASH, 3200, 320),
            TripLine("22 Oct", "DP-25790", PaymentMethod.BKASH, 7800, 780),
            TripLine("21 Oct", "Fixed · 9 seats", PaymentMethod.NAGAD, 3150, 315),
            TripLine("19 Oct", "DP-25702", PaymentMethod.CASH, 9000, 900),
        ),
    ).let { it.copy(cashCollected = 6120) }

    override suspend fun withdraw(account: WithdrawAccount, amount: Int, pin: String): Result<WalletTx> {
        delay(600)
        val bal = _summary.value.walletBalance
        return when {
            pin.length != 4 -> Result.failure(IllegalArgumentException("Enter your 4-digit PIN"))
            amount < 500 -> Result.failure(IllegalArgumentException("Minimum withdrawal is ৳500"))
            amount > bal -> Result.failure(IllegalArgumentException("Amount exceeds wallet balance"))
            else -> {
                val tx = WalletTx("w${System.nanoTime()}", "Withdraw to ${account.method.label}", LocalDate.now(), -amount, TxType.WITHDRAWAL)
                _tx.update { listOf(tx) + it }
                _summary.update { it.copy(walletBalance = bal - amount) }
                Result.success(tx)
            }
        }
    }
}

class FakeProfileRepository : ProfileRepository {
    override val driver: StateFlow<Driver> = MutableStateFlow(SampleData.driver)
    override val ratings: StateFlow<List<Rating>> = MutableStateFlow(SampleData.ratings)
    override val notifications: StateFlow<List<AppNotification>> = MutableStateFlow(SampleData.notifications)
}

/** Manual DI. Replace with Hilt/Koin if preferred. */
object ServiceLocator {
    val slotPolicy = SlotLockPolicy(bufferMinutes = 30)
    val auth: AuthRepository by lazy { FakeAuthRepository() }
    val trips: TripRepository by lazy { FakeTripRepository() }
    val requests: RequestRepository by lazy { FakeRequestRepository(trips, slotPolicy) }
    val earnings: EarningsRepository by lazy { FakeEarningsRepository() }
    val profile: ProfileRepository by lazy { FakeProfileRepository() }
    private val appScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Default)

    /** Pusher when PUSHER_KEY is configured, otherwise the simulator. */
    val realtime: com.durpalla.driver.realtime.RealtimeClient by lazy {
        if (com.durpalla.driver.BuildConfig.PUSHER_KEY.isNotBlank())
            com.durpalla.driver.realtime.PusherRealtimeClient(com.durpalla.driver.BuildConfig.PUSHER_KEY, com.durpalla.driver.BuildConfig.PUSHER_CLUSTER, com.durpalla.driver.BuildConfig.PUSHER_AUTH_URL)
        else com.durpalla.driver.realtime.FakeRealtimeClient(appScope, { requests.requests.value.filter { it.status == RequestStatus.OPEN || it.status == RequestStatus.BIDDING } })
    }
    var botProtection: com.durpalla.driver.security.BotProtection = com.durpalla.driver.security.NoopBotProtection
}
