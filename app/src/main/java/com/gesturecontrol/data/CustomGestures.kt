package com.gesturecontrol.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import com.gesturecontrol.gestures.custom.ActionSpec
import com.gesturecontrol.gestures.custom.CustomTemplate
import com.gesturecontrol.gestures.custom.SampleCodec
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** Profile key of the global profile (gestures active in every selected app). */
const val GLOBAL_PROFILE = "*"

/** A user-recorded gesture. [profile] is an app package or [GLOBAL_PROFILE]. */
@Entity(tableName = "custom_gestures", indices = [Index("profile")])
data class CustomGestureEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val profile: String,
    val name: String,
    /** [SampleCodec] text: only normalized landmark trajectories, no images. */
    val samples: String,
    val radius: Float,
    val sensitivity: Float,
    val cooldownMs: Long,
    /** [ActionSpec.encode] text. */
    val action: String,
    val createdAt: Long,
)

@Dao
interface CustomGestureDao {
    @Query("SELECT * FROM custom_gestures WHERE profile = :profile ORDER BY createdAt")
    fun observe(profile: String): Flow<List<CustomGestureEntity>>

    @Query("SELECT * FROM custom_gestures WHERE profile = :profile ORDER BY createdAt")
    suspend fun get(profile: String): List<CustomGestureEntity>

    @Query("SELECT * FROM custom_gestures WHERE id = :id")
    suspend fun byId(id: Long): CustomGestureEntity?

    @Query("SELECT profile, COUNT(*) AS count FROM custom_gestures GROUP BY profile")
    fun observeCounts(): Flow<List<ProfileCount>>

    @Insert
    suspend fun insert(entity: CustomGestureEntity): Long

    @Insert
    suspend fun insertAll(entities: List<CustomGestureEntity>)

    @Update
    suspend fun update(entity: CustomGestureEntity)

    @Query("DELETE FROM custom_gestures WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM custom_gestures WHERE profile = :profile")
    suspend fun deleteProfile(profile: String)
}

data class ProfileCount(val profile: String, val count: Int)

fun CustomGestureEntity.toTemplate() = CustomTemplate(
    id = id,
    name = name,
    samples = SampleCodec.decode(samples),
    radius = radius,
    sensitivity = sensitivity,
    cooldownMs = cooldownMs,
)

@Singleton
class CustomGestureRepository @Inject constructor(private val dao: CustomGestureDao) {

    fun gestures(profile: String): Flow<List<CustomGestureEntity>> = dao.observe(profile)

    /** Gestures active for an app: its own profile plus the global one. */
    fun activeFor(packageName: String?): Flow<List<CustomGestureEntity>> =
        combine(dao.observe(GLOBAL_PROFILE), packageName?.let { dao.observe(it) } ?: dao.observe("\u0000")) { global, app ->
            app + global
        }

    val counts: Flow<Map<String, Int>> = dao.observeCounts().map { list -> list.associate { it.profile to it.count } }

    suspend fun byId(id: Long) = dao.byId(id)

    suspend fun add(entity: CustomGestureEntity): Long = dao.insert(entity)

    suspend fun update(entity: CustomGestureEntity) = dao.update(entity)

    suspend fun delete(id: Long) = dao.delete(id)

    /** Removes all gestures of a profile. */
    suspend fun reset(profile: String) = dao.deleteProfile(profile)

    /** Copies all gestures of [from] into [to] (existing gestures of [to] are kept). */
    suspend fun duplicate(from: String, to: String) {
        val now = System.currentTimeMillis()
        dao.insertAll(dao.get(from).mapIndexed { i, g -> g.copy(id = 0, profile = to, createdAt = now + i) })
    }

    suspend fun export(profile: String): List<CustomGestureEntity> = dao.get(profile)

    suspend fun import(profile: String, gestures: List<CustomGestureEntity>) {
        val now = System.currentTimeMillis()
        dao.insertAll(gestures.mapIndexed { i, g -> g.copy(id = 0, profile = profile, createdAt = now + i) })
    }
}
