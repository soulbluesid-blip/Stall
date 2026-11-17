package com.mazastall.hishob

import android.os.Bundle
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class Item(val name: String, val qty: Int, val price: Double, val unitCost: Double) {
    val sales get() = qty * price
    val cost get() = qty * unitCost
}
data class DayRecord(val date: String, val items: List<Item>, val other: Double) {
    val plates get() = items.sumOf { it.qty }
    val sales get() = items.sumOf { it.sales }
    val expenses get() = items.sumOf { it.cost } + other
    val profit get() = sales - expenses
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { StallApp() }
    }
}

private fun loadRecords(ctx: Context): MutableList<DayRecord> {
    val raw = ctx.getSharedPreferences("stall", Context.MODE_PRIVATE).getString("records", "[]") ?: "[]"
    val arr = JSONArray(raw)
    val out = mutableListOf<DayRecord>()
    for (i in 0 until arr.length()) {
        val o = arr.getJSONObject(i)
        val ia = o.getJSONArray("items")
        val items = (0 until ia.length()).map { j ->
            val x = ia.getJSONObject(j)
            Item(x.getString("name"), x.getInt("qty"), x.getDouble("price"), x.getDouble("unitCost"))
        }
        out.add(DayRecord(o.getString("date"), items, o.getDouble("other")))
    }
    return out
}
private fun saveRecords(ctx: Context, records: List<DayRecord>) {
    val arr = JSONArray()
    records.forEach { r ->
        val o = JSONObject().put("date", r.date).put("other", r.other)
        val ia = JSONArray()
        r.items.forEach { ia.put(JSONObject().put("name", it.name).put("qty", it.qty).put("price", it.price).put("unitCost", it.unitCost)) }
        o.put("items", ia); arr.put(o)
    }
    ctx.getSharedPreferences("stall", Context.MODE_PRIVATE).edit().putString("records", arr.toString()).apply()
}
private fun money(v: Double) = "₹" + String.format(Locale("en", "IN"), "%.2f", v)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StallApp() {
    val ctx = LocalContext.current
    val today = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()) }
    val names = listOf("पाणीपुरी", "शेवपुरी", "आलू पोंगा", "भेळ")
    val defaults = listOf(10.0, 20.0, 20.0, 20.0)
    var date by remember { mutableStateOf(today) }
    var other by remember { mutableStateOf("0") }
    var qty by remember { mutableStateOf(List(4) { "0" }) }
    var price by remember { mutableStateOf(defaults.map { it.toString() }) }
    var unitCost by remember { mutableStateOf(List(4) { "0" }) }
    var records by remember { mutableStateOf(loadRecords(ctx)) }
    val monthRecords = records.filter { it.date.take(7) == date.take(7) }
    var tab by remember { mutableIntStateOf(0) }
    val backupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) ctx.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(recordsJson(records)) }
    }
    val csvLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) ctx.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(recordsCsv(monthRecordsForExport(records, date))) }
    }
    val items = names.indices.map { i -> Item(names[i], qty[i].toIntOrNull() ?: 0, price[i].toDoubleOrNull() ?: 0.0, unitCost[i].toDoubleOrNull() ?: 0.0) }
    val current = DayRecord(date, items, other.toDoubleOrNull() ?: 0.0)
    MaterialTheme(colorScheme = lightColorScheme(primary = androidx.compose.ui.graphics.Color(0xFF176B45))) {
        Scaffold(topBar = { TopAppBar(title = { Text("माझा स्टॉल हिशोब", fontWeight = FontWeight.Bold) }) }) { pad ->
            Column(Modifier.padding(pad).padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                TabRow(selectedTabIndex = tab) {
                    Tab(tab == 0, { tab = 0 }, text = { Text("आजचा हिशोब") })
                    Tab(tab == 1, { tab = 1 }, text = { Text("महिन्याचा रिपोर्ट") })
                }
                if (tab == 0) {
                    OutlinedTextField(date, { date = it }, label = { Text("तारीख (YYYY-MM-DD)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    Text("विक्रीची माहिती", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    names.forEachIndexed { i, name ->
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(name, fontWeight = FontWeight.Bold)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    NumberField("प्लेट्स", qty[i], { v -> qty = qty.toMutableList().also { it[i] = v } }, Modifier.weight(1f), true)
                                    NumberField("दर ₹", price[i], { v -> price = price.toMutableList().also { it[i] = v } }, Modifier.weight(1f))
                                    NumberField("प्लेट खर्च ₹", unitCost[i], { v -> unitCost = unitCost.toMutableList().also { it[i] = v } }, Modifier.weight(1f))
                                }
                                Text("विक्री: ${money(items[i].sales)}")
                            }
                        }
                    }
                    NumberField("इतर खर्च ₹ (भाडे, गॅस, प्रवास इ.)", other, { other = it }, Modifier.fillMaxWidth())
                    SummaryCard(current)
                    Button(onClick = {
                        records = records.filterNot { it.date == date }.toMutableList().also { it.add(current) }.sortedBy { it.date }
                        saveRecords(ctx, records)
                    }, modifier = Modifier.fillMaxWidth()) { Text("आजचा हिशोब सेव्ह करा") }
                    OutlinedButton(onClick = {
                        qty = List(4) { "0" }; other = "0"
                    }, modifier = Modifier.fillMaxWidth()) { Text("नवीन हिशोबासाठी फॉर्म रिकामा करा") }
                    Text("टीप: एका प्लेटचा खर्च स्वतंत्रपणे भरा. इतर खर्चात तो पुन्हा धरू नका.", style = MaterialTheme.typography.bodySmall)
                } else {
                    OutlinedTextField(date, { date = it }, label = { Text("महिना निवडण्यासाठी तारीख (YYYY-MM-DD)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    val totalSales = monthRecords.sumOf { it.sales }
                    val totalExpenses = monthRecords.sumOf { it.expenses }
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("महिन्याचा रिपोर्ट: ${date.take(7)}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text("एकूण दिवस नोंदवले: ${monthRecords.size}")
                            Text("एकूण प्लेट्स: ${monthRecords.sumOf { it.plates }}")
                            Text("एकूण विक्री: ${money(totalSales)}")
                            Text("एकूण खर्च: ${money(totalExpenses)}")
                            Text("निव्वळ नफा / तोटा: ${money(totalSales - totalExpenses)}", fontWeight = FontWeight.Bold, color = if (totalSales-totalExpenses >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(onClick = { backupLauncher.launch("MazaStallHishob_Backup.json") }, modifier = Modifier.weight(1f)) {
                            Text("पूर्ण Backup")
                        }
                        Button(onClick = { csvLauncher.launch("MazaStallHishob_${date.take(7)}.csv") }, modifier = Modifier.weight(1f)) {
                            Text("Excel Report")
                        }
                    }
                    Text("Backup मध्ये सर्व दिवसांच्या नोंदी JSON स्वरूपात सेव्ह होतील. Excel Report CSV स्वरूपात आहे; तो Excel किंवा Google Sheets मध्ये उघडता येईल.", style = MaterialTheme.typography.bodySmall)
                    Text("दिवसनिहाय नोंदी", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    monthRecords.sortedByDescending { it.date }.forEach { r ->
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(12.dp)) {
                                Text(r.date, fontWeight = FontWeight.Bold)
                                Text("प्लेट्स: ${r.plates}  •  विक्री: ${money(r.sales)}")
                                Text("खर्च: ${money(r.expenses)}  •  नफा: ${money(r.profit)}")
                            }
                        }
                    }
                    if (monthRecords.isEmpty()) Text("या महिन्यात अजून नोंदी सेव्ह केलेल्या नाहीत.")
                }
            }
        }
    }
}

@Composable
fun NumberField(label: String, value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier, integer: Boolean = false) {
    OutlinedTextField(value, onChange, label = { Text(label) }, modifier = modifier, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = if (integer) KeyboardType.Number else KeyboardType.Decimal))
}
@Composable
fun SummaryCard(r: DayRecord) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("आजचा हिशोब", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("एकूण प्लेट्स: ${r.plates}")
            Text("एकूण विक्री: ${money(r.sales)}")
            Text("मालाचा खर्च: ${money(r.items.sumOf { it.cost })}")
            Text("इतर खर्च: ${money(r.other)}")
            Divider()
            Text("निव्वळ नफा / तोटा: ${money(r.profit)}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
                color = if (r.profit >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
        }
    }
}


private fun monthRecordsForExport(records: List<DayRecord>, date: String) =
    records.filter { it.date.take(7) == date.take(7) }.sortedBy { it.date }

private fun recordsJson(records: List<DayRecord>): String {
    val arr = JSONArray()
    records.forEach { r ->
        val o = JSONObject().put("date", r.date).put("other", r.other)
        val ia = JSONArray()
        r.items.forEach { ia.put(JSONObject().put("name", it.name).put("qty", it.qty).put("price", it.price).put("unitCost", it.unitCost)) }
        o.put("items", ia)
        arr.put(o)
    }
    return arr.toString(2)
}

private fun recordsCsv(records: List<DayRecord>): String {
    val out = StringBuilder()
    out.append("Date,Product,Plates,Rate,Sales,PerPlateCost,ProductCost,OtherExpenses,TotalExpenses,NetProfit\\n")
    records.forEach { r ->
        r.items.forEach { item ->
            out.append(listOf(r.date, item.name, item.qty.toString(), item.price.toString(),
                item.sales.toString(), item.unitCost.toString(), item.cost.toString(),
                r.other.toString(), r.expenses.toString(), r.profit.toString()).joinToString(",") { csvCell(it) })
            out.append("\\n")
        }
    }
    return out.toString()
}
private fun csvCell(value: String): String = "\\"" + value.replace("\\"", "\\"\\"") + "\\""
