package com.durpalla.driver.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.durpalla.driver.data.*
import com.durpalla.driver.domain.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class AuthState(val phone: String = "", val loading: Boolean = false, val error: String? = null, val kycType: VehicleType = VehicleType.SPEEDBOAT, val uploaded: Set<String> = emptySet())

data class BidFormState(val amount: String = "", val note: String = "", val error: String? = null, val submitting: Boolean = false, val submitted: Bid? = null) {
    val amountInt: Int? get() = amount.toIntOrNull()
}

data class WithdrawState(val account: WithdrawAccount? = null, val amount: String = "", val pin: String = "", val error: String? = null, val loading: Boolean = false, val done: WalletTx? = null)

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
) : ViewModel() {

    val requests = requestRepo.requests
    val bids = requestRepo.bids
    val trips = tripRepo.trips
    val predefined = tripRepo.predefined
    val blockedDays = tripRepo.blockedDays
    val summary = earningsRepo.summary
    val transactions = earningsRepo.transactions
    val accounts get() = earningsRepo.accounts
    val driver = profileRepo.driver
    val ratings = profileRepo.ratings
    val notifications = profileRepo.notifications

    private val _online = MutableStateFlow(true)
    val online: StateFlow<Boolean> = _online
    fun setOnline(v: Boolean) { _online.value = v }

    /** Open requests that are not slot-blocked first; conflicts are still shown with a "Slot booked" tag. */
    val openRequests: StateFlow<List<TripRequest>> = requests.map { l -> l.filter { it.status == RequestStatus.OPEN || it.status == RequestStatus.BIDDING } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun isSlotBlocked(r: TripRequest): Boolean = !policy.canBid(r, trips.value)
    fun conflictFor(r: TripRequest): Trip? = (policy.check(r.slot, trips.value) as? SlotCheck.Conflict)?.let { c -> trips.value.firstOrNull { it.id == c.withTripId } }

    // ---- Auth
    private val _auth = MutableStateFlow(AuthState())
    val authState: StateFlow<AuthState> = _auth
    fun setPhone(p: String) = _auth.update { it.copy(phone = p.filter(Char::isDigit).take(11), error = null) }
    fun sendOtp(onOk: () -> Unit) = viewModelScope.launch {
        if (_auth.value.phone.length != 11) { _auth.update { it.copy(error = "Enter an 11-digit mobile number") }; return@launch }
        _auth.update { it.copy(loading = true) }; auth.requestOtp(_auth.value.phone); _auth.update { it.copy(loading = false) }; onOk()
    }
    fun verifyOtp(code: String, onOk: (kycDone: Boolean) -> Unit) = viewModelScope.launch {
        auth.verifyOtp(_auth.value.phone, code).onSuccess(onOk).onFailure { e -> _auth.update { it.copy(error = e.message) } }
    }
    fun setKycType(t: VehicleType) = _auth.update { it.copy(kycType = t) }
    fun toggleDoc(d: String) = _auth.update { it.copy(uploaded = if (d in it.uploaded) it.uploaded - d else it.uploaded + d) }
    fun submitKyc(onOk: () -> Unit) = viewModelScope.launch { auth.submitKyc(_auth.value.kycType, _auth.value.uploaded.toList()); onOk() }

    // ---- Bidding
    private val _bid = MutableStateFlow(BidFormState())
    val bidForm: StateFlow<BidFormState> = _bid
    fun resetBid(prefill: Int? = null) { _bid.value = BidFormState(amount = prefill?.toString() ?: "") }
    fun setBidAmount(v: String) = _bid.update { it.copy(amount = v.filter(Char::isDigit).take(6), error = null) }
    fun setBidNote(v: String) = _bid.update { it.copy(note = v.take(140)) }
    fun submitBid(requestId: String) = viewModelScope.launch {
        val f = _bid.value
        val amt = f.amountInt ?: run { _bid.update { it.copy(error = "Enter a rent amount") }; return@launch }
        _bid.update { it.copy(submitting = true) }
        requestRepo.placeBid(requestId, amt, f.note)
            .onSuccess { b -> _bid.update { it.copy(submitting = false, submitted = b) } }
            .onFailure { e -> _bid.update { it.copy(submitting = false, error = e.message) } }
    }
    fun skip(requestId: String) = viewModelScope.launch { requestRepo.skip(requestId) }
    fun simulateWin(bidId: String, onTrip: (Trip) -> Unit) = viewModelScope.launch { requestRepo.simulateWin(bidId).onSuccess(onTrip) }

    // ---- Trips
    fun setTripStatus(id: String, s: TripStatus, m: PaymentMethod? = null) = viewModelScope.launch { tripRepo.updateStatus(id, s, m) }
    fun savePredefined(p: PredefinedTrip) = viewModelScope.launch { tripRepo.savePredefined(p) }
    fun toggleDay(d: java.time.LocalDate) = viewModelScope.launch { tripRepo.toggleBlocked(d) }

    // ---- Earnings
    private val _recon = MutableStateFlow<Reconciliation?>(null)
    val reconciliation: StateFlow<Reconciliation?> = _recon
    fun loadReconciliation(period: String) = viewModelScope.launch { _recon.value = earningsRepo.reconciliation(period) }

    private val _wd = MutableStateFlow(WithdrawState())
    val withdraw: StateFlow<WithdrawState> = _wd
    fun resetWithdraw() { _wd.value = WithdrawState(account = earningsRepo.accounts.first()) }
    fun setWdAccount(a: WithdrawAccount) = _wd.update { it.copy(account = a) }
    fun setWdAmount(v: String) = _wd.update { it.copy(amount = v.filter(Char::isDigit).take(7), error = null) }
    fun setPin(v: String) = _wd.update { it.copy(pin = v.filter(Char::isDigit).take(4), error = null) }
    fun confirmWithdraw() = viewModelScope.launch {
        val s = _wd.value
        val acc = s.account ?: return@launch
        _wd.update { it.copy(loading = true) }
        earningsRepo.withdraw(acc, s.amount.toIntOrNull() ?: 0, s.pin)
            .onSuccess { tx -> _wd.update { it.copy(loading = false, done = tx) } }
            .onFailure { e -> _wd.update { it.copy(loading = false, error = e.message) } }
    }
}
