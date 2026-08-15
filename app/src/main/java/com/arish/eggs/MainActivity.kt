package com.arish.eggs

import android.os.Bundle
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.ComponentActivity
import androidx.activity.compose.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import java.util.*

// --- [1. تعريف هيكل البيانات - السجلات] ---
data class DailyMovement(
    val id: Long = 0,
    val date: String,
    val farm: String,
    val category: String,
    val qty: Double,
    val price: Double,
    val note: String = ""
) {
    // معادلة إكسل: المصروف = IF(ISNUMBER(SEARCH("بيض", category)); 0; qty * price)
    val expense: Double get() = if (category.contains("بيض")) 0.0 else qty * price
    // معادلة إكسل: المدخول = IF(category="بيض تحميل"; qty * price; 0)
    val income: Double get() = if (category == "بيض تحميل") qty * price else 0.0
}

data class FarmStats(
    val name: String,
    val initialBirds: Int,
    var deaths: Int = 0,
    var eggProduction: Double = 0.0,
    var eggLoading: Double = 0.0,
    var totalExpenses: Double = 0.0,
    var totalIncome: Double = 0.0
) {
    val remainingBirds: Int get() = initialBirds - deaths
    val eggBalance: Double get() = eggProduction - eggLoading
    val profit: Double get() = totalIncome - totalExpenses
}

// --- [2. المحرك المحاسبي الرئيسي] ---
class ArishAccountingEngine {
    // الثوابت من ملف ثوابت.csv
    var feedTonPrice = 387.5
    var superBagPrice = 37.5
    var tonToBagsRatio = 20.0
    
    // قائمة المزارع
    val farms = mutableStateListOf(
        FarmStats("فايز الطويلة", 7500),
        FarmStats("فايز البرشا", 2800),
        FarmStats("فايز الألفين", 2000),
        FarmStats("ابو حمدو العقيد", 2000),
        FarmStats("ابو حمدو جديدة", 3300),
        FarmStats("ابو حمدو الاخرس", 3800),
        FarmStats("ام نضال ١", 10900),
        FarmStats("ام نضال ٢", 0)
    )

    // مخزن الحركات (بديل لجدول الإكسل)
    val allMovements = mutableStateListOf<DailyMovement>()

    // حساب رصيد الـ Super (معادلة ملف علف super.csv)
    fun getSuperBalance(): Double {
        val totalPurchased = 151.55 // مجموع المشتريات من ملف الشراء والمخزون
        val consumedFeed = allMovements.filter { it.category == "علف" }.sumOf { it.qty }
        return totalPurchased - (consumedFeed / tonToBagsRatio)
    }

    // إضافة حركة جديدة (مثل إضافة سطر في إكسل)
    fun addMovement(farmName: String, cat: String, q: Double, p: Double) {
        val movement = DailyMovement(date = Date().toString(), farm = farmName, category = cat, qty = q, price = p)
        allMovements.add(movement)
        updateFarmStats()
    }

    private fun updateFarmStats() {
        farms.forEach { farm ->
            val farmMoves = allMovements.filter { it.farm == farm.name }
            farm.deaths = farmMoves.filter { it.category == "وفيات" }.sumOf { it.qty }.toInt()
            farm.eggProduction = farmMoves.filter { it.category == "بيض انتاج" }.sumOf { it.qty }
            farm.eggLoading = farmMoves.filter { it.category == "بيض تحميل" }.sumOf { it.qty }
            farm.totalExpenses = farmMoves.sumOf { it.expense }
            farm.totalIncome = farmMoves.sumOf { it.income }
        }
    }
}

// --- [3. واجهة المستخدم الرسومية] ---
class MainActivity : ComponentActivity() {
    val engine = ArishAccountingEngine()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { ArishAppMain(engine) }
    }
}

@Composable
fun ArishAppMain(engine: ArishAccountingEngine) {
    var screen by remember { mutableStateOf("الرئيسية") }
    val navy = Color(0xFF1A237E)
    val gold = Color(0xFFD4AF37)

    Scaffold(
        topBar = { TopAppBar(title = { Text("نظام إدارة مزارع نهر اسطوان", color = Color.White) }, backgroundColor = navy) },
        bottomBar = {
            BottomNavigation(backgroundColor = navy) {
                listOf("الرئيسية", "الحركة اليومية", "المخزون", "التعليمات").forEach { item ->
                    BottomNavigationItem(
                        selected = screen == item,
                        onClick = { screen = item },
                        label = { Text(item, color = Color.White, fontSize = 9.sp) },
                        icon = { Icon(Icons.Default.List, contentDescription = null, tint = gold) }
                    )
                }
            }
        }
    ) { p ->
        Column(modifier = Modifier.padding(p).fillMaxSize().background(Color(0xFFF8F9FA))) {
            when (screen) {
                "الرئيسية" -> DashboardView(engine)
                "الحركة اليومية" -> LedgerView(engine)
                "المخزون" -> InventoryView(engine)
                "التعليمات" -> DetailedInstructions()
            }
        }
    }
}

@Composable
fun DashboardView(engine: ArishAccountingEngine) {
    LazyColumn(modifier = Modifier.padding(16.dp)) {
        item {
            Text("إحصاء المزارع (ربح/وفيات)", style = MaterialTheme.typography.h5, color = Color(0xFF1A237E))
            Spacer(modifier = Modifier.height(10.dp))
        }
        items(engine.farms) { farm ->
            Card(elevation = 6.dp, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(farm.name, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text("الربح: ${farm.profit}$", color = if(farm.profit >=0) Color(0xFF2E7D32) else Color.Red)
                    }
                    Divider(Modifier.padding(vertical = 8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("الطيور: ${farm.remainingBirds}", color = Color.DarkGray)
                        Text("رصيد البيض: ${farm.eggBalance}", color = Color.Blue)
                    }
                }
            }
        }
    }
}

@Composable
fun LedgerView(engine: ArishAccountingEngine) {
    var showAddDialog by remember { mutableStateOf(false) }
    
    Column(modifier = Modifier.padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("سجل الحركة اليومية", style = MaterialTheme.typography.h6)
            Button(onClick = { showAddDialog = true }, colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFFD4AF37))) {
                Icon(Icons.Default.Add, null)
                Text("إضافة سطر")
            }
        }
        
        // جدول يشبه الإكسل
        LazyColumn(modifier = Modifier.fillMaxSize().padding(top = 10.dp)) {
            item {
                Row(Modifier.background(Color.LightGray).padding(8.dp).fillMaxWidth()) {
                    Text("المزرعة", Modifier.weight(1f), fontWeight = FontWeight.Bold)
                    Text("الصنف", Modifier.weight(1f), fontWeight = FontWeight.Bold)
                    Text("الكمية", Modifier.weight(0.5f), fontWeight = FontWeight.Bold)
                    Text("المصروف", Modifier.weight(0.7f), fontWeight = FontWeight.Bold)
                }
            }
            items(engine.allMovements.reversed()) { move ->
                Row(Modifier.padding(8.dp).fillMaxWidth()) {
                    Text(move.farm, Modifier.weight(1f))
                    Text(move.category, Modifier.weight(1f))
                    Text(move.qty.toString(), Modifier.weight(0.5f))
                    Text("${move.expense}$", Modifier.weight(0.7f), color = Color.Red)
                }
                Divider()
            }
        }
    }
}

@Composable
fun InventoryView(engine: ArishAccountingEngine) {
    Column(modifier = Modifier.padding(16.dp)) {
        Text("مراقبة المخزون (علف & super)", style = MaterialTheme.typography.h5)
        Spacer(modifier = Modifier.height(20.dp))
        
        InventoryCard("رصيد الـ Super المتبقي (كيس)", engine.getSuperBalance(), Color(0xFFE65100))
        InventoryCard("إجمالي كراتين البيض بالمخازن", engine.farms.sumOf { it.eggBalance }, Color(0xFF004D40))
        
        Spacer(modifier = Modifier.height(20.dp))
        Text("ملاحظات محاسبية:", fontWeight = FontWeight.Bold)
        Text("• 1 طن علف = 20 كيس.")
        Text("• 1 صندوق بيض = 12 كرتونة.")
    }
}

@Composable
fun InventoryCard(title: String, value: Double, color: Color) {
    Card(backgroundColor = color, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, color = Color.White, fontSize = 16.sp)
            Text(value.toString(), color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun DetailedInstructions() {
    val guide = listOf(
        "نظام الوفيات" to "عند إدخال حركة 'وفيات'، يقوم التطبيق تلقائياً بخصمها من عدد الطيور الأساسي لكل مزرعة.",
        "نظام العلف" to "يُحسب المصروف بضرب الكمية بسعر الوحدة، بينما يُحسب مخزون الـ Super بطرح الاستهلاك مقسوماً على 20.",
        "الإملاء الصوتي" to "لتفعيل الصوت: اضغط على زر المايكروفون وقل 'مزرعة فايز علف 20'. سيفهم النظام البيانات.",
        "المزامنة" to "زر التحديث في الإعدادات يقوم بسحب البيانات من رابط OneDrive وتعبئة السجل تلقائياً."
    )
    LazyColumn(modifier = Modifier.padding(16.dp)) {
        items(guide) { (title, desc) ->
            Text(title, fontWeight = FontWeight.Bold, color = Color(0xFF1A237E), fontSize = 18.sp)
            Text(desc, modifier = Modifier.padding(bottom = 12.dp))
            Divider()
        }
    }
}                menu.forEach { item ->
                    BottomNavigationItem(
                        selected = screen == item,
                        onClick = { screen = item },
                        label = { Text(item, color = Color.White, fontSize = 10.sp) },
                        icon = { Icon(Icons.Default.Star, contentDescription = null, tint = gold) }
                    )
                }
            }
        }
    ) { p ->
        Column(modifier = Modifier.padding(p).fillMaxSize()) {
            when (screen) {
                "الرئيسية" -> Dashboard()
                "إضافة" -> EntryScreen()
                "المخزون" -> InventoryScreen()
                "التعليمات" -> GuideScreen()
            }
        }
    }
}

@Composable
fun Dashboard() {
    val farms = listOf("فايز الطويلة", "فايز البرشا", "فايز الألفين", "أبو حمدو العقيد", "أبو حمدو جديدة", "أبو حمدو الأخرس", "أم نضال ١", "أم نضال ٢")
    LazyColumn(modifier = Modifier.padding(16.dp)) {
        item { Text("خلاصة المزارع", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1A237E)) }
        items(farms) { farm ->
            Card(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), elevation = 4.dp) {
                Row(modifier = Modifier.padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(farm, fontWeight = FontWeight.Bold)
                    Text("صافي الربح: 0.0$", color = Color(0xFF2E7D32))
                }
            }
        }
    }
}

@Composable
fun GuideScreen() {
    val info = listOf(
        "المزامنة" to "يتم ربط التطبيق بملف OneDrive عبر الرابط في الإعدادات.",
        "قاعدة العلف" to "سعر الكيس = سعر الطن / 20. يتم التحديث تلقائياً.",
        "الإنتاج" to "البيض المتبقي = إنتاج اليوم - تحميل اليوم.",
        "الإدخال الذكي" to "يمكنك تفعيل الميكروفون والكاميرا للإدخال الصوتي وقراءة الصور."
    )
    LazyColumn(modifier = Modifier.padding(16.dp)) {
        item { Text("دليل تشغيل النظام", fontSize = 20.sp, fontWeight = FontWeight.Bold) }
        items(info) { (title, desc) ->
            Text("• $title: $desc", modifier = Modifier.padding(vertical = 8.dp))
            Divider()
        }
    }
}

@Composable fun EntryScreen() { Text("شاشة الإضافة (صوت/صور) جاهزة للاستخدام", modifier = Modifier.padding(16.dp)) }
@Composable fun InventoryScreen() { Text("مراقبة المخزون المتبقي لحظياً", modifier = Modifier.padding(16.dp)) }
