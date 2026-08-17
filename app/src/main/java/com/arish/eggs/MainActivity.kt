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

// --- 1. قاعدة البيانات (هيكل v24 المستقر) ---

@Entity(tableName = "arish_final_v24")
data class Transaction(
    @PrimaryKey val id: Long, 
    var farm: String,
    var category: String,
    var qty: Double,
    var price: Double
)

@Dao interface TransactionDao {
    @Query("SELECT * FROM arish_final_v24 ORDER BY id DESC")
    suspend fun getAll(): List<Transaction>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insert(tr: Transaction)
    @Update suspend fun update(tr: Transaction)
    @Delete suspend fun delete(tr: Transaction)
}

@Database(entities = [Transaction::class], version = 24, exportSchema = false)
abstract class ArishDatabase : RoomDatabase() { abstract fun dao(): TransactionDao }

// --- 2. المحرك المحاسبي بنظام التحميل المتأخر (Lazy Load) ---

class ArishLogic(val context: Context) {
    private var db: ArishDatabase? = null
    val transactions = mutableStateListOf<Transaction>()
    val farms = listOf("فايز الطويلة", "فايز البرشا", "فايز الألفين", "ابو حمدو العقيد", "ابو حمدو جديدة", "ابو حمدو الاخرس", "ام نضال ١", "ام نضال ٢")
    
    private val scope = CoroutineScope(Dispatchers.Main + Job())

    // دالة التشغيل الآمن - تعمل في الخلفية بعد فتح التطبيق
    fun startSafeEngine() {
        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    db = Room.databaseBuilder(context, ArishDatabase::class.java, "arish_v24_final.db")
                        .fallbackToDestructiveMigration()
                        .build()
                    val list = db?.dao()?.getAll() ?: emptyList()
                    withContext(Dispatchers.Main) {
                        transactions.clear()
                        transactions.addAll(list)
                    }
                }
            } catch (e: Exception) { }
        }
    }

    fun addRow() {
        scope.launch {
            val newTr = Transaction(id = System.currentTimeMillis(), farm = farms[0], category = "علف", qty = 0.0, price = 19.375)
            transactions.add(0, newTr)
            withContext(Dispatchers.IO) { db?.dao()?.insert(newTr) }
        }
    }

    fun updateCell(tr: Transaction) {
        scope.launch(Dispatchers.IO) { db?.dao()?.update(tr) }
    }

    fun deleteRow(tr: Transaction) {
        scope.launch {
            transactions.remove(tr)
            withContext(Dispatchers.IO) { db?.dao()?.delete(tr) }
        }
    }

    fun getSuperStock(): Double = 151.55 - (transactions.filter { it.category == "علف" }.sumOf { it.qty } / 20.0)
    
    fun getProfit(f: String): Double {
        val m = transactions.filter { it.farm == f }
        val inc = m.sumOf { if(it.category == "بيض تحميل" || it.category == "مدخول") it.qty * it.price else 0.0 }
        val exp = m.sumOf { if(!(it.category.contains("بيض") || it.category == "مدخول")) it.qty * it.price else 0.0 }
        return inc - exp
    }
}

// --- 3. الواجهة الرئيسية (تفتح فوراً) ---

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val logic = ArishLogic(applicationContext)

        setContent {
            MaterialTheme {
                // الانتظار حتى تظهر الواجهة ثم تشغيل المحرك
                LaunchedEffect(Unit) {
                    delay(2000) // 2 ثانية أمان للتابلت
                    logic.startSafeEngine()
                }
                MainScreen(logic)
            }
        }
    }
}

@Composable
fun MainScreen(logic: ArishLogic) {
    var tab by rememberSaveable { mutableStateOf(1) }
    val navy = Color(0xFF0D47A1)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("نهر اسطوان المحاسبي", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = navy),
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
                    NavigationBarItem(selected = tab == i, onClick = { tab = i }, icon = { Icon(icons[i], null, tint = if(tab == i) Color.Yellow else Color.White) }, label = { Text(label, color = Color.White, fontSize = 10.sp) })
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
        Button(onClick = { logic.addRow() }, modifier = Modifier.fillMaxWidth().height(45.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))) {
            Icon(Icons.Default.Add, null); Text(" إضافة سطر جديد")
        }
        Spacer(Modifier.height(4.dp))
        Row(Modifier.background(Color(0xFF455A64)).padding(8.dp).fillMaxWidth()) {
            HeaderCell("المزرعة", 1.2f); HeaderCell("الصنف", 1f); HeaderCell("الكمية", 0.6f); HeaderCell("القيمة", 0.8f)
        }
        LazyColumn(Modifier.fillMaxSize()) {
            items(logic.transactions, key = { it.id }) { tr ->
                EditableRow(tr, logic)
                Divider(color = Color(0xFFEEEEEE))
            }
        }
    }
}

@Composable
fun EditableRow(tr: Transaction, logic: ArishLogic) {
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
            Text(tr.category, fontSize = 9.sp, color = if(tr.category=="مدخول") Color(0xFF2E7D32) else Color.Black)
            DropdownMenu(expanded = expC, onDismissRequest = { expC = false }) {
                cats.forEach { c -> DropdownMenuItem(text = { Text(c) }, onClick = { tr.category = c; logic.updateCell(tr); expC = false }) }
            }
        }
        var txt by remember { mutableStateOf(tr.qty.toString()) }
        BasicTextField(value = txt, onValueChange = { txt = it; tr.qty = it.toDoubleOrNull() ?: 0.0; logic.updateCell(tr) },
            modifier = Modifier.weight(0.6f).border(0.5.dp, Color.LightGray).padding(10.dp),
            textStyle = TextStyle(fontSize = 11.sp), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
        Text(text = String.format("%.1f", tr.qty * tr.price), modifier = Modifier.weight(0.8f).padding(4.dp), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (tr.category == "بيض تحميل" || tr.category == "مدخول") Color(0xFF2E7D32) else Color.Red)
        IconButton(onClick = { logic.deleteRow(tr) }) { Icon(Icons.Default.Delete, null, tint = Color.LightGray) }
    }
}

@Composable fun RowScope.HeaderCell(t: String, w: Float) = Text(t, Modifier.weight(w).padding(4.dp), Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
@Composable fun SummaryView(l: ArishLogic) { Text("شاشة الأرباح (سيتم تحميلها بعد قليل...)", Modifier.padding(16.dp)) }
@Composable fun InventoryView(l: ArishLogic) { Text("رصيد السوبر: ${String.format("%.2f", l.getSuperStock())} كيس", Modifier.padding(16.dp)) }
