package com.durpalla.driver.ui

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.durpalla.driver.ui.screens.OtpScreen
import com.durpalla.driver.ui.screens.PhoneLoginScreen
import com.durpalla.driver.ui.theme.DurpallaTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Screenshot tests. Record with: ./gradlew testDebugUnitTest -Proborazzi.test.record=true */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w390dp-h844dp-xxhdpi")
class AuthScreenshotTest {
    @Test fun login() = captureRoboImage("build/shots/driver_login.png") { DurpallaTheme { PhoneLoginScreen(AuthState(phone = "01711234567"), {}, {}) } }
    @Test fun otp() = captureRoboImage("build/shots/driver_otp.png") { DurpallaTheme { OtpScreen("01711234567", null, {}, {}) } }
    @Config(qualifiers = "w320dp-h568dp-xhdpi")
    @Test fun loginSmall() = captureRoboImage("build/shots/driver_login_small.png") { DurpallaTheme { PhoneLoginScreen(AuthState(phone = "017"), {}, {}) } }
}
