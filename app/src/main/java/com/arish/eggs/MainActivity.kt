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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.*

// --- 1. إعداد مخزن البيانات (DataStore) ---
private val Context.dataStore by preferencesDataStore(name = "arish_data_v30")

// --- 2. نموذج السجل المحاسبي ---
data class Transaction(
    val id: Long = System.currentTimeMillis(),
    var farm: String,
    var category: String,
    var qty: Double,
    var price: Double
) {
    val isIncome: Boolean get() = category == "بيض تحميل" || category == "مدخول"
    val valDisplay: Double get() = qty * price
}

// --- 3. المحرك المحاسبي الذكي (ViewModel) ---
class ArishViewModel(application: Application) : AndroidViewModel(application) {
    private val context = application.applicationContext
    private val gson = Gson()
    private val KEY = stringPreferencesKey("records_v30")
    
    val transactions = mutableStateListOf<Transaction>()
    val farms = listOf("فايز الطويلة", "فايز البرشا", "فايز الألفين", "ابو حمدو العقيد", "ابو حمدو جديدة", "ابو حمدو الاخرس", "ام نضال ١", "ام نضال ٢")
    val initialBirds = mapOf("فايز الطويلة" to 7500, "فايز البرشا" to 2800, "فايز الألفين" to 2000, "ابو حمدو العقيد" to 2000, "ابو حمدو جديدة" to 3300, "ابو حمدو الاخرس" to 3800, "ام نضال ١" to 10900)

    init { loadData() }

    private fun loadData() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val prefs = context.dataStore.data.catch { if (it is IOException) emit(emptyPreferences()) else throw it }.first()
                val json = prefs[KEY] ?: ""
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

    private fun saveData() {
        val json = gson.toJson(transactions.toList())
        viewModelScope.launch(Dispatchers.IO) {
            context.dataStore.edit { it[KEY] = json }
        }
    }

    fun addRow() {
        transactions.add(0, Transaction(farm = farms[0], category = "علف", qty = 0.0, price = 19.375))
        saveData()
    }

    fun updateRow(tr: Transaction) {
        val i = transactions.indexOfFirst { it.id == tr.id }
        if (i != -1) { transactions[i] = tr.copy(); saveData() }
    }

    fun deleteRow(tr: Transaction) { transactions.remove(tr); saveData() }

    fun getSuperStock(): Double = 151.55 - (transactions.filter { it.category == "علف" }.sumOf { it.qty } / 20.0)
    
    fun getStats(f: String): Triple<Double, Int, Double> {
        val m = transactions.filter { it.farm == f }
        val inc = m.sumOf { if(it.isIncome) it.qty * it.price else 0.0 }
        val exp = m.sumOf { if(!it.isIncome && !it.category.contains("بيض")) it.qty * it.price else 0.0 }
        val d = m.filter { it.category == "وفيات" }.sumOf { it.qty }.toInt()
        val e = m.filter { it.category == "بيض انتاج" }.sumOf { it.qty } - m.filter { it.category == "بيض تحميل" }.sumOf { it.qty }
        return Triple(inc - exp, (initialBirds[f] ?: 0) - d, e)
    }
}

// --- 4. واجهة المستخدم ---
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val vm: ArishViewModel = viewModel()
            MaterialTheme { MainApp(vm) }
        }
    }
}

@Composable
fun MainApp(vm: ArishViewModel) {
    var tab by rememberSaveable { mutableStateOf(1) }
    val navy = Color(0xFF0D47A1)

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("نهر اسطوان المحاسبي", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp) },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = navy),
                actions = {
                    IconButton(onClick = { 
                        val i = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/aichaallouche100-bit/ArishApp/actions"))
                        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        vm.getApplication<Application>().startActivity(i)
                    }) { Icon(Icons.Default.CloudDownload, null, tint = Color.Yellow) }
                }
            )
        },
        bottomBar = {
            NavigationBar(containerColor = navy) {
                val menu = listOf("الملخص", "الجدول", "المخازن")
                val icons = listOf(Icons.Default.Analytics, Icons.Default.GridOn, Icons.Default.Warehouse)
                menu.forEachIndexed { i, label ->
                    NavigationBarItem(selected = tab == i, onClick = { tab = i }, icon = { Icon(icons[i], null, tint = if(tab == i) Color.Yellow else Color.White) }, label = { Text(label, color = Color.White, fontSize = 10.sp) })
                }
            }
        }
    ) { p ->
        Box(Modifier.padding(p).fillMaxSize().background(Color(0xFFF1F3F4))) {
            when (tab) {
                0 -> SummaryScreen(vm)
                1 -> GridScreen(vm)
                2 -> InventoryScreen(vm)
            }
        }
    }
}

@Composable
fun GridScreen(vm: ArishViewModel) {
    Column(Modifier.padding(4.dp)) {
        Button(onClick = { vm.addRow() }, modifier = Modifier.fillMaxWidth().height(48.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)), shape = RoundedCornerShape(8.dp)) {
            Icon(Icons.Default.Add, null); Text(" إضافة سطر إكسل جديد", fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(4.dp))
        Row(Modifier.background(Color(0xFF455A64)).padding(8.dp).fillMaxWidth()) {
            HeaderCell("المزرعة", 1.2f); HeaderCell("الصنف", 1f); HeaderCell("الكمية", 0.6f); HeaderCell("القيمة", 0.8f)
        }
        LazyColumn(Modifier.fillMaxSize()) {
            items(vm.transactions, key = { it.id }) { tr ->
                EditableRow(tr, vm)
                Divider(color = Color(0xFFEEEEEE), thickness = 0.5.dp)
            }
        }
    }
}

@Composable
fun EditableRow(tr: Transaction, vm: ArishViewModel) {
    Row(Modifier.background(Color.White).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        var expF by remember { mutableStateOf(false) }
        Box(Modifier.weight(1.2f).border(0.5.dp, Color(0xFFE0E0E0)).clickable { expF = true }.padding(10.dp)) {
            Text(tr.farm, fontSize = 9.sp)
            DropdownMenu(expanded = expF, onDismissRequest = { expF = false }) {
                vm.farms.forEach { f -> DropdownMenuItem(text = { Text(f) }, onClick = { tr.farm = f; vm.updateRow(tr); expF = false }) }
            }
        }
        var expC by remember { mutableStateOf(false) }
        val cats = listOf("علف", "بيض انتاج", "بيض تحميل", "وفيات", "مدخول", "دواء")
        Box(Modifier.weight(1f).border(0.5.dp, Color(0xFFE0E0E0)).clickable { expC = true }.padding(10.dp)) {
            Text(tr.category, fontSize = 9.sp, color = if(tr.isIncome) Color(0xFF2E7D32) else Color.Black)
            DropdownMenu(expanded = expC, onDismissRequest = { expC = false }) {
                cats.forEach { c -> DropdownMenuItem(text = { Text(c) }, onClick = { tr.category = c; vm.updateRow(tr); expC = false }) }
            }
        }
        var txt by remember { mutableStateOf(tr.qty.toString()) }
        BasicTextField(value = txt, onValueChange = { 
            txt = it; tr.qty = it.toDoubleOrNull() ?: 0.0; vm.updateRow(tr) 
        }, modifier = Modifier.weight(0.6f).border(0.5.dp, Color(0xFFE0E0E0)).padding(10.dp), textStyle = TextStyle(fontSize = 11.sp), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
        Text(text = String.format("%.1f", tr.qty * tr.price), modifier = Modifier.weight(0.8f).padding(4.dp), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (tr.isIncome) Color(0xFF2E7D32) else Color.Red)
        IconButton(onClick = { vm.deleteRow(tr) }, Modifier.size(24.dp)) { Icon(Icons.Default.Delete, null, tint = Color.LightGray, modifier = Modifier.size(16.dp)) }
    }
}

@Composable fun RowScope.HeaderCell(t: String, w: Float) = Text(t, Modifier.weight(w).padding(4.dp), Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)

@Composable fun SummaryScreen(vm: ArishViewModel) {
    LazyColumn(Modifier.padding(16.dp)) {
        item { Text("خلاصة المزارع", style = MaterialTheme.typography.headlineSmall, color = Color(0xFF0D47A1), fontWeight = FontWeight.Bold) }
        items(vm.farms) { farm ->
            val s = vm.getStats(farm)
            Card(Modifier.fillMaxWidth().padding(vertical = 4.dp), elevation = CardDefaults.cardElevation(4.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                        Text(farm, fontWeight = FontWeight.Bold); Text("${String.format("%.1f", s.first)} $", color = if(s.first >= 0) Color(0xFF2E7D32) else Color.Red, fontWeight = FontWeight.Bold)
                    }
                    Row(Modifier.fillMaxWidth().padding(top = 8.dp), Arrangement.SpaceEvenly) {
                        Text("طيور: ${s.second}", fontSize = 11.sp); Text("بيض: ${s.third} كرتونة", fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

@Composable fun InventoryScreen(vm: ArishViewModel) {
    Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("تحليل المخازن", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Card(modifier = Modifier.fillMaxWidth().padding(top = 20.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFE65100))) {
            Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("رصيد Super المتبقي", color = Color.White)
                Text("${String.format("%.2f", vm.getSuperStock())} كيس", fontSize = 38.sp, color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
    }
}
