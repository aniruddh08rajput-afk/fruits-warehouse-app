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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import org.xmlpull.v1.XmlPullParser
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipInputStream

data class StockRow(
    val product: String = "",
    val brand: String = "",
    val count: Double = 0.0,
    val inward: Double = 0.0,
    val outward: Double = 0.0,
    val balance: Double = 0.0,
    val location: String = "",
    val physicalCount: Double? = null,
    val movement: Double = 0.0
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                AppScreen()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppScreen() {
    val context = LocalContext.current
    var rows by remember { mutableStateOf(listOf<StockRow>()) }
    var message by remember { mutableStateOf("Excel file import karo") }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                context.contentResolver.openInputStream(uri)?.use {
                    rows = parseXlsx(it)
                    message = if (rows.isEmpty()) {
                        "Excel me data nahi mila"
                    } else {
                        "${rows.size} rows import ho gayi"
                    }
                }
            } catch (e: Exception) {
                message = "Import failed: ${e.message}"
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Fruits Warehouse") })
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        picker.launch(arrayOf(
                            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                            "application/octet-stream"
                        ))
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Import Excel")
                }

                Button(
                    onClick = {
                        message = exportCsv(context, rows)
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Export CSV")
                }
            }

            Spacer(Modifier.height(12.dp))
            Text(message)
            Spacer(Modifier.height(12.dp))

            if (rows.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Excel import karne ke baad stock dikhega.")
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(rows) { row ->
                        StockCard(
                            row = row,
                            onPhysicalChange = { value ->
                                rows = rows.map {
                                    if (it.product == row.product &&
                                        it.brand == row.brand &&
                                        it.location == row.location
                                    ) {
                                        it.copy(physicalCount = value)
                                    } else {
                                        it
                                    }
                                }
                            },
                            onMovementChange = { value ->
                                rows = rows.map {
                                    if (it.product == row.product &&
                                        it.brand == row.brand &&
                                        it.location == row.location
                                    ) {
                                        it.copy(movement = value)
                                    } else {
                                        it
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun StockCard(
    row: StockRow,
    onPhysicalChange: (Double) -> Unit,
    onMovementChange: (Double) -> Unit
) {
    var physicalText by remember(row) {
        mutableStateOf(row.physicalCount?.toString() ?: "")
    }
    var movementText by remember(row) {
        mutableStateOf(if (row.movement == 0.0) "" else row.movement.toString())
    }

    Card {
        Column(Modifier.padding(12.dp)) {
            Text(
                text = row.product.ifBlank { "Unnamed product" },
                style = MaterialTheme.typography.titleMedium
            )
            Text("Brand: ${row.brand.ifBlank { "-" }}")
            Text("Location: ${row.location.ifBlank { "-" }}")
            Text("System count: ${row.count}")
            Text("Inward: ${row.inward} | Outward: ${row.outward}")
            Text("Balance: ${row.balance}")

            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value = physicalText,
                onValueChange = {
                    physicalText = it
                    onPhysicalChange(it.toDoubleOrNull() ?: 0.0)
                },
                label = { Text("Physical count") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value = movementText,
                onValueChange = {
                    movementText = it
                    onMovementChange(it.toDoubleOrNull() ?: 0.0)
                },
                label = { Text("Movement (+ inward / - outward)") },
                modifier = Modifier.fillMaxWidth()
            )

            val difference = (row.physicalCount ?: row.count) - row.count
            Text(
                text = "Difference: $difference",
                color = if (difference == 0.0) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.error
                }
            )
        }
    }
}

private fun parseXlsx(input: InputStream): List<StockRow> {
    val files = HashMap<String, ByteArray>()

    ZipInputStream(input).use { zip ->
        var entry = zip.nextEntry
        while (entry != null) {
            if (!entry.isDirectory) {
                files[entry.name] = zip.readBytes()
            }
            entry = zip.nextEntry
        }
    }

    val sharedStrings = files["xl/sharedStrings.xml"]
        ?.let { parseSharedStrings(it) }
        ?: emptyList()

    val sheetFile = files.keys.firstOrNull {
        it.startsWith("xl/worksheets/sheet") && it.endsWith(".xml")
    } ?: return emptyList()

    val sheet = files[sheetFile] ?: return emptyList()
    val table = parseSheet(sheet, sharedStrings)
    if (table.isEmpty()) return emptyList()

    val header = table.first()
    fun col(name: String): String {
        val target = name.lowercase()
        header.forEach { (index, value) ->
            if (value.lowercase().trim() == target) {
                return table.getOrNull(1)?.get(index) ?: ""
            }
        }
        return ""
    }

    return table.drop(1).map { current ->
        fun value(name: String): String {
            val target = name.lowercase()
            header.forEach { (index, headerValue) ->
                if (headerValue.lowercase().trim() == target) {
                    return current[index] ?: ""
                }
            }
            return ""
        }

        val count = value("count").toNumber()
        val inward = value("inward").toNumber()
        val outward = value("outward").toNumber()
        val balance = value("balance").toNumber()

        StockRow(
            product = value("product"),
            brand = value("brand"),
            count = count,
            inward = inward,
            outward = outward,
            balance = if (balance != 0.0) balance else count + inward - outward,
            location = value("location")
        )
    }
}

private fun parseSharedStrings(bytes: ByteArray): List<String> {
    val result = mutableListOf<String>()
    val parser = Xml.newPullParser()
    parser.setInput(ByteArrayInputStream(bytes), "UTF-8")

    var event = parser.eventType
    var inside = false
    val builder = StringBuilder()

    while (event != XmlPullParser.END_DOCUMENT) {
        when (event) {
            XmlPullParser.START_TAG -> {
                if (parser.name == "si") {
                    inside = true
                    builder.clear()
                }
            }
            XmlPullParser.TEXT -> {
                if (inside) builder.append(parser.text)
            }
            XmlPullParser.END_TAG -> {
                if (parser.name == "si") {
                    result.add(builder.toString())
                    inside = false
                }
            }
        }
        event = parser.next()
    }

    return result
}

private fun parseSheet(
    bytes: ByteArray,
    shared: List<String>
): List<Map<Int, String>> {
    val rows = mutableListOf<Map<Int, String>>()
    val parser = Xml.newPullParser()
    parser.setInput(ByteArrayInputStream(bytes), "UTF-8")

    var event = parser.eventType
    var currentRow = mutableMapOf<Int, String>()
    var currentCell = ""
    var currentType = ""

    while (event != XmlPullParser.END_DOCUMENT) {
        when (event) {
            XmlPullParser.START_TAG -> {
                when (parser.name) {
                    "row" -> currentRow = mutableMapOf()
                    "c" -> {
                        currentCell = parser.getAttributeValue(null, "r") ?: ""
                        currentType = parser.getAttributeValue(null, "t") ?: ""
                    }
                    "v" -> {
                        val value = parser.nextText()
                        val columnIndex = cellColumnIndex(currentCell)
                        val finalValue = if (currentType == "s") {
                            shared.getOrNull(value.toIntOrNull() ?: -1) ?: value
                        } else {
                            value
                        }
                        currentRow[columnIndex] = finalValue
                    }
                }
            }
            XmlPullParser.END_TAG -> {
                if (parser.name == "row" && currentRow.isNotEmpty()) {
                    rows.add(currentRow.toMap())
                }
            }
        }
        event = parser.next()
    }

    return rows
}

private fun cellColumnIndex(cellReference: String): Int {
    var index = 0
    for (char in cellReference) {
        if (!char.isLetter()) break
        index = index * 26 + (char.uppercaseChar() - 'A' + 1)
    }
    return index - 1
}

private fun String.toNumber(): Double {
    return trim().replace(",", "").toDoubleOrNull() ?: 0.0
}

private fun exportCsv(context: Context, rows: List<StockRow>): String {
    if (rows.isEmpty()) return "Pehle Excel import karo"

    return try {
        val fileName = "fruits_warehouse_" +
            SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
                .format(Date()) + ".csv"

        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, fileName)
            put(MediaStore.Downloads.MIME_TYPE, "text/csv")
        }

        val uri = context.contentResolver.insert(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            values
        ) ?: return "CSV file create nahi hui"

        context.contentResolver.openOutputStream(uri)?.use { output ->
            output.bufferedWriter().use { writer ->
                writer.appendLine("Product,Brand,System Count,Inward,Outward,Balance,Location,Physical Count,Difference,Movement")
                rows.forEach { row ->
                    val physical = row.physicalCount ?: row.count
                    val difference = physical - row.count
                    writer.appendLine(
                        listOf(
                            row.product,
                            row.brand,
                            row.count,
                            row.inward,
                            row.outward,
                            row.balance,
                            row.location,
                            physical,
                            difference,
                            row.movement
                        ).joinToString(",")
                    )
                }
            }
        }

        "CSV export ho gayi: $fileName"
    } catch (e: Exception) {
        "CSV export failed: ${e.message}"
    }
}
