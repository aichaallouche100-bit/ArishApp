@file:OptIn(ExperimentalMaterial3Api::class)

package com.arish.eggs

import android.os.Bundle
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape // الإصلاح هنا
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
import java.util.*

// --- 1. قاعدة البيانات (Room Database) ---

@Entity(tableName = "transactions")
data class Transaction(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    var farm: String,
    var category: String,
    var qty: Double,
    var price: Double
) {
    val incomeValue: Double get() = if (category == "بيض تحميل" || category == "مدخول") qty * price else 0.0
    val expenseValue: Double get() = if (category.contains("بيض") || category == "مدخول") 0.0 else qty * price
}

@Dao
interface TransactionDao {
    @Query("SELECT * FROM transactions ORDER BY id DESC")
    fun getAll(): List<Transaction>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(tr: Transaction): Long
    @Update
    fun update(tr: Transaction)
    @Delete
    fun delete(tr: Transaction)
}

@Database(entities = [Transaction::class], version = 1)
abstract class ArishDatabase : RoomDatabase() {
    abstract fun dao(): TransactionDao
}

// --- 2. المحرك المحاسبي الذكي ---

class ArishLogic(context: Context) {
    private val db = Room.databaseBuilder(context, ArishDatabase::class.java, "arish_db")
        .allowMainThreadQueries().build()
    
    val transactions = mutableStateListOf<Transaction>()
    val farms = listOf("فايز الطويلة", "فايز البرشا", "فايز الألفين", "ابو حمدو العقيد", "ابو حمدو جديدة", "ابو حمدو الاخرس", "ام نضال ١", "ام نضال ٢")

    init {
        transactions.addAll(db.dao().getAll())
    }

    fun addNewBlankRow() {
        val newTr = Transaction(farm = farms[0], category = "علف", qty = 0.0, price = 19.375)
        val id = db.dao().insert(newTr)
        transactions.add(0, newTr.copy(id = id.toInt()))
    }

    fun updateCell(tr: Transaction) {
        db.dao().update(tr)
        val index = transactions.indexOfFirst { it.id == tr.id }
        if (index != -1) transactions[index] = tr.copy() // تفعيل التحديث اللحظي
    }

    fun deleteRow(tr: Transaction) {
        db.dao().delete(tr)
        transactions.remove(tr)
    }

    fun calculateProfit(farmName: String): Double {
        val moves = transactions.filter { it.farm == farmName }
        return moves.sumOf { it.incomeValue } - moves.sumOf { it.expenseValue }
    }

    fun getSuperStock(): Double {
        val consumed = transactions.filter { it.category == "علف" }.sumOf { it.qty }
        return 151.55 - (consumed / 20.0)
    }
}

// --- 3. واجهة المستخدم ---

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val logic = ArishLogic(this)
        setContent { MaterialTheme { MainApp(logic) } }
    }
}

@Composable
fun MainApp(logic: ArishLogic) {
    var tab by rememberSaveable { mutableStateOf(1) }
    val navy = Color(0xFF0D47A1)

    Scaffold(
        bottomBar = {
            NavigationBar(containerColor = navy) {
                val menu = listOf("الملخص", "الجدول الحي", "المخزون")
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
                0 -> SummaryScreen(logic)
                1 -> LiveExcelGrid(logic)
                2 -> InventoryScreen(logic)
            }
        }
    }
}

@Composable
fun LiveExcelGrid(logic: ArishLogic) {
    Column(Modifier.padding(4.dp)) {
        Button(
            onClick = { logic.addNewBlankRow() },
            modifier = Modifier.fillMaxWidth().height(45.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
            shape = RoundedCornerShape(8.dp)
        ) {
            Icon(Icons.Default.Add, null)
            Text("إضافة سطر جديد للجدول")
        }

        Spacer(Modifier.height(4.dp))

        Row(Modifier.background(Color(0xFF455A64)).padding(6.dp).fillMaxWidth()) {
            HeaderCell("المزرعة", 1.2f)
            HeaderCell("الصنف", 1f)
            HeaderCell("الكمية", 0.6f)
            HeaderCell("القيمة", 0.8f)
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
        // المزرعة
        var expF by remember { mutableStateOf(false) }
        Box(Modifier.weight(1.2f).border(0.5.dp, Color.LightGray).clickable { expF = true }.padding(8.dp)) {
            Text(tr.farm, fontSize = 10.sp)
            DropdownMenu(expanded = expF, onDismissRequest = { expF = false }) {
                logic.farms.forEach { f ->
                    DropdownMenuItem(text = { Text(f) }, onClick = {
                        tr.farm = f
                        logic.updateCell(tr)
                        expF = false
                    })
                }
            }
        }

        // الصنف
        var expC by remember { mutableStateOf(false) }
        val cats = listOf("علف", "بيض انتاج", "بيض تحميل", "وفيات", "مدخول", "دواء")
        Box(Modifier.weight(1f).border(0.5.dp, Color.LightGray).clickable { expC = true }.padding(8.dp)) {
            Text(tr.category, fontSize = 10.sp, color = if(tr.category == "مدخول") Color(0xFF2E7D32) else Color.Black)
            DropdownMenu(expanded = expC, onDismissRequest = { expC = false }) {
                cats.forEach { c ->
                    DropdownMenuItem(text = { Text(c) }, onClick = {
                        tr.category = c
                        logic.updateCell(tr)
                        expC = false
                    })
                }
            }
        }

        // الكمية
        EditableCell(tr.qty.toString(), Modifier.weight(0.6f)) {
            tr.qty = it.toDoubleOrNull() ?: 0.0
            logic.updateCell(tr)
        }

        // القيمة النهائية
        val total = tr.qty * tr.price
        Text(
            text = String.format("%.1f", total),
            modifier = Modifier.weight(0.8f).padding(8.dp),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = if (tr.incomeValue > 0) Color(0xFF2E7D32) else Color.Red
        )
        
        // زر الحذف
        IconButton(onClick = { logic.deleteRow(tr) }, Modifier.size(24.dp)) {
            Icon(Icons.Default.Delete, null, tint = Color.Gray, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
fun EditableCell(value: String, modifier: Modifier, onValueChange: (String) -> Unit) {
    var textState by remember { mutableStateOf(value) }
    BasicTextField(
        value = textState,
        onValueChange = { 
            textState = it
            onValueChange(it) 
        },
        modifier = modifier.border(0.5.dp, Color.LightGray).padding(8.dp),
        textStyle = TextStyle(fontSize = 11.sp),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
    )
}

@Composable
fun RowScope.HeaderCell(text: String, weight: Float) {
    Text(text, Modifier.weight(weight).padding(4.dp), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
}

@Composable
fun SummaryScreen(logic: ArishLogic) {
    LazyColumn(Modifier.padding(16.dp)) {
        item { Text("أرباح المزارع التراكمية", style = MaterialTheme.typography.headlineSmall, color = Color(0xFF0D47A1)) }
        items(logic.farms) { farm ->
            Row(Modifier.fillMaxWidth().padding(12.dp), Arrangement.SpaceBetween) {
                Text(farm, fontWeight = FontWeight.Medium)
                Text("${String.format("%.2f", logic.calculateProfit(farm))} $", color = Color(0xFF2E7D32), fontWeight = FontWeight.Bold)
            }
            Divider()
        }
    }
}

@Composable
fun InventoryScreen(logic: ArishLogic) {
    Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("رصيد المستودع (السحابي)", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(20.dp))
        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFE65100)), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("رصيد Super المتبقي", color = Color.White)
                Text("${String.format("%.2f", logic.getSuperStock())} كيس", fontSize = 40.sp, color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
    }
}
