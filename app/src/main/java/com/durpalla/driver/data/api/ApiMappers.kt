package com.durpalla.driver.data.api

import com.durpalla.driver.R
import com.durpalla.driver.domain.*
import com.durpalla.driver.i18n.UiText
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId

/** apigw driver JSON (camelCase, string ids, integer BDT, ISO-8601 times) → domain models. Lenient: nulls get neutral defaults. */
object ApiMappers {
    fun JsonObject.opt(k: String): JsonElement? = get(k)?.takeUnless { it.isJsonNull }
    fun JsonObject.s(k: String, d: String = ""): String = opt(k)?.asString ?: d
    fun JsonObject.i(k: String, d: Int = 0): Int = opt(k)?.asDouble?.toInt() ?: d
    fun JsonObject.o(k: String): JsonObject? = opt(k)?.takeIf { it.isJsonObject }?.asJsonObject
    fun JsonElement.objects(): List<JsonObject> = if (isJsonArray) asJsonArray.filter { it.isJsonObject }.map { it.asJsonObject } else emptyList()

    /** ISO-8601 with offset → device-local time (falls back to a plain local date-time). */
    fun time(s: String?): LocalDateTime? = s?.takeIf { it.isNotBlank() }?.let {
        runCatching { OffsetDateTime.parse(it).atZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime() }.getOrElse { _ -> runCatching { LocalDateTime.parse(it) }.getOrNull() }
    }
    private fun date(s: String?): LocalDate = time(s)?.toLocalDate() ?: LocalDate.now()

    fun vehicleType(s: String?): VehicleType = VehicleType.entries.firstOrNull { it.name.equals(s, true) } ?: VehicleType.BOAT

    private fun slot(o: JsonObject): TimeSlot {
        val start = time(o.s("start")) ?: LocalDateTime.now()
        val end = time(o.s("end"))?.takeIf { it.isAfter(start) } ?: start.plusHours(1)
        return TimeSlot(start, end)
    }

    fun customer(o: JsonObject?): Customer = Customer(o?.s("name").orEmpty(), o?.opt("rating")?.asDouble ?: 0.0, o?.i("trips") ?: 0)

    fun requestStatus(s: String?): RequestStatus = when (s?.lowercase()) {
        "assigned", "accepted", "booked" -> RequestStatus.ASSIGNED
        "expired" -> RequestStatus.EXPIRED
        "cancelled", "canceled" -> RequestStatus.CANCELLED
        "bidding" -> RequestStatus.BIDDING
        else -> RequestStatus.OPEN
    }

    fun request(o: JsonObject): TripRequest = TripRequest(
        id = o.s("id"), vehicleType = vehicleType(o.s("vehicleType")), from = o.s("from"), to = o.s("to"), slot = slot(o),
        passengers = o.i("passengers"), customer = customer(o.o("customer")),
        suggestedMin = o.i("suggestedMin"), suggestedMax = o.i("suggestedMax"), bidsCount = o.i("bidsCount"),
        expiresInSec = o.i("expiresInSec"), note = o.s("note"), distanceKm = o.opt("distanceKm")?.asDouble ?: 0.0,
        status = requestStatus(o.s("status")),
    )

    fun bidStatus(s: String?): BidStatus = when (s?.lowercase()) {
        "accepted", "won" -> BidStatus.WON
        "rejected", "lost", "withdrawn" -> BidStatus.LOST
        "expired" -> BidStatus.EXPIRED
        else -> BidStatus.PENDING
    }

    /** `request` is embedded in GET bids; [fallback] is used right after placing a bid. */
    fun bid(o: JsonObject, fallback: TripRequest? = null): Bid? {
        val req = o.o("request")?.let(::request) ?: fallback ?: return null
        return Bid(o.s("id"), o.s("requestId", req.id), req, o.i("amount"), o.s("note"), bidStatus(o.s("status")))
    }

    fun tripStatus(s: String?): TripStatus = TripStatus.entries.firstOrNull { it.name.equals(s, true) } ?: TripStatus.UPCOMING
    fun method(s: String?): PaymentMethod? = PaymentMethod.entries.firstOrNull { it.name.equals(s, true) }

    fun trip(o: JsonObject): Trip = Trip(
        id = o.s("id"), code = o.s("code"), vehicleType = vehicleType(o.s("vehicleType")), from = o.s("from"), to = o.s("to"), slot = slot(o),
        passengers = o.i("passengers"), customer = customer(o.o("customer")), fare = o.i("fare"), advancePaid = o.i("advancePaid"),
        status = tripStatus(o.s("status")), paymentMethod = method(o.s("paymentMethod")), commission = o.i("commission"),
        callLog = o.opt("callLog")?.objects()?.map(::callLog).orEmpty(),
    )

    fun callLog(o: JsonObject) = com.durpalla.driver.call.CallLogEntry(
        callId = o.s("callId"), callerRole = com.durpalla.driver.call.CallRole.of(o.s("callerRole")),
        status = com.durpalla.driver.call.CallStatus.of(o.s("status")) ?: com.durpalla.driver.call.CallStatus.ENDED,
        createdAt = o.opt("createdAt")?.asLong ?: 0, answeredAt = o.opt("answeredAt")?.asLong, endedAt = o.opt("endedAt")?.asLong,
        durationSec = o.i("durationSec"), endedBy = o.opt("endedBy")?.asString, reason = o.opt("reason")?.asString,
        provider = com.durpalla.driver.call.CallProviderKind.of(o.opt("provider")?.asString),
    )

    private val docNames = mapOf("nid_front" to "NID (front)", "nid_back" to "NID (back)", "license" to "Driving / Master licence",
        "vehicle_reg" to "Vehicle registration", "vehicle_photo" to "Vehicle photo", "selfie" to "Selfie")

    fun docStatus(s: String?): DocStatus = when (s?.lowercase()) {
        "approved", "verified" -> DocStatus.VERIFIED
        "submitted", "pending", "under_review" -> DocStatus.UNDER_REVIEW
        "expiring" -> DocStatus.EXPIRING
        else -> DocStatus.MISSING
    }

    /** GET me + GET kyc → profile. */
    fun driver(me: JsonObject, kyc: JsonObject?): Driver {
        val type = vehicleType(me.s("vehicleType"))
        val vehicle = me.o("vehicle")?.let { v -> Vehicle(v.s("title"), type, v.s("registrationNo"), v.i("capacity"), me.opt("kycComplete")?.asBoolean ?: false) }
        val docs = kyc?.opt("documents")?.objects()?.map { d -> DocumentItem(docNames[d.s("type")] ?: d.s("type"), docStatus(d.s("status"))) }.orEmpty()
        return Driver(
            id = me.s("id"), name = me.s("name"), phone = me.s("phone"), rating = me.opt("rating")?.asDouble ?: 0.0, totalTrips = me.i("trips"), area = me.s("area"),
            vehicles = listOfNotNull(vehicle), documents = docs, kycComplete = me.opt("kycComplete")?.asBoolean ?: false,
            canBid = me.opt("canBid")?.asBoolean ?: false, online = me.opt("online")?.asBoolean ?: true,
            walletBalance = me.i("walletBalance"), hasWithdrawPin = me.opt("hasWithdrawPin")?.asBoolean ?: false,
        )
    }

    fun rating(o: JsonObject): Rating =
        Rating(o.s("customerName"), o.i("stars"), o.s("comment"), date(o.s("createdAt")), o.s("tripCode", "#" + o.s("tripId")))

    fun predefined(o: JsonObject): PredefinedTrip = PredefinedTrip(
        id = o.s("id"), vehicleType = vehicleType(o.s("vehicleType")), from = o.s("from"), to = o.s("to"), departure = o.s("departure"),
        days = o.opt("days")?.takeIf { it.isJsonArray }?.asJsonArray?.map { it.asString }?.toSet().orEmpty(),
        seats = o.i("seats"), rent = o.i("rent"), active = o.opt("active")?.asBoolean ?: true,
    )

    /** PredefinedTrip → request body (days upper-case SAT..FRI, departure HH:MM, ASCII numbers). */
    fun predefinedBody(p: PredefinedTrip): Map<String, Any> = mapOf(
        "vehicleType" to p.vehicleType.name, "from" to p.from, "to" to p.to, "departure" to p.departure,
        "days" to p.days.map { it.uppercase().take(3) }, "seats" to p.seats, "rent" to p.rent, "active" to p.active,
    )

    fun payoutAccount(o: JsonObject): WithdrawAccount? = method(o.s("method"))?.let { WithdrawAccount(it, o.s("account"), o.s("id")) }

    fun blockedDays(o: JsonObject): Set<LocalDate> =
        o.opt("blockedDays")?.takeIf { it.isJsonArray }?.asJsonArray?.mapNotNull { runCatching { LocalDate.parse(it.asString) }.getOrNull() }?.toSet().orEmpty()

    /** Notification text by event (title, body), localised via string resources. */
    fun notification(o: JsonObject): AppNotification {
        val p = o.o("payload") ?: JsonObject()
        val req = p.o("request")
        val (title, body) = when (o.s("event")) {
            "trip.requested" -> UiText.res(R.string.notif_request_title) to UiText.res(R.string.notif_request_body, req?.s("from").orEmpty(), req?.s("to").orEmpty())
            "bid.accepted" -> UiText.res(R.string.notif_accepted_title) to UiText.res(R.string.notif_accepted_body, p.s("tripCode"))
            "bid.rejected" -> UiText.res(R.string.notif_rejected_title) to UiText.res(R.string.notif_rejected_body)
            "trip.cancelled" -> UiText.res(R.string.notif_cancelled_title) to UiText.res(R.string.notif_cancelled_body)
            "payment.received" -> UiText.res(R.string.notif_payment_title) to UiText.res(R.string.notif_payment_body, UiText.Taka(p.i("amount")))
            else -> UiText.Raw(o.s("event")) to UiText.Raw("")
        }
        val t = time(o.s("createdAt"))
        val fmt = java.time.format.DateTimeFormatter.ofPattern("d MMM, h:mm a", java.util.Locale.ENGLISH)
        return AppNotification(title, body, t?.format(fmt).orEmpty(), !(o.opt("read")?.asBoolean ?: false), o.s("id"), o.s("route").ifBlank { null })
    }

    fun walletTx(o: JsonObject): WalletTx {
        val type = when (o.s("type")) { "trip_earning", "earning" -> TxType.EARNING; "commission" -> TxType.COMMISSION; "withdrawal" -> TxType.WITHDRAWAL; else -> TxType.ADJUSTMENT }
        val sign = if (o.s("direction") == "debit") -1 else 1
        return WalletTx(o.s("id"), o.s("note", o.s("type")), date(o.s("createdAt")), sign * o.i("amount"), type)
    }

    fun withdrawal(o: JsonObject): WalletTx =
        WalletTx("wd-" + o.s("id"), "${method(o.s("method"))?.label ?: o.s("method")} · ${o.s("account")}", date(o.s("createdAt")), -o.i("amount"), TxType.WITHDRAWAL)

    fun reconciliation(o: JsonObject, lines: List<TripLine>): Reconciliation = Reconciliation(
        period = o.s("period"), completedTrips = o.i("trips"), gross = o.i("grossFare"), commission = o.i("commission"),
        paidOut = o.i("withdrawn"), withdrawals = o.i("withdrawals"), cashCollected = o.i("cashCollected"), lines = lines,
    )
}
