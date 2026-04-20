package muhamad.irfan.si_tahu.data

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await

object PenggunaFirestoreCompat {
    private const val COLLECTION_NEW = "Pengguna"
    private const val COLLECTION_OLD = "pengguna"

    fun findByAuthUid(
        firestore: FirebaseFirestore,
        authUid: String,
        onFound: (DocumentSnapshot) -> Unit,
        onNotFound: () -> Unit,
        onError: (Exception) -> Unit
    ) {
        if (authUid.isBlank()) {
            onNotFound()
            return
        }

        firestore.collection(COLLECTION_NEW)
            .whereEqualTo("authUid", authUid)
            .limit(1)
            .get()
            .addOnSuccessListener { newSnapshot ->
                val newDoc = newSnapshot.documents.firstOrNull()
                if (newDoc != null) {
                    onFound(newDoc)
                    return@addOnSuccessListener
                }

                firestore.collection(COLLECTION_OLD)
                    .whereEqualTo("authUid", authUid)
                    .limit(1)
                    .get()
                    .addOnSuccessListener { oldSnapshot ->
                        val oldDoc = oldSnapshot.documents.firstOrNull()
                        if (oldDoc != null) {
                            onFound(oldDoc)
                        } else {
                            onNotFound()
                        }
                    }
                    .addOnFailureListener(onError)
            }
            .addOnFailureListener(onError)
    }

    fun findByEmail(
        firestore: FirebaseFirestore,
        email: String,
        onFound: (DocumentSnapshot) -> Unit,
        onNotFound: () -> Unit,
        onError: (Exception) -> Unit
    ) {
        if (email.isBlank()) {
            onNotFound()
            return
        }

        firestore.collection(COLLECTION_NEW)
            .whereEqualTo("email", email)
            .limit(1)
            .get()
            .addOnSuccessListener { newSnapshot ->
                val newDoc = newSnapshot.documents.firstOrNull()
                if (newDoc != null) {
                    onFound(newDoc)
                    return@addOnSuccessListener
                }

                firestore.collection(COLLECTION_OLD)
                    .whereEqualTo("email", email)
                    .limit(1)
                    .get()
                    .addOnSuccessListener { oldSnapshot ->
                        val oldDoc = oldSnapshot.documents.firstOrNull()
                        if (oldDoc != null) {
                            onFound(oldDoc)
                        } else {
                            onNotFound()
                        }
                    }
                    .addOnFailureListener(onError)
            }
            .addOnFailureListener(onError)
    }

    fun migrateLegacyDocIfNeeded(
        firestore: FirebaseFirestore,
        doc: DocumentSnapshot,
        authUid: String? = null,
        onComplete: (DocumentSnapshot) -> Unit,
        onError: (Exception) -> Unit
    ) {
        if (doc.reference.parent.id == COLLECTION_NEW) {
            onComplete(doc)
            return
        }

        val data = HashMap(doc.data ?: emptyMap<String, Any?>())
        if (!authUid.isNullOrBlank()) {
            data["authUid"] = authUid
        }
        if (!data.containsKey("aktif")) {
            data["aktif"] = true
        }
        if (!data.containsKey("bolehMasuk")) {
            data["bolehMasuk"] = (data["aktif"] as? Boolean) ?: true
        }
        data["diperbaruiPada"] = Timestamp.now()

        firestore.collection(COLLECTION_NEW)
            .document(doc.id)
            .set(data, SetOptions.merge())
            .addOnSuccessListener {
                firestore.collection(COLLECTION_NEW)
                    .document(doc.id)
                    .get()
                    .addOnSuccessListener(onComplete)
                    .addOnFailureListener(onError)
            }
            .addOnFailureListener(onError)
    }

    suspend fun findByAuthUidSuspend(
        firestore: FirebaseFirestore,
        authUid: String
    ): DocumentSnapshot? {
        if (authUid.isBlank()) return null

        val newDoc = firestore.collection(COLLECTION_NEW)
            .whereEqualTo("authUid", authUid)
            .limit(1)
            .get()
            .await()
            .documents
            .firstOrNull()
        if (newDoc != null) return newDoc

        val oldDoc = firestore.collection(COLLECTION_OLD)
            .whereEqualTo("authUid", authUid)
            .limit(1)
            .get()
            .await()
            .documents
            .firstOrNull()
        if (oldDoc != null) return oldDoc

        val byIdNew = runCatching {
            firestore.collection(COLLECTION_NEW).document(authUid).get().await()
        }.getOrNull()
        if (byIdNew != null && byIdNew.exists()) return byIdNew

        val byIdOld = runCatching {
            firestore.collection(COLLECTION_OLD).document(authUid).get().await()
        }.getOrNull()
        if (byIdOld != null && byIdOld.exists()) return byIdOld

        return null
    }
}
