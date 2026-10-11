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
import androidx.compose.foundation.text.KeyboardOptions
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

data class StockRow(
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
    var physical: Double? = null
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
fun WarehouseApp(context: Context) {
    val prefs = remember {
        context.getSharedPreferences("warehouse", Context.MODE_PRIVATE)
    }

    val rows = remember {
        mutableStateListOf<StockRow>().apply {
            addAll(loadRows(prefs.getString("rows", null)))
        }
    }

    var tab by remember { mutableStateOf("Stock") }
    var search by remember { mutableStateOf("") }
    var selectedLot by remember { mutableStateOf("") }
    var quantity by remember { mutableStateOf("") }
    var movement by remember { mutableStateOf("Inward") }
    var message by remember { mutableStateOf("Excel upload karein.") }

    fun save() {
        prefs.edit().putString("rows", toJson(rows).toString()).apply()
    }

    val excelPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult

        try {
            val stream = context.contentResolver.openInputStream(uri)

            if (stream == null) {
                message = "File open nahi hui."
                return@rememberLauncherForActivityResult
            }

            stream.use {
                val imported = readExcel(it)

                if (imported.isEmpty()) {
                    message = "Excel me Lot No. aur Balance column check karein."
                } else {
                    rows.clear()
                    rows.addAll(imported)
                    save()
                    message = "${imported.size} lots import ho gaye."
                }
            }
        } catch (e: Exception) {
            message = "Excel error: ${e.message}"
        }
    }

    val csvSaver = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult

        try {
            val output = context.contentResolver.openOutputStream(uri)

            if (output == null) {
                message = "CSV file nahi ban payi."
                return@rememberLauncherForActivityResult
            }

            output.bufferedWriter().use { writer ->
                writer.write("Customer,Date,Lot,Product,Brand,Count,In,Out,Balance,Physical,Location
")

                rows.forEach {
                    writer.write(
                        listOf(
                            it.customer, it.date, it.lot, it.product, it.brand, it.count,
                            it.inward.toString(), it.outward.toString(), it.balance.toString(),
                            it.physical?.toString() ?: "", it.location
                        ).joinToString(",")
                    )
                    writer.write("
")
                }
            }

            message = "CSV export ho gayi."
        } catch (e: Exception) {
            message = "Export error: ${e.message}"
        }
    }

    val visibleRows = rows.filter {
        search.isBlank() ||
            it.lot.contains(search, true) ||
            it.customer.contains(search, true) ||
            it.product.contains(search, true)
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Fruits Warehouse") }) }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(12.dp)
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf("Stock", "Counting", "Difference", "Movement").forEach {
                    FilterChip(
                        selected = tab == it,
                        onClick = { tab = it },
                        label = { Text(it) }
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            Text(message, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))

            when (tab) {
                "Stock" -> {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                excelPicker.launch(arrayOf("*/*"))
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Excel Upload")
                        }

                        OutlinedButton(onClick = {
                            csvSaver.launch("warehouse-report.csv")
                        }) {
                            Text("CSV")
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    OutlinedTextField(
                        value = search,
                        onValueChange = { search = it },
                        label = { Text("Search Lot / Customer / Product") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Spacer(Modifier.height(8.dp))

                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(visibleRows) { row ->
                            Card(Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(12.dp)) {
                                    Text("LOT ${row.lot}", fontWeight = FontWeight.Bold)
                                    Text(row.product)
                                    Text("Customer: ${row.customer}")
                                    Text("Balance: ${row.balance}")
                                    Text("Location: ${row.location}")
                                }
                            }
                        }
                    }
                }

                "Counting" -> {
                    OutlinedTextField(
                        value = selectedLot,
                        onValueChange = { selectedLot = it },
                        label = { Text("Lot Number") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Spacer(Modifier.height(8.dp))

                    val found = rows.filter {
                        selectedLot.isBlank() || it.lot.contains(selectedLot, true)
                    }

                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(found) { row ->
                            var text by remember(row.lot) {
                                mutableStateOf(row.physical?.toString() ?: "")
                            }

                            Card(Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(12.dp)) {
                                    Text("LOT ${row.lot}", fontWeight = FontWeight.Bold)
                                    Text("System Balance: ${row.balance}")

                                    OutlinedTextField(
                                        value = text,
                                        onValueChange = {
                                            text = it
                                            row.physical = it.toDoubleOrNull()
                                            save()
                                        },
                                        label = { Text("Physical Count") },
                                        keyboardOptions = KeyboardOptions(
                                            keyboardType = KeyboardType.Decimal
                                        ),
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true
                                    )
                                }
                            }
                        }
                    }
                }

                "Difference" -> {
                    val diffRows = rows.filter {
                        it.physical != null && it.physical != it.balance
                    }

                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(diffRows) { row ->
                            Card(Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(12.dp)) {
                                    Text("LOT ${row.lot}", fontWeight = FontWeight.Bold)
                                    Text("System: ${row.balance}")
                                    Text("Physical: ${row.physical}")
                                    Text("Difference: ${(row.physical ?: 0.0) - row.balance}")

                                    Button(onClick = {
                                        row.balance = row.physical ?: row.balance
                                        save()
                                        message = "Lot ${row.lot} balance update ho gaya."
                                    }) {
                                        Text("Approve")
                                    }
                                }
                            }
                        }
                    }
                }

                else -> {
                    OutlinedTextField(
                        value = selectedLot,
                        onValueChange = { selectedLot = it },
                        label = { Text("Lot Number") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Spacer(Modifier.height(8.dp))

                    OutlinedTextField(
                        value = quantity,
                        onValueChange = { quantity = it },
                        label = { Text("Quantity") },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Decimal
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Spacer(Modifier.height(8.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(
                            selected = movement == "Inward",
                            onClick = { movement = "Inward" },
                            label = { Text("Inward") }
                        )

                        FilterChip(
                            selected = movement == "Outward",
                            onClick = { movement = "Outward" },
                            label = { Text("Outward") }
                        )
                    }

                    Spacer(Modifier.height(8.dp))

                    Button(
                        onClick = {
                            val row = rows.firstOrNull {
                                it.lot.equals(selectedLot.trim(), true)
                            }
                            val qty = quantity.toDoubleOrNull()

                            if (row == null || qty == null || qty <= 0) {
                                message = "Sahi Lot aur Quantity daalein."
                                return@Button
                            }

                            if (movement == "Inward") {
                                row.inward += qty
                                row.balance += qty
                            } else {
                                if (qty > row.balance) {
                                    message = "Outward balance se zyada hai."
                                    return@Button
                                }

                                row.outward += qty
                                row.balance -= qty
                            }

                            save()
                            message = "$movement saved for Lot ${row.lot}."
                            quantity = ""
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Save $movement")
                    }
                }
            }
        }
    }
}

private fun toJson(rows: List<StockRow>): JSONArray {
    return JSONArray().apply {
        rows.forEach {
            put(
                JSONObject().apply {
                    put("customer", it.customer)
                    put("date", it.date)
                    put("lot", it.lot)
                    put("product", it.product)
                    put("brand", it.brand)
                    put("count", it.count)
                    put("inward", it.inward)
                    put("outward", it.outward)
                    put("balance", it.balance)
                    put("location", it.location)

                    if (it.physical == null) {
                        put("physical", JSONObject.NULL)
                    } else {
                        put("physical", it.physical)
                    }
                }
            )
        }
    }
}

private fun loadRows(json: String?): List<StockRow> {
    if (json.isNullOrBlank()) return emptyList()

    return try {
        val array = JSONArray(json)

        (0 until array.length()).map {
            val obj = array.getJSONObject(it)

            StockRow(
                customer = obj.optString("customer"),
                date = obj.optString("date"),
                lot = obj.optString("lot"),
                product = obj.optString("product"),
                brand = obj.optString("brand"),
                count = obj.optString("count"),
                inward = obj.optDouble("inward"),
                outward = obj.optDouble("outward"),
                balance = obj.optDouble("balance"),
                location = obj.optString("location"),
                physical = if (obj.isNull("physical")) null else obj.optDouble("physical")
            )
        }
    } catch (_: Exception) {
        emptyList()
    }
}

private fun readExcel(input: InputStream): List<StockRow> {
    val files = mutableMapOf<String, ByteArray>()

    ZipInputStream(input).use { zip ->
        while (true) {
            val entry = zip.nextEntry ?: break

            if (
                entry.name == "xl/sharedStrings.xml" ||
                entry.name == "xl/worksheets/sheet1.xml"
            ) {
                val output = ByteArrayOutputStream()
                zip.copyTo(output)
                files[entry.name] = output.toByteArray()
            }

            zip.closeEntry()
        }
    }

    val sheet = files["xl/worksheets/sheet1.xml"] ?: return emptyList()
    val shared = files["xl/sharedStrings.xml"]?.let { readSharedStrings(it) } ?: emptyList()
    val sheetRows = readSheet(sheet, shared)

    var customer = ""
    var headers: Map<Int, String>? = null
    val result = mutableListOf<StockRow>()

    sheetRows.forEach { cells ->
        val text = cells.values.joinToString(" ")

        if (text.contains("Customer Name", true)) {
            customer = cells.values.lastOrNull {
                it.isNotBlank() && !it.contains("Customer Name", true)
            } ?: "Unknown Customer"
            return@forEach
        }

        if (
            cells.values.any { it.contains("Lot", true) } &&
            cells.values.any { it.contains("Balance", true) }
        ) {
            headers = cells.mapValues { it.value.lowercase(Locale.ROOT) }
            return@forEach
        }

        val head = headers ?: return@forEach

        fun value(vararg names: String): String {
            val wanted = names.map { it.lowercase(Locale.ROOT) }

            val match = head.entries.firstOrNull { (_, header) ->
                wanted.any { header.contains(it) }
            }

            return match?.key?.let { cells[it] ?: "" } ?: ""
        }

        val lot = value("lot no", "lot number", "lot")

        if (lot.isBlank() || lot.equals("total", true)) return@forEach

        result.add(
            StockRow(
                customer = customer.ifBlank { "Unknown Customer" },
                date = value("date"),
                lot = lot,
                product = value("product name", "product"),
                brand = value("brand"),
                count = value("count"),
                inward = value("in", "inward").toDoubleOrNull() ?: 0.0,
                outward = value("out", "outward").toDoubleOrNull() ?: 0.0,
                balance = value("balance").toDoubleOrNull() ?: 0.0,
                location = value("location")
            )
        )
    }

    return result
}

private fun readSheet(
    bytes: ByteArray,
    shared: List<String>
): List<Map<Int, String>> {
    val parser = Xml.newPullParser()
    parser.setInput(ByteArrayInputStream(bytes), "UTF-8")

    val rows = mutableListOf<Map<Int, String>>()
    var row = mutableMapOf<Int, String>()
    var cellRef = ""
    var cellType = ""
    var value = StringBuilder()
    var reading = false

    var event = parser.eventType

    while (event != XmlPullParser.END_DOCUMENT) {
        when (event) {
            XmlPullParser.START_TAG -> when (parser.name) {
                "row" -> row = mutableMapOf()
                "c" -> {
                    cellRef = parser.getAttributeValue(null, "r") ?: ""
                    cellType = parser.getAttributeValue(null, "t") ?: ""
                    value = StringBuilder()
                }
                "v", "t" -> reading = true
            }

            XmlPullParser.TEXT -> if (reading) value.append(parser.text)

            XmlPullParser.END_TAG -> when (parser.name) {
                "v", "t" -> reading = false

                "c" -> {
                    val index = columnIndex(cellRef)
                    val raw = value.toString().trim()

                    val finalValue = when (cellType) {
                        "s" -> shared.getOrNull(raw.toIntOrNull() ?: -1) ?: ""
                        else -> raw
                    }

                    if (index >= 0) row[index] = finalValue
                }

                "row" -> if (row.isNotEmpty()) rows.add(row.toMap())
                
