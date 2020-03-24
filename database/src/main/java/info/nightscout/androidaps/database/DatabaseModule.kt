package info.nightscout.androidaps.database

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import javax.inject.Qualifier
import javax.inject.Singleton

@Module
open class DatabaseModule {

    @DbFileNameQualifier
    @Provides
    fun dbFileName() = "androidaps.db"

    @Provides
    @Singleton
    internal fun provideAppDatabase(context: Context, @DbFileNameQualifier fileName: String) =
        Room.databaseBuilder(context, AppDatabase::class.java, fileName).build()

    @Qualifier
    annotation class DbFileNameQualifier
}