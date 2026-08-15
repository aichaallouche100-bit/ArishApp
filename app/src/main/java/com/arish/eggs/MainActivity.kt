package com.arish.eggs

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import java.util.*

// --- [1. النماذج المحاسبية المتقدمة] ---

data class Transaction(
    val id: String = UUID.randomUUID().toString(),
    val farmName: String,
    val type: String, // علف، بيض انتاج، بيض تحميل، وفيات، دواء، صيانة، رواتب، عام
    val quantity: Double,
    val unitPrice: Double,
    val date: String = java.text.SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
) {
    val totalValue: Double get() = quantity * unitPrice
    // معادلة إكسل: المصروف هو كل شيء عدا بيض تحميل
    val isExpense: Boolean get() = type != "بيض تحميل"
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

// --- [2. العقل المدبر المحاسبي (كل معادلاتك هنا)] ---

class ArishLogic {
    var feedTonPrice by mutableStateOf(387.5) // سعر طن العلف
    var superBagPrice = 37.5 // سعر كيس السوبر
    var tonToBagRatio = 20.0 // طن = 20 كيس
    val initialSuperPurchase = 151.55 // إجمالي مشتريات السوبر من ملفك

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
            
            // معادلة الوفيات
            farm.currentDeaths = farmMoves.filter { it.type == "وفيات" }.sumOf { it.quantity }.toInt()
            
            // معادلة البيض (إنتاج - تحميل)
            val prod = farmMoves.filter { it.type == "بيض انتاج" }.sumOf { it.quantity }
            val load = farmMoves.filter { it.type == "بيض تحميل" }.sumOf { it.quantity }
            farm.eggsInStock = prod - load
            
            // معادلة الأرباح
            farm.totalExpenses = farmMoves.filter { it.isExpense }.sumOf { it.totalValue }
            farm.totalIncome = farmMoves.filter { !it.isExpense }.sumOf { it.totalValue }
        }
    }

    // معادلة مخزون السوبر المتبقي (ملف علف super.csv)
    fun getSuperBalance(): Double {
        val consumedFeed = transactions.filter { it.type == "علف" }.sumOf { it.quantity }
        return initialSuperPurchase - (consumedFeed / tonToBagRatio)
    }
}

// --- [3. واجهة المستخدم - الجماليات والإدخال] ---

class MainActivity : ComponentActivity() {
    private val logic = ArishLogic()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ArishTheme {
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
                val items = listOf("الرئيسية", "الحركة", "المخزون", "دليل")
                val icons = listOf(Icons.Default.Dashboard, Icons.AutoMirrored.Filled.Receipt, Icons.Default.Inventory, Icons.Default.MenuBook)
                
                items.forEachIndexed { index, label ->
                    NavigationBarItem(
                        selected = activeTab == index,
                        onClick = { activeTab = index },
                        icon = { Icon(icons[index], contentDescription = label, tint = if(activeTab == index) goldAccent else Color.White) },
                        label = { Text(label, color = Color.White, fontSize = 10.sp) }
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding).fillMaxSize().background(Color(0xFFF5F7FA))) {
            when (activeTab) {
                0 -> DashboardScreen(logic)
                1 -> LedgerScreen(logic)
                2 -> InventoryScreen(logic)
                3 -> DetailedGuide()
            }
        }
    }
}

@Composable
fun DashboardScreen(logic: ArishLogic) {
    LazyColumn(modifier = Modifier.padding(16.dp)) {
        item {
            Text("نظام الرقابة المالية - نهر اسطوان", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Color(0xFF0D47A1))
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
                    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(farm.name, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Surface(color = if(farm.netProfit >= 0) Color(0xFFE8F5E9) else Color(0xFFFFEBEE), shape = RoundedCornerShape(12.dp)) {
                            Text(
                                text = "${String.format("%.2f", farm.netProfit)} $",
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                color = if(farm.netProfit >= 0) Color(0xFF2E7D32) else Color.Red,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    Row(Modifier.fillMaxWidth(), Arrangement.SpaceEvenly) {
                        QuickStat("الطيور", farm.remainingBirds.toString(), Icons.Default.Pets, Color.DarkGray)
                        QuickStat("البيض", farm.eggsInStock.toString(), Icons.Default.Egg, Color(0xFFE65100))
                        QuickStat("المصروف", String.format("%.1f", farm.totalExpenses), Icons.Default.TrendingDown, Color.Red)
                    }
                }
            }
        }
    }
}

@Composable
fun QuickStat(label: String, value: String, icon: ImageVector, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, contentDescription = null, tint = color.copy(alpha = 0.7f), modifier = Modifier.size(22.dp))
        Text(value, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = color)
        Text(label, fontSize = 10.sp, color = Color.Gray)
    }
}

@Composable
fun LedgerScreen(logic: ArishLogic) {
    var selectedFarm by remember { mutableStateOf(logic.farms[0].name) }
    var selectedType by remember { mutableStateOf("علف") }
    var qty by remember { mutableStateOf("") }
    var farmExpanded by remember { mutableStateOf(false) }
    var typeExpanded by remember { mutableStateOf(false) }

    val types = listOf("علف", "بيض انتاج", "بيض تحميل", "وفيات", "دواء", "صوص", "نثريات")

    Column(modifier = Modifier.padding(16.dp)) {
        Text("إضافة سطر جديد للسجل", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        
        Card(modifier = Modifier.padding(vertical = 12.dp).fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Column(Modifier.padding(16.dp)) {
                // اختيار المزرعة
                Box {
                    OutlinedButton(onClick = { farmExpanded = true }, Modifier.fillMaxWidth()) {
                        Text("المزرعة: $selectedFarm")
                    }
                    DropdownMenu(expanded = farmExpanded, onDismissRequest = { farmExpanded = false }) {
                        logic.farms.forEach { f ->
                            DropdownMenuItem(text = { Text(f.name) }, onClick = { selectedFarm = f.name; farmExpanded = false })
                        }
                    }
                }
                
                // اختيار الصنف
                Row(Modifier.padding(vertical = 8.dp)) {
                    Box(Modifier.weight(1f)) {
                        OutlinedButton(onClick = { typeExpanded = true }, Modifier.fillMaxWidth()) {
                            Text("الصنف: $selectedType")
                        }
                        DropdownMenu(expanded = typeExpanded, onDismissRequest = { typeExpanded = false }) {
                            types.forEach { t ->
                                DropdownMenuItem(text = { Text(t) }, onClick = { selectedType = t; typeExpanded = false })
                            }
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    OutlinedTextField(
                        value = qty,
                        onValueChange = { qty = it },
                        label = { Text("الكمية") },
                        modifier = Modifier.weight(1f)
                    )
                }

                Button(
                    onClick = {
                        val q = qty.toDoubleOrNull() ?: 0.0
                        val p = when(selectedType) {
                            "علف" -> (logic.feedTonPrice / 20.0)
                            "بيض تحميل" -> 2.25
                            else -> 0.0 // يمكن تعديله يدوياً
                        }
                        logic.addEntry(selectedFarm, selectedType, q, p)
                        qty = ""
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0D47A1))
                ) {
                    Text("إضافة الحركة وتحديث الحسابات")
                }
                
                Row(Modifier.fillMaxWidth().padding(top = 12.dp), Arrangement.SpaceEvenly) {
                    AssistChip(onClick = { /* تفعيل الصوت */ }, label = { Text("إملاء صوتي") }, leadingIcon = { Icon(Icons.Default.Mic, null, tint = Color.Red) })
                    AssistChip(onClick = { /* تفعيل الكاميرا */ }, label = { Text("قراءة صورة") }, leadingIcon = { Icon(Icons.Default.CameraAlt, null) })
                }
            }
        }

        // جدول الحركات
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
                    Text(tr.farmName, Modifier.weight(1f), fontSize = 14.sp)
                    Text(tr.type, Modifier.weight(1f), fontSize = 14.sp)
                    Text("${String.format("%.2f", tr.totalValue)}$", Modifier.weight(0.7f), color = if(tr.isExpense) Color.Red else Color(0xFF2E7D32), fontWeight = FontWeight.Bold)
                }
                HorizontalDivider(alpha = 0.5f)
            }
        }
    }
}

@Composable
fun InventoryScreen(logic: ArishLogic) {
    Column(modifier = Modifier.padding(16.dp)) {
        Text("التحليل الاستراتيجي للمخزون", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(20.dp))
        
        InventoryCard("رصيد الـ Super المتبقي (كيس)", String.format("%.2f", logic.getSuperBalance()), Color(0xFFEF6C00))
        InventoryCard("إجمالي كراتين البيض", logic.farms.sumOf { it.eggsInStock }.toString(), Color(0xFF00695C))
        
        Spacer(Modifier.height(20.dp))
        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFE3F2FD))) {
            Column(Modifier.padding(16.dp)) {
                Text("تنبيه الحسابات:", fontWeight = FontWeight.Bold, color = Color(0xFF0D47A1))
                Text("• يتم خصم 1 كيس سوبر مقابل كل 20 كيس علف مستهلك في المزارع.")
                Text("• سعر كيس العلف الحالي المحتسب: ${logic.feedTonPrice/20} $")
            }
        }
    }
}

@Composable
fun InventoryCard(title: String, value: String, color: Color) {
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp), colors = CardDefaults.cardColors(containerColor = color)) {
        Column(Modifier.padding(24.dp), Alignment.CenterHorizontally) {
            Text(title, color = Color.White, fontSize = 16.sp)
            Text(value, color = Color.White, fontSize = 42.sp, fontWeight = FontWeight.ExtraBold)
        }
    }
}

@Composable
fun DetailedGuide() {
    val items = listOf(
        "نظام المزارع" -> "يحتوي التطبيق على المزارع الـ 8 (فايز، أبو حمدو، أم نضال) مع أعداد الطيور الأساسية المبرمجة.",
        "قاعدة الربح" -> "الربح = (كمية بيض التحميل × سعر الكرتونة) - (جميع المصاريف الأخرى).",
        "مخزون السوبر" -> "ينقص المخزون آلياً عند إدخال سطر 'علف' لأي مزرعة بمعامل تحويل 1/20.",
        "الإملاء الصوتي" -> "يمكنك التحدث بـ 'مزرعة فايز علف 50' وسيقوم النظام بملء البيانات (تحت التطوير)."
    )
    LazyColumn(modifier = Modifier.padding(16.dp)) {
        item { Text("دليل التشغيل المحاسبي", style = MaterialTheme.typography.headlineSmall, color = Color(0xFF0D47A1), fontWeight = FontWeight.Bold) }
        items(items.size) { i ->
            val key = items.keys.elementAt(i)
            val value = items.values.elementAt(i)
            Card(Modifier.padding(vertical = 8.dp).fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(key, fontWeight = FontWeight.Bold, color = Color(0xFFC6A700))
                    Text(value, fontSize = 14.sp)
                }
            }
        }
    }
}

@Composable
fun ArishTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Color(0xFF0D47A1),
            secondary = Color(0xFFC6A700)
        ),
        content = content
    )
}

// دالة مساعدة لفك تضارب الألوان (Divider)
@Composable fun HorizontalDivider(modifier: Modifier = Modifier, alpha: Float = 1f) {
    Box(modifier.fillMaxWidth().height(1.dp).background(Color.Gray.copy(alpha = alpha)))
}
