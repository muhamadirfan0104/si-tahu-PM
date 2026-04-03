package muhamad.irfan.si_tahu.ui.expense

import android.content.Intent
import android.os.Bundle
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import muhamad.irfan.si_tahu.databinding.ActivityExpenseFormBinding
import muhamad.irfan.si_tahu.ui.base.AktivitasDasar
import muhamad.irfan.si_tahu.util.EkstraAplikasi
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AktivitasFormPengeluaran : AktivitasDasar() {

    private lateinit var binding: ActivityExpenseFormBinding
    private val firestore by lazy { FirebaseFirestore.getInstance() }
    private var editingExpenseId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityExpenseFormBinding.inflate(layoutInflater)
        setContentView(binding.root)
        bindToolbar(binding.toolbar, "Form Pengeluaran", "Tambah atau edit pengeluaran")

        editingExpenseId = intent.getStringExtra(EkstraAplikasi.EXTRA_EXPENSE_ID)

        if (editingExpenseId.isNullOrBlank()) {
            binding.etDate.setText(currentDateOnly())
        } else {
            loadExpense(editingExpenseId!!)
        }

        binding.btnSave.setOnClickListener {
            saveExpense()
        }
    }

    private fun loadExpense(expenseId: String) {
        firestore.collection("pengeluaran")
            .document(expenseId)
            .get()
            .addOnSuccessListener { doc ->
                if (!doc.exists()) {
                    showMessage("Data pengeluaran tidak ditemukan.")
                    return@addOnSuccessListener
                }

                binding.etDate.setText(doc.getString("kunciTanggal").orEmpty())
                binding.etCategory.setText(doc.getString("namaPengeluaran").orEmpty())
                binding.etAmount.setText((doc.getLong("nominal") ?: 0L).toString())
                binding.etNote.setText(doc.getString("catatan").orEmpty())
            }
            .addOnFailureListener { e ->
                showMessage("Gagal memuat pengeluaran: ${e.message}")
            }
    }

    private fun saveExpense() {
        val tanggalText = binding.etDate.text?.toString()?.trim().orEmpty()
        val namaPengeluaran = binding.etCategory.text?.toString()?.trim().orEmpty()
        val nominal = binding.etAmount.text?.toString()?.trim()?.toLongOrNull() ?: 0L
        val catatan = binding.etNote.text?.toString()?.trim().orEmpty()

        if (tanggalText.isBlank()) {
            binding.etDate.error = "Tanggal wajib diisi"
            binding.etDate.requestFocus()
            return
        }

        if (namaPengeluaran.isBlank()) {
            binding.etCategory.error = "Nama pengeluaran wajib diisi"
            binding.etCategory.requestFocus()
            return
        }

        if (nominal <= 0L) {
            binding.etAmount.error = "Nominal harus lebih dari 0"
            binding.etAmount.requestFocus()
            return
        }

        val tanggalPengeluaran = parseDateOnlyToTimestamp(tanggalText)
        if (tanggalPengeluaran == null) {
            binding.etDate.error = "Format tanggal harus yyyy-MM-dd"
            binding.etDate.requestFocus()
            return
        }

        val uid = currentUserId()
        if (uid.isBlank()) {
            showMessage("Session user tidak ditemukan.")
            return
        }

        firestore.collection("pengguna")
            .document(uid)
            .get()
            .addOnSuccessListener { userDoc ->
                val namaPembuat = userDoc.getString("namaPengguna").orEmpty()
                writeExpense(
                    uid = uid,
                    namaPembuat = namaPembuat,
                    tanggalText = tanggalText,
                    tanggalPengeluaran = tanggalPengeluaran,
                    namaPengeluaran = namaPengeluaran,
                    nominal = nominal,
                    catatan = catatan
                )
            }
            .addOnFailureListener {
                writeExpense(
                    uid = uid,
                    namaPembuat = "",
                    tanggalText = tanggalText,
                    tanggalPengeluaran = tanggalPengeluaran,
                    namaPengeluaran = namaPengeluaran,
                    nominal = nominal,
                    catatan = catatan
                )
            }
    }

    private fun writeExpense(
        uid: String,
        namaPembuat: String,
        tanggalText: String,
        tanggalPengeluaran: Timestamp,
        namaPengeluaran: String,
        nominal: Long,
        catatan: String
    ) {
        val now = Timestamp.now()
        val docId = editingExpenseId ?: firestore.collection("pengeluaran").document().id

        val data = hashMapOf<String, Any>(
            "tanggalPengeluaran" to tanggalPengeluaran,
            "kunciTanggal" to tanggalText,
            "jenisPengeluaran" to normalizeExpenseType(namaPengeluaran),
            "namaPengeluaran" to namaPengeluaran,
            "nominal" to nominal,
            "catatan" to catatan,
            "idPembuat" to uid,
            "namaPembuat" to namaPembuat,
            "diperbaruiPada" to now
        )

        if (editingExpenseId == null) {
            data["dibuatPada"] = now
        }

        val ref = firestore.collection("pengeluaran").document(docId)
        val task = if (editingExpenseId == null) ref.set(data) else ref.update(data)

        task.addOnSuccessListener {
            showMessage("Pengeluaran berhasil disimpan.")
            startActivity(Intent(this, AktivitasDaftarPengeluaran::class.java))
            finish()
        }.addOnFailureListener { e ->
            showMessage("Gagal menyimpan pengeluaran: ${e.message}")
        }
    }

    private fun parseDateOnlyToTimestamp(value: String): Timestamp? {
        return try {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            sdf.isLenient = false
            val date = sdf.parse(value) ?: return null
            Timestamp(date)
        } catch (_: Exception) {
            null
        }
    }

    private fun currentDateOnly(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        return sdf.format(Date())
    }

    private fun normalizeExpenseType(input: String): String {
        return input.trim()
            .uppercase()
            .replace(" ", "_")
    }
}