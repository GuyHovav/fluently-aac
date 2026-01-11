package com.example.myaac.model

import com.google.firebase.Timestamp

data class UserProfile(
    val uid: String = "",
    val email: String = "",
    val displayName: String = "",
    val photoUrl: String? = null,
    val subscriptionStatus: String = "free", // "free", "pro"
    val createdAt: Timestamp? = null
)
