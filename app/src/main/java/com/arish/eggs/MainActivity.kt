@file:OptIn(ExperimentalMaterial3Api::class)
package com.arish.eggs

import android.os.Bundle
import android.content.*
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

// --- 1. قاعدة البيانات (هيكل v14 الجديد كلياً) ---
@Entity(tableName = "arish_final_data_v14")
data class Transaction(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    var farm: String,
    var category: String,
    var qty: Double,
    var price: Double
) {
    val isIncome: Boolean get() = category == "بيض تحميل" || category == "مدخول"
    val isExpense: Boolean get() = !(category.contains("بيض") || category == "مدخول")
}

@Dao interface TransactionDao {
    @Query("SELECT * FROM arish_final_data_v14 ORDER BY id DESC")
    suspend fun getAll(): List<Transaction>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insert(tr: Transaction): Long
    @Update suspend fun update(tr: Transaction)
    @Delete suspend fun delete(tr: Transaction)
}

@Database(entities = [Transaction::class], version = 14, exportSchema = false)
abstract class ArishDatabase : RoomDatabase() { abstract fun dao(): TransactionDao }

// --- 2. المحرك المحاسبي الذكي (Logic) ---
class ArishLogic(val context: Context) {
    private var db: ArishDatabase? = null
    val transactions = mutableStateListOf<Transaction>()
    val farms = listOf("فايز الطويلة", "فايز البرشا", "فايز الألفين", "ابو حمدو العقيد", "ابو حمدو جديدة", "ابو حمدو الاخرس", "ام نضال ١", "ام نضال ٢")
    val initialBirds = mapOf("فايز الطويلة" to 7500, "فايز البرشا" to 2800, "فايز الألفين" to 2000, "ابو حمدو العقيد" to 2000, "ابو حمدو جديدة" to 3300, "ابو حمدو الاخرس" to 3800, "ام نضال ١" to 10900)

    private val scope = CoroutineScope(Dispatchers.Main + Job())

    fun initDatabase() {
        scope.launch {
            try {
                db = Room.databaseBuilder(context, ArishDatabase::class.java, "arish_ultimate_v14.db")
                    .fallbackToDestructiveMigration().build()
                val list = withContext(Dispatchers.IO) { db?.dao()?.getAll() ?: emptyList() }
                transactions.addAll(list)
            } catch (e: Exception) { }
        }
    }

    fun addRow() {
        scope.launch {
            val newTr = Transaction(farm = farms[0], category = "علف", qty = 0.0, price = 19.375)
            val id = withContext(Dispatchers.IO) { db?.dao()?.insert(newTr) ?: 0L }
            transactions.add(0, newTr.copy(id = id.toInt()))
        }
    }

    fun updateCell(tr: Transaction) {
        scope.launch(Dispatchers.IO) { try { db?.dao()?.update(tr) } catch (e: Exception) {} }
    }

    fun deleteRow(tr: Transaction) {
        scope.launch {
            withContext(Dispatchers.IO) { try { db?.dao()?.delete(tr) } catch (e: Exception) {} }
            transactions.remove(tr)
        }
    }

    fun getSuperStock(): Double = 151.55 - (transactions.filter { it.category == "علف" }.sumOf { it.qty } / 20.0)
    
    fun getProfit(f: String): Double {
        val moves = transactions.filter { it.farm == f }
        return moves.sumOf { (if(it.isIncome) it.qty * it.price else 0.0) - (if(it.isExpense) it.qty * it.price else 0.0) }
    }
}

// --- 3. الواجهة الرئيسية ---
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val logic = ArishLogic(applicationContext)
        logic.initDatabase() // تحميل في الخلفية

        setContent {
            MaterialTheme {
                var tab by rememberSaveable { mutableStateOf(1) }
                Scaffold(
                    topBar = {
                        CenterAlignedTopAppBar(
                            title = { Text("منتجات نهر اسطوان", color = Color.White, fontWeight = FontWeight.Bold) },
                            colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color(0xFF0D47A1)),
                            actions = {
                                IconButton(onClick = { 
                                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/aichaallouche100-bit/ArishApp/actions")))
                                }) { Icon(Icons.Default.CloudDownload, null, tint = Color.Yellow) }
                            }
                        )
                    },
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
                            0 -> SummaryView(logic)
                            1 -> LiveGrid(logic)
                            2 -> InventoryView(logic)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LiveGrid(logic: ArishLogic) {
    Column(Modifier.padding(4.dp)) {
        Button(onClick = { logic.addRow() }, modifier = Modifier.fillMaxWidth().height(45.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))) {
            Icon(Icons.Default.Add, null); Text(" إضافة سطر إكسل جديد")
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.background(Color(0xFF455A64)).padding(8.dp).fillMaxWidth()) {
            HeaderCell("المزرعة", 1.2f); HeaderCell("الصنف", 1f); HeaderCell("الكمية", 0.6f); HeaderCell("القيمة", 0.8f)
        }
        LazyColumn(Modifier.fillMaxSize()) {
            items(logic.transactions, key = { it.id }) { tr ->
                EditableRow(tr, logic)
                Divider(color = Color(0xFFEEEEEE), thickness = 0.5.dp)
            }
        }
    }
}

@Composable
fun EditableRow(tr: Transaction, logic: ArishLogic) {
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
            Text(tr.category, fontSize = 9.sp, color = if(tr.isIncome) Color(0xFF2E7D32) else Color.Black)
            DropdownMenu(expanded = expC, onDismissRequest = { expC = false }) {
                cats.forEach { c -> DropdownMenuItem(text = { Text(c) }, onClick = { tr.category = c; logic.updateCell(tr); expC = false }) }
            }
        }
        var txt by remember { mutableStateOf(tr.qty.toString()) }
        BasicTextField(value = txt, onValueChange = { txt = it; tr.qty = it.toDoubleOrNull() ?: 0.0; logic.updateCell(tr) },
            modifier = Modifier.weight(0.6f).border(0.5.dp, Color(0xFFE0E0E0)).padding(10.dp),
            textStyle = TextStyle(fontSize = 11.sp), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
        Text(text = String.format("%.1f", tr.qty * tr.price), modifier = Modifier.weight(0.8f).padding(8.dp), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (tr.isIncome) Color(0xFF2E7D32) else Color.Red)
        IconButton(onClick = { logic.deleteRow(tr) }, Modifier.size(24.dp)) { Icon(Icons.Default.Delete, null, tint = Color.LightGray, modifier = Modifier.size(16.dp)) }
    }
}

@Composable fun HeaderCell(t: String, w: Float) = Text(t, Modifier.weight(w).padding(4.dp), Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
@Composable fun SummaryView(l: ArishLogic) {
    LazyColumn(Modifier.padding(16.dp)) {
        item { Text("خلاصة الأرباح", style = MaterialTheme.typography.headlineSmall, color = Color(0xFF0D47A1), fontWeight = FontWeight.Bold) }
        items(l.farms) { f -> 
            Row(Modifier.fillMaxWidth().padding(12.dp), Arrangement.SpaceBetween) {
                Text(f); Text("${String.format("%.2f", l.getProfit(f))} $", color = Color(0xFF2E7D32), fontWeight = FontWeight.Bold)
            }
            Divider() 
        }
    }
}
@Composable fun InventoryView(l: ArishLogic) {
    Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("المخازن", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Card(modifier = Modifier.fillMaxWidth().padding(top = 20.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFE65100))) {
            Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("رصيد Super المتبقي", color = Color.White)
                Text("${String.format("%.2f", l.getSuperStock())} كيس", fontSize = 40.sp, color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
    }
}
