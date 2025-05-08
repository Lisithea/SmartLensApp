package com.example.smartlens.di

import android.content.Context
import com.example.smartlens.service.OcrService
import com.example.smartlens.util.OcrTester
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.scopes.ViewModelScoped

@Module
@InstallIn(ViewModelComponent::class)
object OcrTesterModule {

    @Provides
    @ViewModelScoped
    fun provideOcrTester(
        @ApplicationContext context: Context,
        ocrService: OcrService
    ): OcrTester {
        return OcrTester(context, ocrService)
    }
}