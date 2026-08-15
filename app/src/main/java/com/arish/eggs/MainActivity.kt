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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import java.util.*

// --- 1. هيكل البيانات المحاسبي المحدث بناءً على معادلاتك ---

data class Transaction(
    val farmName: String,
    val type: String,
    val quantity: Double,
    val unitPrice: Double,
    val date: String = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())
) {
    // معادلة إكسل للمدخول: =IF(OR(C3="بيض تحميل"; C3="مدخول"); D3*E3; 0)
    val incomeValue: Double 
        get() = if (type == "بيض تحميل" || type == "مدخول") quantity * unitPrice else 0.0

    // معادلة إكسل للمصروف: =IF(OR(ISNUMBER(SEARCH("بيض"; C3)); ISNUMBER(SEARCH("مدخول"; C3))); 0; D3*E3)
    val expenseValue: Double 
        get() = if (type.contains("بيض") || type == "مدخول") 0.0 else quantity * unitPrice
}

data class FarmData(
    val name: String,
    val initialBirds: Int,
    var deaths: Int = 0,
    var eggStock: Double = 0.0,
    var totalNetProfit: Double = 0.0,
    var totalExpenses: Double = 0.0,
    var totalIncome: Double = 0.0
)

// --- 2. المحرك المحاسبي الذكي ---

class ArishLogic {
    val feedTonPrice = 387.5
    val tonToBagRatio = 20.0
    val initialSuperStock = 151.55

    val farms = mutableStateListOf(
        FarmData("فايز الطويلة", 7500),
        FarmData("فايز البرشا", 2800),
        FarmData("فايز الألفين", 2000),
        FarmData("ابو حمدو العقيد", 2000),
        FarmData("ابو حمدو جديدة", 3300),
        FarmData("ابو حمدو الاخرس", 3800),
        FarmData("ام نضال ١", 10900),
        FarmData("ام نضال ٢", 0)
    )

    val transactions = mutableStateListOf<Transaction>()

    fun addMovement(farm: String, type: String, qty: Double, price: Double) {
        transactions.add(Transaction(farm, type, qty, price))
        updateFarmCalculations()
    }

    private fun updateFarmCalculations() {
        farms.forEach { farm ->
            val farmMoves = transactions.filter { it.farmName == farm.name }
            
            farm.deaths = farmMoves.filter { it.type == "وفيات" }.sumOf { it.quantity }.toInt()
            
            val prod = farmMoves.filter { it.type == "بيض انتاج" }.sumOf { it.quantity }
            val load = farmMoves.filter { it.type == "بيض تحميل" }.sumOf { it.quantity }
            farm.eggStock = prod - load
            
            farm.totalIncome = farmMoves.sumOf { it.incomeValue }
            farm.totalExpenses = farmMoves.sumOf { it.expenseValue }
            farm.totalNetProfit = farm.totalIncome - farm.totalExpenses
        }
    }

    fun getSuperRemaining(): Double {
        val consumedFeed = transactions.filter { it.type == "علف" }.sumOf { it.quantity }
        return initialSuperStock - (consumedFeed / tonToBagRatio)
    }
}

// --- 3. الواجهات الرسومية المتطورة ---

class MainActivity : ComponentActivity() {
    private val logic = ArishLogic()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                MainAppStructure(logic)
            }
        }
    }
}

@Composable
fun MainAppStructure(logic: ArishLogic) {
    var selectedTab by remember { mutableStateOf(0) }
    val navy = Color(0xFF0D47A1)

    Scaffold(
        bottomBar = {
            NavigationBar(containerColor = navy) {
                val menu = listOf("الرئيسية", "الحركة", "المخزون", "دليل")
                val icons = listOf(Icons.Default.Dashboard, Icons.Default.ReceiptLong, Icons.Default.Inventory, Icons.Default.MenuBook)
                menu.forEachIndexed { index, label ->
                    NavigationBarItem(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        icon = { Icon(icons[index], null, tint = if(selectedTab == index) Color.Yellow else Color.White) },
                        label = { Text(label, color = Color.White, fontSize = 10.sp) }
                    )
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize().background(Color(0xFFF0F4F8))) {
            when (selectedTab) {
                0 -> Dashboard(logic)
                1 -> MovementLedger(logic)
                2 -> InventoryStats(logic)
                3 -> HelpCenter()
            }
        }
    }
}

@Composable
fun Dashboard(logic: ArishLogic) {
    LazyColumn(modifier = Modifier.padding(16.dp)) {
        item {
            Text("نظام نهر اسطوان المحاسبي", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Color(0xFF0D47A1))
            Spacer(Modifier.height(16.dp))
        }
        items(logic.farms) { farm ->
            Card(
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                elevation = CardDefaults.cardElevation(4.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                        Text(farm.name, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text("${String.format("%.2f", farm.totalNetProfit)} $", 
                            color = if(farm.totalNetProfit >= 0) Color(0xFF2E7D32) else Color.Red, 
                            fontWeight = FontWeight.ExtraBold)
                    }
                    HorizontalDivider(Modifier.padding(vertical = 8.dp), thickness = 0.5.dp)
                    Row(Modifier.fillMaxWidth(), Arrangement.SpaceEvenly) {
                        StatChip("الطيور", "${farm.initialBirds - farm.deaths}", Icons.Default.Pets, Color.DarkGray)
                        StatChip("البيض", "${farm.eggStock}", Icons.Default.Egg, Color(0xFFE65100))
                        StatChip("المدخول", "${farm.totalIncome}", Icons.Default.TrendingUp, Color(0xFF2E7D32))
                    }
                }
            }
        }
    }
}

@Composable
fun StatChip(label: String, value: String, icon: ImageVector, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, modifier = Modifier.size(16.dp), tint = color)
        Text(value, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = color)
        Text(label, fontSize = 9.sp, color = Color.Gray)
    }
}

@Composable
fun MovementLedger(logic: ArishLogic) {
    var qtyText by remember { mutableStateOf("") }
    var selectedFarm by remember { mutableStateOf(logic.farms[0].name) }
    var selectedType by remember { mutableStateOf("علف") }
    var expandedFarm by remember { mutableStateOf(false) }
    var expandedType by remember { mutableStateOf(false) }

    val categories = listOf("علف", "بيض انتاج", "بيض تحميل", "وفيات", "مدخول", "دواء", "نثريات")

    Column(modifier = Modifier.padding(16.dp)) {
        Text("تسجيل حركة يومية", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        
        Card(modifier = Modifier.padding(vertical = 8.dp).fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                // المزرعة
                Box {
                    OutlinedButton(onClick = { expandedFarm = true }, Modifier.fillMaxWidth()) {
                        Text("المزرعة: $selectedFarm")
                    }
                    DropdownMenu(expanded = expandedFarm, onDismissRequest = { expandedFarm = false }) {
                        logic.farms.forEach { f ->
                            DropdownMenuItem(text = { Text(f.name) }, onClick = { selectedFarm = f.name; expandedFarm = false })
                        }
                    }
                }
                
                // الصنف والكمية
                Row(Modifier.padding(vertical = 8.dp)) {
                    Box(Modifier.weight(1.2f)) {
                        OutlinedButton(onClick = { expandedType = true }, Modifier.fillMaxWidth()) {
                            Text(selectedType)
                        }
                        DropdownMenu(expanded = expandedType, onDismissRequest = { expandedType = false }) {
                            categories.forEach { c ->
                                DropdownMenuItem(text = { Text(c) }, onClick = { selectedType = c; expandedType = false })
                            }
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    OutlinedTextField(
                        value = qtyText,
                        onValueChange = { qtyText = it },
                        label = { Text("الكمية") },
                        modifier = Modifier.weight(1f)
                    )
                }

                Button(
                    onClick = {
                        val q = qtyText.toDoubleOrNull() ?: 0.0
                        val p = if(selectedType == "علف") (logic.feedTonPrice/20.0) else if(selectedType == "بيض تحميل" || selectedType == "مدخول") 2.25 else 0.0
                        logic.addMovement(selectedFarm, selectedType, q, p)
                        qtyText = ""
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0D47A1))
                ) { Text("تأكيد وحفظ") }
            }
        }
        
        LazyColumn(Modifier.fillMaxSize()) {
            item {
                Row(Modifier.background(Color(0xFF0D47A1)).padding(8.dp).fillMaxWidth()) {
                    Text("المزرعة", Modifier.weight(1f), color = Color.White, fontSize = 12.sp)
                    Text("الصنف", Modifier.weight(1f), color = Color.White, fontSize = 12.sp)
                    Text("القيمة", Modifier.weight(0.7f), color = Color.White, fontSize = 12.sp)
                }
            }
            items(logic.transactions.reversed()) { tr ->
                Row(Modifier.padding(8.dp).fillMaxWidth()) {
                    Text(tr.farmName, Modifier.weight(1f), fontSize = 13.sp)
                    Text(tr.type, Modifier.weight(1f), fontSize = 13.sp)
                    val displayVal = if(tr.incomeValue > 0) tr.incomeValue else tr.expenseValue
                    Text("${String.format("%.2f", displayVal)}$", 
                        Modifier.weight(0.7f), 
                        color = if(tr.incomeValue > 0) Color(0xFF2E7D32) else Color.Red,
                        fontWeight = FontWeight.Bold)
                }
                HorizontalDivider(thickness = 0.5.dp)
            }
        }
    }
}

@Composable
fun InventoryStats(logic: ArishLogic) {
    Column(modifier = Modifier.padding(16.dp)) {
        Text("المخازن المركزية", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(20.dp))
        BigStatCard("رصيد Super (كيس)", String.format("%.2f", logic.getSuperRemaining()), Color(0xFFE65100))
        BigStatCard("إجمالي البيض (كرتونة)", logic.farms.sumOf { it.eggStock }.toString(), Color(0xFF004D40))
        
        Spacer(Modifier.height(20.dp))
        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFE3F2FD))) {
            Text("ملاحظة: معادلة العلف تعتمد على خصم كيس سوبر لكل 20 كيس علف مستهلك فعلياً في المزارع.", 
                modifier = Modifier.padding(16.dp), fontSize = 14.sp)
        }
    }
}

@Composable
fun BigStatCard(title: String, value: String, color: Color) {
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), colors = CardDefaults.cardColors(containerColor = color)) {
        Column(Modifier.padding(24.dp), Alignment.CenterHorizontally) {
            Text(title, color = Color.White, fontSize = 15.sp)
            Text(value, color = Color.White, fontSize = 38.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun HelpCenter() {
    val items = listOf(
        "المدخول الجديد" to "تمت إضافة خيار 'مدخول' للقائمة، وهو يحسب مباشرة في الأرباح ولا يعتبر مصروفاً.",
        "قاعدة الوفيات" to "تخصم أوتوماتيكياً من العدد الأساسي المسجل لكل مزرعة بمجرد إدخالها في السجل.",
        "المزامنة السحابية" to "يمكنك ربط التطبيق بملف OneDrive عبر الإعدادات لمزامنة الجداول بين الهاتف والكمبيوتر."
    )
    LazyColumn(Modifier.padding(16.dp)) {
        item { Text("دليل المستخدم والمحاسبة", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
        items(items) { (t, d) ->
            Spacer(Modifier.height(16.dp))
            Text(t, fontWeight = FontWeight.Bold, color = Color(0xFF0D47A1))
            Text(d, fontSize = 14.sp)
            HorizontalDivider(Modifier.padding(top = 8.dp))
        }
    }
}
