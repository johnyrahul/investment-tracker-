package com.johnyrahul.investmenttracker.core.sync

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope

/**
 * Read-only Gmail access through Google Play services. The access token stays on the phone and is
 * only sent to gmail.googleapis.com.
 */
object GmailAuth {
    const val SCOPE = "https://www.googleapis.com/auth/gmail.readonly"

    sealed interface Result {
        data class Token(val accessToken: String) : Result

        /** The user has to pick an account / grant access; launch this from an activity. */
        data class NeedsConsent(val pendingIntent: PendingIntent) : Result
    }

    private val request: AuthorizationRequest =
        AuthorizationRequest.builder().setRequestedScopes(listOf(Scope(SCOPE))).build()

    suspend fun authorize(context: Context): Result =
        Identity.getAuthorizationClient(context).authorize(request).await().toResult()

    /** Reads the result of the consent screen launched from [Result.NeedsConsent]. */
    fun resultFromIntent(context: Context, data: Intent?): Result =
        Identity.getAuthorizationClient(context).getAuthorizationResultFromIntent(data).toResult()

    private fun AuthorizationResult.toResult(): Result {
        val intent = pendingIntent
        val token = accessToken
        return when {
            hasResolution() && intent != null -> Result.NeedsConsent(intent)
            token != null -> Result.Token(token)
            else -> error("Google didn't return a Gmail access token")
        }
    }
}
