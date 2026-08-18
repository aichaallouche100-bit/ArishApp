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
import androidx.compose.ui.unit.Dp
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

// --- 1. الذاكرة الدائمة ---
private val Context.dataStore by preferencesDataStore(name = "arish_eggs_v33_final")

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
    // معادلة المصروف: يستثني أي صنف فيه "بيض" أو "مدخول"
    val expenseVal: Double get() = if (category.contains("بيض") || category == "مدخول" || category.contains("مدخول")) 0.0 else qty * price
    // معادلة المدخول: فقط "بيض تحميل" أو "مدخول"
    val incomeVal: Double get() = if (category == "بيض تحميل" || category == "مدخول") qty * price else 0.0
}

data class FarmConfig(val name: String, var birds: Int, var dailyFeedRate: Double = 0.0, var rateStartDate: String = "")

// --- 3. المحرك المحاسبي الذكي ---
class ArishViewModel(application: Application) : AndroidViewModel(application) {
    private val context = application.applicationContext
    private val gson = Gson()
    private val DATA_KEY = stringPreferencesKey("arish_data_v33")
    
    val transactions = mutableStateListOf<Transaction>()
    val farms = mutableStateListOf<FarmConfig>()
    var notepad by mutableStateOf("")

    init {
        farms.addAll(listOf(
            FarmConfig("فايز الطويلة", 7500), FarmConfig("فايز البرشا", 2800),
            FarmConfig("فايز الألفين", 2000), FarmConfig("ابو حمدو العقيد", 2000),
            FarmConfig("ابو حمدو جديدة", 3300), FarmConfig("ابو حمدو الاخرس", 3800),
            FarmConfig("ام نضال ١", 10900), FarmConfig("ام نضال ٢", 0)
        ))
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val prefs = context.dataStore.data.catch { if (it is IOException) emit(emptyPreferences()) else throw it }.first()
                val json = prefs[DATA_KEY] ?: ""
                if (json.isNotEmpty()) {
                    val dataMap: Map<String, Any> = gson.fromJson(json, object : TypeToken<Map<String, Any>>() {}.type)
                    withContext(Dispatchers.Main) {
                        // استرجاع البيانات وتحديث القوائم
                    }
                }
            } catch (e: Exception) { }
        }
    }

    fun saveData() {
        val json = gson.toJson(mapOf("tr" to transactions.toList(), "f" to farms.toList(), "note" to notepad))
        viewModelScope.launch(Dispatchers.IO) { context.dataStore.edit { it[DATA_KEY] = json } }
    }

    fun addRow(index: Int = 0) {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        transactions.add(index, Transaction(date = today, farm = "فايز الطويلة", category = "بيض انتاج"))
        saveData()
    }

    // حساب السوبر (النقطة ثانياً-2): مجموع السوبر - مجموع العلف/20
    fun getSuperStock(): Double {
        val totalSuper = transactions.filter { it.category == "super" }.sumOf { it.qty }
        val totalFeed = transactions.filter { it.category == "علف" }.sumOf { it.qty }
        return totalSuper - (totalFeed / 20.0)
    }

    fun getFarmSummary(fName: String): Map<String, Double> {
        val m = transactions.filter { it.farm == fName }
        val exp = m.sumOf { it.expenseVal }
        val inc = m.sumOf { it.incomeVal }
        val deaths = m.filter { it.category == "وفيات" }.sumOf { it.qty }
        val birds = (farms.find { it.name == fName }?.birds?.toDouble() ?: 0.0) - deaths
        val eggs = m.filter { it.category == "بيض انتاج" }.sumOf { it.qty } - m.filter { it.category == "بيض تحميل" }.sumOf { it.qty }
        
        // حساب العلف المتبقي (التعليفة)
        val feedIn = m.filter { it.category == "علف" }.sumOf { it.qty }
        // (تبسيطاً سيتم خصم التعليفة اليومية من تاريخ الإدخال)
        return mapOf("exp" to exp, "inc" to inc, "profit" to (inc - exp), "eggs" to eggs, "birds" to birds, "feed" to feedIn)
    }
}

// --- 4. الواجهة ---
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
    val navy = Color(0xFF0D47A1)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("ARISH EGGS", fontWeight = FontWeight.Black, color = Color.White) },
                actions = { Image(painter = painterResource(id = R.drawable.logo_arish), contentDescription = null, modifier = Modifier.size(50.dp).padding(4.dp)) },
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
                0 -> MovementGrid(vm)
                1 -> SummaryGrid(vm)
                2 -> ConstantsGrid(vm)
            }
        }
    }
}

@Composable
fun MovementGrid(vm: ArishViewModel) {
    var search by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().padding(4.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(value = search, onValueChange = { search = it }, placeholder = { Text("بحث...") }, modifier = Modifier.weight(1f).height(50.dp), shape = RoundedCornerShape(25.dp), leadingIcon = { Icon(Icons.Default.Search, null) })
            Spacer(Modifier.width(8.dp))
            Button(onClick = { vm.addRow(0) }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD4AF37))) { Text("إضافة سطر") }
        }
        Spacer(Modifier.height(8.dp))
        
        // الجدول الممتد (عرض كامل)
        Row(Modifier.background(Color(0xFF455A64)).fillMaxWidth().horizontalScroll(rememberScrollState())) {
            HeaderCell("التاريخ", 100.dp); HeaderCell("المزرعة", 130.dp); HeaderCell("الصنف", 110.dp)
            HeaderCell("كمية", 80.dp); HeaderCell("سعر", 80.dp); HeaderCell("مصروف", 90.dp)
            HeaderCell("مدخول", 90.dp); HeaderCell("ملاحظات", 160.dp)
        }
        
        LazyColumn(Modifier.fillMaxSize()) {
            itemsIndexed(vm.transactions.filter { it.farm.contains(search) || it.date.contains(search) }) { index, tr ->
                Row(Modifier.background(Color.White).fillMaxWidth().horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
                    EditableCell(tr.date, 100.dp) { tr.date = it; vm.saveData() }
                    
                    // المزرعة مع خيار أخرى (يدوي)
                    PickerCell(tr.farm, 130.dp, vm.farms.map { it.name } + listOf("عام", "أخرى")) { tr.farm = it; vm.saveData() }
                    
                    // الصنف مع خيار أخرى (يدوي)
                    PickerCell(tr.category, 110.dp, listOf("بيض انتاج", "بيض تحميل", "علف", "super", "مدخول", "أخرى")) { tr.category = it; vm.saveData() }
                    
                    EditableCell(tr.qty.toString(), 80.dp, true) { tr.qty = it.toDoubleOrNull() ?: 0.0; vm.saveData() }
                    EditableCell(tr.price.toString(), 80.dp, true) { tr.price = it.toDoubleOrNull() ?: 0.0; vm.saveData() }
                    
                    DataCell(String.format("%.0f", tr.expenseVal), 90.dp, Color.Red, FontWeight.Bold)
                    DataCell(String.format("%.0f", tr.incomeVal), 90.dp, Color(0xFF2E7D32), FontWeight.Bold)
                    
                    EditableCell(tr.notes, 160.dp) { tr.notes = it; vm.saveData() }
                    
                    IconButton(onClick = { vm.addRow(index + 1) }) { Icon(Icons.Default.AddCircle, null, tint = Color(0xFF0D47A1)) }
                    IconButton(onClick = { vm.transactions.remove(tr); vm.saveData() }) { Icon(Icons.Default.Delete, null, tint = Color.LightGray) }
                }
                Divider()
            }
        }
    }
}

@Composable
fun SummaryGrid(vm: ArishViewModel) {
    Column(Modifier.fillMaxSize().padding(8.dp).horizontalScroll(rememberScrollState())) {
        Row(Modifier.background(Color(0xFF455A64))) {
            HeaderCell("المزرعة", 130.dp); HeaderCell("المصروف", 100.dp); HeaderCell("المدخول", 100.dp)
            HeaderCell("الربح", 100.dp); HeaderCell("البيض", 100.dp); HeaderCell("العلف", 100.dp); HeaderCell("الطيور", 100.dp)
        }
        LazyColumn {
            items(vm.farms) { f ->
                val s = vm.getFarmSummary(f.name)
                SummaryRow(f.name, s)
            }
            item { SummaryRow("عام", vm.getFarmSummary("عام"), isGeneral = true) }
            item {
                Row(Modifier.background(Color(0xFFFFF9C4))) {
                    DataCell("مخزون SUPER", 130.dp, Color.Black, FontWeight.Bold)
                    repeat(4) { DataCell("-", 100.dp) }
                    DataCell(String.format("%.2f", vm.getSuperStock()), 100.dp, Color(0xFFE65100), FontWeight.Bold)
                    DataCell("-", 100.dp)
                }
            }
            // سطر المجموع (نقطة 1)
            item {
                Row(Modifier.background(Color.LightGray)) {
                    DataCell("المجموع الكلي", 130.dp, fw = FontWeight.Bold)
                    val totalExp = vm.farms.sumOf { vm.getFarmSummary(it.name)["exp"] ?: 0.0 }
                    DataCell(totalExp.toString(), 100.dp, Color.Red, FontWeight.Bold)
                    // ... تكرار لباقي الأعمدة
                }
            }
        }
    }
}

@Composable
fun ConstantsGrid(vm: ArishViewModel) {
    var newFarmName by remember { mutableStateOf("") }
    Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
        Text("إدارة المزارع", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Row {
            OutlinedTextField(value = newFarmName, onValueChange = { newFarmName = it }, label = { Text("اسم مزرعة") }, modifier = Modifier.weight(1f))
            Button(onClick = { if(newFarmName.isNotEmpty()) vm.farms.add(FarmConfig(newFarmName, 0)); vm.saveData() }) { Text("إضافة") }
        }
        Spacer(Modifier.height(20.dp))
        Text("جدول التعليفة (الاستهلاك اليومي):", fontWeight = FontWeight.Bold)
        vm.farms.forEach { f ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(f.name, Modifier.width(110.dp))
                EditableCell(f.rateStartDate, 100.dp) { f.rateStartDate = it; vm.saveData() }
                EditableCell(f.dailyFeedRate.toString(), 80.dp, true) { f.dailyFeedRate = it.toDoubleOrNull() ?: 0.0; vm.saveData() }
            }
        }
        Spacer(Modifier.height(20.dp))
        Text("المفكرة المفتوحة:", fontWeight = FontWeight.Bold)
        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF9C4))) {
            Column(Modifier.padding(12.dp)) {
                Text("علف: (كيس)؛ طن = 20 كيس\nسوبر: 1 كيس لكل 20 كيس علف\nبيض: صندوق = 12 كرتونة", fontSize = 12.sp)
                Divider(Modifier.padding(vertical = 8.dp))
                BasicTextField(value = vm.notepad, onValueChange = { vm.notepad = it; vm.saveData() }, modifier = Modifier.fillMaxWidth().height(150.dp))
            }
        }
    }
}

// دالات التصميم
@Composable fun HeaderCell(t: String, w: Dp, h: Boolean = true) = Text(text = t, modifier = Modifier.width(w).padding(8.dp), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
@Composable fun DataCell(t: String, w: Dp, color: Color = Color.Black, fw: FontWeight = FontWeight.Normal) = Text(text = t, modifier = Modifier.width(w).padding(8.dp), color = color, fontSize = 11.sp, fontWeight = fw)
@Composable fun EditableCell(v: String, w: Dp, isNum: Boolean = false, onVal: (String) -> Unit) {
    var t by remember(v) { mutableStateOf(if(v=="0.0") "0" else v) }
    BasicTextField(value = t, onValueChange = { t = it; onVal(it) }, modifier = Modifier.width(w).border(0.5.dp, Color.LightGray).padding(8.dp), textStyle = TextStyle(fontSize = 11.sp), keyboardOptions = KeyboardOptions(keyboardType = if(isNum) KeyboardType.Number else KeyboardType.Text))
}

@Composable
fun PickerCell(current: String, w: Dp, options: List<String>, onSelect: (String) -> Unit) {
    var exp by remember { mutableStateOf(false) }
    var manual by remember { mutableStateOf(false) }
    Box(Modifier.width(w).border(0.5.dp, Color.LightGray).clickable { exp = true }.padding(8.dp)) {
        Text(current, fontSize = 10.sp)
        DropdownMenu(expanded = exp, onDismissRequest = { exp = false }) {
            options.forEach { opt ->
                DropdownMenuItem(text = { Text(opt) }, onClick = { 
                    if(opt == "أخرى") manual = true else onSelect(opt)
                    exp = false 
                })
            }
        }
    }
}

@Composable
fun SummaryRow(name: String, s: Map<String, Double>, isGeneral: Boolean = false) {
    Row(Modifier.background(if(isGeneral) Color(0xFFE3F2FD) else Color.White)) {
        DataCell(name, 130.dp, fw = FontWeight.Bold)
        DataCell(s["exp"].toString(), 100.dp, Color.Red)
        DataCell(s["inc"].toString(), 100.dp, Color(0xFF2E7D32))
        DataCell(s["profit"].toString(), 100.dp, fw = FontWeight.ExtraBold)
        if(!isGeneral) {
            DataCell(s["eggs"].toString(), 100.dp, Color.Blue)
            DataCell(s["feed"].toString(), 100.dp)
            DataCell(s["birds"].toString(), 100.dp)
        } else repeat(3) { DataCell("-", 100.dp) }
    }
    Divider()
}

@Composable fun ArishTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = lightColorScheme(primary = Color(0xFF0D47A1), secondary = Color(0xFFD4AF37)), content = content)
}
