package org.matomocamp.companion.inject

import android.content.Context
import androidx.annotation.WorkerThread
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import org.matomocamp.companion.db.AppDatabase
import org.matomocamp.companion.db.BookmarksDao
import org.matomocamp.companion.db.ScheduleDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.runBlocking
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    private const val DB_FILE = "matomocamp.sqlite"
    private const val DB_DATASTORE_FILE = "database"

    @Provides
    @Named("Database")
    fun provideDataStore(@ApplicationContext context: Context): DataStore<Preferences> {
        return PreferenceDataStoreFactory.create(
                produceFile = { context.preferencesDataStoreFile(DB_DATASTORE_FILE) }
        )
    }

    @Provides
    @Singleton
    fun provideAppDatabase(
        @ApplicationContext context: Context,
        @Named("Database") dataStore: DataStore<Preferences>
    ): AppDatabase {
        return Room.databaseBuilder(context, AppDatabase::class.java, DB_FILE)
            .setJournalMode(RoomDatabase.JournalMode.TRUNCATE)
            .fallbackToDestructiveMigration()
            .addCallback(object : RoomDatabase.Callback() {
                @WorkerThread
                override fun onDestructiveMigration(db: SupportSQLiteDatabase) {
                    runBlocking {
                        dataStore.edit { it.clear() }
                    }
                }
            })
            .build()
            .also {
                // Manual dependency injection
                it.dataStore = dataStore
            }
    }

    @Provides
    fun provideScheduleDao(appDatabase: AppDatabase): ScheduleDao = appDatabase.scheduleDao

    @Provides
    fun provideBookmarksDao(appDatabase: AppDatabase): BookmarksDao = appDatabase.bookmarksDao
}