package com.example.myaac.data.repository

import android.net.Uri
import com.example.myaac.model.AacButton
import com.example.myaac.model.Board
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.tasks.await
import java.io.File

class CloudRepository {
    private val firestore: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    private val storage: FirebaseStorage by lazy { FirebaseStorage.getInstance() }

    suspend fun backupBoard(board: Board, userId: String) {
        // Upload Board Images
        val cloudIconPath = uploadImageIfLocal(board.iconPath, userId)
        val cloudBgPath = uploadImageIfLocal(board.backgroundImagePath, userId)

        // Upload Button Images
        val cloudButtons = board.buttons.map { button ->
            val cloudButtonIconPath = uploadImageIfLocal(button.iconPath, userId)
            button.copy(iconPath = cloudButtonIconPath)
        }

        val cloudBoard = board.copy(
            iconPath = cloudIconPath,
            backgroundImagePath = cloudBgPath,
            buttons = cloudButtons
        )

        // Save to Firestore
        firestore.collection("users").document(userId)
            .collection("boards").document(board.id)
            .set(cloudBoard)
            .await()
    }

    suspend fun restoreBoards(userId: String): List<Board> {
        val snapshot = firestore.collection("users").document(userId)
            .collection("boards").get().await()
        
        return snapshot.documents.mapNotNull { doc ->
            doc.toObject(Board::class.java)
        }
    }

    private suspend fun uploadImageIfLocal(path: String?, userId: String): String? {
        if (path == null) return null
        if (path.startsWith("http")) return path // Already a generic URL (or cloud URL)

        // It is a local path (file:// or content:// or absolute path)
        val uri = Uri.parse(path)
        val file = File(uri.path ?: path)
        if (!file.exists()) return null // Or handle error

        val filename = file.name
        val storageRef = storage.reference.child("users/$userId/images/$filename")
        
        // Check if exists? For now, overwrite or simple upload.
        // Optimization: Check hash or metadata.
        
        storageRef.putFile(Uri.fromFile(file)).await()
        return storageRef.downloadUrl.await().toString()
    }
}
