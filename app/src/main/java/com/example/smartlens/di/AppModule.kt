package com.example.smartlens.di

import android.content.Context
import com.example.smartlens.repository.DocumentRepository
import com.example.smartlens.service.*
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideGson(): Gson {
        return GsonBuilder()
            .serializeNulls()
            .create()
    }

    @Provides
    @Singleton
    fun provideOcrService(@ApplicationContext context: Context): OcrService {
        return OcrService(context)
    }

    @Provides
    @Singleton
    fun provideGeminiService(@ApplicationContext context: Context, gson: Gson): GeminiService {
        return GeminiService(context, gson)
    }

    @Provides
    @Singleton
    fun provideDocumentRepository(@ApplicationContext context: Context, gson: Gson): DocumentRepository {
        return DocumentRepository(context, gson)
    }

    @Provides
    @Singleton
    fun provideExcelExportService(@ApplicationContext context: Context): ExcelExportService {
        return ExcelExportService(context)
    }

    @Provides
    @Singleton
    fun provideDocumentShareService(@ApplicationContext context: Context, gson: Gson): DocumentShareService {
        return DocumentShareService(context, gson)
    }

    @Provides
    @Singleton
    fun provideUserProfileManager(@ApplicationContext context: Context): UserProfileManager {
        return UserProfileManager(context)
    }

    @Provides
    @Singleton
    fun provideMotivationalQuotesService(@ApplicationContext context: Context): MotivationalQuotesService {
        return MotivationalQuotesService(context)
    }
}