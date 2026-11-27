package com.motionsalt.ccstudio.ui.tune

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.motionsalt.ccstudio.core.CcPlugin
import java.util.Locale
import kotlin.math.floor

@Composable
fun CcRuler(param: CcPlugin.Param, value: Double, onStart: () -> Unit, onValue: (Double) -> Unit) {
    val start by rememberUpdatedState(onStart)
    val emit by rememberUpdatedState(onValue)
    val current by rememberUpdatedState(value)
    var typing by remember { mutableStateOf(false) }
    var text by remember { mutableStateOf("") }
    val min = param.min * param.scale
    val max = param.max * param.scale
    val position = value * param.scale
    val range = max - min
    val ticks = range / 75.0
    val visible = range / 3.0
    val formatted = String.format(Locale.US,"%.${param.decimals}f",position)
    val entered = text.replace(',','.').toDoubleOrNull()?.takeIf { it.isFinite() && it in min..max }
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
        Text(param.label)
        TextButton(onClick={text=formatted;typing=true}) { Text(formatted) }
    }
    val color = MaterialTheme.colorScheme.outline
    val accent = MaterialTheme.colorScheme.primary
    Canvas(Modifier.fillMaxWidth().height(44.dp)
        .semantics {
            contentDescription=param.label
            progressBarRangeInfo=ProgressBarRangeInfo(position.toFloat(),min.toFloat()..max.toFloat())
            setProgress { start(); emit((it/param.scale.toDouble()).coerceIn(param.min,param.max)); true }
        }
        .pointerInput(param.id) { detectTapGestures(onDoubleTap={ start(); emit(param.initial) }) }
        .pointerInput(param.id) {
            var raw = 0.0
            detectDragGestures(onDragStart={raw=current*param.scale;start()}) { change, amount ->
                change.consume()
                raw=(raw-amount.x/size.width*visible).coerceIn(min,max)
                emit((raw/param.scale).coerceIn(param.min,param.max))
            }
        }) {
        val pixelsPerValue=size.width/visible
        val mid=size.width*.5f
        val first=floor((position-visible*.5-min)/ticks).toInt().coerceAtLeast(0)
        for(i in first..(first+28).coerceAtMost(75)) {
            val x=(mid+(min+i*ticks-position)*pixelsPerValue).toFloat()
            if(x in 0f..size.width) drawLine(color,Offset(x,size.height),Offset(x,size.height*(if(i%5==0).32f else .62f)),1.dp.toPx())
        }
        drawLine(accent,Offset(mid,0f),Offset(mid,size.height),2.dp.toPx())
    }
    if(typing) AlertDialog(
        onDismissRequest={typing=false},title={Text(param.label)},
        text={OutlinedTextField(text,{text=it},singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),supportingText={Text("$min to $max")})},
        confirmButton={TextButton(enabled=entered!=null,onClick={entered?.let { start();emit(it/param.scale) };typing=false}){Text("Apply")}},
        dismissButton={TextButton(onClick={typing=false}){Text("Cancel")}}
    )
}
