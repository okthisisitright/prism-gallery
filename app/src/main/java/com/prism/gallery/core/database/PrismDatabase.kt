package com.prism.gallery.core.database

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "categories")
data class Category(@PrimaryKey(autoGenerate = true) val id: Long = 0, val name: String, val symbol: String = "◇", val accent: Int = 0, val coverKey: String? = null, val position: Int = 0, val pinned: Boolean = false, val hidden: Boolean = false)
@Entity(tableName = "memberships", primaryKeys = ["categoryId", "mediaKey"], foreignKeys = [ForeignKey(entity = Category::class, parentColumns = ["id"], childColumns = ["categoryId"], onDelete = ForeignKey.CASCADE)], indices = [Index("categoryId"), Index("mediaKey")])
data class Membership(val categoryId: Long, val mediaKey: String)
@Entity(tableName = "favorites")
data class Favorite(@PrimaryKey val mediaKey: String)
@Entity(tableName = "vault")
data class VaultRecord(@PrimaryKey val id: String, val created: Long, val sourceMayExist: Boolean = true)

@Dao
interface PrismDao {
    @Query("SELECT * FROM categories ORDER BY pinned DESC, position, id") fun categories(): Flow<List<Category>>
    @Query("SELECT * FROM memberships") fun memberships(): Flow<List<Membership>>
    @Query("SELECT * FROM favorites") fun favorites(): Flow<List<Favorite>>
    @Insert suspend fun create(category: Category): Long
    @Update suspend fun update(category: Category)
    @Query("DELETE FROM categories WHERE id = :id") suspend fun deleteCategory(id: Long)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun addMemberships(memberships: List<Membership>)
    @Query("DELETE FROM memberships WHERE categoryId = :id AND mediaKey IN (:keys)") suspend fun removeMembers(id: Long, keys: List<String>)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun favorite(favorite: Favorite)
    @Query("DELETE FROM favorites WHERE mediaKey = :key") suspend fun unfavorite(key: String)
    @Query("SELECT * FROM vault ORDER BY created DESC") fun vault(): Flow<List<VaultRecord>>
    @Insert suspend fun addVault(record: VaultRecord)
    @Insert fun addVaultBlocking(record: VaultRecord)
    @Query("DELETE FROM vault WHERE id = :id") suspend fun deleteVault(id: String)
    @Query("UPDATE vault SET sourceMayExist = :exists WHERE id = :id") suspend fun vaultSource(id: String, exists: Boolean)
    @Query("SELECT id FROM vault") suspend fun vaultIds(): List<String>
    @Transaction suspend fun categorize(id: Long, keys: List<String>) { addMemberships(keys.distinct().map { Membership(id, it) }) }
}
@Database(entities = [Category::class, Membership::class, Favorite::class, VaultRecord::class], version = 1, exportSchema = true)
abstract class PrismDatabase : RoomDatabase() {
    abstract fun dao(): PrismDao
    companion object { fun open(context: Context) = Room.databaseBuilder(context, PrismDatabase::class.java, "prism.db").build() }
}
