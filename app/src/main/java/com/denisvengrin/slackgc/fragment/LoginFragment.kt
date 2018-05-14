package com.denisvengrin.slackgc.fragment

import android.annotation.SuppressLint
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.support.v4.app.Fragment
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import com.denisvengrin.slackgc.BuildConfig
import com.denisvengrin.slackgc.R
import com.denisvengrin.slackgc.SlackGCApp
import com.denisvengrin.slackgc.data.AuthResponse
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import kotlinx.android.synthetic.main.fragment_login.*

class LoginFragment : BaseFragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_login, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setHasOptionsMenu(false)

        webView.initWebView { url ->
            val code: String? = Uri.parse(url).getQueryParameter("code")
            if (code != null) {
                obtainAccessToken(code)
            }
        }

        loadAuthPage()
    }

    private fun loadAuthPage() {
        webView.loadUrl("https://slack.com/oauth/authorize?" +
                "client_id=${getString(R.string.slack_api_client_id)}&" +
                "scope=files:read,files:write:user&" +
                "redirect_uri=$REDIRECT_URL")
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun WebView.initWebView(redirectCallback: (url: String) -> Unit) {
        webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                if (url?.startsWith(REDIRECT_URL) == true) {
                    redirectCallback(url)

                    return true
                }
                return false
            }
        }

        webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                super.onProgressChanged(view, newProgress)

                setWebProgress(newProgress)
            }
        }

        if (BuildConfig.DEBUG && Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            WebView.setWebContentsDebuggingEnabled(true)
        }

        with(settings) {
            javaScriptEnabled = true
            domStorageEnabled = true

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            }
        }
    }

    private fun obtainAccessToken(code: String) {
        Log.d(TAG, "code $code")

        setProgressLoading(true)

        val fields = mapOf("client_id" to getString(R.string.slack_api_client_id),
                "client_secret" to getString(R.string.slack_api_secret),
                "code" to code,
                "redirect_uri" to REDIRECT_URL)

        SlackGCApp[activity!!].appComponent.api().login(fields)
                .subscribeOn(Schedulers.io())
                .flatMapCompletable {
                    Log.d(TAG, "token ${it.token}")
                    SlackGCApp[context!!].appComponent.storage().setAuthResponse(it)
                }
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    setProgressLoading(false)
                    openFilesListFragment()
                }, {
                    it.printStackTrace()
                    setProgressLoading(false)
                })
                .addToCompositeDisposable()
    }

    private fun openFilesListFragment() {
        fragmentManager
                ?.beginTransaction()
                ?.replace(R.id.container, FilesListFragment.newInstance())
                ?.commit()
    }

    private fun setProgressLoading(load: Boolean) {
        if (load) {
            progressBar.visibility = View.VISIBLE
        } else {
            progressBar.visibility = View.GONE
        }
    }

    private fun setWebProgress(progress: Int) {
        if (progress == 100) {
            progressBarWeb.visibility = View.GONE
        } else {
            progressBarWeb.visibility = View.VISIBLE
            progressBarWeb.isIndeterminate = false
            progressBarWeb.progress = progress
        }
    }

    companion object {

        const val REDIRECT_URL = "http://slack.gc/redirect"
        const val TAG = "LoginFragment"

        fun newInstance() = LoginFragment()
    }

}