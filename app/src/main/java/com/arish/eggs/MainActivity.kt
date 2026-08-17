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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.room.*
import kotlinx.coroutines.*
import java.util.*

// --- 1. قاعدة البيانات (الهيكل الذي لا ينهار) ---

@Entity(tableName = "arish_table_v28")
data class Transaction(
    @PrimaryKey val id: Long, 
    var farm: String,
    var category: String,
    var qty: Double,
    var price: Double
) {
    val incomeVal: Double get() = if (category == "بيض تحميل" || category == "مدخول") qty * price else 0.0
    val expenseVal: Double get() = if (category.contains("بيض") || category == "مدخول") 0.0 else qty * price
}

@Dao interface TransactionDao {
    @Query("SELECT * FROM arish_table_v28 ORDER BY id DESC")
    fun getAll(): List<Transaction>
    @Insert(onConflict = OnConflictStrategy.REPLACE) fun insertImmediate(tr: Transaction)
    @Update fun updateImmediate(tr: Transaction)
    @Delete fun deleteImmediate(tr: Transaction)
}

@Database(entities = [Transaction::class], version = 28, exportSchema = false)
abstract class ArishDatabase : RoomDatabase() { abstract fun dao(): TransactionDao }

// --- 2. المحرك المحاسبي بنظام "التشغيل المتأخر" (الحل الذي نجح سابقاً) ---

class ArishLogic(val context: Context) {
    var db: ArishDatabase? = null
    val transactions = mutableStateListOf<Transaction>()
    val farms = listOf("فايز الطويلة", "فايز البرشا", "فايز الألفين", "ابو حمدو العقيد", "ابو حمدو جديدة", "ابو حمدو الاخرس", "ام نضال ١", "ام نضال ٢")
    val farmInitialBirds = mapOf("فايز الطويلة" to 7500, "فايز البرشا" to 2800, "فايز الألفين" to 2000, "ابو حمدو العقيد" to 2000, "ابو حمدو جديدة" to 3300, "ابو حمدو الاخرس" to 3800, "ام نضال ١" to 10900, "ام نضال ٢" to 0)

    fun startEngine() {
        try {
            db = Room.databaseBuilder(context, ArishDatabase::class.java, "arish_v28_stable.db")
                .allowMainThreadQueries()
                .setJournalMode(RoomDatabase.JournalMode.TRUNCATE) // هذا السطر سيحل مشكلة نسيان البيانات
                .fallbackToDestructiveMigration()
                .build()
            refreshData()
        } catch (e: Exception) { }
    }

    fun refreshData() {
        val list = db?.dao()?.getAll() ?: emptyList()
        transactions.clear()
        transactions.addAll(list)
    }

    fun addRow() {
        val newTr = Transaction(id = System.currentTimeMillis(), farm = farms[0], category = "علف", qty = 0.0, price = 19.375)
        db?.dao()?.insertImmediate(newTr) // حفظ قسري فوراً
        transactions.add(0, newTr)
    }

    fun updateCell(tr: Transaction) { db?.dao()?.updateImmediate(tr) }
    fun deleteRow(tr: Transaction) { db?.dao()?.deleteImmediate(tr); transactions.remove(tr) }

    fun getSuperStock(): Double = 151.55 - (transactions.filter { it.category == "علف" }.sumOf { it.qty } / 20.0)
    
    fun getStats(f: String): Triple<Double, Int, Double> {
        val m = transactions.filter { it.farm == f }
        val profit = m.sumOf { it.incomeVal - it.expenseVal }
        val deaths = m.filter { it.category == "وفيات" }.sumOf { it.qty }.toInt()
        val eggStock = m.filter { it.category == "بيض انتاج" }.sumOf { it.qty } - m.filter { it.category == "بيض تحميل" }.sumOf { it.qty }
        return Triple(profit, (farmInitialBirds[f] ?: 0) - deaths, eggStock)
    }
}

// --- 3. الواجهة الرئيسية (نظام v20 الناجح) ---

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val logic = ArishLogic(applicationContext)
        setContent { 
            MaterialTheme {
                // نستخدم نفس فكرة التأخير التي منعت الانهيار سابقاً
                LaunchedEffect(Unit) {
                    delay(1000)
                    logic.startEngine()
                }
                MainApp(logic) 
            } 
        }
    }
}

@Composable
fun MainApp(logic: ArishLogic) {
    var tab by rememberSaveable { mutableStateOf(1) }
    val navy = Color(0xFF0D47A1)

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("منتجات نهر اسطوان المحاسبي", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp) },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = navy),
                actions = {
                    IconButton(onClick = { 
                        val i = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/aichaallouche100-bit/ArishApp/actions"))
                        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        logic.context.startActivity(i)
                    }) { Icon(Icons.Default.CloudDownload, null, tint = Color.Yellow) }
                }
            )
        },
        bottomBar = {
            NavigationBar(containerColor = navy) {
                val menu = listOf("الملخص", "الجدول", "المخازن")
                val icons = listOf(Icons.Default.Analytics, Icons.Default.GridOn, Icons.Default.Warehouse)
                menu.forEachIndexed { i, label ->
                    NavigationBarItem(selected = tab == i, onClick = { tab = i }, icon = { Icon(icons[i], null, tint = if(tab==i) Color.Yellow else Color.White) }, label = { Text(label, color = Color.White, fontSize = 10.sp) })
                }
            }
        }
    ) { p ->
        Box(Modifier.padding(p).fillMaxSize().background(Color(0xFFF1F3F4))) {
            when (tab) {
                0 -> SummaryScreen(logic)
                1 -> LiveGridScreen(logic)
                2 -> InventoryScreen(logic)
            }
        }
    }
}

@Composable
fun LiveGridScreen(logic: ArishLogic) {
    Column(Modifier.padding(4.dp)) {
        Button(onClick = { logic.addRow() }, modifier = Modifier.fillMaxWidth().height(45.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)), shape = RoundedCornerShape(8.dp)) {
            Icon(Icons.Default.Add, null); Text(" إضافة سطر إكسل جديد", fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(4.dp))
        Row(Modifier.background(Color(0xFF455A64)).padding(8.dp).fillMaxWidth()) {
            HeaderCell("المزرعة", 1.2f); HeaderCell("الصنف", 1f); HeaderCell("الكمية", 0.6f); HeaderCell("القيمة", 0.8f)
        }
        LazyColumn(Modifier.fillMaxSize()) {
            items(logic.transactions, key = { it.id }) { tr ->
                Row(Modifier.background(Color.White).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    var expF by remember { mutableStateOf(false) }
                    Box(Modifier.weight(1.2f).border(0.5.dp, Color(0xFFE0E0E0)).clickable { expF = true }.padding(10.dp)) {
                        Text(tr.farm, fontSize = 9.sp)
                        DropdownMenu(expanded = expF, onDismissRequest = { expF = false }) {
                            logic.farms.forEach { f -> DropdownMenuItem(text = { Text(f) }, onClick = { tr.farm = f; logic.updateCell(tr); expF = false }) }
                        }
                    }
                    var expC by remember { mutableStateOf(false) }
                    val cats = listOf("علف", "بيض انتاج", "بيض تحميل", "وفيات", "مدخول", "دواء")
                    Box(Modifier.weight(1f).border(0.5.dp, Color(0xFFE0E0E0)).clickable { expC = true }.padding(10.dp)) {
                        Text(tr.category, fontSize = 9.sp, color = if(tr.category=="مدخول") Color(0xFF2E7D32) else Color.Black)
                        DropdownMenu(expanded = expC, onDismissRequest = { expC = false }) {
                            cats.forEach { c -> DropdownMenuItem(text = { Text(c) }, onClick = { tr.category = c; logic.updateCell(tr); expC = false }) }
                        }
                    }
                    var txt by remember { mutableStateOf(tr.qty.toString()) }
                    BasicTextField(value = txt, onValueChange = { txt = it; tr.qty = it.toDoubleOrNull() ?: 0.0; logic.updateCell(tr) },
                        modifier = Modifier.weight(0.6f).border(0.5.dp, Color(0xFFE0E0E0)).padding(10.dp),
                        textStyle = TextStyle(fontSize = 11.sp), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                    Text(text = String.format("%.1f", tr.qty * tr.price), modifier = Modifier.weight(0.8f).padding(4.dp), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (tr.category == "بيض تحميل" || tr.category == "مدخول") Color(0xFF2E7D32) else Color.Red)
                    IconButton(onClick = { logic.deleteRow(tr) }, Modifier.size(24.dp)) { Icon(Icons.Default.Delete, null, tint = Color.LightGray, modifier = Modifier.size(16.dp)) }
                }
                Divider(color = Color(0xFFEEEEEE), thickness = 0.5.dp)
            }
        }
    }
}

@Composable fun RowScope.HeaderCell(t: String, w: Float) = Text(t, Modifier.weight(w).padding(4.dp), Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)

@Composable fun SummaryScreen(l: ArishLogic) {
    LazyColumn(Modifier.padding(16.dp)) {
        item { Text("خلاصة المزارع", style = MaterialTheme.typography.headlineSmall, color = Color(0xFF0D47A1), fontWeight = FontWeight.Bold) }
        items(l.farms) { farm ->
            val s = l.getStats(farm)
            Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), elevation = CardDefaults.cardElevation(4.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                        Text(farm, fontWeight = FontWeight.Bold); Text("${String.format("%.1f", s.first)} $", color = if(s.first >= 0) Color(0xFF2E7D32) else Color.Red, fontWeight = FontWeight.Bold)
                    }
                    Row(Modifier.fillMaxWidth().padding(top = 8.dp), Arrangement.SpaceEvenly) {
                        Text("طيور متبقية: ${s.second}", fontSize = 11.sp); Text("بيض: ${s.third} كرتونة", fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

@Composable fun InventoryScreen(l: ArishLogic) {
    Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("تحليل المخازن", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Card(modifier = Modifier.fillMaxWidth().padding(top = 20.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFE65100))) {
            Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("رصيد Super المتبقي", color = Color.White)
                Text("${String.format("%.2f", l.getSuperStock())} كيس", fontSize = 38.sp, color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
    }
}
