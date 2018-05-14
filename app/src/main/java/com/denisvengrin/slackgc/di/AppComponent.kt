package com.denisvengrin.slackgc.di

import com.denisvengrin.slackgc.network.SlackApi
import com.denisvengrin.slackgc.prefs.SlackStorage
import dagger.Component
import javax.inject.Singleton

@Singleton
@Component(modules = [ApiModule::class, PersistenceModule::class])
interface AppComponent {
    fun api(): SlackApi
    fun storage(): SlackStorage
}