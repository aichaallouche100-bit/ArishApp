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

// --- 1. قاعدة البيانات (تم تثبيت الجداول والأسماء) ---

@Entity(tableName = "arish_master_v8")
data class Transaction(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    var farm: String,
    var category: String,
    var qty: Double,
    var price: Double
) {
    // معادلة المدخول: =IF(OR(C3="بيض تحميل"; C3="مدخول"); D3*E3; 0)
    val incomeVal: Double get() = if (category == "بيض تحميل" || category == "مدخول") qty * price else 0.0

    // معادلة المصروف: =IF(OR(ISNUMBER(SEARCH("بيض"; C3)); ISNUMBER(SEARCH("مدخول"; C3))); 0; D3*E3)
    val expenseVal: Double get() = if (category.contains("بيض") || category == "مدخول") 0.0 else qty * price
}

@Dao interface TransactionDao {
    @Query("SELECT * FROM arish_master_v8 ORDER BY id DESC")
    suspend fun getAll(): List<Transaction>
    @Insert suspend fun insert(tr: Transaction): Long
    @Update suspend fun update(tr: Transaction)
    @Delete suspend fun delete(tr: Transaction)
}

@Database(entities = [Transaction::class], version = 8, exportSchema = false)
abstract class ArishDatabase : RoomDatabase() { abstract fun dao(): TransactionDao }

// --- 2. المحرك المحاسبي الذكي (Logic) ---

class ArishLogic(val context: Context) {
    private val db = Room.databaseBuilder(context, ArishDatabase::class.java, "arish_final_secure.db")
        .fallbackToDestructiveMigration().build()
    
    val transactions = mutableStateListOf<Transaction>()
    
    // المزارع وأعداد الطيور كما في ملفاتك
    val farmInitialData = mapOf(
        "فايز الطويلة" to 7500, "فايز البرشا" to 2800, "فايز الألفين" to 2000,
        "ابو حمدو العقيد" to 2000, "ابو حمدو جديدة" to 3300, "ابو حمدو الاخرس" to 3800,
        "ام نضال ١" to 10900, "ام نضال ٢" to 0
    )

    private val scope = CoroutineScope(Dispatchers.Main + Job())

    fun loadData() {
        scope.launch {
            val list = withContext(Dispatchers.IO) { try { db.dao().getAll() } catch(e:Exception) { emptyList() } }
            transactions.clear()
            transactions.addAll(list)
        }
    }

    fun addRow() {
        scope.launch {
            val newTr = Transaction(farm = "فايز الطويلة", category = "علف", qty = 0.0, price = 19.375)
            val id = withContext(Dispatchers.IO) { db.dao().insert(newTr) }
            transactions.add(0, newTr.copy(id = id.toInt()))
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

    // الحسابات التراكمية لكل مزرعة
    fun getFarmStats(name: String): Triple<Double, Int, Double> {
        val moves = transactions.filter { it.farm == name }
        val profit = moves.sumOf { it.incomeVal - it.expenseVal }
        val deaths = moves.filter { it.category == "وفيات" }.sumOf { it.qty }.toInt()
        val eggStock = moves.filter { it.category == "بيض انتاج" }.sumOf { it.qty } - 
                       moves.filter { it.category == "بيض تحميل" }.sumOf { it.qty }
        return Triple(profit, (farmInitialData[name] ?: 0) - deaths, eggStock)
    }

    fun getSuperStock(): Double = 151.55 - (transactions.filter { it.category == "علف" }.sumOf { it.qty } / 20.0)
}

// --- 3. تصميم واجهة المستخدم (Excel Experience) ---

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val logic = ArishLogic(applicationContext)
        setContent {
            MaterialTheme {
                LaunchedEffect(Unit) { logic.loadData() }
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
                title = { Text("منتجات نهر اسطوان المحاسبي", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = navy),
                actions = {
                    IconButton(onClick = { 
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/aichaallouche100-bit/ArishApp/actions"))
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        logic.context.startActivity(intent)
                    }) { Icon(Icons.Default.CloudDownload, "Update", tint = Color.Yellow) }
                }
            )
        },
        bottomBar = {
            NavigationBar(containerColor = navy) {
                val menu = listOf("الملخص", "الجدول الحي", "المخازن")
                val icons = listOf(Icons.Default.Analytics, Icons.Default.GridOn, Icons.Default.Warehouse)
                menu.forEachIndexed { i, label ->
                    NavigationBarItem(
                        selected = tab == i,
                        onClick = { tab = i },
                        icon = { Icon(icons[i], null, tint = if(tab == i) Color.Yellow else Color.White) },
                        label = { Text(label, color = Color.White, fontSize = 10.sp) }
                    )
                }
            }
        }
    ) { p ->
        Box(Modifier.padding(p).fillMaxSize().background(Color(0xFFF1F3F4))) {
            when (tab) {
                0 -> SummaryView(logic)
                1 -> LiveGridView(logic)
                2 -> InventoryView(logic)
            }
        }
    }
}

@Composable
fun LiveGridView(logic: ArishLogic) {
    Column(Modifier.padding(4.dp)) {
        Button(
            onClick = { logic.addRow() },
            modifier = Modifier.fillMaxWidth().height(45.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
            shape = RoundedCornerShape(8.dp)
        ) {
            Icon(Icons.Default.Add, null)
            Text(" إضافة سطر إكسل جديد", fontWeight = FontWeight.Bold)
        }
        
        Spacer(Modifier.height(4.dp))

        // رأس الجدول (Excel Header)
        Row(Modifier.background(Color(0xFF455A64)).padding(8.dp).fillMaxWidth()) {
            TableCell("المزرعة", 1.2f, true); TableCell("الصنف", 1f, true)
            TableCell("الكمية", 0.6f, true); TableCell("القيمة", 0.8f, true)
        }

        LazyColumn(Modifier.fillMaxSize()) {
            items(logic.transactions, key = { it.id }) { tr ->
                Row(Modifier.background(Color.White).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    // المزرعة
                    var expF by remember { mutableStateOf(false) }
                    Box(Modifier.weight(1.2f).border(0.5.dp, Color(0xFFE0E0E0)).clickable { expF = true }.padding(10.dp)) {
                        Text(tr.farm, fontSize = 10.sp)
                        DropdownMenu(expanded = expF, onDismissRequest = { expF = false }) {
                            logic.farmInitialData.keys.forEach { f ->
                                DropdownMenuItem(text = { Text(f) }, onClick = { tr.farm = f; logic.updateCell(tr); expF = false })
                            }
                        }
                    }
                    // الصنف
                    var expC by remember { mutableStateOf(false) }
                    val cats = listOf("علف", "بيض انتاج", "بيض تحميل", "وفيات", "مدخول", "دواء")
                    Box(Modifier.weight(1f).border(0.5.dp, Color(0xFFE0E0E0)).clickable { expC = true }.padding(10.dp)) {
                        Text(tr.category, fontSize = 10.sp, color = if(tr.category == "مدخول") Color(0xFF2E7D32) else Color.Black)
                        DropdownMenu(expanded = expC, onDismissRequest = { expC = false }) {
                            cats.forEach { c -> DropdownMenuItem(text = { Text(c) }, onClick = { tr.category = c; logic.updateCell(tr); expC = false }) }
                        }
                    }
                    // الكمية
                    var txt by remember { mutableStateOf(tr.qty.toString()) }
                    BasicTextField(
                        value = txt,
                        onValueChange = { txt = it; tr.qty = it.toDoubleOrNull() ?: 0.0; logic.updateCell(tr) },
                        modifier = Modifier.weight(0.6f).border(0.5.dp, Color(0xFFE0E0E0)).padding(10.dp),
                        textStyle = TextStyle(fontSize = 11.sp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    // القيمة (محسوبة)
                    Text(
                        text = String.format("%.1f", tr.qty * tr.price),
                        modifier = Modifier.weight(0.8f).padding(8.dp),
                        fontSize = 11.sp, fontWeight = FontWeight.Bold,
                        color = if (tr.incomeVal > 0) Color(0xFF2E7D32) else Color.Red
                    )
                    IconButton(onClick = { logic.deleteRow(tr) }, Modifier.size(24.dp)) {
                        Icon(Icons.Default.Delete, null, tint = Color.LightGray, modifier = Modifier.size(16.dp))
                    }
                }
                Divider(color = Color(0xFFEEEEEE), thickness = 0.5.dp)
            }
        }
    }
}

@Composable fun SummaryView(l: ArishLogic) {
    LazyColumn(Modifier.padding(16.dp)) {
        item { Text("خلاصة المزارع والإنتاج", style = MaterialTheme.typography.headlineSmall, color = Color(0xFF0D47A1), fontWeight = FontWeight.Bold) }
        items(l.farmInitialData.keys.toList()) { farm ->
            val (profit, birds, eggs) = l.getFarmStats(farm)
            Card(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), elevation = CardDefaults.cardElevation(4.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                        Text(farm, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text("${String.format("%.1f", profit)} $", color = if(profit >= 0) Color(0xFF2E7D32) else Color.Red, fontWeight = FontWeight.Bold)
                    }
                    Divider(Modifier.padding(vertical = 8.dp))
                    Row(Modifier.fillMaxWidth(), Arrangement.SpaceEvenly) {
                        SmallStat("طيور متبقية", birds.toString())
                        SmallStat("رصيد البيض", eggs.toString())
                    }
                }
            }
        }
    }
}

@Composable fun SmallStat(l: String, v: String) = Column(horizontalAlignment = Alignment.CenterHorizontally) {
    Text(v, fontWeight = FontWeight.Bold, fontSize = 14.sp)
    Text(l, fontSize = 9.sp, color = Color.Gray)
}

@Composable fun InventoryView(l: ArishLogic) {
    Column(Modifier.padding(20.dp)) {
        Text("تحليل المخازن", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(20.dp))
        BigStatCard("رصيد Super المتبقي (كيس)", String.format("%.2f", l.getSuperStock()), Color(0xFFE65100))
        BigStatCard("إجمالي كراتين البيض", l.transactions.filter { it.category == "بيض انتاج" }.sumOf { it.qty }.minus(l.transactions.filter { it.category == "بيض تحميل" }.sumOf { it.qty }).toString(), Color(0xFF004D40))
        Spacer(Modifier.height(10.dp))
        Text("• ملاحظة: يتم خصم 1 كيس سوبر مقابل كل 20 كيس علف مستهلك.", fontSize = 11.sp, color = Color.Gray)
    }
}

@Composable fun BigStatCard(t: String, v: String, c: Color) = Card(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), colors = CardDefaults.cardColors(containerColor = c)) {
    Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(t, color = Color.White, fontSize = 14.sp); Text(v, color = Color.White, fontSize = 38.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable fun RowScope.TableCell(t: String, w: Float, h: Boolean) = Text(t, Modifier.weight(w).padding(4.dp), color = if(h) Color.White else Color.Black, fontSize = 11.sp, fontWeight = if(h) FontWeight.Bold else FontWeight.Normal)
