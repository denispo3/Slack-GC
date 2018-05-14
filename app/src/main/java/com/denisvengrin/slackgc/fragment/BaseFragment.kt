package com.denisvengrin.slackgc.fragment

import android.support.v4.app.Fragment
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.disposables.Disposable

open class BaseFragment : Fragment() {

    private var compositeDisposable: CompositeDisposable = CompositeDisposable()

    fun Disposable.addToCompositeDisposable(): Disposable {
        compositeDisposable.add(this)
        return this
    }

    override fun onDestroyView() {
        compositeDisposable.clear()

        super.onDestroyView()
    }
}