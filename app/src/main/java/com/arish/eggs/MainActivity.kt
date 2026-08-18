@file:OptIn(ExperimentalMaterial3Api::class)
package com.arish.eggs

import android.app.Application
import android.content.Context
import android.os.Bundle
import android.content.Intent
import android.net.Uri
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import java.text.SimpleDateFormat
import java.io.IOException
import java.util.*

// --- 1. إعداد مخزن البيانات (DataStore) لضمان الحفظ الدائم ---
private val Context.dataStore by preferencesDataStore(name = "arish_eggs_v32_final")

// --- 2. نموذج البيانات (Models) ---
data class Transaction(
    val id: Long = System.currentTimeMillis(),
    var date: String,
    var farm: String,
    var category: String,
    var qty: Double,
    var price: Double,
    var notes: String = ""
) {
    // معادلة المدخول: =IF(OR(C3="بيض تحميل"; C3="مدخول"); D3*E3; 0)
    val incomeVal: Double get() = if (category == "بيض تحميل" || category == "مدخول") qty * price else 0.0
    // معادلة المصروف: =IF(OR(ISNUMBER(SEARCH("بيض"; C3)); ISNUMBER(SEARCH("مدخول"; C3))); 0; D3*E3)
    val expenseVal: Double get() = if (category.contains("بيض") || category == "مدخول") 0.0 else qty * price
}

data class FarmConfig(val name: String, var initialBirds: Int)

// --- 3. المحرك المحاسبي والمنطقي (ViewModel) ---
class ArishViewModel(application: Application) : AndroidViewModel(application) {
    private val context = application.applicationContext
    private val gson = Gson()
    private val DATA_KEY = stringPreferencesKey("arish_data_master")
    
    val transactions = mutableStateListOf<Transaction>()
    val farms = mutableStateListOf<FarmConfig>()

    init {
        // تحميل المزارع الـ 8 الأساسية
        farms.addAll(listOf(
            FarmConfig("فايز الطويلة", 7500), FarmConfig("فايز البرشا", 2800),
            FarmConfig("فايز الألفين", 2000), FarmConfig("ابو حمدو العقيد", 2000),
            FarmConfig("ابو حمدو جديدة", 3300), FarmConfig("ابو حمدو الاخرس", 3800),
            FarmConfig("ام نضال ١", 10900), FarmConfig("ام نضال ٢", 0)
        ))
        loadAllData()
    }

    private fun loadAllData() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val prefs = context.dataStore.data.catch { if (it is IOException) emit(emptyPreferences()) else throw it }.first()
                val json = prefs[DATA_KEY] ?: ""
                if (json.isNotEmpty()) {
                    val list: List<Transaction> = gson.fromJson(json, object : TypeToken<List<Transaction>>() {}.type)
                    withContext(Dispatchers.Main) {
                        transactions.clear()
                        transactions.addAll(list)
                    }
                }
            } catch (e: Exception) { }
        }
    }

    fun saveData() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val json = gson.toJson(transactions.toList())
                context.dataStore.edit { it[DATA_KEY] = json }
            } catch (e: Exception) { }
        }
    }

    fun addRow() {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        transactions.add(0, Transaction(date = today, farm = "عام", category = "بيض انتاج", qty = 0.0, price = 0.0))
        saveData()
    }

    fun deleteRow(tr: Transaction) {
        transactions.remove(tr)
        saveData()
    }

    fun getSuperStock(): Double = 151.55 - (transactions.filter { it.category == "علف" }.sumOf { it.qty } / 20.0)

    fun getFarmSummary(farmName: String): Map<String, Double> {
        val moves = transactions.filter { it.farm == farmName }
        val exp = moves.sumOf { it.expenseVal }
        val inc = moves.sumOf { it.incomeVal }
        val deaths = moves.filter { it.category == "وفيات" }.sumOf { it.qty }
        val eggs = moves.filter { it.category == "بيض انتاج" }.sumOf { it.qty } - moves.filter { it.category == "بيض تحميل" }.sumOf { it.qty }
        val initial = farms.find { it.name == farmName }?.initialBirds?.toDouble() ?: 0.0
        return mapOf("exp" to exp, "inc" to inc, "profit" to (inc - exp), "eggs" to eggs, "birds" to (initial - deaths))
    }
}

// --- 4. واجهة المستخدم (UI) ---

@Composable
fun ArishTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = lightColorScheme(primary = Color(0xFF0D47A1), secondary = Color(0xFFD4AF37)), content = content)
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val vm: ArishViewModel = viewModel()
            ArishTheme { MainNavigation(vm) }
        }
    }
}

@Composable
fun MainNavigation(vm: ArishViewModel) {
    var tab by rememberSaveable { mutableStateOf(0) }
    val navy = Color(0xFF0D47A1)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = "ARISH EGGS", color = Color.White, fontWeight = FontWeight.Black) },
                actions = { 
                    // اللوغو في الزاوية اليمنى
                    Image(painter = painterResource(id = R.drawable.logo_arish), contentDescription = null, modifier = Modifier.size(50.dp).padding(4.dp)) 
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = navy)
            )
        },
        bottomBar = {
            NavigationBar(containerColor = navy) {
                val menu = listOf("الحركة اليومية", "الملخص", "الثوابت")
                val icons = listOf(Icons.Default.ReceiptLong, Icons.Default.Analytics, Icons.Default.Settings)
                menu.forEachIndexed { i, label ->
                    NavigationBarItem(selected = tab == i, onClick = { tab = i }, icon = { Icon(icons[i], null, tint = if(tab==i) Color.Yellow else Color.White) }, label = { Text(label, color = Color.White, fontSize = 10.sp) })
                }
            }
        }
    ) { p ->
        Box(Modifier.padding(p).fillMaxSize().background(Color(0xFFF1F3F4))) {
            when (tab) {
                0 -> DailyMovementScreen(vm)
                1 -> SummaryScreen(vm)
                2 -> ConstantsScreen(vm)
            }
        }
    }
}

@Composable
fun DailyMovementScreen(vm: ArishViewModel) {
    var search by remember { mutableStateOf("") }
    Column(Modifier.padding(4.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(value = search, onValueChange = { search = it }, placeholder = { Text("بحث...") }, modifier = Modifier.weight(1f).height(50.dp), shape = RoundedCornerShape(25.dp))
            Spacer(Modifier.width(8.dp))
            Button(onClick = { vm.addRow() }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0D47A1))) {
                Icon(Icons.Default.Add, null); Text("إضافة")
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.background(Color(0xFF455A64)).fillMaxWidth().horizontalScroll(rememberScrollState())) {
            HeaderCell("التاريخ", 100.dp); HeaderCell("المزرعة", 120.dp); HeaderCell("الصنف", 100.dp)
            HeaderCell("كمية", 70.dp); HeaderCell("سعر", 70.dp); HeaderCell("مصروف", 80.dp); HeaderCell("مدخول", 80.dp)
        }
        LazyColumn(Modifier.fillMaxSize()) {
            items(vm.transactions.filter { it.farm.contains(search) || it.date.contains(search) }) { tr ->
                Row(Modifier.background(Color.White).horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
                    EditableCell(tr.date, 100.dp) { tr.date = it; vm.saveData() }
                    FarmPicker(tr, vm)
                    CategoryPicker(tr, vm)
                    EditableCell(tr.qty.toString(), 70.dp) { tr.qty = it.toDoubleOrNull() ?: 0.0; vm.saveData() }
                    EditableCell(tr.price.toString(), 70.dp) { tr.price = it.toDoubleOrNull() ?: 0.0; vm.saveData() }
                    OutputCell(String.format("%.1f", tr.expenseVal), 80.dp, Color.Red)
                    OutputCell(String.format("%.1f", tr.incomeVal), 80.dp, Color(0xFF2E7D32))
                    IconButton(onClick = { vm.deleteRow(tr) }) { Icon(Icons.Default.Delete, null, tint = Color.LightGray) }
                }
                HorizontalDivider(color = Color.LightGray, thickness = 0.5.dp)
            }
        }
    }
}

@Composable
fun FarmPicker(tr: Transaction, vm: ArishViewModel) {
    var exp by remember { mutableStateOf(false) }
    Box(Modifier.width(120.dp).border(0.5.dp, Color.LightGray).clickable { exp = true }.padding(8.dp)) {
        Text(text = tr.farm, fontSize = 10.sp) // تم التصحيح هنا
        DropdownMenu(expanded = exp, onDismissRequest = { exp = false }) {
            (vm.farms.map { it.name } + "عام" + "أخرى").forEach { f ->
                DropdownMenuItem(text = { Text(f) }, onClick = { tr.farm = f; vm.saveData(); exp = false })
            }
        }
    }
}

@Composable
fun CategoryPicker(tr: Transaction, vm: ArishViewModel) {
    var exp by remember { mutableStateOf(false) }
    val cats = listOf("بيض انتاج", "بيض تحميل", "علف", "super", "أخرى")
    Box(Modifier.width(100.dp).border(0.5.dp, Color.LightGray).clickable { exp = true }.padding(8.dp)) {
        Text(text = tr.category, fontSize = 10.sp) // تم التصحيح هنا
        DropdownMenu(expanded = exp, onDismissRequest = { exp = false }) {
            cats.forEach { c -> DropdownMenuItem(text = { Text(c) }, onClick = { tr.category = c; vm.saveData(); exp = false }) }
        }
    }
}

@Composable
fun SummaryScreen(vm: ArishViewModel) {
    Column(Modifier.padding(8.dp).horizontalScroll(rememberScrollState())) {
        Row(Modifier.background(Color(0xFF455A64))) {
            HeaderCell("المزرعة", 120.dp); HeaderCell("المصروف", 100.dp); HeaderCell("المدخول", 100.dp)
            HeaderCell("الربح", 100.dp); HeaderCell("البيض", 100.dp); HeaderCell("الطيور", 100.dp)
        }
        LazyColumn {
            items(vm.farms) { f ->
                val s = vm.getFarmSummary(f.name)
                Row(Modifier.background(Color.White)) {
                    DataCell(f.name, 120.dp, FontWeight.Bold)
                    DataCell(s["exp"].toString(), 100.dp, Color.Red)
                    DataCell(s["inc"].toString(), 100.dp, Color(0xFF2E7D32))
                    DataCell(s["profit"].toString(), 100.dp, FontWeight.ExtraBold)
                    DataCell(s["eggs"].toString(), 100.dp, Color.Blue)
                    DataCell(s["birds"].toString(), 100.dp)
                }
                HorizontalDivider()
            }
            item {
                Row(Modifier.background(Color(0xFFE3F2FD))) {
                    DataCell("العام (سوبر)", 120.dp, FontWeight.Bold)
                    DataCell("-", 100.dp); DataCell("-", 100.dp); DataCell("-", 100.dp); DataCell("-", 100.dp)
                    DataCell(String.format("%.2f", vm.getSuperStock()), 100.dp, Color(0xFFE65100), FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun ConstantsScreen(vm: ArishViewModel) {
    Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
        Text("إدارة المزارع والطيور", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        vm.farms.forEach { f ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                Text(f.name, Modifier.width(120.dp))
                var count by remember { mutableStateOf(f.initialBirds.toString()) }
                OutlinedTextField(value = count, onValueChange = { count = it; f.initialBirds = it.toIntOrNull() ?: 0; vm.saveData() }, modifier = Modifier.width(100.dp).height(50.dp))
            }
        }
        Spacer(Modifier.height(20.dp))
        Text("ملاحظات الوحدات:", fontWeight = FontWeight.Bold)
        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF9C4)), modifier = Modifier.fillMaxWidth()) {
            Text("• طن علف = 20 كيس.\n• 1 كيس سوبر لكل 20 كيس علف.\n• صندوق = 12 كرتونة.", modifier = Modifier.padding(16.dp))
        }
    }
}

// دالات مساعدة للتصميم
@Composable fun HeaderCell(t: String, w: androidx.compose.ui.unit.Dp) = Text(t, Modifier.width(w).padding(8.dp), Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
@Composable fun EditableCell(v: String, w: androidx.compose.ui.unit.Dp, onVal: (String) -> Unit) {
    var t by remember { mutableStateOf(v) }
    BasicTextField(value = t, onValueChange = { t = it; onVal(it) }, modifier = Modifier.width(w).border(0.5.dp, Color.LightGray).padding(8.dp), textStyle = TextStyle(fontSize = 11.sp))
}
@Composable fun OutputCell(v: String, w: androidx.compose.ui.unit.Dp, c: Color) = Text(text = v, modifier = Modifier.width(w).border(0.5.dp, Color.LightGray).padding(8.dp), color = c, fontSize = 11.sp, fontWeight = FontWeight.Bold)
@Composable fun DataCell(t: String, w: androidx.compose.ui.unit.Dp, color: Color = Color.Black, fw: FontWeight = FontWeight.Normal) = Text(text = t, modifier = Modifier.width(w).padding(8.dp), color = color, fontSize = 11.sp, fontWeight = fw)
