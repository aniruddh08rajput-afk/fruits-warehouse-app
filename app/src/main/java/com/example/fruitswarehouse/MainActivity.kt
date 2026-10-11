package com.example.fruitswarehouse

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.util.Xml
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import org.json.JSONArray
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.Locale
import java.util.zip.ZipInputStream

private data class StockRow(
    val customer: String,
    val date: String,
    val lot: String,
    val product: String,
    val brand: String,
    val count: String,
    var inward: Double,
    var outward: Double,
    var balance: Double,
    val location: String,
    var physical: Double? = null,
    var approved: Boolean = false
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                WarehouseApp(this)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WarehouseApp(context: Context) {
    val prefs = remember {
        context.getSharedPreferences("warehouse_store", Context.MODE_PRIVATE)
    }

    val rows = remember {
        mutableStateListOf<StockRow>().apply {
            addAll(loadRows(prefs.getString("rows", null)))
        }
    }

    var tab by remember { mutableStateOf("Stock") }
    var search by remember { mutableStateOf("") }
    var selectedLot by remember { mutableStateOf("") }
    var qtyText by remember { mutableStateOf("") }
    var movementType by remember { mutableStateOf("Inward") }
    var movementNote by remember { mutableStateOf("") }
    var message by remember {
        mutableStateOf("पहले Final Stock Excel (.xlsx) अपलोड करें।")
    }

    fun save() {
        prefs.edit()
            .putString("rows", rowsToJson(rows).toString())
            .apply()
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult

        try {
            val input = context.contentResolver.openInputStream(uri)

            if (input == null) {
                message = "फाइल खुल नहीं पाई। दोबारा चुनें।"
                return@rememberLauncherForActivityResult
            }

            input.use { stream ->
                val imported = parseXlsx(stream)

                if (imported.isEmpty()) {
                    message = "कोई Stock row नहीं मिली। Excel में Lot No. और Balance कॉलम जाँचें।"
                } else {
                    rows.clear()
                    rows.addAll(imported)
                    save()
                    message = "${imported.size} Lot import हुए। Final Stock शुरुआती Balance बन गया।"
                }
            }
        } catch (e: Exception) {
            message = "Excel पढ़ने में समस्या: ${e.message ?: "फाइल format जाँचें"}"
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult

        try {
            val output = context.contentResolver.openOutputStream(uri)

            if (output == null) {
                message = "Export file नहीं बन पाई।"
                return@rememberLauncherForActivityResult
            }

            output.bufferedWriter(Charsets.UTF_8).use { writer ->
                writer.write(
                    "Customer,InwardDate,Lot No.,Product Name,Brand,Count,In,Out,Balance,Physical Count,Difference,Location
"
                )

                rows.forEach { row ->
                    val difference = row.physical?.minus(row.balance)

                    val line = listOf(
                        row.customer,
                        row.date,
                        row.lot,
                        row.product,
                        row.brand,
                        row.count,
                        formatNumber(row.inward),
                        formatNumber(row.outward),
                        formatNumber(row.balance),
                        row.physical?.let { formatNumber(it) } ?: "",
                        difference?.let { formatNumber(it) } ?: "",
                        row.location
                    ).joinToString(",") { csvSafe(it) }

                    writer.write(line + "
")
                }
            }

            message = "Report export हो गई।"
        } catch (e: Exception) {
            message = "Export नहीं हुई: ${e.message}"
        }
    }

    val filteredRows = rows.filter { row ->
        val query = search.trim().lowercase(Locale.getDefault())

        query.isBlank() || listOf(
            row.customer,
            row.lot,
            row.product,
            row.brand,
            row.location
        ).any { it.lowercase(Locale.getDefault()).contains(query) }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Fruits Warehouse") })
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(12.dp)
        ) {
            Text(
                text = "Lot-wise Cold Store Stock",
                style = Mate
