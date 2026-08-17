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
import androidx.compose.ui.unit.dp // تم إضافة هذا السطر لحل المشكلة
import androidx.compose.ui.unit.sp // تم إضافة هذا السطر لحل المشكلة
import androidx.room.*
import java.util.*

// 1. قاعدة البيانات (هيكل v18 المحسن)
@Entity(tableName = "arish_table_v18")
data class Transaction(
    @PrimaryKey val id: Long, 
    var farm: String,
    var category: String,
    var qty: Double,
    var price: Double
)

@Dao interface TransactionDao {
    @Query("SELECT * FROM arish_table_v18 ORDER BY id DESC")
    fun getAll(): List<Transaction>
    @Insert(onConflict = OnConflictStrategy.REPLACE) fun insert(tr: Transaction)
    @Update fun update(tr: Transaction)
    @Delete fun delete(tr: Transaction)
}

@Database(entities = [Transaction::class], version = 18, exportSchema = false)
abstract class ArishDatabase : RoomDatabase() { abstract fun dao(): TransactionDao }

// 2. المحرك المحاسبي
class ArishLogic(val context: Context) {
    private val db = Room.databaseBuilder(context, ArishDatabase::class.java, "arish_v18_final.db")
        .allowMainThreadQueries().fallbackToDestructiveMigration().build()
    
    val transactions = mutableStateListOf<Transaction>()
    val farms = listOf("فايز الطويلة", "فايز البرشا", "فايز الألفين", "ابو حمدو العقيد", "ابو حمدو جديدة", "ابو حمدو الاخرس", "ام نضال ١", "ام نضال ٢")
    val farmInitialBirds = mapOf("فايز الطويلة" to 7500, "فايز البرشا" to 2800, "فايز الألفين" to 2000, "ابو حمدو العقيد" to 2000, "ابو حمدو جديدة" to 3300, "ابو حمدو الاخرس" to 3800, "ام نضال ١" to 10900)

    fun load() {
        transactions.clear()
        transactions.addAll(db.dao().getAll())
    }

    fun addRow() {
        val newTr = Transaction(id = System.currentTimeMillis(), farm = farms[0], category = "علف", qty = 0.0, price = 19.375)
        db.dao().insert(newTr)
        transactions.add(0, newTr)
    }

    fun updateCell(tr: Transaction) { db.dao().update(tr) }
    fun deleteRow(tr: Transaction) { db.dao().delete(tr); transactions.remove(tr) }

    fun getSuperStock(): Double = 151.55 - (transactions.filter { it.category == "علف" }.sumOf { it.qty } / 20.0)
    
    fun getStats(f: String): Triple<Double, Int, Double> {
        val m = transactions.filter { it.farm == f }
        val inc = m.filter { it.category == "بيض تحميل" || it.category == "مدخول" }.sumOf { it.qty * it.price }
        val exp = m.filter { !(it.category.contains("بيض") || it.category == "مدخول") }.sumOf { it.qty * it.price }
        val d = m.filter { it.category == "وفيات" }.sumOf { it.qty }.toInt()
        val e = m.filter { it.category == "بيض انتاج" }.sumOf { it.qty } - m.filter { it.category == "بيض تحميل" }.sumOf { it.qty }
        return Triple(inc - exp, (farmInitialBirds[f] ?: 0) - d, e)
    }
}

// 3. الواجهة الرئيسية
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val logic = ArishLogic(applicationContext)
        logic.load()
        setContent { MaterialTheme { MainApp(logic) } }
    }
}

@Composable
fun MainApp(logic: ArishLogic) {
    var tab by rememberSaveable { mutableStateOf(1) }
    Scaffold(
        bottomBar = {
            NavigationBar(containerColor = Color(0xFF0D47A1)) {
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
                1 -> LiveGrid(logic)
                2 -> InventoryScreen(logic)
            }
        }
    }
}

@Composable
fun LiveGrid(logic: ArishLogic) {
    Column(Modifier.padding(4.dp)) {
        Button(onClick = { logic.addRow() }, modifier = Modifier.fillMaxWidth().height(45.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))) {
            Icon(Icons.Default.Add, null); Text(" إضافة سطر جديد")
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.background(Color(0xFF455A64)).padding(8.dp).fillMaxWidth()) {
            TableCell("المزرعة", 1.2f); TableCell("الصنف", 1f); TableCell("الكمية", 0.6f); TableCell("القيمة", 0.8f)
        }
        LazyColumn(Modifier.fillMaxSize()) {
            items(logic.transactions, key = { it.id }) { tr ->
                Row(Modifier.background(Color.White).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    var expF by remember { mutableStateOf(false) }
                    Box(Modifier.weight(1.2f).border(0.5.dp, Color.LightGray).clickable { expF = true }.padding(10.dp)) {
                        Text(tr.farm, fontSize = 9.sp)
                        DropdownMenu(expanded = expF, onDismissRequest = { expF = false }) {
                            logic.farms.forEach { f -> DropdownMenuItem(text = { Text(f) }, onClick = { tr.farm = f; logic.updateCell(tr); expF = false }) }
                        }
                    }
                    var expC by remember { mutableStateOf(false) }
                    val cats = listOf("علف", "بيض انتاج", "بيض تحميل", "وفيات", "مدخول", "دواء")
                    Box(Modifier.weight(1f).border(0.5.dp, Color.LightGray).clickable { expC = true }.padding(10.dp)) {
                        Text(tr.category, fontSize = 9.sp)
                        DropdownMenu(expanded = expC, onDismissRequest = { expC = false }) {
                            cats.forEach { c -> DropdownMenuItem(text = { Text(c) }, onClick = { tr.category = c; logic.updateCell(tr); expC = false }) }
                        }
                    }
                    var txt by remember { mutableStateOf(tr.qty.toString()) }
                    BasicTextField(value = txt, onValueChange = { txt = it; tr.qty = it.toDoubleOrNull() ?: 0.0; logic.updateCell(tr) },
                        modifier = Modifier.weight(0.6f).border(0.5.dp, Color.LightGray).padding(10.dp),
                        textStyle = TextStyle(fontSize = 11.sp), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                    Text(text = String.format("%.1f", tr.qty * tr.price), modifier = Modifier.weight(0.8f).padding(8.dp), fontSize = 11.sp, color = if (tr.category == "بيض تحميل" || tr.category == "مدخول") Color(0xFF2E7D32) else Color.Red)
                    IconButton(onClick = { logic.deleteRow(tr) }) { Icon(Icons.Default.Delete, null, tint = Color.LightGray, modifier = Modifier.size(18.dp)) }
                }
                Divider(thickness = 0.5.dp)
            }
        }
    }
}

@Composable fun RowScope.TableCell(t: String, w: Float) = Text(t, Modifier.weight(w).padding(4.dp), Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)

@Composable fun SummaryScreen(l: ArishLogic) {
    LazyColumn(Modifier.padding(16.dp)) {
        items(l.farms) { f -> 
            val s = l.getStats(f)
            Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Text(f, fontWeight = FontWeight.Bold)
                    Text("الربح: ${String.format("%.1f", s.first)} $", color = Color(0xFF2E7D32))
                    Text("طيور: ${s.second} | بيض: ${s.third}", fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable fun InventoryScreen(l: ArishLogic) {
    Column(Modifier.padding(20.dp)) {
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFFE65100))) {
            Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("رصيد Super المتبقي", color = Color.White)
                Text("${String.format("%.2f", l.getSuperStock())} كيس", fontSize = 35.sp, color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
    }
}
