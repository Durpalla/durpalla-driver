package com.durpalla.driver.data

import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import com.durpalla.driver.R
import com.durpalla.driver.data.api.ApiClient
import com.durpalla.driver.data.api.ApiMappers
import com.durpalla.driver.data.api.ApiMappers.objects
import com.durpalla.driver.data.api.ApiMappers.o
import com.durpalla.driver.data.api.ApiMappers.s
import com.durpalla.driver.data.session.Session
import com.durpalla.driver.data.session.SessionStore
import com.durpalla.driver.domain.*
import com.durpalla.driver.i18n.UiText
import com.durpalla.driver.i18n.UserFacingException
import com.google.gson.JsonObject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import java.time.LocalDate
import java.util.UUID

private fun JsonObject?.orFail(): JsonObject = this ?: throw IllegalStateException("bad response")

class ApiAuthRepository(
    private val api: ApiClient,
    private val session: SessionStore,
    /** content URI → (bytes, mime); null if unreadable. */
    private val readFile: (String) -> Pair<ByteArray, String>? = { null },
    private val deviceName: String = android.os.Build.MODEL ?: "android",
) : AuthRepository {
    @Volatile private var fcmToken: String? = null

    override suspend fun requestOtp(phone: String, captchaToken: String?) =
        api.post("auth/otp", mapOf("phone" to phone, "recaptchaToken" to captchaToken)).map { }

    override suspend fun verifyOtp(phone: String, code: String): Result<Boolean> =
        api.post("auth/otp/verify", mapOf("phone" to phone, "code" to code, "deviceName" to deviceName)).mapCatching { d ->
            val o = d.asJsonObject
            session.login(Session(o.s("token"), o.o("driver")?.s("id").orEmpty()))
            fcmToken?.let { registerDeviceToken(it) }
            o.get("kycComplete")?.asBoolean ?: false
        }

    /** Remembered so it is (re)sent right after login. */
    override suspend fun registerDeviceToken(token: String): Result<Unit> {
        fcmToken = token
        if (session.token == null) return Result.success(Unit)
        return api.post("devices", mapOf("fcmToken" to token, "platform" to "android")).map { }
    }

    /** POST kyc multipart: vehicleType + documents[type] = image file. */
    override suspend fun submitKyc(type: VehicleType, files: Map<String, String>): Result<Unit> = runCatching {
        val body = okhttp3.MultipartBody.Builder().setType(okhttp3.MultipartBody.FORM).addFormDataPart("vehicleType", type.name)
        files.forEach { (docType, uri) ->
            val (bytes, mime) = readFile(uri) ?: throw UserFacingException(UiText.res(R.string.err_kyc_docs))
            body.addFormDataPart("documents[$docType]", "$docType.${if (mime.endsWith("png")) "png" else "jpg"}", bytes.toRequestBody(mime.toMediaTypeOrNull()))
        }
        api.post("kyc", body.build()).getOrThrow()
        Unit
    }

    override suspend fun logout(): Result<Unit> {
        val r = api.post("auth/logout", mapOf("fcmToken" to fcmToken)).map { }
        session.logout()
        return r
    }

    suspend fun firebaseToken(): String? = api.get("auth/firebase-token").getOrNull()?.takeIf { it.isJsonObject }?.asJsonObject?.s("token")
}

class ApiTripRepository(private val api: ApiClient) : TripRepository {
    private val _trips = MutableStateFlow<List<Trip>>(emptyList())
    override val trips: StateFlow<List<Trip>> = _trips
    private val _predefined = MutableStateFlow<List<PredefinedTrip>>(emptyList())
    override val predefined: StateFlow<List<PredefinedTrip>> = _predefined
    private val _blocked = MutableStateFlow<Set<LocalDate>>(emptySet())
    override val blockedDays: StateFlow<Set<LocalDate>> = _blocked

    /** GET trips + predefined-trips + availability. */
    override suspend fun refresh(): Result<Unit> = runCatching {
        _trips.value = api.get("trips").getOrThrow().objects().map(ApiMappers::trip).sortedByDescending { it.slot.start }
        _predefined.value = api.get("predefined-trips").getOrThrow().objects().map(ApiMappers::predefined)
        _blocked.value = ApiMappers.blockedDays(api.get("availability").getOrThrow().asJsonObject)
    }

    override suspend fun refreshTrip(tripId: String) = api.get("trips/$tripId").mapCatching { d -> ApiMappers.trip(d.asJsonObject).also(::upsert) }

    private fun upsert(t: Trip) = _trips.update { l -> if (l.any { it.id == t.id }) l.map { if (it.id == t.id) t else it } else listOf(t) + l }

    override suspend fun updateStatus(tripId: String, status: TripStatus, method: PaymentMethod?): Result<Unit> = when (status) {
        TripStatus.ON_THE_WAY, TripStatus.ARRIVED, TripStatus.IN_PROGRESS ->
            api.post("trips/$tripId/status", mapOf("status" to status.name)).mapCatching { d -> upsert(ApiMappers.trip(d.asJsonObject)) }
        TripStatus.COMPLETED ->
            api.post("trips/$tripId/collect", mapOf("method" to (method ?: PaymentMethod.CASH).name)).mapCatching { d -> upsert(ApiMappers.trip(d.asJsonObject)) }
        TripStatus.CANCELLED -> Result.success(_trips.update { l -> l.map { if (it.id == tripId) it.copy(status = status) else it } })
        TripStatus.UPCOMING -> Result.success(Unit)
    }

    /** New (blank id) → POST predefined-trips, else PUT predefined-trips/{id}. */
    override suspend fun savePredefined(trip: PredefinedTrip): Result<Unit> {
        val body = ApiMappers.predefinedBody(trip)
        val res = if (trip.id.isBlank() || trip.id.startsWith("new")) api.post("predefined-trips", body) else api.put("predefined-trips/${trip.id}", body)
        return res.mapCatching { d ->
            val saved = ApiMappers.predefined(d.asJsonObject)
            _predefined.update { l -> if (l.any { it.id == saved.id }) l.map { if (it.id == saved.id) saved else it } else l + saved }
        }
    }

    override suspend fun deletePredefined(id: String): Result<Unit> =
        api.delete("predefined-trips/$id").map { _predefined.update { l -> l.filterNot { it.id == id } } }

    /** POST availability/days {date, blocked}. */
    override suspend fun toggleBlocked(day: LocalDate): Result<Unit> =
        api.post("availability/days", mapOf("date" to day.toString(), "blocked" to (day !in _blocked.value)))
            .mapCatching { d -> _blocked.value = ApiMappers.blockedDays(d.asJsonObject) }

    override fun clear() { _trips.value = emptyList(); _predefined.value = emptyList(); _blocked.value = emptySet() }
}

class ApiRequestRepository(private val api: ApiClient, private val trips: TripRepository, private val policy: SlotLockPolicy) : RequestRepository {
    private val _requests = MutableStateFlow<List<TripRequest>>(emptyList())
    override val requests: StateFlow<List<TripRequest>> = _requests
    private val _bids = MutableStateFlow<List<Bid>>(emptyList())
    override val bids: StateFlow<List<Bid>> = _bids
    /** Skipped this session (the server also filters them out of GET requests and fan-out). */
    private val skipped = mutableSetOf<String>()

    override suspend fun refresh(): Result<Unit> = runCatching {
        val feed = api.get("requests").getOrThrow().objects().map(ApiMappers::request)
        val mine = api.get("bids").getOrThrow().objects().mapNotNull { ApiMappers.bid(it) }
        _bids.value = mine
        _requests.value = feed.filterNot { it.id in skipped }
    }

    override suspend fun placeBid(requestId: String, amount: Int, note: String): Result<Bid> {
        val req = _requests.value.firstOrNull { it.id == requestId }
        return api.post("requests/$requestId/bids", mapOf("amount" to amount, "note" to note.ifBlank { null })).mapCatching { d ->
            val bid = ApiMappers.bid(d.asJsonObject, req) ?: throw UserFacingException(UiText.res(R.string.err_request_gone))
            _bids.update { l -> listOf(bid) + l.filterNot { it.id == bid.id } }
            _requests.update { l -> l.map { if (it.id == requestId) it.copy(status = RequestStatus.BIDDING, bidsCount = it.bidsCount + 1) else it } }
            bid
        }
    }

    override suspend fun skip(requestId: String) {
        skipped += requestId; _requests.update { l -> l.filterNot { it.id == requestId } }
        api.post("requests/$requestId/skip")
    }

    override suspend fun withdrawBid(bidId: String) = api.delete("bids/$bidId").map {
        _bids.update { l -> l.map { if (it.id == bidId) it.copy(status = BidStatus.LOST) else it } }
    }

    override suspend fun accepted(bidId: String, tripId: String, tripCode: String, advancePaid: Int): Result<Trip> {
        trips.trips.value.firstOrNull { it.id == tripId }?.let { return Result.success(it) } // idempotent (Pusher + FCM)
        return trips.refreshTrip(tripId).onSuccess { t ->
            val won = _bids.value.firstOrNull { it.id == bidId }?.copy(status = BidStatus.WON)
            if (won != null) _bids.update { l -> policy.resolveAfterWin(won, l.map { if (it.id == bidId) won else it }) }
            _requests.update { l -> l.map { if (it.id == won?.requestId) it.copy(status = RequestStatus.ASSIGNED) else it } }
        }
    }

    override suspend fun rejected(bidId: String) = _bids.update { l -> l.map { if (it.id == bidId && it.status == BidStatus.PENDING) it.copy(status = BidStatus.LOST) else it } }
    override suspend fun upsertRequest(r: TripRequest) { if (r.id !in skipped) _requests.update { l -> if (l.any { it.id == r.id }) l.map { if (it.id == r.id) r else it } else listOf(r) + l } }
    override suspend fun updateCompetition(requestId: String, bidCount: Int) =
        _requests.update { l -> l.map { if (it.id == requestId) it.copy(bidsCount = bidCount) else it } }
    override fun clear() { _requests.value = emptyList(); _bids.value = emptyList(); skipped.clear() }
}

class ApiEarningsRepository(private val api: ApiClient, private val trips: TripRepository) : EarningsRepository {
    private val _summary = MutableStateFlow(EarningsSummary.Empty)
    override val summary: StateFlow<EarningsSummary> = _summary
    private val _tx = MutableStateFlow<List<WalletTx>>(emptyList())
    override val transactions: StateFlow<List<WalletTx>> = _tx
    private val _accounts = MutableStateFlow<List<WithdrawAccount>>(emptyList())
    override val accounts: StateFlow<List<WithdrawAccount>> = _accounts

    override suspend fun refresh(): Result<Unit> = runCatching {
        val s = api.get("earnings").getOrThrow().asJsonObject
        _tx.value = api.get("wallet/transactions").getOrThrow().objects().map(ApiMappers::walletTx)
        _accounts.value = api.get("payout-accounts").getOrThrow().objects().mapNotNull(ApiMappers::payoutAccount)
        _summary.value = EarningsCalc.summary(s, trips.trips.value, LocalDate.now())
    }

    override suspend fun reconciliation(period: String) = api.get("reconciliation", mapOf("period" to period)).mapCatching { d ->
        ApiMappers.reconciliation(d.asJsonObject, EarningsCalc.lines(trips.trips.value, d.asJsonObject.s("from"), d.asJsonObject.s("to")))
    }

    override suspend fun withdraw(account: WithdrawAccount, amount: Int, pin: String): Result<WalletTx> =
        api.post("withdrawals", mapOf("method" to account.method.name, "account" to account.account, "amount" to amount, "pin" to pin),
            mapOf("Idempotency-Key" to UUID.randomUUID().toString())).mapCatching { d ->
            val tx = ApiMappers.withdrawal(d.asJsonObject)
            _tx.update { listOf(tx) + it }
            _summary.update { it.copy(walletBalance = it.walletBalance - amount) }
            tx
        }
    /** POST payout-accounts; the new account becomes the default. */
    override suspend fun addAccount(method: PaymentMethod, account: String): Result<WithdrawAccount> =
        api.post("payout-accounts", mapOf("method" to method.name, "account" to account, "isDefault" to true)).mapCatching { d ->
            val a = ApiMappers.payoutAccount(d.asJsonObject) ?: error("bad account")
            _accounts.update { l -> listOf(a) + l.filterNot { it.id == a.id } }
            a
        }

    override fun clear() { _summary.value = EarningsSummary.Empty; _tx.value = emptyList(); _accounts.value = emptyList() }
}

class ApiProfileRepository(private val api: ApiClient, private val trips: TripRepository) : ProfileRepository {
    private val _driver = MutableStateFlow(Driver.Empty)
    override val driver: StateFlow<Driver> = _driver
    private val _ratings = MutableStateFlow<List<Rating>>(emptyList())
    override val ratings: StateFlow<List<Rating>> = _ratings
    private val _notifications = MutableStateFlow<List<AppNotification>>(emptyList())
    override val notifications: StateFlow<List<AppNotification>> = _notifications

    override suspend fun refresh(): Result<Unit> = runCatching {
        val me = api.get("me").getOrThrow().asJsonObject
        val kyc = api.get("kyc").getOrNull()?.takeIf { it.isJsonObject }?.asJsonObject
        _driver.value = ApiMappers.driver(me, kyc)
        _ratings.value = api.get("ratings").getOrThrow().objects().map(ApiMappers::rating)
        refreshNotifications().getOrThrow()
    }

    override suspend fun setOnline(online: Boolean): Result<Unit> {
        val before = _driver.value
        _driver.value = before.copy(online = online) // optimistic
        return api.post("status", mapOf("online" to online)).mapCatching { d ->
            _driver.value = ApiMappers.driver(d.asJsonObject, null).copy(documents = before.documents)
        }.onFailure { _driver.value = before }
    }
    override suspend fun refreshNotifications(): Result<Unit> = api.get("notifications").map { d ->
        _notifications.value = d.asJsonObject.get("items")?.objects().orEmpty().map(ApiMappers::notification)
    }

    /** POST notifications/read (all). */
    override suspend fun markNotificationsRead(): Result<Unit> = api.post("notifications/read").map {
        _notifications.update { l -> l.map { it.copy(unread = false) } }
    }

    override fun clear() { _driver.value = Driver.Empty; _ratings.value = emptyList(); _notifications.value = emptyList() }
}

/** Client-side aggregates over real trips (the earnings endpoint has totals, not a daily breakdown). */
object EarningsCalc {
    private val dayLabels = listOf("Mo", "Tu", "We", "Th", "Fr", "Sa", "Su")
    private fun net(t: Trip) = t.fare - t.commission

    fun summary(api: JsonObject, trips: List<Trip>, today: LocalDate): EarningsSummary {
        val done = trips.filter { it.status == TripStatus.COMPLETED }
        fun on(d: LocalDate) = done.filter { it.slot.end.toLocalDate() == d }
        val week = (6 downTo 0).map { today.minusDays(it.toLong()) }
        val prevWeek = (13 downTo 7).map { today.minusDays(it.toLong()) }
        val weekTrips = week.flatMap(::on)
        val weekTotal = weekTrips.sumOf(::net)
        val prevTotal = prevWeek.flatMap(::on).sumOf(::net)
        val i = { k: String -> api.get(k)?.takeUnless { it.isJsonNull }?.asInt ?: 0 }
        return EarningsSummary(
            walletBalance = i("balance"), pendingSettlement = i("pendingWithdrawals"),
            nextPayout = api.get("nextPayout")?.takeUnless { it.isJsonNull }?.asString?.let { runCatching { LocalDate.parse(it).format(java.time.format.DateTimeFormatter.ofPattern("EEE d MMM", java.util.Locale.ENGLISH)) }.getOrNull() }.orEmpty(),
            today = i("today"), todayTrips = i("tripsToday"),
            daily = week.map { d -> dayLabels[d.dayOfWeek.value - 1] to on(d).sumOf(::net) },
            weekTotal = weekTotal, weekChangePct = if (prevTotal > 0) ((weekTotal - prevTotal) * 100 / prevTotal) else 0,
            weekTrips = weekTrips.size, weekCommission = weekTrips.sumOf { it.commission },
        )
    }

    fun lines(trips: List<Trip>, from: String, to: String): List<TripLine> {
        val f = runCatching { LocalDate.parse(from) }.getOrNull() ?: return emptyList()
        val t = runCatching { LocalDate.parse(to) }.getOrNull() ?: return emptyList()
        val fmt = java.time.format.DateTimeFormatter.ofPattern("d MMM", java.util.Locale.ENGLISH)
        return trips.filter { it.status == TripStatus.COMPLETED && it.slot.end.toLocalDate() in f..t }
            .sortedByDescending { it.slot.end }
            .map { TripLine(it.slot.end.format(fmt), it.code, it.paymentMethod ?: PaymentMethod.CASH, it.fare, it.commission) }
    }
}
