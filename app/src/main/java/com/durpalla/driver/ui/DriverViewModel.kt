package com.durpalla.driver.ui

import com.durpalla.driver.R
import com.durpalla.driver.i18n.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.durpalla.driver.data.*
import com.durpalla.driver.domain.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import com.durpalla.driver.realtime.*
import com.durpalla.driver.security.BotProtection

data class LiveBanner(val kind: Kind, val title: UiText, val body: UiText, val route: String) {
    enum class Kind { ACCEPTED, REQUEST, INFO, WARNING }
}

data class AuthState(val phone: String = "", val loading: Boolean = false, val error: UiText? = null, val kycType: VehicleType = VehicleType.SPEEDBOAT, val uploaded: Set<String> = emptySet(), val files: Map<String, String> = emptyMap())

/** [amount] is always ASCII digits (normalised on input), so parsing never depends on locale. */
data class BidFormState(val amount: String = "", val note: String = "", val error: UiText? = null, val submitting: Boolean = false, val submitted: Bid? = null) {
    val amountInt: Int? get() = DigitFormatter.parseInt(amount)
}

data class WithdrawState(val account: WithdrawAccount? = null, val amount: String = "", val pin: String = "", val error: UiText? = null, val loading: Boolean = false, val done: WalletTx? = null)

/**
 * Shared screen state for the whole driver app. Screens themselves are stateless composables
 * (easy to preview); the nav graph wires them to this ViewModel.
 */
class DriverViewModel(
    private val auth: AuthRepository = ServiceLocator.auth,
    private val requestRepo: RequestRepository = ServiceLocator.requests,
    private val tripRepo: TripRepository = ServiceLocator.trips,
    private val earningsRepo: EarningsRepository = ServiceLocator.earnings,
    private val profileRepo: ProfileRepository = ServiceLocator.profile,
    val policy: SlotLockPolicy = ServiceLocator.slotPolicy,
    private val realtime: RealtimeClient = ServiceLocator.realtime,
    private val bot: () -> BotProtection = { ServiceLocator.botProtection },
    private val session: com.durpalla.driver.data.session.SessionStore = ServiceLocator.session,
) : ViewModel() {

    // ---- Realtime (Pusher / FCM)
    val connection: StateFlow<ConnectionState> = realtime.connection
    private val _live = MutableStateFlow<Map<String, LiveBid>>(emptyMap())
    /** Live competition per request id: bid count, lowest bid, my status. */
    val liveBids: StateFlow<Map<String, LiveBid>> = _live
    private val _banner = MutableStateFlow<LiveBanner?>(null)
    /** In-app high-priority banner (e.g. customer accepted your bid). */
    val banner: StateFlow<LiveBanner?> = _banner
    fun dismissBanner() { _banner.value = null }

    /** Logged in = a stored Sanctum token. */
    val loggedIn: StateFlow<Boolean> = session.session.map { it != null }.stateIn(viewModelScope, SharingStarted.Eagerly, session.session.value != null)
    /** Server answered 401: the UI goes back to login. */
    val sessionExpired: SharedFlow<Unit> = session.expired
    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing

    init {
        viewModelScope.launch { realtime.events.collect { onEvent(it) } }
        viewModelScope.launch {
            session.session.map { it?.driverId }.distinctUntilChanged().collect { id ->
                if (id != null) { realtime.connect(id) { session.token }; refreshAll() }
                else { realtime.disconnect(); requestRepo.clear(); tripRepo.clear(); earningsRepo.clear(); profileRepo.clear() }
            }
        }
    }

    /** Loads everything from the API. Trips first: earnings and ratings are derived from them. */
    fun refreshAll() = viewModelScope.launch {
        if (session.session.value == null) return@launch
        _refreshing.value = true
        val results = listOf(tripRepo.refresh(), requestRepo.refresh(), profileRepo.refresh(), earningsRepo.refresh())
        _refreshing.value = false
        results.firstNotNullOfOrNull { it.exceptionOrNull() }?.let(::showError)
    }

    private fun showError(e: Throwable) {
        if ((e as? com.durpalla.driver.data.api.ApiException)?.status == 401) return // handled by sessionExpired
        _banner.value = LiveBanner(LiveBanner.Kind.WARNING, UiText.res(R.string.err_load_title), e.toUiText(R.string.err_generic), "")
    }

    internal suspend fun onEvent(e: RealtimeEvent) {
        _live.update { BidReducer.reduce(it, e) }
        when (e) {
            is RealtimeEvent.TripRequested -> { requestRepo.upsertRequest(e.request); _banner.value = LiveBanner(LiveBanner.Kind.REQUEST, UiText.res(R.string.banner_new_request, UiText.res(e.request.vehicleType.res)), UiText.res(R.string.dot_join, UiText.res(R.string.route_from_to, e.request.from, e.request.to), UiText.plural(R.plurals.pax, e.request.passengers, e.request.passengers)), "request/${e.request.id}") }
            is RealtimeEvent.BidPlaced -> requestRepo.updateCompetition(e.requestId, e.bidCount)
            is RealtimeEvent.BidUpdated -> requestRepo.updateCompetition(e.requestId, e.bidCount)
            is RealtimeEvent.BidAccepted -> requestRepo.accepted(e.bidId, e.tripId, e.tripCode, e.advancePaid).onSuccess { t ->
                realtime.watchTrip(t.id)
                _banner.value = LiveBanner(LiveBanner.Kind.ACCEPTED, UiText.res(R.string.banner_accepted), UiText.res(R.string.banner_accepted_body, t.from, t.to, UiText.Taka(t.fare)), "won/${t.id}")
            }
            is RealtimeEvent.BidRejected -> { requestRepo.rejected(e.bidId); _banner.value = LiveBanner(LiveBanner.Kind.INFO, UiText.res(R.string.banner_not_selected), reasonText(e.reason, R.string.reason_other_driver), "requests") }
            is RealtimeEvent.TripCancelled -> { tripRepo.updateStatus(e.tripId, TripStatus.CANCELLED); _banner.value = LiveBanner(LiveBanner.Kind.WARNING, UiText.res(R.string.banner_cancelled), reasonText(e.reason, R.string.reason_cancelled_customer), "trip/${e.tripId}") }
            is RealtimeEvent.PaymentReceived -> _banner.value = LiveBanner(LiveBanner.Kind.INFO, UiText.res(R.string.banner_payment), UiText.res(R.string.banner_payment_body, UiText.Taka(e.amount), UiText.res(e.method.res)), "wallet")
            is RealtimeEvent.SlotLocked, is RealtimeEvent.SlotReleased -> { tripRepo.refresh() }
        }
    }

    override fun onCleared() { realtime.disconnect() }

    val requests = requestRepo.requests
    val bids = requestRepo.bids
    val trips = tripRepo.trips
    val predefined = tripRepo.predefined
    val blockedDays = tripRepo.blockedDays
    val summary = earningsRepo.summary
    val transactions = earningsRepo.transactions
    val accounts = earningsRepo.accounts
    val driver = profileRepo.driver
    val online: StateFlow<Boolean> = driver.map { it.online }.stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val ratings = profileRepo.ratings
    val notifications = profileRepo.notifications

    fun setOnline(v: Boolean) = viewModelScope.launch { profileRepo.setOnline(v).onFailure(::showError) }

    /** Open requests that are not slot-blocked first; conflicts are still shown with a "Slot booked" tag. */
    val openRequests: StateFlow<List<TripRequest>> = requests.map { l -> l.filter { it.status == RequestStatus.OPEN || it.status == RequestStatus.BIDDING } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun isSlotBlocked(r: TripRequest): Boolean = !policy.canBid(r, trips.value)
    fun conflictFor(r: TripRequest): Trip? = (policy.check(r.slot, trips.value) as? SlotCheck.Conflict)?.let { c -> trips.value.firstOrNull { it.id == c.withTripId } }

    // ---- Auth
    private val _auth = MutableStateFlow(AuthState())
    val authState: StateFlow<AuthState> = _auth
    /** Accepts Bangla (০-৯) or ASCII input; state and API always get ASCII. */
    fun setPhone(p: String) = _auth.update { it.copy(phone = DigitFormatter.digitsOnly(p, 11), error = null) }
    fun sendOtp(onOk: () -> Unit) = viewModelScope.launch {
        if (_auth.value.phone.length != 11) { _auth.update { it.copy(error = UiText.res(R.string.err_phone)) }; return@launch }
        _auth.update { it.copy(loading = true) }
        val captcha = bot().token(BotProtection.LOGIN) // reCAPTCHA Enterprise; null when disabled
        auth.requestOtp(_auth.value.phone, captcha)
            .onSuccess { _auth.update { it.copy(loading = false) }; onOk() }
            .onFailure { e -> _auth.update { it.copy(loading = false, error = e.toUiText(R.string.err_generic)) } }
    }
    fun verifyOtp(code: String, onOk: (kycDone: Boolean) -> Unit) = viewModelScope.launch {
        auth.verifyOtp(_auth.value.phone, DigitFormatter.digitsOnly(code, 6)).onSuccess(onOk).onFailure { e -> _auth.update { it.copy(error = e.toUiText(R.string.err_otp)) } }
    }
    fun setKycType(t: VehicleType) = _auth.update { it.copy(kycType = t) }
    /** Photo picked for KYC document [d] (content URI); null clears it. */
    fun setDocFile(d: String, uri: String?) = _auth.update {
        if (uri == null) it.copy(uploaded = it.uploaded - d, files = it.files - d) else it.copy(uploaded = it.uploaded + d, files = it.files + (d to uri))
    }
    fun submitKyc(onOk: () -> Unit) = viewModelScope.launch {
        _auth.update { it.copy(loading = true, error = null) }
        auth.submitKyc(_auth.value.kycType, _auth.value.files)
            .onSuccess { _auth.update { it.copy(loading = false) }; profileRepo.refresh(); onOk() }
            .onFailure { e -> _auth.update { it.copy(loading = false, error = e.toUiText(R.string.err_generic)) } }
    }
    fun deletePredefined(id: String) = viewModelScope.launch { tripRepo.deletePredefined(id).onFailure(::showError) }
    fun openNotifications() = viewModelScope.launch { profileRepo.refreshNotifications(); profileRepo.markNotificationsRead() }
    fun addPayoutAccount(method: PaymentMethod, account: String) = viewModelScope.launch {
        _wd.update { it.copy(loading = true, error = null) }
        earningsRepo.addAccount(method, DigitFormatter.toAsciiDigits(account).trim())
            .onSuccess { a -> _wd.update { it.copy(loading = false, account = a) } }
            .onFailure { e -> _wd.update { it.copy(loading = false, error = e.toUiText(R.string.err_generic)) } }
    }
    fun logout(onDone: () -> Unit) = viewModelScope.launch { auth.logout(); onDone() }

    // ---- Bidding
    private val _bid = MutableStateFlow(BidFormState())
    val bidForm: StateFlow<BidFormState> = _bid
    fun resetBid(prefill: Int? = null) { _bid.value = BidFormState(amount = prefill?.let { Integer.toString(it) } ?: "") }
    fun setBidAmount(v: String) = _bid.update { it.copy(amount = DigitFormatter.digitsOnly(v, 6), error = null) }
    fun setBidNote(v: String) = _bid.update { it.copy(note = v.take(140)) }
    fun submitBid(requestId: String) = viewModelScope.launch {
        val f = _bid.value
        val amt = f.amountInt ?: run { _bid.update { it.copy(error = UiText.res(R.string.err_rent)) }; return@launch }
        _bid.update { it.copy(submitting = true) }
        requestRepo.placeBid(requestId, amt, f.note)
            .onSuccess { b ->
                _bid.update { it.copy(submitting = false, submitted = b) }
                _live.update { BidReducer.myBid(it, requestId, b.id, b.amount) }
            }
            .onFailure { e -> _bid.update { it.copy(submitting = false, error = e.toUiText(R.string.err_generic)) } }
    }
    fun skip(requestId: String) = viewModelScope.launch { requestRepo.skip(requestId) }
    fun withdrawBid(bidId: String) = viewModelScope.launch { requestRepo.withdrawBid(bidId).onFailure(::showError) }

    // ---- Trips
    fun setTripStatus(id: String, s: TripStatus, m: PaymentMethod? = null) = viewModelScope.launch {
        tripRepo.updateStatus(id, s, m).onSuccess { if (s == TripStatus.COMPLETED) earningsRepo.refresh() }.onFailure(::showError)
    }
    fun savePredefined(p: PredefinedTrip) = viewModelScope.launch { tripRepo.savePredefined(p).onFailure(::showError) }
    fun toggleDay(d: java.time.LocalDate) = viewModelScope.launch { tripRepo.toggleBlocked(d).onFailure(::showError) }

    // ---- Earnings
    private val _recon = MutableStateFlow<Reconciliation?>(null)
    val reconciliation: StateFlow<Reconciliation?> = _recon
    fun loadReconciliation(period: String) = viewModelScope.launch { earningsRepo.reconciliation(period).onSuccess { _recon.value = it }.onFailure(::showError) }

    private val _wd = MutableStateFlow(WithdrawState())
    val withdraw: StateFlow<WithdrawState> = _wd
    fun resetWithdraw() { _wd.value = WithdrawState(account = earningsRepo.accounts.value.firstOrNull()) }
    fun setWdAccount(a: WithdrawAccount) = _wd.update { it.copy(account = a) }
    fun setWdAmount(v: String) = _wd.update { it.copy(amount = DigitFormatter.digitsOnly(v, 7), error = null) }
    fun setPin(v: String) = _wd.update { it.copy(pin = DigitFormatter.digitsOnly(v, 4), error = null) }
    fun confirmWithdraw() = viewModelScope.launch {
        val s = _wd.value
        val acc = s.account ?: run { _wd.update { it.copy(error = UiText.res(R.string.err_no_payout_account)) }; return@launch }
        _wd.update { it.copy(loading = true) }
        earningsRepo.withdraw(acc, DigitFormatter.parseInt(s.amount) ?: 0, DigitFormatter.digitsOnly(s.pin, 4))
            .onSuccess { tx -> _wd.update { it.copy(loading = false, done = tx) } }
            .onFailure { e -> _wd.update { it.copy(loading = false, error = e.toUiText(R.string.err_generic)) } }
    }
}
