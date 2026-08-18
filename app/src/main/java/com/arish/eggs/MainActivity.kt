@file:OptIn(ExperimentalMaterial3Api::class)
package com.arish.eggs

import android.app.Application
import android.content.*
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
import java.util.*

// --- 1. حاوية البيانات الشاملة (AppState) لضمان الحفظ الموحد ---
data class AppState(
    val transactions: List<Transaction> = emptyList(),
    val farms: List<FarmConfig> = listOf(
        FarmConfig(name = "فايز الطويلة", birds = 7500),
        FarmConfig(name = "فايز البرشا", birds = 2800),
        FarmConfig(name = "فايز الألفين", birds = 2000),
        FarmConfig(name = "ابو حمدو العقيد", birds = 2000),
        FarmConfig(name = "ابو حمدو جديدة", birds = 3300),
        FarmConfig(name = "ابو حمدو الاخرس", birds = 3800),
        FarmConfig(name = "ام نضال ١", birds = 10900),
        FarmConfig(name = "ام نضال ٢", birds = 0)
    ),
    val notepad: String = ""
)

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
    val incomeVal: Double get() = if (isIncome) qty * price else 0.0
    val expenseVal: Double get() = if (category.contains("بيض") || category == "مدخول") 0.0 else qty * price
}

data class FarmConfig(val id: String = UUID.randomUUID().toString(), var name: String, var birds: Int)

// --- 2. مدير التخزين (DataStore Manager) ---
private val Context.dataStore by preferencesDataStore(name = "arish_eggs_v36")

class ArishRepository(private val context: Context) {
    private val gson = Gson()
    private val DATA_KEY = stringPreferencesKey("app_state_json")

    val stateFlow: Flow<AppState> = context.dataStore.data
        .catch { if (it is java.io.IOException) emit(emptyPreferences()) else throw it }
        .map { prefs ->
            val json = prefs[DATA_KEY] ?: ""
            if (json.isEmpty()) AppState()
            else gson.fromJson(json, AppState::class.java)
        }.flowOn(Dispatchers.IO)

    suspend fun updateState(newState: AppState) {
        withContext(Dispatchers.IO) {
            try {
                context.dataStore.edit { it[DATA_KEY] = gson.toJson(newState) }
            } catch (e: Exception) { e.printStackTrace() }
        }
    }
}

// --- 3. المحرك الإداري (ViewModel) ---
class ArishViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = ArishRepository(application)
    val state = repository.stateFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppState())

    fun triggerUpdate(update: (AppState) -> AppState) {
        viewModelScope.launch {
            val currentState = state.value
            repository.updateState(update(currentState))
        }
    }

    fun addRow(atIndex: Int = 0) {
        triggerUpdate { current ->
            val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            val newList = current.transactions.toMutableList()
            newList.add(atIndex, Transaction(date = today, farm = "عام", category = "بيض انتاج"))
            current.copy(transactions = newList)
        }
    }

    fun deleteRow(tr: Transaction) {
        triggerUpdate { current -> current.copy(transactions = current.transactions.filter { it.id != tr.id }) }
    }

    fun updateNotepad(text: String) {
        triggerUpdate { it.copy(notepad = text) }
    }

    fun getSuperStock(): Double {
        val trs = state.value.transactions
        val s = trs.filter { it.category == "super" }.sumOf { it.qty }
        val f = trs.filter { it.category == "علف" }.sumOf { it.qty }
        return s - (f / 20.0)
    }

    fun getFarmStats(fName: String): Map<String, Double> {
        val m = state.value.transactions.filter { it.farm == fName }
        val inc = m.sumOf { it.incomeVal }; val exp = m.sumOf { it.expenseVal }
        val deaths = m.filter { it.category == "وفيات" }.sumOf { it.qty }
        val eggs = m.filter { it.category == "بيض انتاج" }.sumOf { it.qty } - m.filter { it.category == "بيض تحميل" }.sumOf { it.qty }
        val initial = state.value.farms.find { it.name == fName }?.birds?.toDouble() ?: 0.0
        return mapOf("inc" to inc, "exp" to exp, "profit" to (inc - exp), "eggs" to eggs, "birds" to (initial - deaths))
    }
}

// --- 4. واجهة المستخدم (UI) ---
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val vm: ArishViewModel = viewModel()
            val appState by vm.state.collectAsState()
            
            MaterialTheme(colorScheme = lightColorScheme(primary = Color(0xFF0D47A1), secondary = Color(0xFFD4AF37))) {
                var tab by remember { mutableStateOf(0) }
                Scaffold(
                    topBar = { ArishTopBar() },
                    bottomBar = { ArishBottomBar(tab) { tab = it } }
                ) { p ->
                    Box(Modifier.padding(p).fillMaxSize().background(Color(0xFFF1F3F4))) {
                        when (tab) {
                            0 -> MovementScreen(vm, appState)
                            1 -> SummaryScreen(vm, appState)
                            2 -> ConstantsScreen(vm, appState)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ArishTopBar() {
    CenterAlignedTopAppBar(
        title = { Text(text = "ARISH EGGS", fontWeight = FontWeight.Black, color = Color.White) },
        actions = { Image(painter = painterResource(id = R.drawable.logo_arish), contentDescription = null, modifier = Modifier.size(50.dp).padding(4.dp)) },
        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color(0xFF0D47A1))
    )
}

@Composable
fun ArishBottomBar(selectedTab: Int, onTabSelect: (Int) -> Unit) {
    NavigationBar(containerColor = Color(0xFF0D47A1)) {
        val items = listOf("الحركة اليومية", "الملخص", "الثوابت")
        val icons = listOf(Icons.Default.ReceiptLong, Icons.Default.Analytics, Icons.Default.Settings)
        items.forEachIndexed { i, l ->
            NavigationBarItem(selected = selectedTab == i, onClick = { onTabSelect(i) }, icon = { Icon(icons[i], null, tint = if (selectedTab == i) Color.Yellow else Color.White) }, label = { Text(text = l, color = Color.White, fontSize = 10.sp) })
        }
    }
}

@Composable
fun MovementScreen(vm: ArishViewModel, state: AppState) {
    var search by remember { mutableStateOf("") }
    val scroll = rememberScrollState()
    Column(Modifier.fillMaxSize().padding(4.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(value = search, onValueChange = { search = it }, placeholder = { Text(text = "بحث...") }, modifier = Modifier.weight(1f).height(50.dp), shape = RoundedCornerShape(25.dp), leadingIcon = { Icon(Icons.Default.Search, null) })
            Spacer(Modifier.width(8.dp))
            Button(onClick = { vm.addRow(0) }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0D47A1))) { Text(text = "إضافة") }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.background(Color(0xFF455A64)).fillMaxWidth().horizontalScroll(scroll)) {
            HeaderCell("التاريخ", 100.dp); HeaderCell("المزرعة", 135.dp); HeaderCell("الصنف", 115.dp)
            HeaderCell("كمية", 75.dp); HeaderCell("سعر", 75.dp); HeaderCell("مصروف", 85.dp); HeaderCell("مدخول", 85.dp); HeaderCell("ملاحظات", 165.dp)
        }
        LazyColumn(Modifier.fillMaxSize()) {
            items(state.transactions.filter { it.farm.contains(search) || it.date.contains(search) }) { tr ->
                Row(Modifier.fillMaxWidth().horizontalScroll(scroll), verticalAlignment = Alignment.CenterVertically) {
                    EditableCell(tr.date, 100.dp) { tr.date = it; vm.triggerUpdate { it } }
                    DynamicPicker(tr.farm, 135.dp, state.farms.map { it.name } + listOf("عام", "أخرى")) { tr.farm = it; vm.triggerUpdate { it } }
                    DynamicPicker(tr.category, 115.dp, listOf("بيض انتاج", "بيض تحميل", "علف", "super", "مدخول", "أخرى")) { tr.category = it; vm.triggerUpdate { it } }
                    EditableCell(tr.qty.toString(), 75.dp, true) { tr.qty = it.toDoubleOrNull() ?: 0.0; vm.triggerUpdate { it } }
                    EditableCell(tr.price.toString(), 75.dp, true) { tr.price = it.toDoubleOrNull() ?: 0.0; vm.triggerUpdate { it } }
                    DataCell(String.format("%.0f", tr.expenseVal), 85.dp, color = Color.Red, fw = FontWeight.Bold)
                    DataCell(String.format("%.0f", tr.incomeVal), 85.dp, color = Color(0xFF2E7D32), fw = FontWeight.Bold)
                    EditableCell(tr.notes, 165.dp) { tr.notes = it; vm.triggerUpdate { it } }
                    IconButton(onClick = { vm.addRow(state.transactions.indexOf(tr) + 1) }) { Icon(Icons.Default.AddCircle, null, tint = Color(0xFFD4AF37)) }
                    IconButton(onClick = { vm.deleteRow(tr) }) { Icon(Icons.Default.Delete, null, tint = Color.LightGray) }
                }
                Divider()
            }
        }
    }
}

@Composable
fun SummaryScreen(vm: ArishViewModel, state: AppState) {
    val scroll = rememberScrollState()
    Column(Modifier.fillMaxSize().padding(8.dp).horizontalScroll(scroll)) {
        Row(Modifier.background(Color(0xFF455A64))) {
            HeaderCell("المزرعة", 130.dp); HeaderCell("المصروف", 95.dp); HeaderCell("المدخول", 95.dp)
            HeaderCell("الربح", 95.dp); HeaderCell("البيض", 95.dp); HeaderCell("الطيور", 95.dp)
        }
        LazyColumn {
            items(state.farms) { f ->
                val s = vm.getFarmStats(f.name)
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
                Row(Modifier.background(Color(0xFFFFF9C4))) {
                    DataCell("مخزون SUPER", 130.dp, fw = FontWeight.Bold)
                    repeat(3) { DataCell("-", 95.dp) }
                    DataCell(String.format("%.2f", vm.getSuperStock()), 95.dp, color = Color(0xFFE65100), fw = FontWeight.Bold)
                    DataCell("-", 95.dp)
                }
            }
        }
    }
}

@Composable
fun ConstantsScreen(vm: ArishViewModel, state: AppState) {
    var nName by remember { mutableStateOf("") }
    Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
        Text(text = "إدارة المزارع والطيور", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(value = nName, onValueChange = { nName = it }, label = { Text(text = "مزرعة جديدة") }, modifier = Modifier.weight(1f))
            Button(onClick = { if(nName.isNotEmpty()) { vm.triggerUpdate { it.copy(farms = it.farms + FarmConfig(name = nName, birds = 0)) }; nName = "" } }) { Text(text = "+") }
        }
        state.farms.forEach { f ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                Text(text = f.name, modifier = Modifier.weight(1f))
                var bT by remember(f.birds) { mutableStateOf(f.birds.toString()) }
                BasicTextField(value = bT, onValueChange = { bT = it; f.birds = it.toIntOrNull() ?: 0; vm.triggerUpdate { it } }, modifier = Modifier.width(75.dp).border(0.5.dp, Color.Gray).padding(4.dp))
                IconButton(onClick = { vm.triggerUpdate { current -> current.copy(farms = current.farms.filter { it.id != f.id }) } }) { Icon(Icons.Default.Delete, null, tint = Color.Red) }
            }
        }
        Spacer(Modifier.height(20.dp)); Text(text = "المفكرة المفتوحة:", fontWeight = FontWeight.Bold)
        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF9C4))) {
            Column(Modifier.padding(12.dp)) {
                Text(text = "طن علف=20 كيس | صندوق=12 كرتونة", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                BasicTextField(value = state.notepad, onValueChange = { vm.updateNotepad(it) }, modifier = Modifier.fillMaxWidth().height(150.dp).padding(top = 8.dp))
            }
        }
    }
}

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
            options.forEach { o -> DropdownMenuItem(text = { Text(text = o) }, onClick = { if (o == "أخرى") isM = true else onSelect(o); exp = false }) }
        }
    }
}
