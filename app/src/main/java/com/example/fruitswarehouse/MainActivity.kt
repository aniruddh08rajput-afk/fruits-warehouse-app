package com.example.fruitswarehouse

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Xml
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
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
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Date
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
        setContent { WarehouseApp(this) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WarehouseApp(context: Context) {
    val prefs = remember { context.getSharedPreferences("warehouse_store", Context.MODE_PRIVATE) }
    val rows = remember { mutableStateListOf<StockRow>().apply { addAll(loadRows(prefs.getString("rows", null))) } }
    var tab by remember { mutableStateOf("Stock") }
    var search by remember { mutableStateOf("") }
    var selectedLot by remember { mutableStateOf("") }
    var qtyText by remember { mutableStateOf("") }
    var movementType by remember { mutableStateOf("Inward") }
    var movementNote by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("पहले Final Stock Excel (.xlsx) अपलोड करें।") }

    fun save() { prefs.edit().putString("rows", rowsToJson(rows).toString()).apply() }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            try {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    val imported = parseXlsx(input)
                    if (imported.isEmpty()) {
                        message = "कोई Stock row नहीं मिली। Excel में Lot No. और Balance वाले कॉलम जाँचें।"
                    } else {
                        rows.clear()
                        rows.addAll(imported)
                        save()
                        message = "${imported.size} Lot import हुए। यह Final Stock अब शुरुआती बैलेंस है।"
                    }
                } ?: run { message = "फाइल खुल नहीं पाई। दोबारा चुनें।" }
            } catch (e: Exception) {
                message = "Excel पढ़ने में समस्या: ${e.message ?: "फाइल का format जाँचें"}"
            }
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri: Uri? ->
        if (uri != null) {
            try {
                context.contentResolver.openOutputStream(uri)?.bufferedWriter(Charsets.UTF_8)?.use { out ->
                    out.write("Customer,InwardDate,Lot No.,Product Name,Brand,Count,In,Out,Balance,Physical Count,Difference,Location\n")
                    rows.forEach { r ->
                        val diff = r.physical?.minus(r.balance)
                        val fields = listOf(r.customer, r.date, r.lot, r.product, r.brand, r.count,
                            num(r.inward), num(r.outward), num(r.balance), r.physical?.let(::num) ?: "", diff?.let(::num) ?: "", r.location)
                        out.write(fields.joinToString(",") { csv(it) } + "\n")
                    }
                }
                message = "Report export हो गई।"
            } catch (e: Exception) { message = "Export नहीं हुई: ${e.message}" }
        }
    }

    val filtered = rows.filter {
        val q = search.trim().lowercase(Locale.getDefault())
        q.isBlank() || listOf(it.customer, it.lot, it.product, it.brand, it.location).any { v -> v.lowercase(Locale.getDefault()).contains(q) }
    }

    MaterialTheme {
        Scaffold(topBar = { TopAppBar(title = { Text("Fruits Warehouse") }) }) { pad ->
            Column(Modifier.fillMaxSize().padding(pad).padding(12.dp)) {
                Text("Lot-wise Cold Store Stock", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    listOf("Stock", "Counting", "Difference", "Movement").forEach { name ->
                        FilterChip(selected = tab == name, onClick = { tab = name }, label = { Text(name) })
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(8.dp))
                when (tab) {
                    "Stock" -> {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { importLauncher.launch(arrayOf("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "application/octet-stream", "*/*")) }, modifier = Modifier.weight(1f)) { Text("Final Stock Excel Upload") }
                            OutlinedButton(onClick = { exportLauncher.launch("Warehouse-Report.csv") }) { Text("Export CSV") }
                        }
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(value = search, onValueChange = { search = it }, label = { Text("Lot / Customer / Product / Location खोजें") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                        Spacer(Modifier.height(6.dp))
                        Summary(rows)
                        Spacer(Modifier.height(6.dp))
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(filtered, key = { it.customer + "-" + it.lot }) { r -> StockCard(r, onCount = { tab = "Counting"; selectedLot = r.lot }) }
                        }
                    }
                    "Counting" -> {
                        Text("हर Lot की असली गिनती दर्ज करें। Lot Number सबसे जरूरी है।")
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(value = search, onValueChange = { search = it; selectedLot = it.trim() }, label = { Text("Lot Number खोजें/लिखें") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                        val found = rows.filter { selectedLot.isNotBlank() && it.lot.contains(selectedLot, true) }.take(15)
                        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(found, key = { "count-${it.customer}-${it.lot}" }) { r ->
                                Card(Modifier.fillMaxWidth()) {
                                    Column(Modifier.padding(10.dp)) {
                                        Text("LOT ${r.lot} • ${r.product}", fontWeight = FontWeight.Bold)
                                        Text("${r.customer} | ${r.location} | System: ${num(r.balance)}")
                                        OutlinedTextField(value = qtyTextFor(r), onValueChange = { value -> r.physical = value.toDoubleOrNull(); save(); message = "Lot ${r.lot} की गिनती सेव हुई।" }, label = { Text("Physical Count") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth(), singleLine = true)
                                    }
                                }
                            }
                        }
                        if (rows.isEmpty()) Text("पहले Stock tab में Final Stock Excel upload करें।")
                    }
                    "Difference" -> {
                        val diffs = rows.filter { it.physical != null && kotlin.math.abs(it.physical!! - it.balance) > 0.0001 }
                        Text("अंतर वाले Lot: ${diffs.size}", fontWeight = FontWeight.Bold)
                        Text("Approve दबाने तक Balance नहीं बदलेगा।", style = MaterialTheme.typography.bodySmall)
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(diffs, key = { "diff-${it.customer}-${it.lot}" }) { r ->
                                val delta = r.physical!! - r.balance
                                Card(Modifier.fillMaxWidth()) {
                                    Column(Modifier.padding(12.dp)) {
                                        Text("LOT ${r.lot} — ${r.product}", fontWeight = FontWeight.Bold)
                                        Text("${r.customer} | ${r.location}")
                                        Text("System: ${num(r.balance)}   Physical: ${num(r.physical!!)}")
                                        Text("Difference: ${if (delta > 0) "+" else ""}${num(delta)}")
                                        Button(onClick = {
                                            r.balance = r.physical!!
                                            r.approved = true
                                            save()
                                            message = "Lot ${r.lot} का Physical Count मंज़ूर करके Balance बदला गया।"
                                        }) { Text("Approve & Adjust Balance") }
                                    }
                                }
                            }
                        }
                        if (diffs.isEmpty()) Text("कोई दर्ज किया हुआ Stock Difference नहीं है।")
                    }
                    else -> {
                        Text("Manual Stock Movement", fontWeight = FontWeight.Bold)
                        Text("यहाँ की एंट्री इस फोन में सेव होगी। Excel transaction import अलग से आपके वास्तविक कॉलम देखकर जोड़ा जा सकता है।")
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(selected = movementType == "Inward", onClick = { movementType = "Inward" }, label = { Text("Inward +") })
                            FilterChip(selected = movementType == "Outward", onClick = { movementType = "Outward" }, label = { Text("Outward −") })
                        }
                        OutlinedTextField(value = selectedLot, onValueChange = { selectedLot = it }, label = { Text("Lot Number") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                        OutlinedTextField(value = qtyText, onValueChange = { qtyText = it }, label = { Text("Quantity / Cartons") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth(), singleLine = true)
                        OutlinedTextField(value = movementNote, onValueChange = { movementNote = it }, label = { Text("Note: Vehicle / RP / Packhouse / Destination") }, modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = {
                            val r = rows.firstOrNull { it.lot.equals(selectedLot.trim(), true) }
                            val qty = qtyText.toDoubleOrNull()
                            if (r == null || qty == null || qty <= 0) {
                                message = "मौजूदा Lot Number चुनें और सही Quantity लिखें।"
                            } else if (movementType == "Outward" && qty > r.balance) {
                                message = "Outward Quantity Balance से ज्यादा है। एंट्री रोक दी गई।"
                            } else {
                                if (movementType == "Inward") { r.inward += qty; r.balance += qty } else { r.outward += qty; r.balance -= qty }
                                save()
                                message = "$movementType ${num(qty)} Lot ${r.lot} में दर्ज हुआ। Note: $movementNote"
                                qtyText = ""; movementNote = ""
                            }
                        }, modifier = Modifier.fillMaxWidth()) { Text("Save $movementType") }
                    }
                }
            }
        }
    }
}

@Composable
private fun Summary(rows: List<StockRow>) {
    val balance = rows.sumOf { it.balance }
    val lots = rows.size
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Column { Text("Total Lots"); Text("$lots", fontWeight = FontWeight.Bold) }
            Column { Text("Balance Total"); Text(num(balance), fontWeight = FontWeight.Bold) }
            Column { Text("Counted"); Text("${rows.count { it.physical != null }}/${rows.size}", fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
private fun StockCard(r: StockRow, onCount: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(10.dp)) {
            Text("LOT ${r.lot}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(r.product, fontWeight = FontWeight.SemiBold)
            Text("Customer: ${r.customer}")
            Text("Brand: ${r.brand} | Count/Size: ${r.count}")
            Text("In: ${num(r.inward)}  Out: ${num(r.outward)}  Balance: ${num(r.balance)}", fontWeight = FontWeight.Medium)
            Text("Location: ${r.location}")
            r.physical?.let { Text("Physical: ${num(it)} | Difference: ${num(it - r.balance)}") }
            TextButton(onClick = onCount) { Text("Enter Physical Count") }
        }
    }
}

private fun qtyTextFor(row: StockRow): String = row.physical?.let(::num) ?: ""
private fun num(n: Double): String = if (n % 1.0 == 0.0) n.toLong().toString() else String.format(Locale.US, "%.2f", n)
private fun csv(s: String): String = "\"" + s.replace("\"", "\"\"") + "\""

private fun rowsToJson(rows: List<StockRow>): JSONArray = JSONArray().apply {
    rows.forEach { r -> put(JSONObject().apply {
        put("customer", r.customer); put("date", r.date); put("lot", r.lot); put("product", r.product); put("brand", r.brand)
        put("count", r.count); put("inward", r.inward); put("outward", r.outward); put("balance", r.balance); put("location", r.location)
        if (r.physical != null) put("physical", r.physical!!) else put("physical", JSONObject.NULL)
        put("approved", r.approved)
    }) }
}

private fun loadRows(json: String?): List<StockRow> {
    if (json.isNullOrBlank()) return emptyList()
    return try {
        val a = JSONArray(json)
        (0 until a.length()).map { i ->
            val o = a.getJSONObject(i)
            StockRow(o.optString("customer"), o.optString("date"), o.optString("lot"), o.optString("product"), o.optString("brand"), o.optString("count"),
                o.optDouble("inward"), o.optDouble("outward"), o.optDouble("balance"), o.optString("location"),
                if (o.isNull("physical")) null else o.optDouble("physical"), o.optBoolean("approved"))
        }
    } catch (_: Exception) { emptyList() }
}

/** Reads ordinary .xlsx files without third-party libraries; first worksheet is used. */
private fun parseXlsx(input: InputStream): List<StockRow> {
    val entries = HashMap<String, ByteArray>()
    ZipInputStream(input).use { zip ->
        while (true) {
            val entry = zip.nextEntry ?: break
            if (!entry.isDirectory && (entry.name == "xl/sharedStrings.xml" || entry.name == "xl/worksheets/sheet1.xml")) {
                val out = ByteArrayOutputStream(); zip.copyTo(out); entries[entry.name] = out.toByteArray()
            }
            zip.closeEntry()
        }
    }
    val sheet = entries["xl/worksheets/sheet1.xml"] ?: throw IllegalArgumentException("Excel की पहली worksheet नहीं मिली")
    val strings = entries["xl/sharedStrings.xml"]?.let(::parseSharedStrings) ?: emptyList()
    val parsedRows = parseSheet(sheet, strings)
    if (parsedRows.isEmpty()) return emptyList()

    var customer = ""
    var headers: Map<Int, String>? = null
    val result = mutableListOf<StockRow>()
    for (cells in parsedRows) {
        val joined = cells.values.joinToString(" ").trim()
        if (joined.contains("Customer Name", true)) {
            val vals = cells.toSortedMap().values.map { it.trim() }.filter { it.isNotBlank() }
            customer = vals.lastOrNull { !it.contains("Customer Name", true) && !it.equals("Total", true) } ?: "Unknown Customer"
            continue
        }
        if (cells.values.any { it.contains("Lot No", true) } && cells.values.any { it.contains("Balance", true) }) {
            headers = cells.mapValues { it.value.trim().lowercase(Locale.ROOT).replace(".", "") }
            continue
        }
        val h = headers ?: continue
        fun norm(value: String) = value.lowercase(Locale.ROOT).replace(Regex("[^a-z0-9]"), "")
        fun col(vararg names: String): String {
            val wanted = names.map(::norm)
            val exact = h.entries.firstOrNull { (_, value) -> norm(value) in wanted }
            val match = exact ?: h.entries.firstOrNull { (_, value) ->
                val normalized = norm(value)
                wanted.any { it.length >= 4 && normalized.contains(it) }
            }
            return match?.key?.let { cells[it]?.trim().orEmpty() }.orEmpty()
        }
        val lot = col("lot no", "lot number")
        if (lot.isBlank() || lot.equals("total", true) || lot.toDoubleOrNull() == null) continue
        val balText = col("balance")
        val inText = col("in")
        val outText = col("out")
        result.add(StockRow(
            customer = customer.ifBlank { "Unknown Customer" },
            date = col("inwarddate", "inward date"),
            lot = lot.removeSuffix(".0"),
            product = col("product name", "product"),
            brand = col("brand"),
            count = col("count"),
            inward = inText.toNumber(), outward = outText.toNumber(), balance = balText.toNumber(),
            location = col("location")
        ))
    }
    return result
}

private fun String.toNumber(): Double = trim().replace(",", "").toDoubleOrNull() ?: 0.0

private fun parseSharedStrings(bytes: ByteArray): List<String> {
    val parser = Xml.newPullParser(); parser.setInput(ByteArrayInputStream(bytes), "UTF-8")
    val result = mutableListOf<String>(); var event = parser.eventType; var inSi = false; var current = StringBuilder()
    while (event != XmlPullParser.END_DOCUMENT) {
        when (event) {
            XmlPullParser.START_TAG -> when (parser.name) {
                "si" -> { inSi = true; current = StringBuilder() }
                "t" -> if (inSi) current.append(parser.nextText())
            }
            XmlPullParser.END_TAG -> if (parser.name == "si") { result.add(current.toString()); inSi = false }
        }
        event = parser.eventType.let { if (it == XmlPullParser.END_DOCUMENT) it else parser.next() }
    }
    return result
}

private fun parseSheet(bytes: ByteArray, shared: List<String>): List<Map<Int, 
