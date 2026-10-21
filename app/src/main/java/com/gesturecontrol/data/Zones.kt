package com.gesturecontrol.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import com.gesturecontrol.gestures.mapping.GlobalActionKind
import com.gesturecontrol.gestures.mapping.MediaKind
import com.gesturecontrol.gestures.mapping.NormRect
import com.gesturecontrol.gestures.mapping.VerticalDirection
import com.gesturecontrol.gestures.mapping.Zone
import com.gesturecontrol.gestures.mapping.ZoneAction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** A screen zone of one app. Coordinates are normalized to the screen. */
@Entity(tableName = "zones", indices = [Index("packageName")])
data class ZoneEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    /** One of [ActionCodec] types. */
    val actionType: String,
    val actionParam: String?,
    /** Z-order: higher is on top. */
    val position: Int,
)

data class PackageZoneCount(val packageName: String, val count: Int)

@Dao
interface ZoneDao {
    @Query("SELECT * FROM zones WHERE packageName = :packageName ORDER BY position, id")
    fun observeForPackage(packageName: String): Flow<List<ZoneEntity>>

    @Query("SELECT packageName, COUNT(*) AS count FROM zones GROUP BY packageName")
    fun observeCounts(): Flow<List<PackageZoneCount>>

    @Query("SELECT COALESCE(MAX(position), -1) FROM zones WHERE packageName = :packageName")
    suspend fun maxPosition(packageName: String): Int

    @Query("SELECT position FROM zones WHERE id = :id")
    suspend fun positionOf(id: Long): Int?

    @Insert
    suspend fun insert(zone: ZoneEntity): Long

    @Update
    suspend fun update(zone: ZoneEntity)

    @Query("DELETE FROM zones WHERE id = :id")
    suspend fun delete(id: Long)
}

/** Converts [ZoneAction] to and from its database columns. */
object ActionCodec {
    fun encode(action: ZoneAction): Pair<String, String?> = when (action) {
        ZoneAction.Tap -> "TAP" to null
        ZoneAction.LongPress -> "LONG_PRESS" to null
        is ZoneAction.Swipe -> "SWIPE" to action.direction.name
        is ZoneAction.Global -> "GLOBAL" to action.kind.name
        is ZoneAction.Media -> "MEDIA" to action.kind.name
    }

    /** Unknown values fall back to a tap rather than crashing. */
    fun decode(type: String, param: String?): ZoneAction = runCatching {
        when (type) {
            "LONG_PRESS" -> ZoneAction.LongPress
            "SWIPE" -> ZoneAction.Swipe(VerticalDirection.valueOf(param!!))
            "GLOBAL" -> ZoneAction.Global(GlobalActionKind.valueOf(param!!))
            "MEDIA" -> ZoneAction.Media(MediaKind.valueOf(param!!))
            else -> ZoneAction.Tap
        }
    }.getOrDefault(ZoneAction.Tap)
}

fun ZoneEntity.toZone() = Zone(id, NormRect(left, top, right, bottom), ActionCodec.decode(actionType, actionParam))

@Singleton
class ZoneRepository @Inject constructor(private val dao: ZoneDao) {

    /** Zones of [packageName] in z-order. */
    fun zonesFor(packageName: String): Flow<List<Zone>> =
        dao.observeForPackage(packageName).map { list -> list.map { it.toZone() } }

    val zoneCounts: Flow<Map<String, Int>> =
        dao.observeCounts().map { list -> list.associate { it.packageName to it.count } }

    suspend fun add(packageName: String, rect: NormRect, action: ZoneAction): Long {
        val (type, param) = ActionCodec.encode(action)
        return dao.insert(
            ZoneEntity(
                packageName = packageName,
                left = rect.left, top = rect.top, right = rect.right, bottom = rect.bottom,
                actionType = type, actionParam = param,
                position = dao.maxPosition(packageName) + 1,
            ),
        )
    }

    /** Updates rectangle and action; the z-order position is kept. */
    suspend fun update(packageName: String, zone: Zone) {
        val position = dao.positionOf(zone.id) ?: return
        val (type, param) = ActionCodec.encode(zone.action)
        dao.update(
            ZoneEntity(
                id = zone.id, packageName = packageName,
                left = zone.rect.left, top = zone.rect.top, right = zone.rect.right, bottom = zone.rect.bottom,
                actionType = type, actionParam = param, position = position,
            ),
        )
    }

    suspend fun delete(id: Long) = dao.delete(id)
}
