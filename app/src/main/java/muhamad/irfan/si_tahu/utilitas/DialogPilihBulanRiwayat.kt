package muhamad.irfan.si_tahu.util

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@Composable
fun DialogPilihBulanRiwayat(
    initialDate: String?,
    primaryColor: Color,
    surfaceColor: Color,
    bgColor: Color,
    textColor: Color,
    mutedColor: Color,
    borderColor: Color,
    onDismiss: () -> Unit,
    onApply: (mulai: String, selesai: String, label: String) -> Unit
) {
    val months = listOf("Jan", "Feb", "Mar", "Apr", "Mei", "Jun", "Jul", "Ags", "Sep", "Okt", "Nov", "Des")
    val fullLabelFormatter = remember { SimpleDateFormat("MMMM yyyy", Locale("id", "ID")) }
    val localFormat = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US) }
    val initialCalendar = remember(initialDate) {
        Calendar.getInstance().apply {
            initialDate
                ?.takeIf { it.isNotBlank() }
                ?.let { runCatching { localFormat.parse(it) }.getOrNull() }
                ?.let { time = it }
        }
    }
    var tempYear by remember { mutableStateOf(initialCalendar.get(Calendar.YEAR)) }
    var tempMonth by remember { mutableStateOf(initialCalendar.get(Calendar.MONTH)) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(24.dp), color = surfaceColor, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("Pilih Bulan & Tahun", fontWeight = FontWeight.Bold, color = textColor, style = MaterialTheme.typography.titleLarge)
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    IconButton(onClick = { tempYear-- }, modifier = Modifier.background(bgColor, CircleShape)) {
                        Icon(Icons.Rounded.ChevronLeft, contentDescription = "Tahun sebelumnya", tint = textColor)
                    }
                    Text("$tempYear", fontWeight = FontWeight.Bold, color = textColor, style = MaterialTheme.typography.titleLarge)
                    IconButton(onClick = { tempYear++ }, modifier = Modifier.background(bgColor, CircleShape)) {
                        Icon(Icons.Rounded.ChevronRight, contentDescription = "Tahun selanjutnya", tint = textColor)
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (row in 0..3) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            for (col in 0..2) {
                                val monthIndex = row * 3 + col
                                val isSelected = tempMonth == monthIndex
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (isSelected) primaryColor else bgColor,
                                    border = BorderStroke(1.dp, if (isSelected) primaryColor else borderColor),
                                    modifier = Modifier.weight(1f).clickable { tempMonth = monthIndex }
                                ) {
                                    Text(
                                        text = months[monthIndex],
                                        color = if (isSelected) Color.White else textColor,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.padding(vertical = 12.dp)
                                    )
                                }
                            }
                        }
                    }
                }
                HorizontalDivider(color = borderColor)
                Text(
                    text = "Filter akan mengambil data dari awal sampai akhir bulan yang dipilih.",
                    color = mutedColor,
                    style = MaterialTheme.typography.labelMedium
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onDismiss) { Text("Batal", color = mutedColor, fontWeight = FontWeight.Bold) }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val start = Calendar.getInstance().apply {
                                set(tempYear, tempMonth, 1, 0, 0, 0)
                                set(Calendar.MILLISECOND, 0)
                            }
                            val end = Calendar.getInstance().apply {
                                set(tempYear, tempMonth, 1, 23, 59, 59)
                                set(Calendar.MILLISECOND, 999)
                                set(Calendar.DAY_OF_MONTH, getActualMaximum(Calendar.DAY_OF_MONTH))
                            }
                            onApply(localFormat.format(start.time), localFormat.format(end.time), fullLabelFormatter.format(start.time))
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = primaryColor)
                    ) { Text("Simpan", fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
}
