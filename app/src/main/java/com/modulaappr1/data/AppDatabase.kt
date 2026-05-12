package com.modulaappr1.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

// =====================================================================
// 1. ENTIDADES (Las Tablas)
// =====================================================================

@Entity(tableName = "chat_sessions")
data class ChatSession(
    @PrimaryKey(autoGenerate = true) val sessionId: Long = 0,
    val title: String = "Nuevo Chat",
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "chat_messages",
    foreignKeys = [ForeignKey(
        entity = ChatSession::class,
        parentColumns = ["sessionId"],
        childColumns = ["sessionId"],
        onDelete = ForeignKey.CASCADE // Si se borra la sesión, se borran sus mensajes
    )],
    indices = [Index("sessionId")]
)
data class ChatMessageEntity(
    @PrimaryKey(autoGenerate = true) val messageId: Long = 0,
    val sessionId: Long,
    val isUser: Boolean,
    val content: String,
    val thoughtProcess: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "vector_chunks",
    foreignKeys = [ForeignKey(
        entity = ChatSession::class,
        parentColumns = ["sessionId"],
        childColumns = ["sessionId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices =[Index("sessionId")]
)
data class VectorChunk(
    @PrimaryKey(autoGenerate = true) val chunkId: Long = 0,
    val sessionId: Long,
    val textContent: String,
    val embedding: FloatArray
)

// =====================================================================
// 2. CONVERTIDOR DE VECTORES (Para FloatArray)
// =====================================================================

class VectorConverters {
    @TypeConverter
    fun fromFloatArray(array: FloatArray?): String? = array?.joinToString(",")

    @TypeConverter
    fun toFloatArray(data: String?): FloatArray? {
        if (data.isNullOrEmpty()) return null
        val stringArray = data.split(",")
        return FloatArray(stringArray.size) { stringArray[it].toFloat() }
    }
}

// =====================================================================
// 3. LOS DAOs (Data Access Objects)
// =====================================================================

@Dao
interface ChatDao {
    // Sesiones
    @Insert
    suspend fun insertSession(session: ChatSession): Long

    @Query("SELECT * FROM chat_sessions ORDER BY timestamp DESC")
    fun getAllSessions(): Flow<List<ChatSession>>

    @Query("UPDATE chat_sessions SET title = :newTitle WHERE sessionId = :id")
    suspend fun updateSessionTitle(id: Long, newTitle: String)

    @Query("DELETE FROM chat_sessions WHERE sessionId = :id")
    suspend fun deleteSession(id: Long)

    // Mensajes
    @Insert
    suspend fun insertMessage(message: ChatMessageEntity)

    @Query("SELECT * FROM chat_messages WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    suspend fun getMessagesForSession(sessionId: Long): List<ChatMessageEntity>
}

@Dao
interface VectorDao {
    @Insert
    suspend fun insertChunk(chunk: VectorChunk)

    @Query("SELECT * FROM vector_chunks WHERE sessionId = :sessionId")
    suspend fun getVectorsForSession(sessionId: Long): List<VectorChunk>
}

// =====================================================================
// 4. LA BASE DE DATOS PRINCIPAL
// =====================================================================

@Database(
    entities =[ChatSession::class, ChatMessageEntity::class, VectorChunk::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(VectorConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun chatDao(): ChatDao
    abstract fun vectorDao(): VectorDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "modula_neural_db"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}