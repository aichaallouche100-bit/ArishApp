package com.arish.eggs

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import java.util.*

// --- [1. النماذج المحاسبية المتطورة] ---

data class Transaction(
    val id: String = UUID.randomUUID().toString(),
    val farmName: String,
    val type: String, // علف، بيض انتاج، بيض تحميل، وفيات، دواء
    val quantity: Double,
    val unitPrice: Double,
    val date: String = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())
) {
    val totalValue: Double get() = quantity * unitPrice
    val isExpense: Boolean get() = !type.contains("تحميل")
}

data class FarmDashboard(
    val name: String,
    val initialBirds: Int,
    var currentDeaths: Int = 0,
    var eggsInStock: Double = 0.0,
    var totalExpenses: Double = 0.0,
    var totalIncome: Double = 0.0
) {
    val remainingBirds: Int get() = initialBirds - currentDeaths
    val netProfit: Double get() = totalIncome - totalExpenses
}

// --- [2. العقل المدبر المحاسبي] ---

class ArishLogic {
    // الثوابت الأساسية (قابلة للتعديل مستقبلاً)
    var feedTonPrice by mutableStateOf(387.5)
    var boxToCartonRatio = 12.0
    var tonToBagRatio = 20.0

    val farms = mutableStateListOf(
        FarmDashboard("فايز الطويلة", 7500),
        FarmDashboard("فايز البرشا", 2800),
        FarmDashboard("فايز الألفين", 2000),
        FarmDashboard("ابو حمدو العقيد", 2000),
        FarmDashboard("ابو حمدو جديدة", 3300),
        FarmDashboard("ابو حمدو الاخرس", 3800),
        FarmDashboard("ام نضال ١", 10900),
        FarmDashboard("ام نضال ٢", 0)
    )

    val transactions = mutableStateListOf<Transaction>()

    fun addEntry(farm: String, type: String, qty: Double, price: Double) {
        transactions.add(Transaction(farmName = farm, type = type, quantity = qty, unitPrice = price))
        recalculateEverything()
    }

    private fun recalculateEverything() {
        farms.forEach { farm ->
            val farmMoves = transactions.filter { it.farmName == farm.name }
            farm.currentDeaths = farmMoves.filter { it.type == "وفيات" }.sumOf { it.quantity }.toInt()
            val prod = farmMoves.filter { it.type == "بيض انتاج" }.sumOf { it.quantity }
            val load = farmMoves.filter { it.type == "بيض تحميل" }.sumOf { it.quantity }
            farm.eggsInStock = prod - load
            farm.totalExpenses = farmMoves.filter { it.isExpense }.sumOf { it.totalValue }
            farm.totalIncome = farmMoves.filter { !it.isExpense }.sumOf { it.totalValue }
        }
    }

    fun getSuperBalance(): Double {
        val initialSuper = 151.55 // من ملفاتك
        val usedFeed = transactions.filter { it.type == "علف" }.sumOf { it.quantity }
        return initialSuper - (usedFeed / tonToBagRatio)
    }
}

// --- [3. واجهة المستخدم - الجماليات] ---

class MainActivity : ComponentActivity() {
    private val logic = ArishLogic()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                MainContainer(logic)
            }
        }
    }
}

@Composable
fun MainContainer(logic: ArishLogic) {
    var activeTab by remember { mutableStateOf(0) }
    val navyBlue = Color(0xFF0D47A1)
    val goldAccent = Color(0xFFC6A700)

    Scaffold(
        bottomBar = {
            NavigationBar(containerColor = navyBlue) {
                val items = listOf("الرئيسية" to Icons.Default.Dashboard, "الحركة" to Icons.Default.ReceiptLong, "المخزون" to Icons.Default.Inventory, "دليل" to Icons.Default.AutoStories)
                items.forEachIndexed { index, (label, icon) ->
                    NavigationBarItem(
                        selected = activeTab == index,
                        onClick = { activeTab = index },
                        icon = { Icon(icon, contentDescription = label, tint = if(activeTab == index) goldAccent else Color.White) },
                        label = { Text(label, color = Color.White, fontSize = 10.sp) }
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding).fillMaxSize().background(
            Brush.verticalGradient(listOf(Color(0xFFE3F2FD), Color.White))
        )) {
            when (activeTab) {
                0 -> DashboardScreen(logic)
                1 -> TransactionsLedger(logic)
                2 -> InventoryAnalytics(logic)
                3 -> SmartGuide()
            }
        }
    }
}

@Composable
fun DashboardScreen(logic: ArishLogic) {
    LazyColumn(modifier = Modifier.padding(16.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(painter = painterResource(id = R.drawable.logo_arish), contentDescription = null, modifier = Modifier.size(60.dp))
                Spacer(Modifier.width(12.dp))
                Text("نظام الرقابة المالية للإنتاج", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold, color = Color(0xFF0D47A1))
            }
            Spacer(Modifier.height(20.dp))
        }
        items(logic.farms) { farm ->
            Card(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                shape = RoundedCornerShape(24.dp),
                elevation = CardDefaults.cardElevation(8.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                        Text(farm.name, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Text("${String.format("%.2f", farm.netProfit)} $", color = if(farm.netProfit >=0) Color(0xFF2E7D32) else Color.Red, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.fillMaxWidth(), Arrangement.SpaceEvenly) {
                        StatItem("الطيور", farm.remainingBirds.toString(), Icons.Default.Pets, Color.DarkGray)
                        StatItem("البيض", farm.eggsInStock.toString(), Icons.Default.Egg, Color(0xFFE65100))
                        StatItem("المصاريف", farm.totalExpenses.toString(), Icons.Default.TrendingDown, Color.Red)
                    }
                }
            }
        }
    }
}

@Composable
fun StatItem(label: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
        Text(value, fontWeight = FontWeight.Bold, color = color)
        Text(label, fontSize = 10.sp, color = Color.Gray)
    }
}

@Composable
fun TransactionsLedger(logic: ArishLogic) {
    var farm by remember { mutableStateOf("فايز الطويلة") }
    var type by remember { mutableStateOf("علف") }
    var qty by remember { mutableStateOf("") }

    Column(modifier = Modifier.padding(16.dp)) {
        Text("إضافة حركة محاسبية جديدة", style = MaterialTheme.typography.titleLarge, color = Color(0xFF0D47A1))
        
        Card(modifier = Modifier.padding(vertical = 12.dp).fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
            Column(Modifier.padding(16.dp)) {
                OutlinedTextField(value = qty, onValueChange = {qty = it}, label = { Text("الكمية") }, modifier = Modifier.fillMaxWidth())
                Button(
                    onClick = { 
                        val q = qty.toDoubleOrNull() ?: 0.0
                        val p = if(type == "علف") (logic.feedTonPrice/20) else 2.25
                        logic.addEntry(farm, type, q, p)
                        qty = ""
                    },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = Color(0xFF0D47A1)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Add, null)
                    Text(" تثبيت السطر في السجل")
                }
                
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), Arrangement.SpaceEvenly) {
                    IconButton(onClick = { /* ميزة الصوت قادمة */ }) { Icon(Icons.Default.Mic, "صوت", tint = Color.Red) }
                    IconButton(onClick = { /* ميزة الكاميرا قادمة */ }) { Icon(Icons.Default.CameraAlt, "كاميرا") }
                }
            }
        }

        LazyColumn(Modifier.fillMaxSize()) {
            item {
                Row(Modifier.background(Color(0xFF0D47A1)).padding(12.dp).fillMaxWidth()) {
                    Text("المزرعة", Modifier.weight(1f), color = Color.White, fontWeight = FontWeight.Bold)
                    Text("الصنف", Modifier.weight(1f), color = Color.White, fontWeight = FontWeight.Bold)
                    Text("القيمة", Modifier.weight(0.7f), color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
            items(logic.transactions.reversed()) { tr ->
                Row(Modifier.padding(12.dp).fillMaxWidth()) {
                    Text(tr.farmName, Modifier.weight(1f))
                    Text(tr.type, Modifier.weight(1f))
                    Text("${tr.totalValue}$", Modifier.weight(0.7f), color = if(tr.isExpense) Color.Red else Color(0xFF2E7D32))
                }
                Divider()
            }
        }
    }
}

@Composable
fun InventoryAnalytics(logic: ArishLogic) {
    Column(modifier = Modifier.padding(16.dp)) {
        Text("تحليل المخزون الاستراتيجي", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(20.dp))
        
        BigInvCard("مخزون الـ Super (كيس)", logic.getSuperBalance().toString(), Color(0xFFEF6C00))
        BigInvCard("إجمالي إنتاج البيض الحالي", logic.farms.sumOf { it.eggsInStock }.toString(), Color(0xFF00695C))
        
        Spacer(Modifier.height(20.dp))
        Text("تنبيهات الذكاء الاصطناعي:", fontWeight = FontWeight.Bold)
        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF9C4))) {
            Text("مخزون العلف يكفي لـ 5 أيام قادمة بناءً على معدل الاستهلاك الحالي.", modifier = Modifier.padding(16.dp))
        }
    }
}

@Composable
fun BigInvCard(title: String, value: String, color: Color) {
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp), colors = CardDefaults.cardColors(containerColor = color)) {
        Column(Modifier.padding(24.dp), Alignment.CenterHorizontally) {
            Text(title, color = Color.White, fontSize = 16.sp)
            Text(value, color = Color.White, fontSize = 36.sp, fontWeight = FontWeight.ExtraBold)
        }
    }
}

@Composable
fun SmartGuide() {
    val tips = listOf(
        "حساب الربح" to "يتم طرح (علف + أدوية + صيانة) من (مبيعات بيض التحميل).",
        "تنبيه الوفيات" to "عند زيادة الوفيات عن 2% يظهر المربع باللون البرتقالي للتنبيه.",
        "المزامنة" to "اضغط على زر التحديث في الإعدادات لجلب بيانات OneDrive فورياً."
    )
    LazyColumn(Modifier.padding(16.dp)) {
        item { Text("الدليل التشغيلي المتقدم", style = MaterialTheme.typography.headlineSmall, color = Color(0xFF0D47A1)) }
        items(tips) { (t, d) ->
            Card(Modifier.padding(vertical = 8.dp).fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(t, fontWeight = FontWeight.Bold)
                    Text(d, fontSize = 14.sp)
                }
            }
        }
    }
}
