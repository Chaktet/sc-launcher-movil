package com.tungsten.fcl.sc

import android.annotation.SuppressLint
import android.app.Dialog
import android.graphics.Color
import android.net.Uri
import android.view.Gravity
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.FrameLayout
import com.tungsten.fcl.activity.MainActivity
import com.tungsten.fcl.setting.Accounts
import com.tungsten.fclcore.auth.OAuth
import com.tungsten.fclcore.task.Schedulers
import java.util.concurrent.CancellationException
import java.util.concurrent.CompletableFuture

/**
 * SC: Microsoft sign-in with the authorization code in an in-app window (PLAN-APP-ANDROID.md, phase 4).
 *
 * The Entra app we use today is the one of the desktop launcher (Helios' client ID, OAUTH_API_KEY). It does not
 * allow FCL's device code flow (AADSTS70002), only the code flow with Microsoft's own "nativeclient" redirect, exactly
 * as the desktop launcher does it. This window loads the authorize page and catches that redirect; FCL's
 * MicrosoftService then does the rest (Xbox Live, XSTS, Minecraft profile) and refreshes the token later with the
 * usual v2.0 endpoint.
 */
object ScMicrosoft {
    private const val REDIRECT = "https://login.microsoftonline.com/common/oauth2/nativeclient"
    private const val TOKEN = "https://login.microsoftonline.com/consumers/oauth2/v2.0/token"

    @JvmField
    val OAUTH = OAuth(
        "https://login.microsoftonline.com/consumers/oauth2/v2.0/authorize",
        TOKEN,
        "https://login.microsoftonline.com/consumers/oauth2/v2.0/devicecode",
        TOKEN
    )

    @Volatile
    private var pending: CompletableFuture<String>? = null

    /** OAuth callback for the code flow: same client ID as FCL's, our window instead of a localhost server. */
    @JvmField
    val CALLBACK: OAuth.Callback = object : OAuth.Callback {
        override fun startServer(): OAuth.Session {
            val future = CompletableFuture<String>()
            pending = future
            return object : OAuth.Session {
                override fun getRedirectURI() = REDIRECT
                override fun waitFor(): String = future.get()
            }
        }

        override fun grantDeviceCode(userCode: String, verificationURI: String) = Unit
        override fun loginCompletedDeviceCode() = Unit
        override fun openBrowser(url: String) {
            val future = pending ?: return
            Schedulers.androidUIThread().execute { showWindow(url, future) }
        }

        override fun getClientId(): String = Accounts.OAUTH_CALLBACK.clientId
        override fun getClientSecret(): String? = null
        override fun isPublicClient() = true
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun showWindow(url: String, future: CompletableFuture<String>) {
        val activity = MainActivity.getInstance()
        if (activity == null) {
            future.completeExceptionally(CancellationException("no activity"))
            return
        }
        val dialog = Dialog(activity, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        val web = WebView(activity)
        web.setBackgroundColor(Color.WHITE)
        web.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = false
            allowContentAccess = false
        }

        fun finish(uri: Uri): Boolean {
            if (!uri.toString().startsWith(REDIRECT)) return false
            val code = uri.getQueryParameter("code")
            if (code != null) future.complete(code)
            else future.completeExceptionally(
                IllegalStateException(uri.getQueryParameter("error_description") ?: uri.getQueryParameter("error") ?: "sin código")
            )
            dialog.dismiss()
            return true
        }
        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                request.isForMainFrame && finish(request.url)

            override fun onPageStarted(view: WebView, url: String, favicon: android.graphics.Bitmap?) {
                if (finish(Uri.parse(url))) view.stopLoading()
            }
        }

        val close = Button(activity).apply {
            text = "✕  Cancelar"
            isAllCaps = false
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#B22839"))
            setOnClickListener { dialog.cancel() }
        }
        val root = FrameLayout(activity)
        root.addView(web, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        root.addView(close, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.END))
        dialog.setContentView(root)
        // Back button or «Cancelar»: the sign-in is cancelled, nothing is saved
        dialog.setOnCancelListener { future.completeExceptionally(InterruptedException("cancelled")) }
        dialog.setOnDismissListener { web.destroy(); if (pending === future) pending = null }
        dialog.show()
        web.loadUrl(url)
    }
}
