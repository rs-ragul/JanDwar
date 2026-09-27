package `in`.jandwar.app.di

import android.content.Context
import android.content.SharedPreferences
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import `in`.jandwar.app.data.local.AssetDataSource
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideContext(@ApplicationContext ctx: Context): Context = ctx

    @Provides
    @Singleton
    fun provideSharedPreferences(@ApplicationContext ctx: Context): SharedPreferences {
        return ctx.getSharedPreferences("jandwar_prefs", Context.MODE_PRIVATE)
    }

    @Provides
    @Singleton
    fun provideAssetDataSource(@ApplicationContext ctx: Context): AssetDataSource {
        return AssetDataSource(ctx)
    }
}
