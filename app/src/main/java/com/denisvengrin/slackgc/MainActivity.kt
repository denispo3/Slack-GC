package com.denisvengrin.slackgc

import android.os.Bundle
import android.support.v4.app.Fragment
import android.support.v7.app.AppCompatActivity
import com.denisvengrin.slackgc.fileslist.FilesListFragment
import com.denisvengrin.slackgc.fragment.LoginFragment
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.schedulers.Schedulers

class MainActivity : AppCompatActivity() {

    private val mDisposable = CompositeDisposable()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)

        if (savedInstanceState == null) {
            checkUserToken()
        }
    }

    private fun checkUserToken() {
        val disposable = SlackGCApp[this].appComponent.storage()
                .getAuthResponse()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    val needLogin = it.token.isEmpty()
                    openNextFragment(needLogin)
                }, {
                    openNextFragment(true)
                })
        mDisposable.add(disposable)
    }

    private fun openNextFragment(needLogin: Boolean) {
        val fragment: Fragment = if (needLogin) {
            LoginFragment.newInstance()
        } else {
            FilesListFragment.newInstance()
        }

        supportFragmentManager.beginTransaction().replace(R.id.container, fragment).commit()
    }

    override fun onDestroy() {
        mDisposable.clear()

        super.onDestroy()
    }

    companion object {
        const val LOG_TAG = "MainActivity"
    }
}
