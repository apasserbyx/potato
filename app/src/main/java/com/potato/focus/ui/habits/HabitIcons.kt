package com.potato.focus.ui.habits

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.DirectionsRun
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.SelfImprovement
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import com.potato.focus.data.HabitIconType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class BuiltInHabitIcon(
    val key: String,
    val label: String,
    val image: ImageVector
)

val builtInHabitIcons = listOf(
    BuiltInHabitIcon("book", "阅读", Icons.Rounded.MenuBook),
    BuiltInHabitIcon("water", "喝水", Icons.Rounded.WaterDrop),
    BuiltInHabitIcon("run", "运动", Icons.Rounded.DirectionsRun),
    BuiltInHabitIcon("meditate", "冥想", Icons.Rounded.SelfImprovement),
    BuiltInHabitIcon("write", "写作", Icons.Rounded.Edit),
    BuiltInHabitIcon("sleep", "睡眠", Icons.Rounded.Bedtime),
    BuiltInHabitIcon("study", "学习", Icons.Rounded.School),
    BuiltInHabitIcon("fitness", "健身", Icons.Rounded.FitnessCenter),
    BuiltInHabitIcon("music", "音乐", Icons.Rounded.MusicNote),
    BuiltInHabitIcon("target", "目标", Icons.Rounded.Flag),
    BuiltInHabitIcon("heart", "健康", Icons.Rounded.Favorite),
    BuiltInHabitIcon("sun", "早起", Icons.Rounded.WbSunny)
)

val habitEmojis = listOf("📖", "🧘", "🏃", "✍️", "💧", "🌱", "🎯", "🛏️")

fun builtInHabitIcon(key: String): ImageVector? =
    builtInHabitIcons.firstOrNull { it.key == key }?.image

fun habitColor(hex: String): Color =
    runCatching { Color(android.graphics.Color.parseColor(hex)) }
        .getOrDefault(Color(0xFF96592F))

@Composable
fun HabitIconVisual(
    iconType: HabitIconType,
    icon: String,
    imagePath: String,
    contentDescription: String,
    size: Dp,
    background: Color,
    tint: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.32f))
            .background(background)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center
    ) {
        when (iconType) {
            HabitIconType.IMAGE -> {
                val bitmap = rememberHabitBitmap(imagePath)
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap,
                        contentDescription = contentDescription,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        imageVector = Icons.Rounded.Flag,
                        contentDescription = contentDescription,
                        tint = tint,
                        modifier = Modifier.size(size * 0.5f)
                    )
                }
            }
            HabitIconType.BUILTIN -> {
                Icon(
                    imageVector = builtInHabitIcon(icon) ?: Icons.Rounded.Flag,
                    contentDescription = contentDescription,
                    tint = tint,
                    modifier = Modifier.size(size * 0.5f)
                )
            }
            HabitIconType.EMOJI -> {
                Text(
                    text = icon.ifBlank { "🌱" },
                    fontSize = (size.value * 0.44f).sp,
                    color = tint,
                    fontWeight = FontWeight.Normal
                )
            }
        }
    }
}

@Composable
private fun rememberHabitBitmap(path: String): ImageBitmap? {
    val context = LocalContext.current
    val result by produceState<ImageBitmap?>(initialValue = null, path) {
        value = if (path.isBlank()) {
            null
        } else {
            withContext(Dispatchers.IO) {
                runCatching {
                    BitmapFactory.decodeFile(path)?.asImageBitmap()
                }.getOrNull()
            }
        }
    }
    return result
}
