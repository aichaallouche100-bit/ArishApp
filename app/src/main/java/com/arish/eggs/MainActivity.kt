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

// --- 1. إعداد مخزن البيانات (DataStore) للحفظ الأبدي ---
private val Context.dataStore by preferencesDataStore(name = "arish_master_storage_v34")

// --- 2. نماذج البيانات (Models) ---
data class Transaction(
    val id: Long = System.currentTimeMillis(),
    var date: String,
    var farm: String,
    var category: String,
    var qty: Double = 0.0,
    var price: Double = 0.0,
    var notes: String = ""
) {
    val isIncome: Boolean get() = category == "بيض تحميل" || category == "مدخول"
    // معادلة إكسل للمصروف والمدخول
    val incomeVal: Double get() = if (isIncome) qty * price else 0.0
    val expenseVal: Double get() = if (category.contains("بيض") || category == "مدخول") 0.0 else qty * price
}

data class FarmConfig(val id: String = UUID.randomUUID().toString(), var name: String, var birds: Int)

// --- 3. المحرك المحاسبي الشامل (ViewModel) ---
class ArishViewModel(application: Application) : AndroidViewModel(application) {
    private val context = application.applicationContext
    private val gson = Gson()
    private val KEY = stringPreferencesKey("arish_all_data")
    
    val transactions = mutableStateListOf<Transaction>()
    val farms = mutableStateListOf<FarmConfig>()
    var notepad by mutableStateOf("")

    init {
        // تحميل المزارع الافتراضية
        val defaults = listOf("فايز الطويلة", "فايز البرشا", "فايز الألفين", "ابو حمدو العقيد", "ابو حمدو جديدة", "ابو حمدو الاخرس", "ام نضال ١", "ام نضال ٢")
        farms.addAll(defaults.map { FarmConfig(name = it, birds = 0) })
        loadAll()
    }

    private fun loadAll() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val prefs = context.dataStore.data.catch { if (it is IOException) emit(emptyPreferences()) else throw it }.first()
                val json = prefs[KEY] ?: ""
                if (json.isNotEmpty()) {
                    val data: Map<String, Any> = gson.fromJson(json, object : TypeToken<Map<String, Any>>() {}.type)
                    // (سيتم استرجاع القوائم والملاحظات هنا)
                }
            } catch (e: Exception) { }
        }
    }

    fun saveData() {
        val json = gson.toJson(mapOf("tr" to transactions.toList(), "f" to farms.toList(), "note" to notepad))
        viewModelScope.launch(Dispatchers.IO) { context.dataStore.edit { it[KEY] = json } }
    }

    fun insertRow(index: Int) {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        transactions.add(index, Transaction(date = today, farm = "عام", category = "بيض انتاج"))
        saveData()
    }

    fun deleteRow(tr: Transaction) { transactions.remove(tr); saveData() }

    // معادلة السوبر: SUMIF(super) - SUMIF(علف)/20
    fun getGeneralSuper(): Double {
        val totalSuper = transactions.filter { it.category == "super" }.sumOf { it.qty }
        val totalFeed = transactions.filter { it.category == "علف" }.sumOf { it.qty }
        return totalSuper - (totalFeed / 20.0)
    }

    fun getFarmStats(fName: String): Map<String, Double> {
        val m = transactions.filter { it.farm == fName }
        val inc = m.sumOf { it.incomeVal }
        val exp = m.sumOf { it.expenseVal }
        val deaths = m.filter { it.category == "وفيات" }.sumOf { it.qty }
        val eggStock = m.filter { it.category == "بيض انتاج" }.sumOf { it.qty } - m.filter { it.category == "بيض تحميل" }.sumOf { it.qty }
        val initial = farms.find { it.name == fName }?.birds?.toDouble() ?: 0.0
        return mapOf("inc" to inc, "exp" to exp, "profit" to (inc-exp), "eggs" to eggStock, "birds" to (initial-deaths))
    }
}

// --- 4. واجهة المستخدم (التصميم الممتد) ---
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
                title = { Text("ARISH EGGS", fontWeight = FontWeight.Black, color = Color.White) },
                actions = { Image(painter = painterResource(id = R.drawable.logo_arish), contentDescription = null, modifier = Modifier.size(50.dp).padding(end = 8.dp)) },
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
    val scrollState = rememberScrollState()

    Column(Modifier.fillMaxSize().padding(4.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(value = search, onValueChange = { search = it }, placeholder = { Text("بحث...") }, modifier = Modifier.weight(1f).height(50.dp), shape = RoundedCornerShape(25.dp), leadingIcon = { Icon(Icons.Default.Search, null) })
            Spacer(Modifier.width(8.dp))
            Button(onClick = { vm.insertRow(0) }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0D47A1))) { Text("إضافة") }
        }
        Spacer(Modifier.height(8.dp))
        
        Row(Modifier.background(Color(0xFF455A64)).fillMaxWidth().horizontalScroll(scrollState)) {
            HeaderCell("التاريخ", 100.dp); HeaderCell("المزرعة", 130.dp); HeaderCell("الصنف", 110.dp)
            HeaderCell("الكمية", 80.dp); HeaderCell("السعر", 80.dp); HeaderCell("مصروف", 90.dp); HeaderCell("مدخول", 90.dp); HeaderCell("ملاحظات", 160.dp)
        }

        LazyColumn(Modifier.fillMaxSize()) {
            itemsIndexed(vm.transactions.filter { it.farm.contains(search) || it.date.contains(search) }) { index, tr ->
                Row(Modifier.fillMaxWidth().horizontalScroll(scrollState), verticalAlignment = Alignment.CenterVertically) {
                    EditableCell(tr.date, 100.dp) { tr.date = it; vm.saveData() }
                    
                    // اختيار المزرعة مع إدخال يدوي (أخرى)
                    DynamicPicker(tr.farm, 130.dp, vm.farms.map { it.name } + listOf("عام", "أخرى")) { tr.farm = it; vm.saveData() }
                    
                    // اختيار الصنف مع إدخال يدوي (أخرى)
                    DynamicPicker(tr.category, 110.dp, listOf("بيض انتاج", "بيض تحميل", "علف", "super", "مدخول", "أخرى")) { tr.category = it; vm.saveData() }
                    
                    EditableCell(tr.qty.toString(), 80.dp, true) { tr.qty = it.toDoubleOrNull() ?: 0.0; vm.saveData() }
                    EditableCell(tr.price.toString(), 80.dp, true) { tr.price = it.toDoubleOrNull() ?: 0.0; vm.saveData() }
                    
                    TextCell(String.format("%.0f", tr.expenseVal), 90.dp, Color.Red)
                    TextCell(String.format("%.0f", tr.incomeVal), 90.dp, Color(0xFF2E7D32))
                    EditableCell(tr.notes, 160.dp) { tr.notes = it; vm.saveData() }

                    IconButton(onClick = { vm.insertRow(index + 1) }) { Icon(Icons.Default.AddCircle, null, tint = Color(0xFFD4AF37)) }
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
            HeaderCell("المزرعة", 130.dp); HeaderCell("المصروف", 100.dp); HeaderCell("المدخول", 100.dp)
            HeaderCell("الربح", 100.dp); HeaderCell("البيض", 100.dp); HeaderCell("الطيور", 100.dp)
        }
        LazyColumn {
            items(vm.farms) { f ->
                val s = vm.getFarmStats(f.name)
                SummaryRow(f.name, s)
            }
            item { SummaryRow("عام", vm.getFarmStats("عام"), isGen = true) }
            item { 
                Row(Modifier.background(Color(0xFFFFF9C4))) {
                    DataCell("مخزون SUPER", 130.dp, fw = FontWeight.Bold)
                    repeat(3) { DataCell("-", 100.dp) }
                    DataCell(String.format("%.2f", vm.getGeneralSuper()), 100.dp, Color(0xFFE65100), FontWeight.Bold)
                    DataCell("-", 100.dp)
                }
            }
            // سطر المجموع (نقطة ثانياً-1)
            item {
                Row(Modifier.background(Color.LightGray)) {
                    DataCell("المجموع الكلي", 130.dp, fw = FontWeight.Bold)
                    val tExp = vm.farms.sumOf { vm.getFarmStats(it.name).second.toDouble() } // مثال للمجموع
                    DataCell(tExp.toString(), 100.dp, fw = FontWeight.Bold)
                    repeat(4) { DataCell("-", 100.dp) }
                }
            }
        }
    }
}

@Composable
fun SummaryRow(name: String, s: Triple<Double, Int, Double>, isGen: Boolean = false) {
    Row(Modifier.background(if(isGen) Color(0xFFE3F2FD) else Color.White)) {
        DataCell(name, 130.dp, fw = FontWeight.Bold)
        DataCell(s.first.toString(), 100.dp, if(s.first >= 0) Color.Black else Color.Red) // مصروف/مدخول تبسيطاً
        DataCell("-", 100.dp) // مثال
        DataCell(s.first.toString(), 100.dp, fw = FontWeight.ExtraBold)
        DataCell(s.third.toString(), 100.dp, Color.Blue)
        DataCell(s.second.toString(), 100.dp)
    }
    Divider()
}

@Composable
fun ConstantsScreen(vm: ArishViewModel) {
    var nName by remember { mutableStateOf("") }
    var nBirds by remember { mutableStateOf("") }
    
    Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
        Text("إدارة المزارع والطيور", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        
        // إضافة مزرعة صغيرة (نقطة ثالثاً-1)
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(value = nName, onValueChange = { nName = it }, label = { Text("الاسم") }, modifier = Modifier.weight(1f))
            OutlinedTextField(value = nBirds, onValueChange = { nBirds = it }, label = { Text("الطيور") }, modifier = Modifier.weight(0.6f))
            Button(onClick = { if(nName.isNotEmpty()) { vm.farms.add(FarmConfig(name = nName, birds = nBirds.toIntOrNull() ?: 0)); vm.saveData() } }) { Text("+") }
        }

        Spacer(Modifier.height(10.dp))

        // قائمة المزارع مع الحذف (نقطة ثالثاً-1)
        vm.farms.forEach { f ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                Text(f.name, Modifier.weight(1f))
                var bText by remember { mutableStateOf(f.birds.toString()) }
                BasicTextField(value = bText, onValueChange = { bText = it; f.birds = it.toIntOrNull() ?: 0; vm.saveData() }, modifier = Modifier.width(60.dp).border(0.5.dp, Color.Gray).padding(4.dp))
                IconButton(onClick = { vm.farms.remove(f); vm.saveData() }) { Icon(Icons.Default.Delete, null, tint = Color.Red) }
            }
        }

        Spacer(Modifier.height(20.dp))
        Text("المفكرة المفتوحة (ملاحظات):", fontWeight = FontWeight.Bold)
        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF9C4))) {
            Column(Modifier.padding(12.dp)) {
                Text("علف: (كيس)؛ طن = 20 كيس\nسوبر: كيس لكل 20 علف\nبيض: صندوق = 12 كرتونة", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Divider(Modifier.padding(vertical = 8.dp))
                BasicTextField(value = vm.notepad, onValueChange = { vm.notepad = it; vm.saveData() }, modifier = Modifier.fillMaxWidth().height(150.dp))
            }
        }
    }
}

// دالة الاختيار الديناميكي (نقطة أولاً-1)
@Composable
fun DynamicPicker(current: String, width: Dp, options: List<String>, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    var isManual by remember { mutableStateOf(false) }
    var manualText by remember { mutableStateOf("") }

    if (isManual) {
        BasicTextField(value = manualText, onValueChange = { manualText = it; onSelect(it) }, 
            modifier = Modifier.width(width).border(0.5.dp, Color(0xFF0D47A1)).padding(8.dp),
            textStyle = TextStyle(fontSize = 10.sp))
    } else {
        Box(Modifier.width(width).border(0.5.dp, Color.LightGray).clickable { expanded = true }.padding(8.dp)) {
            Text(current, fontSize = 10.sp)
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { opt ->
                    DropdownMenuItem(text = { Text(opt) }, onClick = {
                        if(opt == "أخرى") isManual = true else onSelect(opt)
                        expanded = false
                    })
                }
            }
        }
    }
}

@Composable fun HeaderCell(t: String, w: Dp) = Text(t, Modifier.width(w).padding(8.dp), Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
@Composable fun TextCell(t: String, w: Dp, c: Color) = Text(t, Modifier.width(w).border(0.5.dp, Color.LightGray).padding(8.dp), c, fontSize = 11.sp, FontWeight.Bold)
@Composable fun DataCell(t: String, w: Dp, color: Color = Color.Black, fw: FontWeight = FontWeight.Normal) = Text(t, Modifier.width(w).padding(8.dp), color, fontSize = 11.sp, fontWeight = fw)
@Composable fun EditableCell(v: String, w: Dp, isNum: Boolean = false, onVal: (String) -> Unit) {
    var t by remember(v) { mutableStateOf(if(v=="0.0") "0" else v) }
    BasicTextField(value = t, onValueChange = { t = it; onVal(it) }, modifier = Modifier.width(w).border(0.5.dp, Color.LightGray).padding(8.dp), textStyle = TextStyle(fontSize = 11.sp), keyboardOptions = KeyboardOptions(keyboardType = if(isNum) KeyboardType.Number else KeyboardType.Text))
}

@Composable fun ArishTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = lightColorScheme(primary = Color(0xFF0D47A1), secondary = Color(0xFFD4AF37)), content = content)
}
