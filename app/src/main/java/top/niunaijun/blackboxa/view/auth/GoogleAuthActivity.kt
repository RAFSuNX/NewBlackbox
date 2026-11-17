package top.niunaijun.blackboxa.view.auth

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import top.niunaijun.blackboxa.R
import top.niunaijun.blackboxa.databinding.ActivityGoogleAuthBinding
import top.niunaijun.blackboxa.util.inflate
import top.niunaijun.blackboxa.view.base.BaseActivity
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class GoogleAuthActivity : BaseActivity() {

    private val viewBinding: ActivityGoogleAuthBinding by inflate()
    private var userId: Int = 0

    companion object {
        const val EXTRA_USER_ID  = "userId"
        const val RESULT_EMAIL   = "email"
        const val RESULT_TOKEN   = "token"
        const val RESULT_REFRESH = "refresh"

        // Google's embedded account setup — same URL microG uses.
        // No OAuth client registration required; uses Google's own embedded flow.
        private const val EMBEDDED_SETUP =
            "https://accounts.google.com/EmbeddedSetup/identifier" +
            "?flowName=GlifWebSignIn" +
            "&continue=https://accounts.google.com/EmbeddedSetupFinish/identifier%3FflowName%3DGlifWebSignIn"

        // microG's registered OAuth2 client for the token scope exchange.
        // Apache 2.0 licensed, from https://github.com/microg/GmsCore
        private const val MICROG_CLIENT_ID =
            "873132954174-b6dr3pm3hsjjns3lrm2brc5q55gq5mte.apps.googleusercontent.com"

        fun start(context: Context, userId: Int, requestCode: Int) {
            val intent = Intent(context, GoogleAuthActivity::class.java)
            intent.putExtra(EXTRA_USER_ID, userId)
            (context as Activity).startActivityForResult(intent, requestCode)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(viewBinding.root)
        userId = intent.getIntExtra(EXTRA_USER_ID, 0)
        initToolbar(viewBinding.toolbarLayout.toolbar, R.string.add_google_account, true)
        setupWebView()
    }

    @Suppress("SetJavaScriptEnabled")
    private fun setupWebView() {
        val wv = viewBinding.webView
        with(wv.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            // Strip "wv" from UA so Google doesn't block embedded webview
            userAgentString = userAgentString.replace(Regex("\\bwv\\b"), "").trim()
        }

        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(wv, true)
        }

        wv.addJavascriptInterface(JsBridge(), "BlackBoxAuth")

        wv.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) {
                viewBinding.progress.progress = newProgress
                viewBinding.progress.visibility =
                    if (newProgress < 100) View.VISIBLE else View.GONE
            }
        }

        wv.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                return false // Let WebView handle all navigation
            }

            override fun onPageFinished(view: WebView, url: String?) {
                super.onPageFinished(view, url)
                if (url == null) return

                // Detect end of login flow
                if (url.contains("EmbeddedSetupFinish") ||
                    url.contains("myaccount.google.com") ||
                    url.contains("accounts.google.com/o/oauth2/approval")) {
                    extractEmail(view)
                }
            }
        }

        wv.loadUrl(EMBEDDED_SETUP)
    }

    private fun extractEmail(view: WebView) {
        // Try multiple ways to find the user's email from the final page
        view.evaluateJavascript("""
            (function() {
                var email = '';
                // Check meta tags
                document.querySelectorAll('meta').forEach(function(m) {
                    if (m.name === 'google-signin-email' || m.name === 'account-email')
                        email = m.content || email;
                });
                // Check data attributes
                var el = document.querySelector('[data-email],[data-signin-email]');
                if (!email && el) email = el.dataset.email || el.dataset.signinEmail || '';
                // Fallback: scan visible text for an email pattern
                if (!email) {
                    var m = (document.body.innerText || '').match(
                        /[a-zA-Z0-9._%+\-]+@[a-zA-Z0-9.\-]+\.[a-zA-Z]{2,}/
                    );
                    if (m) email = m[0];
                }
                BlackBoxAuth.onEmailFound(email);
            })();
        """.trimIndent(), null)
    }

    inner class JsBridge {
        @JavascriptInterface
        fun onEmailFound(email: String) {
            if (email.isBlank() || !email.contains("@")) return
            runOnUiThread {
                viewBinding.progress.visibility = View.VISIBLE
            }
            // Exchange the authenticated session for an OAuth token in background
            Thread {
                val cookies = CookieManager.getInstance()
                    .getCookie("https://accounts.google.com") ?: ""
                val token = fetchOAuthToken(email, cookies)
                runOnUiThread {
                    viewBinding.progress.visibility = View.GONE
                    val result = Intent().apply {
                        putExtra(RESULT_EMAIL, email)
                        putExtra(RESULT_TOKEN, token ?: "")
                        putExtra(EXTRA_USER_ID, userId)
                    }
                    setResult(RESULT_OK, result)
                    finish()
                }
            }.start()
        }
    }

    /**
     * Uses Google's android auth endpoint (same as microG's AuthManager) to exchange
     * the EmbeddedSetup session for an OAuth2 access token.
     *
     * Endpoint: https://android.googleapis.com/auth
     * This is the standard OAuth token issuance endpoint used by Android GMS.
     */
    private fun fetchOAuthToken(email: String, cookies: String): String? {
        return try {
            val conn = URL("https://android.googleapis.com/auth").openConnection()
                as HttpURLConnection
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.connectTimeout = 15_000
            conn.readTimeout = 15_000
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            conn.setRequestProperty("Cookie", cookies)

            val scope = "oauth2:https://www.googleapis.com/auth/userinfo.email " +
                        "https://www.googleapis.com/auth/userinfo.profile openid"

            val params = mapOf(
                "Email"           to email,
                "service"         to scope,
                "source"          to "android",
                "androidId"       to "0000000000000000",
                "app"             to "com.google.android.googlequicksearchbox",
                "client_sig"      to "38918a453d07199354f8b19af05ec6562ced5788",
                "callerSig"       to "38918a453d07199354f8b19af05ec6562ced5788",
                "device_country"  to "us",
                "operatorCountry" to "us",
                "lang"            to "en",
                "sdk_version"     to "30"
            ).entries.joinToString("&") {
                "${URLEncoder.encode(it.key, "UTF-8")}=${URLEncoder.encode(it.value, "UTF-8")}"
            }

            OutputStreamWriter(conn.outputStream).use { it.write(params) }

            val response = conn.inputStream.bufferedReader().readText()
            // Response is key=value pairs
            response.lines().find { it.startsWith("Auth=") }
                ?.removePrefix("Auth=")
                ?.trim()
        } catch (e: Exception) {
            null
        }
    }
}
