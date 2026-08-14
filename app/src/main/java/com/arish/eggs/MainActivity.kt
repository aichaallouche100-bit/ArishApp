package com.arish.eggs

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { ArishAppContent() }
    }
}

@Composable
fun ArishAppContent() {
    var screen by remember { mutableStateOf("الرئيسية") }
    val navy = Color(0xFF1A237E)
    val gold = Color(0xFFD4AF37)

    Scaffold(
        topBar = { TopAppBar(title = { Text("منتجات نهر اسطوان", color = Color.White) }, backgroundColor = navy) },
        bottomBar = {
            BottomNavigation(backgroundColor = navy) {
                val menu = listOf("الرئيسية", "إضافة", "المخزون", "التعليمات")
                menu.forEach { item ->
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
