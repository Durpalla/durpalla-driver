package com.durpalla.driver.security

import android.app.Application
import android.util.Log
import com.google.android.recaptcha.Recaptcha
import com.google.android.recaptcha.RecaptchaAction
import com.google.android.recaptcha.RecaptchaClient

/**
 * Android counterpart of web reCAPTCHA v3: Google reCAPTCHA Enterprise for mobile. Scores are invisible.
 * The returned token goes to the backend with the OTP request; the backend calls
 * `projects.assessments.create` and rejects low scores (see README).
 */
interface BotProtection {
    /** Returns a token for [action], or null if protection is disabled/unavailable (backend decides policy). */
    suspend fun token(action: String): String?
    companion object { const val LOGIN = "LOGIN" }
}

/** Used when RECAPTCHA_SITE_KEY is empty (local dev, tests). */
object NoopBotProtection : BotProtection { override suspend fun token(action: String): String? = null }

class RecaptchaBotProtection(private val app: Application, private val siteKey: String) : BotProtection {
    private var client: RecaptchaClient? = null
    private suspend fun client(): RecaptchaClient? = client ?: runCatching { Recaptcha.fetchClient(app, siteKey) }
        .onFailure { Log.w("BotProtection", "reCAPTCHA init failed", it) }.getOrNull()?.also { client = it }

    override suspend fun token(action: String): String? {
        val c = client() ?: return null
        val a = if (action == BotProtection.LOGIN) RecaptchaAction.LOGIN else RecaptchaAction.custom(action)
        return c.execute(a, timeout = 10_000L).onFailure { Log.w("BotProtection", "execute failed", it) }.getOrNull()
    }

    companion object {
        fun create(app: Application, siteKey: String): BotProtection = if (siteKey.isBlank()) NoopBotProtection else RecaptchaBotProtection(app, siteKey)
    }
}
