package com.modulaappr1.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

// =====================================================================
// ENTIDADES
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
        onDelete = ForeignKey.CASCADE
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

// Reemplaza VectorChunk — sin embeddings, sin segundo modelo
@Entity(tableName = "documents")
data class DocumentEntity(
    @PrimaryKey(autoGenerate = true) val documentId: Long = 0,
    val name: String,           // "Fisica_Cuantica.md"
    val sourceType: String,     // "TXT" | "MD" | "PDF_CONVERTED"
    val filePath: String,       // Ruta al .md en filesDir/modula_docs/
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "document_chunks",
    foreignKeys = [ForeignKey(
        entity = DocumentEntity::class,
        parentColumns = ["documentId"],
        childColumns = ["documentId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("documentId")]
)
data class DocumentChunk(
    @PrimaryKey(autoGenerate = true) val chunkId: Long = 0,
    val documentId: Long,
    val chunkIndex: Int,
    val textContent: String     // Texto plano — sin FloatArray
)

// =====================================================================
// DAOs
// =====================================================================

@Dao
interface ChatDao {
    @Insert
    suspend fun insertSession(session: ChatSession): Long

    @Query("SELECT * FROM chat_sessions ORDER BY timestamp DESC")
    fun getAllSessions(): Flow<List<ChatSession>>

    @Query("UPDATE chat_sessions SET title = :newTitle WHERE sessionId = :id")
    suspend fun updateSessionTitle(id: Long, newTitle: String)

    @Query("DELETE FROM chat_sessions WHERE sessionId = :id")
    suspend fun deleteSession(id: Long)

    @Insert
    suspend fun insertMessage(message: ChatMessageEntity)

    @Query("SELECT * FROM chat_messages WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    suspend fun getMessagesForSession(sessionId: Long): List<ChatMessageEntity>
}

@Dao
interface DocumentDao {
    @Insert
    suspend fun insertDocument(doc: DocumentEntity): Long

    @Insert
    suspend fun insertChunk(chunk: DocumentChunk)

    @Query("SELECT * FROM documents ORDER BY createdAt DESC")
    fun getAllDocuments(): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM documents ORDER BY createdAt DESC")
    suspend fun getAllDocumentsOnce(): List<DocumentEntity>

    @Query("SELECT * FROM document_chunks WHERE documentId = :documentId ORDER BY chunkIndex ASC")
    suspend fun getChunksForDocument(documentId: Long): List<DocumentChunk>

    // Todos los chunks de todos los documentos — RAG global cross-session
    @Query("SELECT * FROM document_chunks")
    suspend fun getAllChunks(): List<DocumentChunk>

    @Query("DELETE FROM documents WHERE documentId = :documentId")
    suspend fun deleteDocument(documentId: Long)

    @Query("SELECT COUNT(*) FROM documents")
    suspend fun getDocumentCount(): Int
}

// =====================================================================
// BASE DE DATOS — versión 2
// =====================================================================

@Database(
    entities = [
        ChatSession::class,
        ChatMessageEntity::class,
        DocumentEntity::class,
        DocumentChunk::class
    ],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun chatDao(): ChatDao
    abstract fun documentDao(): DocumentDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "modula_neural_db"
                )
                .fallbackToDestructiveMigration()
                .build().also { INSTANCE = it }
            }
        }
    }
}