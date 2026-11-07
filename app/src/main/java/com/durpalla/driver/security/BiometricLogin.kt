package com.durpalla.driver.security

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys

/**
 * Stores the driver's phone + password (encrypted at rest) so BiometricPrompt can unlock
 * password login after logout. Skipable during register; toggleable from Profile.
 */
class BiometricLoginStore(context: Context) {
    private val app = context.applicationContext
    private val prefs by lazy {
        val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        EncryptedSharedPreferences.create(
            "biometric_login",
            masterKeyAlias,
            app,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    fun canAuthenticate(): Boolean =
        BiometricManager.from(app).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) ==
            BiometricManager.BIOMETRIC_SUCCESS

    fun isEnabled(): Boolean = prefs.getBoolean(KEY_ENABLED, false)

    fun isEnabledFor(phone: String): Boolean =
        isEnabled() && prefs.getString(KEY_PHONE, null) == phone

    fun enable(phone: String, password: String) {
        prefs.edit()
            .putBoolean(KEY_ENABLED, true)
            .putString(KEY_PHONE, phone)
            .putString(KEY_PASSWORD, password)
            .apply()
    }

    /** Keep phone/password in sync after a successful password login/change. */
    fun refreshPasswordIfEnabled(phone: String, password: String) {
        if (isEnabledFor(phone)) enable(phone, password)
    }

    fun disable() {
        prefs.edit().clear().apply()
    }

    fun loadCredentials(): Pair<String, String>? {
        if (!isEnabled()) return null
        val phone = prefs.getString(KEY_PHONE, null) ?: return null
        val password = prefs.getString(KEY_PASSWORD, null) ?: return null
        return phone to password
    }

    companion object {
        private const val KEY_ENABLED = "enabled"
        private const val KEY_PHONE = "phone"
        private const val KEY_PASSWORD = "password"
    }
}

/** Shows the system fingerprint / face prompt. [onSuccess] only after a confirmed biometric match. */
fun FragmentActivity.promptBiometric(
    title: String,
    subtitle: String? = null,
    negativeButton: String,
    onSuccess: () -> Unit,
    onError: (String) -> Unit = {},
) {
    val executor = ContextCompat.getMainExecutor(this)
    val prompt = BiometricPrompt(
        this,
        executor,
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                onSuccess()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                if (errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON ||
                    errorCode == BiometricPrompt.ERROR_USER_CANCELED ||
                    errorCode == BiometricPrompt.ERROR_CANCELED
                ) {
                    return
                }
                onError(errString.toString())
            }
        },
    )
    val info = BiometricPrompt.PromptInfo.Builder()
        .setTitle(title)
        .setSubtitle(subtitle)
        .setNegativeButtonText(negativeButton)
        .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK)
        .build()
    prompt.authenticate(info)
}
