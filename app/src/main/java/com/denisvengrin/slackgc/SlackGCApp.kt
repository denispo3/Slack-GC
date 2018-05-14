package com.denisvengrin.slackgc

import android.app.Application
import android.content.Context
import com.denisvengrin.slackgc.di.AppComponent
import com.denisvengrin.slackgc.di.DaggerAppComponent
import com.denisvengrin.slackgc.di.PersistenceModule
import io.reactivex.plugins.RxJavaPlugins

class SlackGCApp : Application() {

    lateinit var appComponent: AppComponent

    override fun onCreate() {
        super.onCreate()

        RxJavaPlugins.setErrorHandler { it.printStackTrace() }

        appComponent = DaggerAppComponent.builder()
                .persistenceModule(PersistenceModule(this))
                .build()
    }

    companion object {
        operator fun get(context: Context): SlackGCApp {
            return context.applicationContext as SlackGCApp
        }
    }
}