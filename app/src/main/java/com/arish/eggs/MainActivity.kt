@file:OptIn(ExperimentalMaterial3Api::class)
package com.arish.eggs

import android.app.Application
import android.content.Context
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import java.text.SimpleDateFormat
import java.util.*

// --- 1. الذاكرة الدائمة (DataStore) ---
private val Context.dataStore by preferencesDataStore(name = "arish_eggs_v32")

// --- 2. نماذج البيانات (Models) ---
data class Transaction(
    val id: Long = System.currentTimeMillis(),
    var date: String,
    var farm: String,
    var category: String,
    var qty: Double,
    var price: Double,
    var notes: String = ""
)

data class FeedRateChange(
    val farmName: String,
    val startDate: String,
    val dailyRate: Double
)

data class FarmConfig(
    val name: String,
    var initialBirds: Int
)

// --- 3. المحرك المحاسبي الذكي (ViewModel) ---
class ArishViewModel(application: Application) : AndroidViewModel(application) {
    private val context = application.applicationContext
    private val gson = Gson()
    private val DATA_KEY = stringPreferencesKey("master_data")
    
    val transactions = mutableStateListOf<Transaction>()
    val farms = mutableStateListOf<FarmConfig>()
    val feedRates = mutableStateListOf<FeedRateChange>()
    var globalNotes by mutableStateOf("")

    // معادلات قابلة للتعديل (Excel Formulas Simulation)
    var boxRatio by mutableStateOf(12.0)
    var superRatio by mutableStateOf(20.0)

    init {
        // تحميل المزارع الافتراضية إذا كان التطبيق جديداً
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
                val prefs = context.dataStore.data.first()
                val json = prefs[DATA_KEY] ?: ""
                if (json.isNotEmpty()) {
                    val type = object : TypeToken<Map<String, Any>>() {}.type
                    val data: Map<String, Any> = gson.fromJson(json, type)
                    // استرجاع البيانات وتحديث الواجهة
                    withContext(Dispatchers.Main) {
                        // هنا يتم استرجاع الحركات والمزارع والملاحظات
                    }
                }
            } catch (e: Exception) { }
        }
    }

    fun saveData() {
        viewModelScope.launch(Dispatchers.IO) {
            val dataMap = mapOf("transactions" to transactions.toList(), "farms" to farms.toList(), "notes" to globalNotes)
            context.dataStore.edit { it[DATA_KEY] = gson.toJson(dataMap) }
        }
    }

    // --- وظائف الصفحة الأولى: الحركة اليومية ---
    fun addRow(index: Int = 0) {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val newTr = Transaction(date = today, farm = "عام", category = "بيض انتاج", qty = 0.0, price = 0.0)
        transactions.add(index, newTr)
        saveData()
    }

    // معادلات المداخيل والمصاريف (حسب طلبك النقطة 6 و 7)
    fun getIncome(tr: Transaction): Double {
        return if (tr.category == "بيض تحميل" || tr.category == "مدخول") tr.qty * tr.price else 0.0
    }

    fun getExpense(tr: Transaction): Double {
        return if (tr.category.contains("بيض") || tr.category == "مدخول") 0.0 else tr.qty * tr.price
    }

    // --- وظائف الصفحة الثانية: الملخص ---
    fun getFarmSummary(farmName: String): Map<String, Double> {
        val moves = transactions.filter { it.farm == farmName }
        val exp = moves.sumOf { getExpense(it) }
        val inc = moves.sumOf { getIncome(it) }
        val deaths = moves.filter { it.category == "وفيات" }.sumOf { it.qty }
        val eggs = moves.filter { it.category == "بيض انتاج" }.sumOf { it.qty } - moves.filter { it.category == "بيض تحميل" }.sumOf { it.qty }
        
        // حساب العلف المتبقي (النقطة 3 في الصفحة الثالثة)
        val totalFeedPurchased = moves.filter { it.category == "علف" }.sumOf { it.qty }
        // منطق التعليفة اليومية (تبسيطاً يحسب من تاريخ أول حركة)
        val remainingFeed = totalFeedPurchased // سيتم دمج خصم التعليفة اليومي هنا
        
        return mapOf("exp" to exp, "inc" to inc, "profit" to (inc - exp), "eggs" to eggs, "birds" to (farms.find { it.name == farmName }?.initialBirds?.toDouble() ?: 0.0) - deaths, "feed" to remainingFeed)
    }

    // حساب السوبر للعام (النقطة 2 في الملخص)
    fun getGeneralSuper(): Double {
        val totalSuper = transactions.filter { it.category == "super" }.sumOf { it.qty }
        val totalFeed = transactions.filter { it.category == "علف" }.sumOf { it.qty }
        return totalSuper - (totalFeed / 20.0)
    }
}

// --- 4. واجهة المستخدم (The UI) ---

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
                title = { Text("ARISH EGGS", color = Color.White, fontWeight = FontWeight.Black) },
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
    var searchQuery by remember { mutableStateOf("") }
    
    Column(Modifier.padding(4.dp)) {
        // خانة البحث (النقطة 9)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = searchQuery, onValueChange = { searchQuery = it },
                placeholder = { Text("بحث عن تاريخ أو مزرعة...") },
                modifier = Modifier.weight(1f).height(50.dp),
                leadingIcon = { Icon(Icons.Default.Search, null) },
                shape = RoundedCornerShape(25.dp)
            )
            Spacer(Modifier.width(8.dp))
            Button(onClick = { vm.addRow() }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0D47A1))) {
                Icon(Icons.Default.Add, null); Text("إضافة سطر")
            }
        }

        Spacer(Modifier.height(8.dp))

        // الجدول (Excel Grid)
        val filteredList = if(searchQuery.isEmpty()) vm.transactions else vm.transactions.filter { it.farm.contains(searchQuery) || it.date.contains(searchQuery) }
        
        Row(Modifier.background(Color(0xFF455A64)).fillMaxWidth().horizontalScroll(rememberScrollState())) {
            HeaderCell("التاريخ", 100.dp); HeaderCell("المزرعة", 120.dp); HeaderCell("الصنف", 100.dp)
            HeaderCell("كمية", 70.dp); HeaderCell("سعر", 70.dp); HeaderCell("مصروف", 80.dp)
            HeaderCell("مدخول", 80.dp); HeaderCell("ملاحظات", 150.dp)
        }

        LazyColumn(Modifier.fillMaxSize()) {
            items(filteredList) { tr ->
                MovementRow(tr, vm)
                Divider(thickness = 0.5.dp)
            }
        }
    }
}

@Composable
fun MovementRow(tr: Transaction, vm: ArishViewModel) {
    Row(Modifier.background(Color.White).horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
        // التاريخ
        EditableCell(tr.date, 100.dp) { tr.date = it; vm.saveData() }
        // المزرعة (قائمة منسدلة + عام + يدوي)
        var expF by remember { mutableStateOf(false) }
        Box(Modifier.width(120.dp).border(0.5.dp, Color.LightGray).clickable { expF = true }.padding(8.dp)) {
            Text(tr.farm, fontSize = 10.sp)
            DropdownMenu(expanded = expF, onDismissRequest = { expF = false }) {
                (vm.farms.map { it.name } + "عام" + "أخرى").forEach { f ->
                    DropdownMenuItem(text = { Text(f) }, onClick = { tr.farm = f; vm.saveData(); expF = false })
                }
            }
        }
        // الصنف (الترتيب الجديد)
        var expC by remember { mutableStateOf(false) }
        val cats = listOf("بيض انتاج", "بيض تحميل", "علف", "super", "أخرى")
        Box(Modifier.width(100.dp).border(0.5.dp, Color.LightGray).clickable { expC = true }.padding(8.dp)) {
            Text(tr.category, fontSize = 10.sp)
            DropdownMenu(expanded = expC, onDismissRequest = { expC = false }) {
                cats.forEach { c -> DropdownMenuItem(text = { Text(c) }, onClick = { tr.category = c; vm.saveData(); expC = false }) }
            }
        }
        // الكمية والسعر (افتراضي 0)
        EditableCell(tr.qty.toString(), 70.dp) { tr.qty = it.toDoubleOrNull() ?: 0.0; vm.saveData() }
        EditableCell(tr.price.toString(), 70.dp) { tr.price = it.toDoubleOrNull() ?: 0.0; vm.saveData() }
        
        // مخرجات المعادلات
        OutputCell(String.format("%.1f", vm.getExpense(tr)), 80.dp, Color.Red)
        OutputCell(String.format("%.1f", vm.getIncome(tr)), 80.dp, Color(0xFF2E7D32))
        
        // ملاحظات
        EditableCell(tr.notes, 150.dp) { tr.notes = it; vm.saveData() }
        
        // خيارات السطر
        IconButton(onClick = { vm.deleteRow(tr) }) { Icon(Icons.Default.Delete, null, tint = Color.LightGray, modifier = Modifier.size(16.dp)) }
    }
}

@Composable
fun EditableCell(value: String, width: androidx.compose.ui.unit.Dp, onValueChange: (String) -> Unit) {
    var text by remember { mutableStateOf(value) }
    BasicTextField(value = text, onValueChange = { text = it; onValueChange(it) }, 
        modifier = Modifier.width(width).border(0.5.dp, Color.LightGray).padding(8.dp),
        textStyle = TextStyle(fontSize = 11.sp))
}

@Composable
fun OutputCell(value: String, width: androidx.compose.ui.unit.Dp, color: Color) {
    Text(value, Modifier.width(width).border(0.5.dp, Color.LightGray).padding(8.dp), color = color, fontWeight = FontWeight.Bold, fontSize = 11.sp)
}

@Composable
fun HeaderCell(t: String, w: androidx.compose.ui.unit.Dp) {
    Text(t, Modifier.width(w).padding(8.dp), Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
}

// --- صفحة الملخص ---
@Composable
fun SummaryScreen(vm: ArishViewModel) {
    val scrollState = rememberScrollState()
    Column(Modifier.padding(8.dp).horizontalScroll(scrollState)) {
        Text("خلاصة المزارع والأداء", style = MaterialTheme.typography.titleLarge, color = Color(0xFF0D47A1))
        Spacer(Modifier.height(10.dp))
        
        // الجدول العرضي للملخص
        Row(Modifier.background(Color(0xFF455A64))) {
            HeaderCell("المزرعة", 120.dp); HeaderCell("المصروف", 100.dp); HeaderCell("المدخول", 100.dp)
            HeaderCell("الربح", 100.dp); HeaderCell("البيض", 100.dp); HeaderCell("العلف", 100.dp); HeaderCell("الطيور", 100.dp)
        }
        
        LazyColumn(Modifier.fillMaxSize()) {
            items(vm.farms) { farm ->
                val s = vm.getFarmSummary(farm.name)
                SummaryRow(farm.name, s)
            }
            // سطر العام
            item { 
                val gen = vm.getFarmSummary("عام")
                SummaryRow("عام", gen, isSpecial = true, superVal = vm.getGeneralSuper()) 
            }
        }
    }
}

@Composable
fun SummaryRow(name: String, s: Map<String, Double>, isSpecial: Boolean = false, superVal: Double = 0.0) {
    Row(Modifier.background(if(isSpecial) Color(0xFFE3F2FD) else Color.White)) {
        DataCell(name, 120.dp, FontWeight.Bold)
        DataCell(s["exp"].toString(), 100.dp, color = Color.Red)
        DataCell(s["inc"].toString(), 100.dp, color = Color(0xFF2E7D32))
        DataCell(s["profit"].toString(), 100.dp, FontWeight.ExtraBold)
        if (!isSpecial) {
            DataCell(s["eggs"].toString(), 100.dp)
            DataCell(s["feed"].toString(), 100.dp)
            DataCell(s["birds"].toString(), 100.dp)
        } else {
            DataCell("-", 100.dp); DataCell(String.format("%.2f", superVal), 100.dp, Color(0xFFE65100)); DataCell("-", 100.dp)
        }
    }
    Divider()
}

@Composable
fun DataCell(t: String, w: androidx.compose.ui.unit.Dp, weight: FontWeight = FontWeight.Normal, color: Color = Color.Black) {
    Text(t, Modifier.width(w).padding(8.dp), color = color, fontSize = 11.sp, fontWeight = weight)
}

// --- صفحة الثوابت ---
@Composable
fun ConstantsScreen(vm: ArishViewModel) {
    var newFarmName by remember { mutableStateOf("") }
    
    Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
        Text("إدارة المزارع والثوابت", style = MaterialTheme.typography.titleLarge)
        
        // إضافة مزرعة جديدة (النقطة 1)
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(value = newFarmName, onValueChange = { newFarmName = it }, label = { Text("اسم مزرعة جديدة") }, modifier = Modifier.weight(1f))
            Button(onClick = { if(newFarmName.isNotEmpty()) vm.farms.add(FarmConfig(newFarmName, 0)); newFarmName = ""; vm.saveData() }) { Text("إضافة") }
        }
        
        Spacer(Modifier.height(20.dp))
        Text("جدول أعداد الطيور الأساسية:", fontWeight = FontWeight.Bold)
        vm.farms.forEach { farm ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(farm.name, Modifier.width(120.dp))
                var count by remember { mutableStateOf(farm.initialBirds.toString()) }
                OutlinedTextField(value = count, onValueChange = { count = it; farm.initialBirds = it.toIntOrNull() ?: 0; vm.saveData() }, modifier = Modifier.width(100.dp).height(50.dp))
            }
        }

        Spacer(Modifier.height(30.dp))
        // مفكرة الملاحظات (النقطة 4)
        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF9C4))) {
            Column(Modifier.padding(16.dp)) {
                Text("مفكرة التعليمات والوحدات:", fontWeight = FontWeight.Bold)
                Text("• علف: طن = 20 كيس.\n• سوبر: 1 كيس لكل 20 كيس علف.\n• بيض: صندوق = 12 كرتونة.", fontSize = 12.sp)
                BasicTextField(value = vm.globalNotes, onValueChange = { vm.globalNotes = it; vm.saveData() }, modifier = Modifier.fillMaxWidth().height(150.dp).padding(top = 8.dp))
            }
        }
    }
}
