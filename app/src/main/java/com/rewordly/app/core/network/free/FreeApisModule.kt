package com.rewordly.app.core.network.free

import com.rewordly.app.BuildConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

/**
 * Retrofit instances for the free public dictionaries.
 *
 * They are separate from the app's main client because each service lives on its own host, and the
 * shared [OkHttpClient] cannot carry two base URLs. The client itself is reused, so connections and the
 * debug logging interceptor stay in one place.
 */
@Module
@InstallIn(SingletonComponent::class)
object FreeApisModule {
    private const val WIKTIONARY_BASE_URL = "https://en.wiktionary.org/"
    private const val MY_MEMORY_BASE_URL = "https://api.mymemory.translated.net/"

    /**
     * Wikimedia asks every client to identify itself; requests without a descriptive agent are throttled
     * or refused outright. The app name and the project URL are what their policy asks for.
     */
    private val USER_AGENT = "Rewordly/${BuildConfig.VERSION_NAME} (https://github.com/rishq/Rewordly)"

    @Provides
    @Singleton
    @WiktionaryRetrofit
    fun provideWiktionaryRetrofit(client: OkHttpClient, json: Json): Retrofit = Retrofit.Builder()
        .baseUrl(WIKTIONARY_BASE_URL)
        .client(
            client.newBuilder()
                .addInterceptor { chain ->
                    chain.proceed(chain.request().newBuilder().header("User-Agent", USER_AGENT).build())
                }
                .build(),
        )
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

    @Provides
    @Singleton
    @MyMemoryRetrofit
    fun provideMyMemoryRetrofit(client: OkHttpClient, json: Json): Retrofit = Retrofit.Builder()
        .baseUrl(MY_MEMORY_BASE_URL)
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

    @Provides
    @Singleton
    fun provideWiktionaryApi(@WiktionaryRetrofit retrofit: Retrofit): WiktionaryApi =
        retrofit.create(WiktionaryApi::class.java)

    @Provides
    @Singleton
    fun provideMyMemoryApi(@MyMemoryRetrofit retrofit: Retrofit): MyMemoryApi = retrofit.create(MyMemoryApi::class.java)
}

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class WiktionaryRetrofit

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class MyMemoryRetrofit
