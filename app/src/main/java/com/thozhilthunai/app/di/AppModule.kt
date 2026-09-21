package com.thozhilthunai.app.di

import com.google.gson.Gson
import com.thozhilthunai.app.domain.DeterministicTextParser
import com.thozhilthunai.app.domain.TextParser
import com.thozhilthunai.app.speech.AndroidSpeechGateway
import com.thozhilthunai.app.speech.SpeechGateway
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AppModule {

    @Binds
    @Singleton
    abstract fun bindTextParser(impl: DeterministicTextParser): TextParser

    @Binds
    @Singleton
    abstract fun bindSpeechGateway(impl: AndroidSpeechGateway): SpeechGateway

    companion object {
        @Provides
        @Singleton
        fun provideGson(): Gson = Gson()
    }
}
