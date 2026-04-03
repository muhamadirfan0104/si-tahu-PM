package muhamad.irfan.si_tahu.data

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore

object UjiFirebaseSederhana {
    fun run() {
        val db = FirebaseFirestore.getInstance()

        val payload = hashMapOf(
            "status" to "ok",
            "updatedAt" to System.currentTimeMillis(),
            "source" to "android_app"
        )

        db.collection("debug")
            .document("koneksi")
            .set(payload)
            .addOnSuccessListener {
                Log.d("UjiFirebaseSederhana", "Write success")
            }
            .addOnFailureListener { e ->
                Log.e("UjiFirebaseSederhana", "Write failed", e)
            }
    }
}