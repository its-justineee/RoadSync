
package com.example.roadsync

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query

class FirestoreManager {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    // UPLOAD INCIDENT REPORT
    fun uploadIncidentReport(
        report: IncidentReport,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val userId = auth.currentUser?.uid

        if (userId == null) {
            onError("User is not authenticated.")
            return
        }

        val reportData = hashMapOf(
            "type" to report.type,
            "description" to report.description,
            "latitude" to report.latitude,
            "longitude" to report.longitude,
            "timestamp" to report.timestamp,
            "userId" to userId
        )

        db.collection("incident_reports")
            .add(reportData)
            .addOnSuccessListener {
                onSuccess()
            }
            .addOnFailureListener { exception ->
                onError(exception.message ?: "Failed to upload report.")
            }
    }

    // REAL-TIME INCIDENT LISTENER
    fun listenToIncidentReports(
        onUpdate: (List<IncidentReport>) -> Unit,
        onError: (String) -> Unit
    ): ListenerRegistration {

        return db.collection("incident_reports")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->

                if (error != null) {
                    onError(error.message ?: "Failed to load reports.")
                    return@addSnapshotListener
                }

                if (snapshot == null) {
                    onUpdate(emptyList())
                    return@addSnapshotListener
                }

                val reports = snapshot.documents.mapNotNull { document ->

                    val type = document.getString("type")
                        ?: return@mapNotNull null

                    val description = document.getString("description")
                        ?: return@mapNotNull null

                    val latitude = (document.get("latitude") as? Number)
                        ?.toDouble() ?: return@mapNotNull null

                    val longitude = (document.get("longitude") as? Number)
                        ?.toDouble() ?: return@mapNotNull null

                    val timestamp = (document.get("timestamp") as? Number)
                        ?.toLong() ?: return@mapNotNull null

                    IncidentReport(
                        id = timestamp,
                        type = type,
                        description = description,
                        latitude = latitude,
                        longitude = longitude,
                        timestamp = timestamp
                    )
                }

                onUpdate(reports)
            }
    }
}
