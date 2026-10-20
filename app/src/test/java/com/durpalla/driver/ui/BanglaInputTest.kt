package com.durpalla.driver.ui

import com.durpalla.driver.data.*
import com.durpalla.driver.domain.*
import com.durpalla.driver.i18n.AppLanguage
import com.durpalla.driver.i18n.AppLocale
import com.durpalla.driver.realtime.ConnectionState
import com.durpalla.driver.realtime.RealtimeClient
import com.durpalla.driver.realtime.RealtimeEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.Locale

/** A driver typing on a Bangla keyboard (০-৯) must still reach the API with ASCII digits. */
@OptIn(ExperimentalCoroutinesApi::class)
class BanglaInputTest {
    private object SilentRealtime : RealtimeClient {
        override val connection = MutableStateFlow(ConnectionState.LIVE)
        override val events = MutableSharedFlow<RealtimeEvent>()
        override fun connect(driverId: String, authToken: () -> String?) {}
        override fun watchTrip(tripId: String) {}
        override fun unwatchTrip(tripId: String) {}
        override fun disconnect() {}
        override fun inject(event: RealtimeEvent) {}
    }

    /** Records the JSON body that would go over the wire. */
    private class RecordingRequests(private val d: RequestRepository) : RequestRepository by d {
        val bodies = mutableListOf<String>()
        override suspend fun placeBid(requestId: String, amount: Int, note: String): Result<Bid> {
            bodies += ApiJson.placeBid(requestId, amount, note); return d.placeBid(requestId, amount, note)
        }
    }
    private class RecordingAuth(private val d: AuthRepository) : AuthRepository by d {
        val bodies = mutableListOf<String>()
        override suspend fun requestOtp(phone: String, captchaToken: String?): Result<Unit> { bodies += ApiJson.requestOtp(phone, captchaToken); return d.requestOtp(phone, captchaToken) }
        override suspend fun verifyOtp(phone: String, code: String): Result<Boolean> { bodies += ApiJson.verifyOtp(phone, code); return d.verifyOtp(phone, code) }
    }
    private class RecordingEarnings(private val d: EarningsRepository) : EarningsRepository by d {
        var amount: Int? = null; var pin: String? = null
        override suspend fun withdraw(account: WithdrawAccount, amount: Int, pin: String): Result<WalletTx> { this.amount = amount; this.pin = pin; return d.withdraw(account, amount, pin) }
    }

    private val trips = FakeTripRepository()
    private val requests = RecordingRequests(FakeRequestRepository(trips, SlotLockPolicy(30)))
    private val auth = RecordingAuth(FakeAuthRepository())
    private val earnings = RecordingEarnings(FakeEarningsRepository())
    private lateinit var vm: DriverViewModel
    private val bnDigit = Regex("[০-৯]")

    @Before fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        AppLocale.language = AppLanguage.BANGLA
        Locale.setDefault(Locale.forLanguageTag("bn-BD"))
        vm = DriverViewModel(auth, requests, trips, earnings, FakeProfileRepository(), SlotLockPolicy(30), SilentRealtime)
    }
    @After fun tearDown() { Dispatchers.resetMain(); AppLocale.language = AppLanguage.ENGLISH; Locale.setDefault(Locale.US) }

    @Test fun banglaBidAmountReachesApiAsAscii() = runTest {
        val r = requests.requests.value.first { !vm.isSlotBlocked(it) }
        vm.setBidAmount("৩১০০")
        assertEquals("3100", vm.bidForm.value.amount)          // state is ASCII
        assertEquals(3100, vm.bidForm.value.amountInt)
        assertEquals("৳৩,১০০", taka(vm.bidForm.value.amountInt!!)) // display is Bangla
        vm.submitBid(r.id).join()
        val body = requests.bodies.single()
        assertTrue(body, body.contains("\"amount\":3100"))
        assertFalse(body, bnDigit.containsMatchIn(body))
        assertEquals(3100, vm.bidForm.value.submitted?.amount)
    }

    @Test fun banglaPhoneAndOtpReachApiAsAscii() = runTest {
        vm.setPhone("০১৭১১-২৩৪৫৬৭")
        assertEquals("01711234567", vm.authState.value.phone)
        vm.sendOtp {}.join()
        vm.verifyOtp("১২৩৪৫৬") {}.join()
        assertTrue(auth.bodies[0], auth.bodies[0].contains("\"phone\":\"+8801711234567\""))
        assertTrue(auth.bodies[1], auth.bodies[1].contains("\"code\":\"123456\""))
        auth.bodies.forEach { assertFalse(it, bnDigit.containsMatchIn(it)) }
    }

    @Test fun banglaWithdrawAmountAndPinAreAscii() = runTest {
        vm.resetWithdraw()
        vm.setWdAmount("৫০০০"); vm.setPin("১২৩৪")
        assertEquals("5000", vm.withdraw.value.amount); assertEquals("1234", vm.withdraw.value.pin)
        vm.confirmWithdraw().join()
        assertEquals(5000, earnings.amount); assertEquals("1234", earnings.pin)
    }

    @Test fun validationMessagesAreTranslatableNotRawEnglish() = runTest {
        vm.setPhone("০১৭")
        vm.sendOtp {}.join()
        assertEquals(com.durpalla.driver.i18n.UiText.res(com.durpalla.driver.R.string.err_phone), vm.authState.value.error)
    }
}
