@file:OptIn(ExperimentalMaterial3Api::class)
package com.arish.eggs

import android.os.Bundle
import android.content.Context
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.*
import androidx.room.*
import kotlinx.coroutines.*
import java.util.*

// 1. قاعدة البيانات (نظام الحفظ الأبدي)
@Entity(tableName = "arish_master_table")
data class Transaction(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    var farm: String,
    var category: String,
    var qty: Double,
    var price: Double
)

@Dao interface TransactionDao {
    @Query("SELECT * FROM arish_master_table ORDER BY id DESC")
    fun getAll(): List<Transaction>
    @Insert fun insert(tr: Transaction): Long
    @Update fun update(tr: Transaction)
    @Delete fun delete(tr: Transaction)
}

@Database(entities = [Transaction::class], version = 5, exportSchema = false)
abstract class ArishDatabase : RoomDatabase() { abstract fun dao(): TransactionDao }

// 2. المحرك المحاسبي مع ميزة التحديث
class ArishLogic(val context: Context) {
    private val db = Room.databaseBuilder(context, ArishDatabase::class.java, "arish_v7.db")
        .fallbackToDestructiveMigration().build()
    
    val transactions = mutableStateListOf<Transaction>()
    val farms = listOf("فايز الطويلة", "فايز البرشا", "فايز الألفين", "ابو حمدو العقيد", "ابو حمدو جديدة", "ابو حمدو الاخرس", "ام نضال ١", "ام نضال ٢")
    private val scope = CoroutineScope(Dispatchers.Main + Job())

    fun loadData() {
        scope.launch {
            val list = withContext(Dispatchers.IO) { db.dao().getAll() }
            transactions.clear()
            transactions.addAll(list)
        }
    }

    fun addRow() {
        scope.launch {
            val newTr = Transaction(farm = farms[0], category = "علف", qty = 0.0, price = 19.375)
            val newId = withContext(Dispatchers.IO) { db.dao().insert(newTr) }
            transactions.add(0, newTr.copy(id = newId.toInt()))
        }
    }

    fun updateCell(tr: Transaction) {
        scope.launch(Dispatchers.IO) { db.dao().update(tr) }
    }

    fun deleteRow(tr: Transaction) {
        scope.launch {
            withContext(Dispatchers.IO) { db.dao().delete(tr) }
            transactions.remove(tr)
        }
    }

    // دالة التحديث (تفتح رابط GitHub للتحميل المباشر)
    fun checkUpdate() {
        // سيقوم هذا الرابط بفتح صفحة الـ Actions في GitHub الخاص بك مباشرة
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/aichaallouche100-bit/ArishApp/actions"))
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    fun getSuperStock(): Double = 151.55 - (transactions.filter { it.category == "علف" }.sumOf { it.qty } / 20.0)
    fun getProfit(f: String): Double {
        val moves = transactions.filter { it.farm == f }
        val inc = moves.filter { it.category == "بيض تحميل" || it.category == "مدخول" }.sumOf { it.qty * it.price }
        val exp = moves.filter { !(it.category.contains("بيض") || it.category == "مدخول") }.sumOf { it.qty * it.price }
        return inc - exp
    }
}

// 3. الواجهة الرئيسية
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val logic = ArishLogic(applicationContext)
        setContent { 
            MaterialTheme {
                LaunchedEffect(Unit) { logic.loadData() }
                MainLayout(logic) 
            }
        }
    }
}

@Composable
fun MainLayout(logic: ArishLogic) {
    var tab by rememberSaveable { mutableStateOf(1) }
    val navy = Color(0xFF0D47A1)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("نهر اسطوان المحاسبي", color = Color.White, fontSize = 18.sp) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = navy),
                actions = {
                    // زر التحديث الذكي في الأعلى
                    IconButton(onClick = { logic.checkUpdate() }) {
                        Icon(Icons.Default.CloudDownload, "تحديث", tint = Color.Yellow)
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar(containerColor = navy) {
                val menu = listOf("الملخص", "الجدول", "المخزون")
                val icons = listOf(Icons.Default.Analytics, Icons.Default.GridOn, Icons.Default.Inventory)
                menu.forEachIndexed { i, label ->
                    NavigationBarItem(
                        selected = tab == i,
                        onClick = { tab = i },
                        icon = { Icon(icons[i], null, tint = Color.White) },
                        label = { Text(label, color = Color.White, fontSize = 10.sp) }
                    )
                }
            }
        }
    ) { p ->
        Box(Modifier.padding(p).fillMaxSize().background(Color(0xFFF1F3F4))) {
            when (tab) {
                0 -> SummaryView(logic)
                1 -> LiveGrid(logic)
                2 -> InventoryView(logic)
            }
        }
    }
}

@Composable
fun LiveGrid(logic: ArishLogic) {
    Column(Modifier.padding(4.dp)) {
        Button(
            onClick = { logic.addRow() },
            modifier = Modifier.fillMaxWidth().height(48.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
            shape = RoundedCornerShape(8.dp)
        ) {
            Icon(Icons.Default.Add, null); Text(" إضافة سطر جديد")
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.background(Color(0xFF455A64)).padding(6.dp).fillMaxWidth()) {
            HeaderCell("المزرعة", 1.2f); HeaderCell("الصنف", 1f); HeaderCell("الكمية", 0.6f); HeaderCell("القيمة", 0.8f)
        }
        LazyColumn(Modifier.fillMaxSize()) {
            items(logic.transactions, key = { it.id }) { tr ->
                EditableRow(tr, logic)
                Divider(color = Color.LightGray, thickness = 0.5.dp)
            }
        }
    }
}

@Composable
fun EditableRow(tr: Transaction, logic: ArishLogic) {
    Row(Modifier.background(Color.White).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        var expF by remember { mutableStateOf(false) }
        Box(Modifier.weight(1.2f).border(0.5.dp, Color(0xFFE0E0E0)).clickable { expF = true }.padding(10.dp)) {
            Text(tr.farm, fontSize = 10.sp)
            DropdownMenu(expanded = expF, onDismissRequest = { expF = false }) {
                logic.farms.forEach { f -> DropdownMenuItem(text = { Text(f) }, onClick = { tr.farm = f; logic.updateCell(tr); expF = false }) }
            }
        }
        var expC by remember { mutableStateOf(false) }
        val cats = listOf("علف", "بيض انتاج", "بيض تحميل", "وفيات", "مدخول", "دواء")
        Box(Modifier.weight(1f).border(0.5.dp, Color(0xFFE0E0E0)).clickable { expC = true }.padding(10.dp)) {
            Text(tr.category, fontSize = 10.sp, color = if(tr.category == "مدخول") Color(0xFF2E7D32) else Color.Black)
            DropdownMenu(expanded = expC, onDismissRequest = { expC = false }) {
                cats.forEach { c -> DropdownMenuItem(text = { Text(c) }, onClick = { tr.category = c; logic.updateCell(tr); expC = false }) }
            }
        }
        var qtyText by remember { mutableStateOf(tr.qty.toString()) }
        BasicTextField(
            value = qtyText,
            onValueChange = { qtyText = it; tr.qty = it.toDoubleOrNull() ?: 0.0; logic.updateCell(tr) },
            modifier = Modifier.weight(0.6f).border(0.5.dp, Color(0xFFE0E0E0)).padding(10.dp),
            textStyle = TextStyle(fontSize = 11.sp),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )
        Text(text = String.format("%.1f", tr.qty * tr.price), modifier = Modifier.weight(0.8f).padding(8.dp), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (tr.category == "بيض تحميل" || tr.category == "مدخول") Color(0xFF2E7D32) else Color.Red)
        IconButton(onClick = { logic.deleteRow(tr) }, Modifier.size(30.dp)) { Icon(Icons.Default.Delete, null, tint = Color.LightGray, modifier = Modifier.size(18.dp)) }
    }
}

@Composable fun RowScope.HeaderCell(t: String, w: Float) = Text(t, Modifier.weight(w).padding(4.dp), Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
@Composable fun SummaryView(l: ArishLogic) {
    LazyColumn(Modifier.padding(16.dp)) {
        item { Text("خلاصة الأرباح", style = MaterialTheme.typography.headlineSmall, color = Color(0xFF0D47A1)) }
        items(l.farms) { f -> 
            Row(Modifier.fillMaxWidth().padding(12.dp), Arrangement.SpaceBetween) {
                Text(f); Text("${String.format("%.2f", l.getProfit(f))} $", color = Color(0xFF2E7D32), fontWeight = FontWeight.Bold)
            }
            Divider() 
        }
    }
}
@Composable fun InventoryView(l: ArishLogic) {
    Column(Modifier.padding(20.dp)) {
        Text("المخازن", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(20.dp))
        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFE65100)), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("رصيد Super المتبقي", color = Color.White)
                Text("${String.format("%.2f", l.getSuperStock())} كيس", fontSize = 35.sp, color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
    }
}
