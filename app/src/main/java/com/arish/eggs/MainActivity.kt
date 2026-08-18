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

// --- 1. إعدادات التخزين ---
private val Context.dataStore by preferencesDataStore(name = "arish_eggs_v33_final")

// --- 2. نماذج البيانات المحدثة ---
data class Transaction(
    val id: Long = System.currentTimeMillis(),
    var date: String,
    var farm: String,
    var category: String,
    var qty: Double = 0.0,
    var price: Double = 0.0,
    var notes: String = ""
) {
    // معادلة المصروف (نقطة ثانياً-6): استبعاد "بيض" و "مدخول"
    val expenseVal: Double get() = if (category.contains("بيض") || category.contains("مدخول")) 0.0 else qty * price
    // معادلة المدخول (نقطة ثانياً-7): فقط "بيض تحميل" أو "مدخول"
    val incomeVal: Double get() = if (category == "بيض تحميل" || category == "مدخول") qty * price else 0.0
}

data class FeedRate(val farm: String, var startDate: String, var ratePerDay: Double)
data class FarmConfig(val name: String, var birds: Int)

// --- 3. المحرك المحاسبي الشامل ---
class ArishViewModel(application: Application) : AndroidViewModel(application) {
    private val context = application.applicationContext
    private val gson = Gson()
    private val KEY = stringPreferencesKey("arish_v33_data")

    val transactions = mutableStateListOf<Transaction>()
    val farms = mutableStateListOf<FarmConfig>()
    val feedRates = mutableStateListOf<FeedRate>()
    var notepad by mutableStateOf("")

    init {
        // المزارع الأساسية الـ 8
        val defaultFarms = listOf("فايز الطويلة", "فايز البرشا", "فايز الألفين", "ابو حمدو العقيد", "ابو حمدو جديدة", "ابو حمدو الاخرس", "ام نضال ١", "ام نضال ٢")
        farms.addAll(defaultFarms.map { FarmConfig(it, 0) })
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val prefs = context.dataStore.data.first()
                val json = prefs[KEY] ?: ""
                if (json.isNotEmpty()) {
                    val data: Map<String, Any> = gson.fromJson(json, object : TypeToken<Map<String, Any>>() {}.type)
                    // استرجاع البيانات برمجياً (تبسيطاً سنقوم بتحديث الحالة)
                }
            } catch (e: Exception) { }
        }
    }

    fun saveData() {
        viewModelScope.launch(Dispatchers.IO) {
            val map = mapOf("tr" to transactions.toList(), "f" to farms.toList(), "note" to notepad)
            context.dataStore.edit { it[KEY] = gson.toJson(map) }
        }
    }

    // إضافة سطر في مكان محدد (نقطة ثانياً-8)
    fun insertRow(atIndex: Int) {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        transactions.add(atIndex, Transaction(date = today, farm = "عام", category = "بيض انتاج"))
        saveData()
    }

    // حساب العلف المتبقي (نظام التعليفة - نقطة ثالثاً-3)
    fun getRemainingFeed(farmName: String): Double {
        val totalPurchased = transactions.filter { it.farm == farmName && it.category == "علف" }.sumOf { it.qty }
        // منطق حساب الأيام المستهلكة من تاريخ البدء
        return totalPurchased // سيتم تفعيل خصم التعليفة اليومي بناءً على جدول التواريخ
    }

    // حساب السوبر (المعادلة المطلوبة في سطر العام)
    fun getSuperBalance(): Double {
        val superTotal = transactions.filter { it.category == "super" }.sumOf { it.qty }
        val feedTotal = transactions.filter { it.category == "علف" }.sumOf { it.qty }
        return superTotal - (feedTotal / 20.0)
    }
}

// --- 4. الواجهة الرسومية (التصميم الجديد الممتد) ---

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val vm: ArishViewModel = viewModel()
            MaterialTheme { MainNavigation(vm) }
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
                title = { Text("ARISH EGGS", fontWeight = FontWeight.Black, color = Color.White) },
                actions = { 
                    Image(painter = painterResource(id = R.drawable.logo_arish), contentDescription = null, modifier = Modifier.size(55.dp).padding(4.dp)) 
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = navy)
            )
        },
        bottomBar = {
            NavigationBar(containerColor = navy) {
                val menu = listOf("الحركة اليومية", "الملخص", "الثوابت")
                val icons = listOf(Icons.Default.ListAlt, Icons.Default.BarChart, Icons.Default.SettingsInputComponent)
                menu.forEachIndexed { i, label ->
                    NavigationBarItem(selected = tab == i, onClick = { tab = i }, icon = { Icon(icons[i], null, tint = if(tab==i) Color.Yellow else Color.White) }, label = { Text(label, color = Color.White, fontSize = 10.sp) })
                }
            }
        }
    ) { p ->
        Box(Modifier.padding(p).fillMaxSize().background(Color(0xFFF1F3F4))) {
            when (tab) {
                0 -> MovementGrid(vm)
                1 -> SummaryTable(vm)
                2 -> ConstantsEditor(vm)
            }
        }
    }
}

@Composable
fun MovementGrid(vm: ArishViewModel) {
    var search by remember { mutableStateOf("") }
    
    Column(Modifier.fillMaxSize().padding(4.dp)) {
        // رأس الصفحة: بحث وإضافة (نقطة 9)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = search, onValueChange = { search = it },
                placeholder = { Text("بحث في السجل...") },
                modifier = Modifier.weight(1f).height(50.dp),
                shape = RoundedCornerShape(25.dp),
                leadingIcon = { Icon(Icons.Default.Search, null) }
            )
            Spacer(Modifier.width(8.dp))
            Button(onClick = { vm.insertRow(0) }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0D47A1))) {
                Text("إضافة سطر")
            }
        }

        Spacer(Modifier.height(8.dp))

        // الجدول الممتد (عرض كامل)
        val filtered = vm.transactions.filter { it.farm.contains(search) || it.date.contains(search) || it.notes.contains(search) }
        
        Row(Modifier.background(Color(0xFF455A64)).fillMaxWidth().horizontalScroll(rememberScrollState())) {
            HeaderCell("التاريخ", 90.dp); HeaderCell("المزرعة", 110.dp); HeaderCell("الصنف", 100.dp)
            HeaderCell("الكمية", 70.dp); HeaderCell("السعر", 70.dp); HeaderCell("مصروف", 80.dp)
            HeaderCell("مدخول", 80.dp); HeaderCell("ملاحظات", 150.dp)
        }

        LazyColumn(Modifier.fillMaxSize()) {
            itemsIndexed(filtered) { index, tr ->
                Row(Modifier.background(Color.White).fillMaxWidth().horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
                    EditableCell(tr.date, 90.dp) { tr.date = it; vm.saveData() }
                    
                    // اختيار المزرعة مع خيار أخرى (نقطة 2)
                    PickerCell(tr.farm, 110.dp, vm.farms.map { it.name } + listOf("عام", "أخرى")) { tr.farm = it; vm.saveData() }
                    
                    // اختيار الصنف مع خيار أخرى (نقطة 3)
                    PickerCell(tr.category, 100.dp, listOf("بيض انتاج", "بيض تحميل", "علف", "super", "مدخول", "أخرى")) { tr.category = it; vm.saveData() }
                    
                    // الكمية والسعر (نقطة 4)
                    EditableCell(tr.qty.toString(), 70.dp, true) { tr.qty = it.toDoubleOrNull() ?: 0.0; vm.saveData() }
                    EditableCell(tr.price.toString(), 70.dp, true) { tr.price = it.toDoubleOrNull() ?: 0.0; vm.saveData() }
                    
                    // المخرجات (نقطة 6 و 7)
                    TextCell(String.format("%.0f", tr.expenseVal), 80.dp, Color.Red)
                    TextCell(String.format("%.0f", tr.incomeVal), 80.dp, Color(0xFF2E7D32))
                    
                    // ملاحظات (نقطة 8)
                    EditableCell(tr.notes, 150.dp) { tr.notes = it; vm.saveData() }

                    // خيارات السطر (نقطة 8)
                    IconButton(onClick = { vm.insertRow(index + 1) }) { Icon(Icons.Default.Add, null, tint = Color(0xFFD4AF37)) }
                    IconButton(onClick = { vm.transactions.remove(tr); vm.saveData() }) { Icon(Icons.Default.Delete, null, tint = Color.LightGray) }
                }
                Divider()
            }
        }
    }
}

@Composable
fun SummaryTable(vm: ArishViewModel) {
    Column(Modifier.fillMaxSize().padding(8.dp).horizontalScroll(rememberScrollState())) {
        Text("خلاصة المزارع والأداء", style = MaterialTheme.typography.titleLarge, color = Color(0xFF0D47A1))
        Spacer(Modifier.height(10.dp))
        
        Row(Modifier.background(Color(0xFF455A64))) {
            HeaderCell("المزرعة", 110.dp); HeaderCell("المصروف", 90.dp); HeaderCell("المدخول", 90.dp)
            HeaderCell("الربح", 90.dp); HeaderCell("البيض", 90.dp); HeaderCell("العلف", 90.dp); HeaderCell("الطيور", 90.dp)
        }

        LazyColumn(Modifier.fillMaxSize()) {
            items(vm.farms) { f ->
                val s = vm.getFarmSummary(f.name)
                SummaryRow(f.name, s)
            }
            // سطر عام (نقطة 1)
            item { SummaryRow("عام", vm.getFarmSummary("عام"), isGeneral = true) }
            // سطر السوبر (نقطة 2)
            item { 
                Row(Modifier.background(Color(0xFFFFF9C4))) {
                    DataCell("مخزون SUPER", 110.dp, Color.Black, FontWeight.Bold)
                    repeat(4) { DataCell("-", 90.dp) }
                    DataCell(String.format("%.2f", vm.getSuperStock()), 90.dp, Color(0xFFE65100), FontWeight.Bold)
                    DataCell("-", 90.dp)
                }
            }
        }
    }
}

@Composable
fun ConstantsEditor(vm: ArishViewModel) {
    Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
        Text("إدارة الثوابت والجداول", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        
        // الملاحظات المفتوحة (نقطة 4)
        Card(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF9C4))) {
            Column(Modifier.padding(12.dp)) {
                Text("مفكرة التعليمات (ثابتة):", fontWeight = FontWeight.Bold)
                Text("علف: (كيس)؛ طن = 20 كيس\nسوبر: كيس لكل 20 علف\nبيض: صندوق = 12 كرتونة", fontSize = 12.sp)
                Divider(Modifier.padding(vertical = 8.dp))
                BasicTextField(
                    value = vm.notepad, onValueChange = { vm.notepad = it; vm.saveData() },
                    modifier = Modifier.fillMaxWidth().height(120.dp),
                    textStyle = TextStyle(fontSize = 14.sp)
                )
            }
        }

        // إضافة مزارع (نقطة 1)
        Text("إحصاء الطيور الأساسي:", fontWeight = FontWeight.Bold)
        vm.farms.forEach { f ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                Text(f.name, Modifier.width(120.dp))
                var birds by remember { mutableStateOf(f.birds.toString()) }
                OutlinedTextField(value = birds, onValueChange = { birds = it; f.birds = it.toIntOrNull() ?: 0; vm.saveData() }, modifier = Modifier.width(100.dp).height(50.dp))
            }
        }
        
        Button(onClick = { /* شاشة إعدادات المعادلات */ }, Modifier.padding(top = 20.dp)) {
            Icon(Icons.Default.Edit, null); Text(" إعدادات كودات الحسابات (إكسل)")
        }
    }
}

// دالات مساعدة للتصميم السريع
@Composable fun HeaderCell(t: String, w: androidx.compose.ui.unit.Dp) = Text(t, Modifier.width(w).padding(8.dp), Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
@Composable fun TextCell(t: String, w: androidx.compose.ui.unit.Dp, c: Color) = Text(t, Modifier.width(w).border(0.5.dp, Color.LightGray).padding(8.dp), c, fontSize = 11.sp, fontWeight = FontWeight.Bold)
@Composable fun DataCell(t: String, w: androidx.compose.ui.unit.Dp, c: Color = Color.Black, fw: FontWeight = FontWeight.Normal) = Text(t, Modifier.width(w).padding(8.dp), c, fontSize = 11.sp, fontWeight = fw)

@Composable
fun SummaryRow(name: String, s: Map<String, Double>, isGeneral: Boolean = false) {
    Row(Modifier.background(if(isGeneral) Color(0xFFE3F2FD) else Color.White)) {
        DataCell(name, 110.dp, fw = FontWeight.Bold)
        DataCell(s["exp"].toString(), 90.dp, Color.Red)
        DataCell(s["inc"].toString(), 90.dp, Color(0xFF2E7D32))
        DataCell(s["profit"].toString(), 90.dp, fw = FontWeight.ExtraBold)
        if (!isGeneral) {
            DataCell(s["eggs"].toString(), 90.dp, Color.Blue)
            DataCell(s["feed"].toString(), 90.dp)
            DataCell(s["birds"].toString(), 90.dp)
        } else {
            repeat(3) { DataCell("-", 90.dp) }
        }
    }
    Divider()
}

@Composable
fun PickerCell(current: String, width: androidx.compose.ui.unit.Dp, options: List<String>, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    var customText by remember { mutableStateOf("") }
    Box(Modifier.width(width).border(0.5.dp, Color.LightGray).clickable { expanded = true }.padding(8.dp)) {
        Text(current, fontSize = 10.sp)
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { opt ->
                DropdownMenuItem(text = { Text(opt) }, onClick = { 
                    if(opt != "أخرى") onSelect(opt)
                    expanded = false 
                })
            }
        }
    }
}

@Composable
fun EditableCell(v: String, w: androidx.compose.ui.unit.Dp, isNum: Boolean = false, onVal: (String) -> Unit) {
    var t by remember { mutableStateOf(if(v=="0.0") "0" else v) }
    BasicTextField(value = t, onValueChange = { t = it; onVal(it) }, 
        modifier = Modifier.width(w).border(0.5.dp, Color.LightGray).padding(8.dp),
        textStyle = TextStyle(fontSize = 11.sp),
        keyboardOptions = KeyboardOptions(keyboardType = if(isNum) KeyboardType.Number else KeyboardType.Text))
}
