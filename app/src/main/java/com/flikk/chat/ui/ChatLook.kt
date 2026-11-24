package com.flikk.chat.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallMade
import androidx.compose.material.icons.automirrored.filled.CallMissed
import androidx.compose.material.icons.automirrored.filled.CallReceived
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.flikk.chat.data.LinkMeta
import com.flikk.chat.data.Msg
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.ceil

/** Bubble tail width (body ee margin lopala, tail top corner lo) */
val TAIL = 8.dp

/** WhatsApp bubble: rounded box + group lo first message ki top corner lo chinna tail */
class BubbleShape(private val mine: Boolean, private val tail: Boolean, private val radius: Float, private val tailW: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val p = Path()
        val left = if (mine) 0f else tailW
        val right = if (mine) size.width - tailW else size.width
        val r = CornerRadius(radius, radius)
        val zero = CornerRadius(0f, 0f)
        p.addRoundRect(
            RoundRect(
                left, 0f, right, size.height,
                topLeftCornerRadius = if (!mine && tail) zero else r,
                topRightCornerRadius = if (mine && tail) zero else r,
                bottomRightCornerRadius = r, bottomLeftCornerRadius = r,
            )
        )
        if (tail) {
            val t = Path()
            if (mine) {
                t.moveTo(right - 1f, 0f); t.lineTo(size.width, 0f); t.lineTo(right - 1f, tailW * 1.3f); t.close()
            } else {
                t.moveTo(left + 1f, 0f); t.lineTo(0f, 0f); t.lineTo(left + 1f, tailW * 1.3f); t.close()
            }
            p.addPath(t)
        }
        return Outline.Generic(p)
    }
}

/** Chat background meeda light ga doodles (WhatsApp wallpaper laaga) */
fun Modifier.doodles(tint: Color): Modifier = drawWithCache {
    val cell = 64.dp.toPx()
    val sw = Stroke(width = 1.3.dp.toPx(), cap = StrokeCap.Round)
    val u = 7.dp.toPx()
    val cols = ceil(size.width / cell).toInt() + 1
    val rows = ceil(size.height / cell).toInt() + 1
    onDrawBehind {
        for (r in 0 until rows) for (c in 0 until cols) {
            val h = (r * 73856093) xor (c * 19349663)
            val kind = Math.floorMod(h, 9)
            val jx = Math.floorMod(h shr 3, 20) - 10
            val jy = Math.floorMod(h shr 7, 20) - 10
            val cx = c * cell + cell / 2 + jx
            val cy = r * cell + cell / 2 + jy
            val rot = Math.floorMod(h shr 11, 60) - 30f
            rotate(rot, Offset(cx, cy)) {
                when (kind) {
                    0 -> drawCircle(tint, u, Offset(cx, cy), style = sw)
                    1 -> { // star
                        val p = Path()
                        for (i in 0 until 10) {
                            val a = Math.PI / 5 * i - Math.PI / 2
                            val rr = if (i % 2 == 0) u * 1.3f else u * 0.55f
                            val x = cx + (rr * Math.cos(a)).toFloat(); val y = cy + (rr * Math.sin(a)).toFloat()
                            if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
                        }
                        p.close(); drawPath(p, tint, style = sw)
                    }
                    2 -> { // heart
                        val p = Path()
                        p.moveTo(cx, cy + u)
                        p.cubicTo(cx - u * 1.6f, cy - u * 0.2f, cx - u * 0.7f, cy - u * 1.4f, cx, cy - u * 0.4f)
                        p.cubicTo(cx + u * 0.7f, cy - u * 1.4f, cx + u * 1.6f, cy - u * 0.2f, cx, cy + u)
                        drawPath(p, tint, style = sw)
                    }
                    3 -> { // smiley
                        drawCircle(tint, u * 1.2f, Offset(cx, cy), style = sw)
                        drawCircle(tint, u * 0.15f, Offset(cx - u * 0.4f, cy - u * 0.3f))
                        drawCircle(tint, u * 0.15f, Offset(cx + u * 0.4f, cy - u * 0.3f))
                        drawArc(tint, 20f, 140f, false, Offset(cx - u * 0.6f, cy - u * 0.4f), Size(u * 1.2f, u * 1.0f), style = sw)
                    }
                    4 -> drawRoundRect(tint, Offset(cx - u, cy - u * 0.7f), Size(u * 2, u * 1.4f), CornerRadius(u * 0.3f), style = sw)
                    5 -> { // play triangle
                        val p = Path(); p.moveTo(cx - u * 0.6f, cy - u); p.lineTo(cx + u, cy); p.lineTo(cx - u * 0.6f, cy + u); p.close()
                        drawPath(p, tint, style = sw)
                    }
                    6 -> { // squiggle
                        val p = Path(); p.moveTo(cx - u * 1.5f, cy)
                        p.quadraticTo(cx - u * 0.75f, cy - u, cx, cy); p.quadraticTo(cx + u * 0.75f, cy + u, cx + u * 1.5f, cy)
                        drawPath(p, tint, style = sw)
                    }
                    7 -> { // plus
                        drawLine(tint, Offset(cx - u, cy), Offset(cx + u, cy), sw.width, StrokeCap.Round)
                        drawLine(tint, Offset(cx, cy - u), Offset(cx, cy + u), sw.width, StrokeCap.Round)
                    }
                    else -> { // music note
                        drawCircle(tint, u * 0.4f, Offset(cx - u * 0.3f, cy + u * 0.7f), style = sw)
                        drawLine(tint, Offset(cx + u * 0.1f, cy + u * 0.7f), Offset(cx + u * 0.1f, cy - u), sw.width, StrokeCap.Round)
                        drawLine(tint, Offset(cx + u * 0.1f, cy - u), Offset(cx + u * 0.8f, cy - u * 0.6f), sw.width, StrokeCap.Round)
                    }
                }
            }
        }
    }
}

/** "4:01 pm" */
fun clockAmPm(ts: Long): String = SimpleDateFormat("h:mm a", Locale.ENGLISH).format(Date(ts)).lowercase()

/** Call entry inside chat: "Voice call · 5 mins" */
@Composable
fun CallContent(m: Msg, ink: Color, sub: Color) {
    val video = m.mime.startsWith("video")
    val missed = m.mime.endsWith("|missed")
    val secs = m.dur / 1000
    val detail = when {
        missed -> tr("Tap to call back")
        m.mime.endsWith("|noanswer") -> tr("No answer")
        m.mime.endsWith("|declined") -> tr("Declined")
        secs >= 60 -> tr("{0} mins", secs / 60)
        else -> tr("{0} secs", secs.coerceAtLeast(1))
    }
    Row(Modifier.padding(start = 4.dp, end = 10.dp, top = 4.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(46.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.08f)), contentAlignment = Alignment.Center) {
            val icon = when {
                video -> Icons.Filled.Videocam
                missed -> Icons.AutoMirrored.Filled.CallMissed
                m.fromMe -> Icons.AutoMirrored.Filled.CallMade
                else -> Icons.AutoMirrored.Filled.CallReceived
            }
            Icon(icon, null, tint = if (missed) EndRed else ink, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(tr(com.flikk.chat.data.callTitle(m)), color = ink, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Text(detail, color = sub, fontSize = 14.sp)
        }
    }
}

/** Link card (thumbnail + title + host) */
@Composable
fun LinkCard(url: String, mine: Boolean) {
    val ctx = LocalContext.current
    val info by produceState(initialValue = LinkMeta.cached(url), url) { value = LinkMeta.get(ctx, url) }
    val li = info ?: return
    val ink = if (mine) Color.White else Ink
    val sub = if (mine) Color.White.copy(alpha = 0.75f) else Muted
    Row(
        Modifier.widthIn(max = 300.dp).padding(bottom = 4.dp).clip(RoundedCornerShape(8.dp))
            .background(Color.Black.copy(alpha = if (palette.dark) 0.22f else 0.06f))
            .clickable { openUrl(ctx, url) },
    ) {
        if (li.image.isNotEmpty()) {
            AsyncImage(
                model = li.image, contentDescription = null, contentScale = ContentScale.Crop,
                modifier = Modifier.size(width = 96.dp, height = 96.dp).background(Color.Black.copy(alpha = 0.2f)),
            )
        }
        Column(Modifier.weight(1f, fill = false).padding(horizontal = 10.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(li.title, color = ink, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (li.desc.isNotEmpty()) Text(li.desc, color = sub, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Link, null, tint = sub, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(4.dp))
                Text(li.host, color = ink, fontSize = 13.sp, maxLines = 1)
            }
        }
    }
}

fun openUrl(ctx: Context, url: String) {
    try { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (_: Exception) {}
}
