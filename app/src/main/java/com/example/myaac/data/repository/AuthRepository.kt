package com.example.myaac.data.repository

import com.example.myaac.model.UserProfile
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class AuthRepository {
    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private val firestore: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }

    val currentUser: FirebaseUser?
        get() = auth.currentUser

    // Observe authentication state
    val authState: Flow<FirebaseUser?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            trySend(auth.currentUser)
        }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    suspend fun createUserProfileIfNew(user: FirebaseUser) {
        val userRef = firestore.collection("users").document(user.uid)
        val snapshot = userRef.get().await()

        if (!snapshot.exists()) {
            val userProfile = UserProfile(
                uid = user.uid,
                email = user.email ?: "",
                displayName = user.displayName ?: "",
                photoUrl = user.photoUrl?.toString(),
                createdAt = com.google.firebase.Timestamp.now()
            )
            userRef.set(userProfile).await()
        }
    }
    
    suspend fun getUserProfile(uid: String): UserProfile? {
        return try {
            val snapshot = firestore.collection("users").document(uid).get().await()
            snapshot.toObject(UserProfile::class.java)
        } catch (e: Exception) {
            null
        }
    }

    fun signOut() {
        auth.signOut()
    }
}
