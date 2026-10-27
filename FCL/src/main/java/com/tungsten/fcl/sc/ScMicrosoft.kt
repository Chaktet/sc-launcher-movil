package com.tungsten.fcl.sc

import android.annotation.SuppressLint
import android.app.Dialog
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.util.Base64
import android.view.Gravity
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.FrameLayout
import com.tungsten.fcl.activity.MainActivity
import com.tungsten.fcl.setting.Accounts
import com.tungsten.fclcore.auth.AuthenticationException
import com.tungsten.fclcore.auth.NoSelectedCharacterException
import com.tungsten.fclcore.auth.OAuth
import com.tungsten.fclcore.auth.ServerDisconnectException
import com.tungsten.fclcore.auth.ServerResponseMalformedException
import com.tungsten.fclcore.task.Schedulers
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * SC: Microsoft sign-in with the authorization code in an in-app window (PLAN-APP-ANDROID.md, phase 4).
 *
 * The Entra app we use today is the desktop launcher's (Helios' client ID, OAUTH_API_KEY). It does not allow FCL's
 * device code flow (AADSTS70002), only the code flow with Microsoft's own "nativeclient" redirect. This window loads
 * the authorize page and catches that redirect; the attempt is bound to its window with `state` and PKCE (S256), so a
 * code that was not issued for this attempt is refused. FCL's MicrosoftService then does Xbox Live, XSTS and the
 * Minecraft profile, and later refreshes the token with the usual v2.0 endpoint (same client, public).
 */
object ScMicrosoft {
    private const val AUTHORIZE = "https://login.microsoftonline.com/consumers/oauth2/v2.0/authorize"
    private const val TOKEN = "https://login.microsoftonline.com/consumers/oauth2/v2.0/token"
    private const val REDIRECT = "https://login.microsoftonline.com/common/oauth2/nativeclient"
    private val REDIRECT_URI: Uri = Uri.parse(REDIRECT)

    private class Attempt(val state: String, val verifier: String) {
        val code = CompletableFuture<String>()
        @Volatile var dialog: Dialog? = null
    }

    /** Only one sign-in at a time; a second request while one is open is ignored. */
    private val lock = Any()
    private var current: Attempt? = null

    private fun random(bytes: Int): String {
        val b = ByteArray(bytes).also { SecureRandom().nextBytes(it) }
        return Base64.encodeToString(b, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
    }

    /**
     * Background thread. Opens the window, waits (at most 15 min) for the code of THIS attempt and exchanges it.
     * Cancelling (back, «Cancelar», the activity going away) throws NoSelectedCharacterException, like FCL's own flow.
     */
    @JvmStatic
    @Throws(AuthenticationException::class)
    fun authenticate(scope: String): OAuth.Result {
        val attempt = Attempt(random(24), random(48))
        synchronized(lock) {
            if (current != null) throw NoSelectedCharacterException()
            current = attempt
        }
        try {
            val challenge = Base64.encodeToString(
                MessageDigest.getInstance("SHA-256").digest(attempt.verifier.toByteArray(Charsets.US_ASCII)),
                Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP
            )
            val url = Uri.parse(debugAuthorize() ?: AUTHORIZE).buildUpon()
                .appendQueryParameter("client_id", clientId())
                .appendQueryParameter("response_type", "code")
                .appendQueryParameter("redirect_uri", REDIRECT)
                .appendQueryParameter("scope", scope)
                .appendQueryParameter("prompt", "select_account")
                .appendQueryParameter("state", attempt.state)
                .appendQueryParameter("code_challenge", challenge)
                .appendQueryParameter("code_challenge_method", "S256")
                .build().toString()
            Schedulers.androidUIThread().execute { showWindow(url, attempt) }
            val code = try {
                attempt.code.get(15, TimeUnit.MINUTES)
            } catch (e: TimeoutException) {
                throw NoSelectedCharacterException()
            } catch (e: InterruptedException) {
                throw NoSelectedCharacterException()
            } catch (e: ExecutionException) {
                if (e.cause is InterruptedException) throw NoSelectedCharacterException()
                throw AuthenticationException(e.cause?.message ?: "Microsoft no devolvió el código", e.cause)
            }
            return exchange(code, attempt.verifier, scope)
        } finally {
            synchronized(lock) { if (current === attempt) current = null }
            attempt.dialog?.let { d -> Schedulers.androidUIThread().execute { if (d.isShowing) d.dismiss() } }
        }
    }

    /** The activity is going away: close the window and cancel the attempt. */
    @JvmStatic
    fun cancel() {
        val a = synchronized(lock) { current } ?: return
        a.code.completeExceptionally(InterruptedException("cancelled"))
        a.dialog?.let { if (it.isShowing) it.dismiss() }
    }

    private fun clientId(): String = Accounts.OAUTH_CALLBACK.clientId

    /**
     * Debug builds only (CI emulator, whose software GPU freezes on Microsoft's real page): a light local page that
     * plays Microsoft's part and redirects to nativeclient with the state it was given. Release builds ignore it.
     */
    private fun debugAuthorize(): String? {
        val c = MainActivity.getInstance() ?: return null
        if ((c.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) == 0) return null
        return c.getSharedPreferences("sc", android.content.Context.MODE_PRIVATE).getString("debug_ms_authorize_url", null)
            ?.takeIf { it.isNotBlank() }
    }

    private fun exchange(code: String, verifier: String, scope: String): OAuth.Result {
        val body = mapOf(
            "client_id" to clientId(), "grant_type" to "authorization_code", "code" to code,
            "redirect_uri" to REDIRECT, "scope" to scope, "code_verifier" to verifier
        ).entries.joinToString("&") { (k, v) -> k + "=" + URLEncoder.encode(v, "UTF-8") }
        try {
            val c = URL(TOKEN).openConnection() as HttpURLConnection
            c.connectTimeout = 15000
            c.readTimeout = 20000
            c.requestMethod = "POST"
            c.doOutput = true
            c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=utf-8")
            c.setRequestProperty("Accept", "application/json")
            try {
                c.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                val stream = if (c.responseCode in 200..299) c.inputStream else c.errorStream
                val j = JSONObject(stream?.bufferedReader()?.use { it.readText() } ?: "{}")
                if (j.has("error")) throw AuthenticationException("Microsoft: " + j.optString("error_description", j.getString("error")).lineSequence().first())
                return OAuth.Result(j.getString("access_token"), j.optString("refresh_token", null))
            } finally {
                c.disconnect()
            }
        } catch (e: IOException) {
            throw ServerDisconnectException(e)
        } catch (e: JSONException) {
            throw ServerResponseMalformedException(e)
        }
    }

    /** True for exactly https://login.microsoftonline.com/common/oauth2/nativeclient (any query). */
    private fun isRedirect(uri: Uri) =
        uri.scheme == REDIRECT_URI.scheme && uri.host == REDIRECT_URI.host && uri.port == -1 &&
            uri.userInfo == null && uri.path == REDIRECT_URI.path

    @SuppressLint("SetJavaScriptEnabled")
    private fun showWindow(url: String, attempt: Attempt) {
        val activity = MainActivity.getInstance()
        if (activity == null || activity.isFinishing || attempt.code.isDone) {
            attempt.code.completeExceptionally(InterruptedException("no activity"))
            return
        }
        val dialog = Dialog(activity, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        attempt.dialog = dialog
        val web = WebView(activity)
        web.setBackgroundColor(Color.WHITE)
        web.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = false
            allowContentAccess = false
        }

        fun finish(uri: Uri): Boolean {
            if (!isRedirect(uri)) return false
            val codes = uri.getQueryParameters("code")
            val states = uri.getQueryParameters("state")
            when {
                codes.size == 1 && states.size == 1 && states[0] == attempt.state -> attempt.code.complete(codes[0])
                uri.getQueryParameter("error") != null -> attempt.code.completeExceptionally(
                    IllegalStateException(uri.getQueryParameter("error_description") ?: uri.getQueryParameter("error"))
                )
                else -> attempt.code.completeExceptionally(IllegalStateException("La respuesta de Microsoft no corresponde a este inicio de sesión."))
            }
            dialog.dismiss()
            return true
        }
        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                request.isForMainFrame && finish(request.url)

            override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                if (url != null && finish(Uri.parse(url))) view.stopLoading()
            }
        }

        val close = Button(activity).apply {
            text = "✕  Cancelar"
            isAllCaps = false
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#B22839"))
            setOnClickListener { dialog.dismiss() }
        }
        val root = FrameLayout(activity)
        root.addView(web, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        root.addView(close, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.END))
        dialog.setContentView(root)
        // Any close without a code (back, «Cancelar», dismissed by the system) cancels the attempt; nothing is saved
        dialog.setOnDismissListener {
            attempt.code.completeExceptionally(InterruptedException("cancelled"))
            web.destroy()
        }
        dialog.show()
        web.loadUrl(url)
    }
}
