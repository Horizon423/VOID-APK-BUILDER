package com.voidkernel.apkbuilder

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object VC {
    val bg = Color(0xFF07070C)
    val surf = Color(0xFF0F0F1A)
    val card = Color(0xFF161626)
    val purple = Color(0xFFB026FF)
    val blue = Color(0xFF00B8FF)
    val green = Color(0xFF2BFF88)
    val red = Color(0xFFFF3B5C)
    val text = Color(0xFFE8E8F5)
    val muted = Color(0xFF8A8AA8)
    val line = Color(0xFF2A2A44)
}

@Composable
fun VoidTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = VC.purple, secondary = VC.blue, tertiary = VC.green,
            background = VC.bg, surface = VC.surf, surfaceVariant = VC.card,
            onPrimary = Color.White, onBackground = VC.text, onSurface = VC.text,
            onSurfaceVariant = VC.muted, error = VC.red, outline = VC.line
        ),
        content = content
    )
}

@Composable
fun NeonCard(modifier: Modifier = Modifier, border: Color = VC.line, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = VC.card),
        border = BorderStroke(1.dp, border)
    ) { Column(Modifier.padding(14.dp), content = content) }
}

@Composable
fun Title(t: String) = Text(t, color = VC.text, fontSize = 18.sp, fontWeight = FontWeight.Bold)

@Composable
fun Muted(t: String, modifier: Modifier = Modifier) = Text(t, color = VC.muted, fontSize = 12.sp, modifier = modifier)

@Composable
fun NeonButton(
    text: String, modifier: Modifier = Modifier, color: Color = VC.purple,
    enabled: Boolean = true, icon: ImageVector? = null, onClick: () -> Unit
) {
    Button(
        onClick = onClick, enabled = enabled, modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(containerColor = color, contentColor = Color.White, disabledContainerColor = VC.line)
    ) {
        if (icon != null) { Icon(icon, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)) }
        Text(text, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun Brand(subtitle: String) {
    Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.Android, null, tint = VC.green, modifier = Modifier.size(34.dp))
        Spacer(Modifier.width(10.dp))
        Column {
            Text("VOID APK BUILDER", color = VC.purple, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
            Muted("$subtitle  |  Developed by Void Kernel 12")
        }
    }
    Spacer(Modifier.height(2.dp))
}
