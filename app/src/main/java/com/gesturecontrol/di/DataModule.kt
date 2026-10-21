package com.gesturecontrol.di

import android.content.Context
import androidx.room.Room
import com.gesturecontrol.data.AppDatabase
import com.gesturecontrol.data.SelectedAppDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DataModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "gesturecontrol.db").build()

    @Provides
    fun provideSelectedAppDao(db: AppDatabase): SelectedAppDao = db.selectedAppDao()
}
