package com.jinyuyuliang.budget.ui
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
val Ink=Color(0xFF143F38)
val Teal=Color(0xFF176957)
val Soft=Color(0xFFF5F7F2)
val Muted=Color(0xFF5D6D67)
@Composable fun BudgetTheme(content:@Composable ()->Unit){MaterialTheme(colorScheme=lightColorScheme(primary=Teal,onPrimary=Color.White,primaryContainer=Color(0xFFDDEFE5),onPrimaryContainer=Ink,background=Soft,surface=Color.White,onSurface=Ink,onBackground=Ink,secondary=Color(0xFF8B6321),error=Color(0xFFAF3838)),content=content)}
