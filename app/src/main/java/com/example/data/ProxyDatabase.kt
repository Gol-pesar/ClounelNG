package com.example.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import com.example.model.ProxyProfile
import com.example.model.TunnelMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Entity(tableName = "proxy_profiles")
data class ProxyProfileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val mode: String,
    val uuid: String,
    val workerHost: String,
    val workerUrl: String,
    val cleanIp: String,
    val subprotocol: String,
    val listenHost: String,
    val listenPort: Int,
    val maxChunk: Int,
    val bufferMax: Int,
    val upstreamHost: String,
    val upstreamPort: Int,
    val upstreamUsername: String,
    val upstreamPassword: String,
    val isDefault: Boolean
)

fun ProxyProfileEntity.toModel(): ProxyProfile = ProxyProfile(
    id = id,
    name = name,
    mode = TunnelMode.fromId(mode),
    uuid = uuid,
    workerHost = workerHost,
    workerUrl = workerUrl,
    cleanIp = cleanIp,
    subprotocol = subprotocol,
    listenHost = listenHost,
    listenPort = listenPort,
    maxChunk = maxChunk,
    bufferMax = bufferMax,
    upstreamHost = upstreamHost,
    upstreamPort = upstreamPort,
    upstreamUsername = upstreamUsername,
    upstreamPassword = upstreamPassword,
    isDefault = isDefault
)

fun ProxyProfile.toEntity(): ProxyProfileEntity = ProxyProfileEntity(
    id = id,
    name = name,
    mode = mode.id,
    uuid = uuid,
    workerHost = workerHost,
    workerUrl = workerUrl,
    cleanIp = cleanIp,
    subprotocol = subprotocol,
    listenHost = listenHost,
    listenPort = listenPort,
    maxChunk = maxChunk,
    bufferMax = bufferMax,
    upstreamHost = upstreamHost,
    upstreamPort = upstreamPort,
    upstreamUsername = upstreamUsername,
    upstreamPassword = upstreamPassword,
    isDefault = isDefault
)

@Dao
interface ProxyProfileDao {
    @Query("SELECT * FROM proxy_profiles ORDER BY isDefault DESC, id ASC")
    fun observeAll(): Flow<List<ProxyProfileEntity>>

    @Query("SELECT COUNT(*) FROM proxy_profiles")
    suspend fun count(): Int

    @Query("SELECT * FROM proxy_profiles WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): ProxyProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: ProxyProfileEntity): Long

    @Update
    suspend fun update(entity: ProxyProfileEntity)

    @Delete
    suspend fun delete(entity: ProxyProfileEntity)

    @Query(
        "DELETE FROM proxy_profiles WHERE isDefault = 1 AND name = 'Cl2 Worker (Twilight-Dream)'"
    )
    suspend fun deleteLegacyDefaultConfigs()
}

@Database(entities = [ProxyProfileEntity::class], version = 1, exportSchema = false)
abstract class ProxyDatabase : RoomDatabase() {
    abstract fun profileDao(): ProxyProfileDao

    companion object {
        @Volatile
        private var instance: ProxyDatabase? = null

        fun getInstance(context: Context): ProxyDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    ProxyDatabase::class.java,
                    "pulse_proxy.db"
                )
                    .setJournalMode(JournalMode.TRUNCATE)
                    .build()
                    .also { instance = it }
            }
        }
    }
}

class ProxyProfileRepository(private val dao: ProxyProfileDao) {
    val profiles: Flow<List<ProxyProfile>> = dao.observeAll().map { list ->
        list.map { it.toModel() }
    }

    suspend fun ensureSeeded(): Long {
        return -1L
    }

    suspend fun purgeLegacyDefaultConfigs() {
        dao.deleteLegacyDefaultConfigs()
    }

    suspend fun saveProfile(profile: ProxyProfile): Long {
        return if (profile.id == 0L) {
            dao.insert(profile.toEntity())
        } else {
            dao.update(profile.toEntity())
            profile.id
        }
    }

    suspend fun deleteProfile(profile: ProxyProfile) {
        dao.delete(profile.toEntity())
    }

    suspend fun getById(id: Long): ProxyProfile? = dao.getById(id)?.toModel()
}
