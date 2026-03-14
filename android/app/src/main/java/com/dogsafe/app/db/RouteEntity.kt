package com.dogsafe.app.db

import androidx.room.*

@Entity(tableName = "routes")
data class RouteEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val gpxFileName: String,
    val distanceKm: Double,
    val pointCount: Int,
    val restrictionCount: Int,
    val safetyStatus: String, // RED, AMBER, GREEN
    val isVisible: Boolean = true,
    val importedAt: Long = System.currentTimeMillis(),
    // Bounding box for quick map queries
    val minLat: Double,
    val maxLat: Double,
    val minLon: Double,
    val maxLon: Double,
    // Serialised restriction case numbers that intersect
    val restrictionCaseNumbers: String = "" // comma separated
)

@Dao
interface RouteDao {
    @Query("SELECT * FROM routes ORDER BY importedAt DESC")
    suspend fun getAll(): List<RouteEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(route: RouteEntity): Long

    @Update
    suspend fun update(route: RouteEntity)

    @Delete
    suspend fun delete(route: RouteEntity)

    @Query("UPDATE routes SET isVisible = :visible WHERE id = :id")
    suspend fun setVisible(id: Int, visible: Boolean)

    @Query("UPDATE routes SET restrictionCount = :count, safetyStatus = :status, restrictionCaseNumbers = :cases WHERE id = :id")
    suspend fun updateSafety(id: Int, count: Int, status: String, cases: String)
}

@Database(entities = [RouteEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun routeDao(): RouteDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun getInstance(context: android.content.Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "dogsafe.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
