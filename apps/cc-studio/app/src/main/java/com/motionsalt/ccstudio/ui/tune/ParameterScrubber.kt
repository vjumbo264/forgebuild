package com.motionsalt.ccstudio.ui.tune

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.motionsalt.ccstudio.core.EffectParam
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.*
import java.util.Locale

/** Operator-specified ruler fallback; values update on every move, not pointer release. */
@Composable
fun ParameterScrubber(param:EffectParam,externalValue:Float,onValue:(Float)->Unit) {
    var value by remember(param.key){mutableFloatStateOf(externalValue)}
    val emit by rememberUpdatedState(onValue)
    LaunchedEffect(externalValue){value=externalValue}
    var typing by remember{mutableStateOf(false)};var text by remember{mutableStateOf("")}
    val scope=rememberCoroutineScope();var fling by remember{mutableStateOf<Job?>(null)}
    val step=param.step.coerceAtLeast(.00001f);val span=(param.max-param.min).coerceAtLeast(step)
    val major=max(step,10f.pow(floor(log10(span/20)).toInt()))
    fun change(v:Float) { val snapped=(round(v/step)*step).coerceIn(param.min,param.max);if(snapped!=value){value=snapped;emit(snapped)} }
    val colors=MaterialTheme.colorScheme
    Column {
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text(param.label,style=MaterialTheme.typography.labelMedium);Text(String.format(Locale.US,"%.3f",value).trimEnd('0').trimEnd('.'),Modifier.clickable{text=value.toString();typing=true},color=colors.primary)}
        Canvas(Modifier.fillMaxWidth().height(52.dp)
            .pointerInput(param.key){detectTapGestures(onDoubleTap={fling?.cancel();change((param.defaultValue as? Number)?.toFloat()?:0f)},onLongPress={text=value.toString();typing=true})}
            .pointerInput(param.key,param.min,param.max){
                val tracker=VelocityTracker();var dy=0f;var distance=0f;var start=0f
                detectDragGestures(onDragStart={fling?.cancel();tracker.resetTracking();dy=0f;distance=0f;start=value},onDrag={event,delta->
                    event.consume();tracker.addPosition(event.uptimeMillis,event.position);dy+=delta.y;distance+=delta.x
                    val fine=if(abs(dy)>24.dp.toPx())20f else 1f
                    change(start-distance*major/(40.dp.toPx()*fine))
                },onDragEnd={
                    val velocity=tracker.calculateVelocity().x
                    if(abs(velocity)>80f&&abs(dy)<24.dp.toPx()) {
                        val old=value;val factor=major/40.dp.toPx()
                        fling=scope.launch { Animatable(0f).animateDecay(velocity,exponentialDecay(frictionMultiplier=2.5f)){change(old-this.value*factor);if(value<=param.min||value>=param.max)throw kotlinx.coroutines.CancellationException("Scrubber reached bound")} }
                    }
                })
            }) {
            val spacing=40.dp.toPx();val center=size.width/2;val first=floor((value-size.width/spacing*major/2)/major).toInt()-1
            val last=ceil((value+size.width/spacing*major/2)/major).toInt()+1
            for(i in first..last){val tick=i*major;if(tick<param.min||tick>param.max)continue;val x=center+(tick-value)/major*spacing;drawLine(colors.onSurfaceVariant.copy(alpha=.65f),Offset(x,size.height*.4f),Offset(x,size.height*.82f),1.dp.toPx());for(j in 1..3){val xx=x+j*spacing/4;drawLine(colors.outline,Offset(xx,size.height*.6f),Offset(xx,size.height*.82f),1.dp.toPx())}}
            drawLine(colors.primary,Offset(center,0f),Offset(center,size.height),2.dp.toPx())
        }
    }
    if(typing)AlertDialog(onDismissRequest={typing=false},title={Text(param.label)},text={OutlinedTextField(text,{text=it},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),supportingText={Text("${param.min} to ${param.max}")})},confirmButton={TextButton(onClick={text.toFloatOrNull()?.takeIf{it.isFinite()}?.let{change(it);typing=false}}){Text("Apply")}},dismissButton={TextButton(onClick={typing=false}){Text("Cancel")}})
}
