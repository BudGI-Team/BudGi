package com.app.di

import android.content.ContentResolver
import android.content.Context
import androidx.room.Room
import com.app.BuildConfig
import com.app.data.local.TokenManager
import com.app.data.local.dao.*
import com.app.data.local.db.AppDatabase
import com.app.data.remote.api.*
import com.app.data.remote.interceptor.AuthInterceptor
import com.app.data.remote.interceptor.TokenRefreshInterceptor
import com.app.data.repository.AuthRepositoryImpl
import com.app.data.repository.SpaceRepositoryImpl
import com.app.data.repository.TransactionRepositoryImpl
import com.app.domain.repository.AuthRepository
import com.app.domain.repository.SpaceRepository
import com.app.domain.repository.TransactionRepository
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.CertificatePinner
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton

/**
 * Application-scoped coroutine scope qualifier.
 * Use this for fire-and-forget operations that must outlive ViewModels
 * (e.g., notification DB inserts, WebSocket management).
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    // ── Application-scoped CoroutineScope ─────────────────────────────────

    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(): CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // ── Database ─────────────────────────────────────────────────────────

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "finance-db")
            .addMigrations(AppDatabase.MIGRATION_12_13, AppDatabase.MIGRATION_13_14, AppDatabase.MIGRATION_14_15)
            // SAFE: Only destroys data from versions BEFORE current.
            .fallbackToDestructiveMigration(true)
            .fallbackToDestructiveMigrationOnDowngrade(true)
            .build()

    @Provides fun provideTransactionDao(db: AppDatabase): TransactionDao = db.transactionDao()
    @Provides fun provideBudgetDao(db: AppDatabase): BudgetDao = db.budgetDao()
    @Provides fun provideUserDao(db: AppDatabase): UserDao = db.userDao()
    @Provides fun provideGoalDao(db: AppDatabase): GoalDao = db.goalDao()
    @Provides fun providePendingOperationDao(db: AppDatabase): PendingOperationDao = db.pendingOperationDao()
    @Provides fun provideChatMessageDao(db: AppDatabase): ChatMessageDao = db.chatMessageDao()
    @Provides fun provideMerchantCategoryMappingDao(db: AppDatabase): com.app.data.local.dao.MerchantCategoryMappingDao = db.merchantCategoryMappingDao()
    @Provides fun provideNotificationDao(db: AppDatabase): NotificationDao = db.notificationDao()
    @Provides fun provideSpaceDao(db: AppDatabase): SpaceDao = db.spaceDao()
    @Provides fun provideCategoryDao(db: AppDatabase): CategoryDao = db.categoryDao()

    @Provides
    fun provideContentResolver(@ApplicationContext context: Context): ContentResolver =
        context.contentResolver

    // ── Networking ────────────────────────────────────────────────────────

    @Provides
    @Singleton
    fun provideGson(): Gson = GsonBuilder()
        .setDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
        .create()

    @Provides
    @Singleton
    fun provideOkHttpClient(
        authInterceptor: AuthInterceptor,
        tokenRefreshInterceptor: TokenRefreshInterceptor
    ): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG)
                HttpLoggingInterceptor.Level.BODY
            else
                HttpLoggingInterceptor.Level.NONE
        }

        val builder = OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .addInterceptor(tokenRefreshInterceptor)
            .addInterceptor(logging)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)

        // Certificate pinning placeholder disabled to prevent release network crashes until production pin is provided
        // val certificatePinner = CertificatePinner.Builder()
        //     .add("api.budgi.app", "sha256/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=")
        //     .build()
        // if (!BuildConfig.DEBUG) {
        //     builder.certificatePinner(certificatePinner)
        // }

        return builder.build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(client: OkHttpClient, gson: Gson): Retrofit =
        Retrofit.Builder()
            .baseUrl(BuildConfig.API_BASE_URL + "/")
            .addConverterFactory(GsonConverterFactory.create(gson))
            .client(client)
            .build()

    // ── API Services ─────────────────────────────────────────────────────

    @Provides
    @Singleton
    fun provideAuthApiService(retrofit: Retrofit): AuthApiService =
        retrofit.create(AuthApiService::class.java)

    @Provides
    @Singleton
    fun provideTransactionApiService(retrofit: Retrofit): TransactionApiService =
        retrofit.create(TransactionApiService::class.java)

    @Provides
    @Singleton
    fun provideBudgetApiService(retrofit: Retrofit): BudgetApiService =
        retrofit.create(BudgetApiService::class.java)

    @Provides
    @Singleton
    fun provideGoalApiService(retrofit: Retrofit): GoalApiService =
        retrofit.create(GoalApiService::class.java)

    @Provides
    @Singleton
    fun provideSyncApiService(retrofit: Retrofit): SyncApiService =
        retrofit.create(SyncApiService::class.java)

    @Provides
    @Singleton
    fun provideAnalyticsApiService(retrofit: Retrofit): AnalyticsApiService =
        retrofit.create(AnalyticsApiService::class.java)

    @Provides
    @Singleton
    fun provideAIChatApiService(retrofit: Retrofit): AIChatApiService =
        retrofit.create(AIChatApiService::class.java)

    @Provides
    @Singleton
    fun provideAlertApiService(retrofit: Retrofit): AlertApiService =
        retrofit.create(AlertApiService::class.java)

    @Provides
    @Singleton
    fun provideSmsApiService(retrofit: Retrofit): SmsApiService =
        retrofit.create(SmsApiService::class.java)

    @Provides
    @Singleton
    fun provideSpaceApiService(retrofit: Retrofit): SpaceApiService =
        retrofit.create(SpaceApiService::class.java)

    @Provides
    @Singleton
    fun provideAppUpdateApiService(retrofit: Retrofit): AppUpdateApiService =
        retrofit.create(AppUpdateApiService::class.java)

    // ── Repositories ─────────────────────────────────────────────────────

    @Provides
    @Singleton
    fun provideAuthRepository(impl: AuthRepositoryImpl): AuthRepository = impl

    @Provides
    @Singleton
    fun provideTransactionRepository(impl: TransactionRepositoryImpl): TransactionRepository = impl

    @Provides
    @Singleton
    fun provideSpaceRepository(impl: SpaceRepositoryImpl): SpaceRepository = impl
}
