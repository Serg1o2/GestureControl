package com.gesturecontrol.data

import androidx.room.AutoMigration
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

/** An app in which gestures are active. */
@Entity(tableName = "selected_apps")
data class SelectedApp(@PrimaryKey val packageName: String)

@Dao
interface SelectedAppDao {
    @Query("SELECT packageName FROM selected_apps")
    fun observePackages(): Flow<List<String>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(app: SelectedApp)

    @Query("DELETE FROM selected_apps WHERE packageName = :packageName")
    suspend fun delete(packageName: String)
}

@Database(
    entities = [SelectedApp::class, ZoneEntity::class, CustomGestureEntity::class],
    version = 3,
    exportSchema = true,
    autoMigrations = [AutoMigration(from = 1, to = 2), AutoMigration(from = 2, to = 3)],
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun selectedAppDao(): SelectedAppDao
    abstract fun zoneDao(): ZoneDao
    abstract fun customGestureDao(): CustomGestureDao
}
