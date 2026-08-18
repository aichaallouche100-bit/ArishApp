@file:OptIn(ExperimentalMaterial3Api::class)
package com.arish.eggs

import android.app.Application
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

// --- 1. التخزين ---
private val Context.dataStore by preferencesDataStore(name = "arish_eggs_v2")

// --- 2. النماذج (Models) ---
data class Transaction(
    val id: Long = System.currentTimeMillis() + (0..1000).random(),
    var date: String = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()),
    var farm: String = "عام",
    var category: String = "بيض انتاج",
    var qty: Double = 0.0,
    var price: Double = 0.0,
    var notes: String = ""
) {
    // معادلة المصاريف (Excel: IF(OR(SEARCH("بيض",C), SEARCH("مدخول",C)), 0, Qty*Price))
    val expenses: Double get() {
        return if (category.contains("بيض") || category == "مدخول") 0.0 else qty * price
    }

    // معادلة المداخيل (Excel: IF(OR(C="بيض تحميل", C="مدخول"), Qty*Price, 0))
    val income: Double get() {
        return if (category == "بيض تحميل" || category == "مدخول") qty * price else 0.0
    }
}

// نموذج لجدول التعليفة في الثوابت
data class FeedAdjustment(
    var date: String,
    var rate: Double // قيمة التعليفة/اليوم
)

// --- 3. المحرك الإداري (ViewModel) ---
class ArishViewModel(application: Application) : AndroidViewModel(application) {
    private val context = application.applicationContext
    private val gson = Gson()
    private val KEY_TRANS = stringPreferencesKey("transactions")
    private val KEY_FARMS = stringPreferencesKey("farms_list")
    private val KEY_BIRDS = stringPreferencesKey("birds_count")

    val transactions = mutableStateListOf<Transaction>()
    var farmsList = mutableStateListOf("فايز الطويلة", "فايز البرشا", "فايز الألفين", "ابو حمدو العقيد", "ابو حمدو جديدة", "ابو حمدو الاخرس", "ام نضال ١", "ام نضال ٢")
    var farmInitialBirds = mutableStateMapOf<String, Int>()
    
    var searchQuery by mutableStateOf("")

    init {
        // قيم افتراضية لأعداد الطيور
        val defaults = mapOf("فايز الطويلة" to 7500, "فايز البرشا" to 2800, "فايز الألفين" to 2000, "ابو حمدو العقيد" to 2000, "ابو حمدو جديدة" to 3300, "ابو حمدو الاخرس" to 3800, "ام نضال ١" to 10900)
        defaults.forEach { (k, v) -> farmInitialBirds[k] = v }
        loadAllData()
    }

    private fun loadAllData() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val prefs = context.dataStore.data.first()
                prefs[KEY_TRANS]?.let { json ->
                    val list: List<Transaction> = gson.fromJson(json, object : TypeToken<List<Transaction>>() {}.type)
                    launch(Dispatchers.Main) { transactions.clear(); transactions.addAll(list) }
                }
                prefs[KEY_FARMS]?.let { json ->
                    val list: List<String> = gson.fromJson(json, object : TypeToken<List<String>>() {}.type)
                    launch(Dispatchers.Main) { farmsList.clear(); farmsList.addAll(list) }
                }
            } catch (e: Exception) {}
        }
    }

    fun saveData() {
        viewModelScope.launch(Dispatchers.IO) {
            context.dataStore.edit {
                it[KEY_TRANS] = gson.toJson(transactions.toList())
                it[KEY_FARMS] = gson.toJson(farmsList.toList())
            }
        }
    }

    fun addRow(index: Int = 0) {
        transactions.add(index, Transaction())
        saveData()
    }

    fun deleteRow(tr: Transaction) {
        transactions.remove(tr)
        saveData()
    }

    // حسابات الملخص
    fun getFarmStats(f: String): FarmSummary {
        val filtered = if(f == "المجموع") transactions else if(f == "عام") transactions.filter { it.farm == "عام" } else transactions.filter { it.farm == f }
        
        val totalExp = filtered.sumOf { it.expenses }
        val totalInc = filtered.sumOf { it.income }
        val prod = filtered.filter { it.category == "بيض انتاج" }.sumOf { it.qty }
        val load = filtered.filter { it.category == "بيض تحميل" }.sumOf { it.qty }
        val deaths = filtered.filter { it.category == "وفيات" }.sumOf { it.qty }.toInt()
        
        // حساب السوبر (فقط للسطر "عام" أو "المجموع" بناءً على طلبك)
        var superStock = 0.0
        if (f == "عام" || f == "المجموع") {
            val totalSuper = transactions.filter { it.category == "super" }.sumOf { it.qty }
            val totalFeed = transactions.filter { it.category == "علف" }.sumOf { it.qty }
            superStock = totalSuper - (totalFeed / 20.0)
        }

        return FarmSummary(
            totalExp, totalInc, totalInc - totalExp, 
            prod - load, (farmInitialBirds[f] ?: 0) - deaths, superStock
        )
    }
}

data class FarmSummary(val exp: Double, val inc: Double, val profit: Double, val eggStock: Double, val birds: Int, val superStock: Double)

// --- 4. الواجهة الرسومية ---
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val vm: ArishViewModel = viewModel()
            ArishTheme { MainApp(vm) }
        }
    }
}

@Composable
fun MainApp(vm: ArishViewModel) {
    var tab by rememberSaveable { mutableStateOf(0) }
    val navy = Color(0xFF0D2149) // كحلي فخم متوافق مع اللوجو
    val gold = Color(0xFFC5A059) // ذهبي

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { 
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // مكان اللوجو (يمكن استبداله بـ Image)
                        Icon(Icons.Default.Egg, "Logo", tint = gold, modifier = Modifier.size(30.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("ARISH EGGS", color = Color.White, fontWeight = FontWeight.Black)
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = navy),
                actions = {
                    if (tab == 0) {
                        TextField(
                            value = vm.searchQuery,
                            onValueChange = { vm.searchQuery = it },
                            placeholder = { Text("بحث...", fontSize = 12.sp) },
                            modifier = Modifier.width(120.dp).height(45.dp).padding(4.dp),
                            colors = TextFieldDefaults.colors(containerColor = Color.White.copy(alpha = 0.1f))
                        )
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar(containerColor = navy) {
                val menu = listOf("الحركة اليومية", "الملخص", "الثوابت")
                val icons = listOf(Icons.Default.History, Icons.Default.Summarize, Icons.Default.Settings)
                menu.forEachIndexed { i, label ->
                    NavigationBarItem(
                        selected = tab == i,
                        onClick = { tab = i },
                        icon = { Icon(icons[i], null, tint = if (tab == i) gold else Color.White) },
                        label = { Text(label, color = Color.White, fontSize = 10.sp) }
                    )
                }
            }
        }
    ) { p ->
        Box(Modifier.padding(p).fillMaxSize().background(Color(0xFFF8F9FA))) {
            when (tab) {
                0 -> DailyMovementsScreen(vm)
                1 -> SummaryScreen(vm)
                2 -> ConstantsScreen(vm)
            }
        }
    }
}

@Composable
fun DailyMovementsScreen(vm: ArishViewModel) {
    val gold = Color(0xFFC5A059)
    Column(Modifier.padding(4.dp)) {
        Button(
            onClick = { vm.addRow(0) },
            modifier = Modifier.fillMaxWidth().height(45.dp),
            colors = ButtonDefaults.buttonColors(containerColor = gold),
            shape = RoundedCornerShape(8.dp)
        ) {
            Icon(Icons.Default.Add, null); Text(" إضافة سطر جديد", fontWeight = FontWeight.Bold)
        }
        
        Spacer(Modifier.height(8.dp))
        
        // رأس الجدول
        Row(Modifier.background(Color(0xFF455A64)).padding(4.dp).fillMaxWidth()) {
            HeaderCell("التاريخ", 0.8f); HeaderCell("المزرعة", 1f); HeaderCell("الصنف", 1f); 
            HeaderCell("كمية", 0.5f); HeaderCell("سعر", 0.5f); HeaderCell("ملاحظات", 0.8f)
        }

        val filteredList = if(vm.searchQuery.isEmpty()) vm.transactions 
                          else vm.transactions.filter { it.farm.contains(vm.searchQuery) || it.notes.contains(vm.searchQuery) || it.date.contains(vm.searchQuery) }

        LazyColumn(Modifier.fillMaxSize()) {
            itemsIndexed(filteredList) { index, tr ->
                EditableRow(tr, vm, onInsert = { vm.addRow(index + 1) })
                Divider(color = Color(0xFFEEEEEE), thickness = 0.5.dp)
            }
        }
    }
}

@Composable
fun EditableRow(tr: Transaction, vm: ArishViewModel, onInsert: () -> Unit) {
    Row(Modifier.background(Color.White).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        // 1. التاريخ
        CellInput(tr.date, 0.8f) { tr.date = it; vm.saveData() }
        
        // 2. المزرعة (قائمة منسدلة)
        var expF by remember { mutableStateOf(false) }
        Box(Modifier.weight(1f).border(0.5.dp, Color.LightGray).clickable { expF = true }.padding(8.dp)) {
            Text(tr.farm, fontSize = 10.sp)
            DropdownMenu(expanded = expF, onDismissRequest = { expF = false }) {
                (vm.farmsList + listOf("عام", "إدخال يدوي")).forEach { f ->
                    DropdownMenuItem(text = { Text(f) }, onClick = { tr.farm = f; vm.saveData(); expF = false })
                }
            }
        }

        // 3. الصنف
        var expC by remember { mutableStateOf(false) }
        val cats = listOf("بيض انتاج", "بيض تحميل", "علف", "super", "أخرى (يدوي)")
        Box(Modifier.weight(1f).border(0.5.dp, Color.LightGray).clickable { expC = true }.padding(8.dp)) {
            Text(tr.category, fontSize = 10.sp)
            DropdownMenu(expanded = expC, onDismissRequest = { expC = false }) {
                cats.forEach { c -> DropdownMenuItem(text = { Text(c) }, onClick = { tr.category = c; vm.saveData(); expC = false }) }
            }
        }

        // 4. الكمية (افتراضي 0 بدون فاصلة)
        CellInput(if(tr.qty == 0.0) "0" else tr.qty.toString(), 0.5f, isNum = true) { tr.qty = it.toDoubleOrNull() ?: 0.0; vm.saveData() }
        
        // 5. السعر
        CellInput(if(tr.price == 0.0) "0" else tr.price.toString(), 0.5f, isNum = true) { tr.price = it.toDoubleOrNull() ?: 0.0; vm.saveData() }

        // 6. ملاحظات
        CellInput(tr.notes, 0.8f) { tr.notes = it; vm.saveData() }

        // خيارات الحذف والإضافة
        var showOptions by remember { mutableStateOf(false) }
        IconButton(onClick = { showOptions = true }, Modifier.size(24.dp)) {
            Icon(Icons.Default.MoreVert, null, tint = Color.Gray)
            DropdownMenu(expanded = showOptions, onDismissRequest = { showOptions = false }) {
                DropdownMenuItem(text = { Text("إدراج سطر أسفل") }, onClick = { onInsert(); showOptions = false }, leadingIcon = { Icon(Icons.Default.AddCircle, null) })
                DropdownMenuItem(text = { Text("حذف السطر", color = Color.Red) }, onClick = { vm.deleteRow(tr); showOptions = false }, leadingIcon = { Icon(Icons.Default.Delete, null, tint = Color.Red) })
            }
        }
    }
}

@Composable
fun SummaryScreen(vm: ArishViewModel) {
    val farmsToDisplay = vm.farmsList + listOf("عام", "المجموع")
    
    LazyColumn(Modifier.fillMaxSize().padding(8.dp)) {
        item {
            Row(Modifier.background(Color(0xFF0D2149)).padding(8.dp)) {
                HeaderCell("المزرعة", 1f); HeaderCell("مصروف", 0.7f); HeaderCell("مدخول", 0.7f); 
                HeaderCell("ربح", 0.7f); HeaderCell("بيض", 0.6f); HeaderCell("طيور", 0.6f)
            }
        }
        items(farmsToDisplay) { farm ->
            val s = vm.getFarmStats(farm)
            Row(Modifier.background(if(farm == "المجموع") Color(0xFFFFECB3) else Color.White).padding(8.dp).border(0.3.dp, Color.LightGray)) {
                Text(farm, Modifier.weight(1f), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Text("${s.exp.toInt()}", Modifier.weight(0.7f), fontSize = 11.sp, color = Color.Red)
                Text("${s.inc.toInt()}", Modifier.weight(0.7f), fontSize = 11.sp, color = Color(0xFF2E7D32))
                Text("${s.profit.toInt()}", Modifier.weight(0.7f), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Text("${s.eggStock.toInt()}", Modifier.weight(0.6f), fontSize = 11.sp)
                Text("${if(farm=="عام") "S:"+s.superStock.toInt() else s.birds}", Modifier.weight(0.6f), fontSize = 11.sp)
            }
        }
    }
}

@Composable
fun ConstantsScreen(vm: ArishViewModel) {
    var newFarmName by remember { mutableStateOf("") }
    
    LazyColumn(Modifier.padding(16.dp)) {
        item { Text("إعدادات المزارع", style = MaterialTheme.typography.titleLarge, color = Color(0xFF0D2149)) }
        
        // إضافة مزرعة جديدة
        item {
            Row(Modifier.padding(vertical = 8.dp)) {
                TextField(value = newFarmName, onValueChange = { newFarmName = it }, placeholder = { Text("اسم المزرعة") }, modifier = Modifier.weight(1f))
                Button(onClick = { if(newFarmName.isNotBlank()){ vm.farmsList.add(newFarmName); vm.saveData(); newFarmName="" } }) { Text("إضافة") }
            }
        }

        items(vm.farmsList) { farm ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                Text(farm, Modifier.weight(1f))
                TextField(
                    value = (vm.farmInitialBirds[farm] ?: 0).toString(),
                    onValueChange = { vm.farmInitialBirds[farm] = it.toIntOrNull() ?: 0 },
                    label = { Text("عدد الطيور") },
                    modifier = Modifier.width(120.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                IconButton(onClick = { vm.farmsList.remove(farm); vm.saveData() }) { Icon(Icons.Default.Delete, null, tint = Color.Red) }
            }
        }

        item { Divider(Modifier.padding(vertical = 16.dp)) }
        
        item {
            Text("مفكرة الملاحظات والثوابت", fontWeight = FontWeight.Bold)
            Card(Modifier.fillMaxWidth().padding(top = 8.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF9C4))) {
                Text(
                    "• علف: (كيس)؛ طن = 20 كيس علف\n" +
                    "• سوبر super: (كيس)؛ 1 كيس لكل 20 كيس علف\n" +
                    "• بيض: (كرتونة)؛ صندوق = 12 كرتونة",
                    Modifier.padding(16.dp), fontSize = 13.sp
                )
            }
        }
    }
}

// مكونات صغيرة (Helper UI)
@Composable fun RowScope.HeaderCell(t: String, w: Float) = Text(t, Modifier.weight(w).padding(2.dp), Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
@Composable fun RowScope.CellInput(v: String, w: Float, isNum: Boolean = false, onValueChange: (String) -> Unit) {
    BasicTextField(
        value = v, onValueChange = onValueChange,
        modifier = Modifier.weight(w).border(0.5.dp, Color.LightGray).padding(8.dp),
        textStyle = TextStyle(fontSize = 10.sp),
        keyboardOptions = KeyboardOptions(keyboardType = if(isNum) KeyboardType.Number else KeyboardType.Text)
    )
}

@Composable fun ArishTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = lightColorScheme(primary = Color(0xFF0D2149), secondary = Color(0xFFC5A059)), content = content)
}
