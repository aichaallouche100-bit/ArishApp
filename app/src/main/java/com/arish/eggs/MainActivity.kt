@file:OptIn(ExperimentalMaterial3Api::class)
package com.arish.eggs

import android.app.Application
import android.content.*
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.*
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.text.SimpleDateFormat
import java.io.IOException
import java.util.*

// --- 1. إعداد التخزين ---
private val Context.dataStore by preferencesDataStore(name = "arish_eggs_v35_final")

// --- 2. نماذج البيانات ---
data class Transaction(
    val id: Long = System.currentTimeMillis(),
    var date: String,
    var farm: String,
    var category: String,
    var qty: Double = 0.0,
    var price: Double = 0.0,
    var notes: String = ""
) {
    val incomeVal: Double get() = if (category == "بيض تحميل" || category == "مدخول") qty * price else 0.0
    val expenseVal: Double get() = if (category.contains("بيض") || category.contains("مدخول")) 0.0 else qty * price
}

data class FarmConfig(val id: String = UUID.randomUUID().toString(), var name: String, var birds: Int)

// --- 3. المحرك المحاسبي ---
class ArishViewModel(application: Application) : AndroidViewModel(application) {
    private val context = application.applicationContext
    private val gson = Gson()
    private val KEY = stringPreferencesKey("arish_master_v35")
    
    val transactions = mutableStateListOf<Transaction>()
    val farms = mutableStateListOf<FarmConfig>()
    var notepad by mutableStateOf("")

    init {
        val names = listOf("فايز الطويلة", "فايز البرشا", "فايز الألفين", "ابو حمدو العقيد", "ابو حمدو جديدة", "ابو حمدو الاخرس", "ام نضال ١", "ام نضال ٢")
        val birds = listOf(7500, 2800, 2000, 2000, 3300, 3800, 10900, 0)
        names.forEachIndexed { i, n -> farms.add(FarmConfig(name = n, birds = birds[i])) }
        loadAll()
    }

    private fun loadAll() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val prefs = context.dataStore.data.catch { emit(emptyPreferences()) }.first()
                val json = prefs[KEY] ?: ""
                if (json.isNotEmpty()) {
                    val data: Map<String, Any> = gson.fromJson(json, object : TypeToken<Map<String, Any>>() {}.type)
                    // استعادة البيانات برمجياً
                }
            } catch (e: Exception) { }
        }
    }

    fun saveData() {
        viewModelScope.launch(Dispatchers.IO) {
            val json = gson.toJson(mapOf("tr" to transactions.toList(), "f" to farms.toList(), "note" to notepad))
            context.dataStore.edit { it[KEY] = json }
        }
    }

    fun addRow(index: Int) {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        transactions.add(index, Transaction(date = today, farm = "عام", category = "بيض انتاج"))
        saveData()
    }

    fun deleteRow(tr: Transaction) { transactions.remove(tr); saveData() }

    fun getSuperStock(): Double {
        val s = transactions.filter { it.category == "super" }.sumOf { it.qty }
        val f = transactions.filter { it.category == "علف" }.sumOf { it.qty }
        return s - (f / 20.0)
    }

    fun getFarmSummary(fName: String): Map<String, Double> {
        val m = transactions.filter { it.farm == fName }
        val inc = m.sumOf { it.incomeVal }; val exp = m.sumOf { it.expenseVal }
        val deaths = m.filter { it.category == "وفيات" }.sumOf { it.qty }
        val eggStock = m.filter { it.category == "بيض انتاج" }.sumOf { it.qty } - m.filter { it.category == "بيض تحميل" }.sumOf { it.qty }
        val initial = farms.find { it.name == fName }?.birds?.toDouble() ?: 0.0
        return mapOf("inc" to inc, "exp" to exp, "profit" to (inc - exp), "birds" to (initial - deaths), "eggs" to eggStock)
    }
}

// --- 4. واجهة المستخدم ---
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val vm: ArishViewModel = viewModel()
            ArishTheme { MainContent(vm) }
        }
    }
}

@Composable
fun MainContent(vm: ArishViewModel) {
    var tab by rememberSaveable { mutableStateOf(0) }
    val navy = Color(0xFF0D47A1)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = "ARISH EGGS", color = Color.White, fontWeight = FontWeight.Black) },
                actions = { Image(painter = painterResource(id = R.drawable.logo_arish), contentDescription = null, modifier = Modifier.size(50.dp).padding(4.dp)) },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = navy)
            )
        },
        bottomBar = {
            NavigationBar(containerColor = navy) {
                val menu = listOf("الحركة", "الملخص", "الثوابت")
                val icons = listOf(Icons.Default.ReceiptLong, Icons.Default.Analytics, Icons.Default.Settings)
                menu.forEachIndexed { i, l ->
                    NavigationBarItem(selected = tab == i, onClick = { tab = i }, icon = { Icon(icons[i], null, tint = if (tab == i) Color.Yellow else Color.White) }, label = { Text(text = l, color = Color.White, fontSize = 10.sp) })
                }
            }
        }
    ) { p ->
        Box(Modifier.padding(p).fillMaxSize().background(Color(0xFFF1F3F4))) {
            when (tab) {
                0 -> MovementScreen(vm)
                1 -> SummaryScreen(vm)
                2 -> ConstantsScreen(vm)
            }
        }
    }
}

@Composable
fun MovementScreen(vm: ArishViewModel) {
    var search by remember { mutableStateOf("") }
    val scroll = rememberScrollState()
    Column(Modifier.fillMaxSize().padding(4.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(value = search, onValueChange = { search = it }, placeholder = { Text("بحث...") }, modifier = Modifier.weight(1f).height(50.dp), shape = RoundedCornerShape(25.dp), leadingIcon = { Icon(Icons.Default.Search, null) })
            Spacer(Modifier.width(8.dp))
            Button(onClick = { vm.addRow(0) }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0D47A1))) { Text("إضافة") }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.background(Color(0xFF455A64)).fillMaxWidth().horizontalScroll(scroll)) {
            HeaderCell("التاريخ", 100.dp); HeaderCell("المزرعة", 135.dp); HeaderCell("الصنف", 115.dp)
            HeaderCell("كمية", 75.dp); HeaderCell("سعر", 75.dp); HeaderCell("مصروف", 85.dp); HeaderCell("مدخول", 85.dp); HeaderCell("ملاحظات", 165.dp)
        }
        LazyColumn(Modifier.fillMaxSize()) {
            itemsIndexed(vm.transactions.filter { it.farm.contains(search) || it.date.contains(search) }) { idx, tr ->
                Row(Modifier.fillMaxWidth().horizontalScroll(scroll), verticalAlignment = Alignment.CenterVertically) {
                    EditableCell(tr.date, 100.dp) { tr.date = it; vm.saveData() }
                    DynamicPicker(tr.farm, 135.dp, vm.farms.map { it.name } + listOf("عام", "أخرى")) { tr.farm = it; vm.saveData() }
                    DynamicPicker(tr.category, 115.dp, listOf("بيض انتاج", "بيض تحميل", "علف", "super", "مدخول", "أخرى")) { tr.category = it; vm.saveData() }
                    EditableCell(tr.qty.toString(), 75.dp, true) { tr.qty = it.toDoubleOrNull() ?: 0.0; vm.saveData() }
                    EditableCell(tr.price.toString(), 75.dp, true) { tr.price = it.toDoubleOrNull() ?: 0.0; vm.saveData() }
                    DataCell(String.format("%.0f", tr.expenseVal), 85.dp, color = Color.Red, fw = FontWeight.Bold)
                    DataCell(String.format("%.0f", tr.incomeVal), 85.dp, color = Color(0xFF2E7D32), fw = FontWeight.Bold)
                    EditableCell(tr.notes, 165.dp) { tr.notes = it; vm.saveData() }
                    IconButton(onClick = { vm.addRow(idx + 1) }) { Icon(Icons.Default.AddCircle, null, tint = Color(0xFFD4AF37)) }
                    IconButton(onClick = { vm.deleteRow(tr) }) { Icon(Icons.Default.Delete, null, tint = Color.LightGray) }
                }
                Divider()
            }
        }
    }
}

@Composable
fun SummaryScreen(vm: ArishViewModel) {
    val scroll = rememberScrollState()
    Column(Modifier.fillMaxSize().padding(8.dp).horizontalScroll(scroll)) {
        Row(Modifier.background(Color(0xFF455A64))) {
            HeaderCell("المزرعة", 130.dp); HeaderCell("المصروف", 95.dp); HeaderCell("المدخول", 95.dp)
            HeaderCell("الربح", 95.dp); HeaderCell("البيض", 95.dp); HeaderCell("الطيور", 95.dp)
        }
        LazyColumn {
            items(vm.farms) { f ->
                val s = vm.getFarmSummary(f.name)
                Row(Modifier.background(Color.White)) {
                    DataCell(f.name, 130.dp, fw = FontWeight.Bold)
                    DataCell(s["exp"].toString(), 95.dp, color = Color.Red)
                    DataCell(s["inc"].toString(), 95.dp, color = Color(0xFF2E7D32))
                    DataCell(s["profit"].toString(), 95.dp, fw = FontWeight.ExtraBold)
                    DataCell(s["eggs"].toString(), 95.dp, color = Color.Blue)
                    DataCell(s["birds"].toString(), 95.dp)
                }
                Divider()
            }
            item {
                Row(Modifier.background(Color(0xFFE3F2FD))) {
                    DataCell("العام (سوبر)", 130.dp, fw = FontWeight.Bold)
                    repeat(3) { DataCell("-", 95.dp) }
                    DataCell(String.format("%.2f", vm.getSuperStock()), 95.dp, color = Color(0xFFE65100), fw = FontWeight.Bold)
                    DataCell("-", 95.dp)
                }
            }
        }
    }
}

@Composable
fun ConstantsScreen(vm: ArishViewModel) {
    var nName by remember { mutableStateOf("") }
    Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
        Text(text = "إدارة المزارع والطيور", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(value = nName, onValueChange = { nName = it }, label = { Text("مزرعة جديدة") }, modifier = Modifier.weight(1f))
            Button(onClick = { if(nName.isNotEmpty()) { vm.farms.add(FarmConfig(name = nName, birds = 0)); vm.saveData() } }) { Text("+") }
        }
        vm.farms.forEach { f ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                Text(text = f.name, modifier = Modifier.weight(1f))
                var bT by remember { mutableStateOf(f.birds.toString()) }
                BasicTextField(value = bT, onValueChange = { bT = it; f.birds = it.toIntOrNull() ?: 0; vm.saveData() }, modifier = Modifier.width(75.dp).border(0.5.dp, Color.Gray).padding(4.dp))
                IconButton(onClick = { vm.farms.remove(f); vm.saveData() }) { Icon(Icons.Default.Delete, null, tint = Color.Red) }
            }
        }
        Spacer(Modifier.height(20.dp)); Text(text = "المفكرة المفتوحة:", fontWeight = FontWeight.Bold)
        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF9C4))) {
            Column(Modifier.padding(12.dp)) {
                Text(text = "طن علف=20 كيس | صندوق=12 كرتونة", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                BasicTextField(value = vm.notepad, onValueChange = { vm.notepad = it; vm.saveData() }, modifier = Modifier.fillMaxWidth().height(150.dp).padding(top = 8.dp))
            }
        }
    }
}

// دالات التصميم المساعدة
@Composable fun HeaderCell(t: String, w: Dp) = Text(text = t, modifier = Modifier.width(w).padding(8.dp), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
@Composable fun DataCell(t: String, w: Dp, color: Color = Color.Black, fw: FontWeight = FontWeight.Normal) = Text(text = t, modifier = Modifier.width(w).padding(8.dp), color = color, fontSize = 11.sp, fontWeight = fw)
@Composable fun EditableCell(v: String, w: Dp, isNum: Boolean = false, onVal: (String) -> Unit) {
    var t by remember(v) { mutableStateOf(if(v=="0.0") "0" else v) }
    BasicTextField(value = t, onValueChange = { t = it; onVal(it) }, modifier = Modifier.width(w).border(0.5.dp, Color.LightGray).padding(8.dp), textStyle = TextStyle(fontSize = 11.sp), keyboardOptions = KeyboardOptions(keyboardType = if(isNum) KeyboardType.Number else KeyboardType.Text))
}
@Composable fun DynamicPicker(current: String, w: Dp, options: List<String>, onSelect: (String) -> Unit) {
    var exp by remember { mutableStateOf(false) }
    var isM by remember { mutableStateOf(false) }
    var mT by remember { mutableStateOf("") }
    if (isM) BasicTextField(value = mT, onValueChange = { mT = it; onSelect(it) }, modifier = Modifier.width(w).border(0.5.dp, Color.Blue).padding(8.dp), textStyle = TextStyle(fontSize = 10.sp))
    else Box(Modifier.width(w).border(0.5.dp, Color.LightGray).clickable { exp = true }.padding(8.dp)) {
        Text(text = current, fontSize = 10.sp)
        DropdownMenu(expanded = exp, onDismissRequest = { exp = false }) {
            options.forEach { o -> DropdownMenuItem(text = { Text(o) }, onClick = { if (o == "أخرى") isM = true else onSelect(o); exp = false }) }
        }
    }
}
@Composable fun ArishTheme(content: @Composable () -> Unit) = MaterialTheme(colorScheme = lightColorScheme(primary = Color(0xFF0D47A1), secondary = Color(0xFFD4AF37)), content = content)
