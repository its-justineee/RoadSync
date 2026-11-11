package com.example.roadsync

import com.google.firebase.auth.FirebaseAuth

class FirebaseManager {

    private val auth = FirebaseAuth.getInstance()

    fun signInAnonymously(
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        if (auth.currentUser != null) {
            onSuccess(auth.currentUser!!.uid)
            return
        }

        auth.signInAnonymously()
            .addOnSuccessListener { result ->
                val userId = result.user?.uid

                if (userId != null) {
                    onSuccess(userId)
                } else {
                    onError("Unable to get user ID.")
                }
            }
            .addOnFailureListener { exception ->
                onError(exception.message ?: "Firebase sign-in failed.")
            }
    }
}
