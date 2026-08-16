@file:OptIn(ExperimentalMaterial3Api::class) // حل مشكلة الـ Experimental API

package com.arish.eggs

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import java.util.*

// --- 1. النماذج المحاسبية ---

data class Transaction(
    val id: String = UUID.randomUUID().toString(),
    val farm: String,
    val category: String,
    val qty: Double,
    val price: Double
)

// --- 2. محرك النظام (تم إصلاح المعادلات) ---

class ArishSystem {
    var feedTonPrice by mutableStateOf(387.5)
    var tonToBagRatio by mutableStateOf(20.0)
    var eggDefaultPrice by mutableStateOf(2.25)
    var initialSuperStock by mutableStateOf(151.55)

    val farms = listOf(
        "فايز الطويلة", "فايز البرشا", "فايز الألفين", "ابو حمدو العقيد",
        "ابو حمدو جديدة", "ابو حمدو الاخرس", "ام نضال ١", "ام نضال ٢"
    )

    // السجل الرئيسي للحركات
    val transactions = mutableStateListOf<Transaction>()

    // المعادلات حسب طلبك الدقيق
    fun isIncome(cat: String): Boolean = cat == "بيض تحميل" || cat == "مدخول"
    
    fun isExpense(cat: String): Boolean {
        // المصروف: ليس بيض وليس مدخول
        return !(cat.contains("بيض") || cat == "مدخول")
    }

    fun calculateProfit(farmName: String): Double {
        val moves = transactions.filter { it.farm == farmName }
        val income = moves.filter { isIncome(it.category) }.sumOf { it.qty * it.price }
        val expense = moves.filter { isExpense(it.category) }.sumOf { it.qty * it.price }
        return income - expense
    }

    fun getSuperStock(): Double {
        val consumedFeed = transactions.filter { it.category == "علف" }.sumOf { it.qty }
        return initialSuperStock - (consumedFeed / tonToBagRatio)
    }
}

// --- 3. واجهة المستخدم ---

class MainActivity : ComponentActivity() {
    private val system = ArishSystem()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme { MainContainer(system) }
        }
    }
}

@Composable
fun MainContainer(system: ArishSystem) {
    var tab by rememberSaveable { mutableStateOf(0) } // يحفظ رقم الصفحة عند تدوير الشاشة
    val navy = Color(0xFF0D47A1)

    Scaffold(
        bottomBar = {
            NavigationBar(containerColor = navy) {
                val menu = listOf("الملخص", "الجدول", "المخزون", "القواعد")
                val icons = listOf(Icons.Default.Dashboard, Icons.Default.GridOn, Icons.Default.Inventory, Icons.Default.Settings)
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
                0 -> SummaryView(system)
                1 -> ExcelGridView(system)
                2 -> InventoryView(system)
                3 -> RulesEditorView(system)
            }
        }
    }
}

@Composable
fun ExcelGridView(system: ArishSystem) {
    var qty by remember { mutableStateOf("") }
    var selectedFarm by remember { mutableStateOf(system.farms[0]) }
    var selectedCat by remember { mutableStateOf("علف") }
    var showFarmMenu by remember { mutableStateOf(false) }

    Column(Modifier.padding(8.dp)) {
        Card(elevation = CardDefaults.cardElevation(4.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1.5f)) {
                        Button(onClick = { showFarmMenu = true }) { Text(selectedFarm, fontSize = 10.sp) }
                        DropdownMenu(expanded = showFarmMenu, onDismissRequest = { showFarmMenu = false }) {
                            system.farms.forEach { f ->
                                DropdownMenuItem(text = { Text(f) }, onClick = { selectedFarm = f; showFarmMenu = false })
                            }
                        }
                    }
                    Spacer(Modifier.width(4.dp))
                    OutlinedTextField(
                        value = qty,
                        onValueChange = { qty = it },
                        placeholder = { Text("الكمية") },
                        modifier = Modifier.weight(1f).height(55.dp)
                    )
                    IconButton(onClick = {
                        val q = qty.toDoubleOrNull() ?: 0.0
                        val p = if(selectedCat == "علف") (system.feedTonPrice/system.tonToBagRatio) else system.eggDefaultPrice
                        system.transactions.add(Transaction(farm = selectedFarm, category = selectedCat, qty = q, price = p))
                        qty = ""
                    }) { Icon(Icons.Default.AddCircle, null, tint = Color(0xFF2E7D32), modifier = Modifier.size(40.dp)) }
                }

                Row(Modifier.horizontalScroll(rememberScrollState()).padding(top = 8.dp)) {
                    val cats = listOf("علف", "بيض انتاج", "بيض تحميل", "وفيات", "مدخول", "دواء")
                    cats.forEach { cat ->
                        FilterChip(
                            selected = selectedCat == cat,
                            onClick = { selectedCat = cat },
                            label = { Text(cat, fontSize = 10.sp) },
                            modifier = Modifier.padding(horizontal = 2.dp)
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        LazyColumn(Modifier.fillMaxSize().border(1.dp, Color.LightGray)) {
            item {
                Row(Modifier.background(Color(0xFF455A64)).padding(8.dp)) {
                    Cell("المزرعة", 1.5f, isHeader = true)
                    Cell("الصنف", 1f, isHeader = true)
                    Cell("الكمية", 0.7f, isHeader = true)
                    Cell("القيمة", 1f, isHeader = true)
                }
            }
            items(system.transactions.reversed()) { tr ->
                Row(Modifier.background(Color.White).border(0.2.dp, Color(0xFFEEEEEE))) {
                    Cell(tr.farm, 1.5f)
                    Cell(tr.category, 1f)
                    Cell(tr.qty.toString(), 0.7f)
                    val dispVal = tr.qty * tr.price
                    val color = if(system.isIncome(tr.category)) Color(0xFF2E7D32) else Color.Red
                    Cell("${String.format("%.2f", dispVal)}$", 1f, customColor = color)
                }
            }
        }
    }
}

@Composable
fun RowScope.Cell(text: String, weight: Float, isHeader: Boolean = false, customColor: Color? = null) {
    val finalColor = when {
        isHeader -> Color.White
        customColor != null -> customColor
        else -> Color.Black
    }
    Text(
        text = text,
        modifier = Modifier.weight(weight).border(0.5.dp, Color.LightGray).padding(8.dp),
        color = finalColor,
        fontSize = 11.sp,
        fontWeight = if(isHeader) FontWeight.Bold else FontWeight.Normal
    )
}

@Composable
fun SummaryView(system: ArishSystem) {
    LazyColumn(modifier = Modifier.padding(16.dp)) {
        item { Text("خلاصة المزارع", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) }
        items(system.farms) { farmName ->
            Card(Modifier.fillMaxWidth().padding(vertical = 4.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Row(Modifier.padding(16.dp), Arrangement.SpaceBetween) {
                    Text(farmName, fontWeight = FontWeight.Bold)
                    Text("${String.format("%.2f", system.calculateProfit(farmName))} $", color = Color(0xFF2E7D32))
                }
            }
        }
    }
}

@Composable
fun InventoryView(system: ArishSystem) {
    Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("المخازن", fontWeight = FontWeight.Bold, fontSize = 20.sp)
        Spacer(Modifier.height(20.dp))
        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFE65100)), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("رصيد Super المتبقي", color = Color.White)
                Text("${String.format("%.2f", system.getSuperStock())} كيس", fontSize = 35.sp, color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun RulesEditorView(system: ArishSystem) {
    var price by remember { mutableStateOf(system.feedTonPrice.toString()) }
    Column(Modifier.padding(16.dp)) {
        Text("قواعد الحساب", style = MaterialTheme.typography.headlineSmall)
        OutlinedTextField(value = price, onValueChange = {price = it}, label = { Text("سعر طن العلف") }, modifier = Modifier.fillMaxWidth())
        Button(onClick = { system.feedTonPrice = price.toDoubleOrNull() ?: 387.5 }, modifier = Modifier.padding(top = 10.dp)) {
            Text("تحديث القاعدة")
        }
    }
}
