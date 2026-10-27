package io.legado.app.ui.login

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslError
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.webkit.CookieManager
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.activityViewModels
import io.legado.app.R
import io.legado.app.base.BaseFragment
import io.legado.app.constant.AppConst
import io.legado.app.data.entities.BaseSource
import io.legado.app.databinding.FragmentWebViewLoginBinding
import io.legado.app.help.http.CookieStore
import io.legado.app.lib.theme.accentColor
import io.legado.app.lib.theme.backgroundColor
import io.legado.app.utils.NetworkUtils
import io.legado.app.utils.applyCompatibilitySettings
import io.legado.app.utils.gone
import io.legado.app.utils.longSnackbar
import io.legado.app.utils.openUrl
import io.legado.app.utils.setOnApplyWindowInsetsListenerCompat
import io.legado.app.utils.snackbar
import io.legado.app.utils.toWebViewRequestHeaders
import io.legado.app.utils.viewbindingdelegate.viewBinding
import splitties.views.bottomPadding

class WebViewLoginFragment : BaseFragment(R.layout.fragment_web_view_login) {

    private val binding by viewBinding(FragmentWebViewLoginBinding::bind)
    private val viewModel by activityViewModels<SourceLoginViewModel>()

    private var checking = false
    private var webViewCookieManager: CookieManager? = null
    private var webViewDestroyed = false

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        binding.root.setBackgroundColor(requireContext().backgroundColor)
        // A15+ 强制 e2e:登录页底部表单/提交按钮不被手势条遮挡;含 IME(登录必有输入)
        binding.root.setOnApplyWindowInsetsListenerCompat { v, windowInsets ->
            val typeMask = WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime()
            val insets = windowInsets.getInsets(typeMask)
            v.bottomPadding = insets.bottom
            windowInsets
        }
        setSupportToolbar(binding.titleBar.toolbar)
        viewModel.source?.let {
            binding.titleBar.title = getString(R.string.login_source, it.getTag())
            initWebView(it)
        }
    }

    override fun onCompatCreateOptionsMenu(menu: Menu) {
        menuInflater.inflate(R.menu.source_webview_login, menu)
    }

    override fun onCompatOptionsItemSelected(item: MenuItem) {
        when (item.itemId) {
            R.id.menu_ok -> {
                if (!checking) {
                    checking = true
                    binding.titleBar.snackbar(R.string.check_host_cookie)
                    viewModel.source?.let {
                        loadUrl(it)
                    }
                }
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun initWebView(source: BaseSource) {
        binding.progressBar.fontColor = accentColor
        val taskScope = source.takeIf { it.enabledCookieJar == true }
            ?.cookieScopeKey
            ?.let(io.legado.app.help.http.CookieBridge::taskCookieScopeValue)
        webViewCookieManager = runCatching {
            binding.webView.applyCompatibilitySettings(
                taskCookieScope = taskScope,
                applyStoredCookies = false
            )
        }.getOrElse {
            binding.root.longSnackbar(it.message ?: "Task WebView profile unavailable")
            return
        }
        binding.webView.settings.apply {
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            domStorageEnabled = true
            useWideViewPort = true
            loadWithOverviewMode = true
            builtInZoomControls = true
            javaScriptEnabled = true
            displayZoomControls = false
        }
        binding.webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                savePageCookie(source, webViewCookieManager ?: CookieManager.getInstance(), url)
                super.onPageStarted(view, url, favicon)
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                savePageCookie(source, webViewCookieManager ?: CookieManager.getInstance(), url)
                if (checking) {
                    activity?.finish()
                }
                super.onPageFinished(view, url)
            }

            override fun shouldOverrideUrlLoading(
                view: WebView,
                request: WebResourceRequest
            ): Boolean {
                return shouldOverrideUrlLoading(request.url)
            }

            @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION", "KotlinRedundantDiagnosticSuppress")
            override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
                return shouldOverrideUrlLoading(Uri.parse(url))
            }

            private fun shouldOverrideUrlLoading(url: Uri): Boolean {
                when (url.scheme) {
                    "http", "https" -> {
                        return false
                    }

                    else -> {
                        binding.root.longSnackbar(R.string.jump_to_another_app, R.string.confirm) {
                            context?.openUrl(url)
                        }
                        return true
                    }
                }
            }

            @SuppressLint("WebViewClientOnReceivedSslError")
            override fun onReceivedSslError(
                view: WebView?,
                handler: SslErrorHandler?,
                error: SslError?
            ) {
                handler?.proceed()
            }
        }
        binding.webView.webChromeClient = object : WebChromeClient() {

            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                super.onProgressChanged(view, newProgress)
                binding.progressBar.setDurProgress(newProgress)
                binding.progressBar.gone(newProgress == 100)
            }

        }
        loadUrl(source)
    }

    private fun savePageCookie(
        source: BaseSource,
        cookieManager: CookieManager,
        url: String?
    ) {
        if (url.isNullOrBlank()) return
        val cookie = cookieManager.getCookie(url) ?: return
        val scope = source.cookieScopeKey
        if (scope != null && source.enabledCookieJar == true) {
            CookieStore.setTaskCookie(scope, url, cookie)
        } else {
            CookieStore.setCookie(source.getKey(), cookie)
        }
    }

    private fun loadUrl(source: BaseSource) {
        val loginUrl = source.loginUrl ?: return
        val absoluteUrl = NetworkUtils.getAbsoluteURL(source.getKey(), loginUrl)
        val taskScope = source.takeIf { it.enabledCookieJar == true }
            ?.cookieScopeKey
            ?.let(io.legado.app.help.http.CookieBridge::taskCookieScopeValue)
        webViewCookieManager = binding.webView.applyCompatibilitySettings(
            absoluteUrl,
            viewModel.headerMap,
            applyStoredCookies = true,
            taskCookieScope = taskScope,
            onCookiesApplied = {
                if (!webViewDestroyed) {
                    binding.webView.loadUrl(
                        absoluteUrl,
                        viewModel.headerMap.toWebViewRequestHeaders()
                    )
                }
            }
        )
    }

    override fun onDestroy() {
        webViewDestroyed = true
        webViewCookieManager?.flush()
        super.onDestroy()
        binding.webView.destroy()
    }

}
