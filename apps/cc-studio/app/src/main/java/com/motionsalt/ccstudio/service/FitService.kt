package com.motionsalt.ccstudio.service

import android.app.*
import android.content.*
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.motionsalt.ccstudio.MainActivity
import com.motionsalt.ccstudio.core.*
import com.motionsalt.ccstudio.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File

/** Foreground fitting owns its worker independently of Compose screens. */
class FitService:Service() {
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Default)
    private var job:Job?=null
    override fun onBind(intent:Intent?):IBinder?=null
    override fun onCreate(){super.onCreate();getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel(CHANNEL,"Colour matching",NotificationManager.IMPORTANCE_LOW))}
    override fun onStartCommand(intent:Intent?,flags:Int,startId:Int):Int {
        if(intent?.action==CANCEL){job?.cancel();return START_NOT_STICKY}
        val id=intent?.getStringExtra("projectId")?:return START_NOT_STICKY
        if(job?.isActive==true)return START_NOT_STICKY
        state.value=FitStatus(id,true,0,"Reading project")
        startForeground(2024,notification("Reading project",0))
        job=scope.launch {
            try {
                runInterruptible {
                    val repo=ProjectRepository(this@FitService);val project=repo.getProject(id)?:error("Project no longer exists")
                    val complete=project.pairs.filter{it.isComplete};require(complete.isNotEmpty()) { "A complete base/target pair is required" }
                    val inputs=complete.map{p->
                        fun load(frame:ImageFrame):MatchFrame {
                            val options=BitmapFactory.Options();options.inJustDecodeBounds=true;BitmapFactory.decodeFile(frame.filePath,options)
                            var scale=1;while(maxOf(options.outWidth,options.outHeight)/scale>1280)scale*=2
                            val bmp=BitmapFactory.decodeFile(frame.filePath,BitmapFactory.Options().apply{inSampleSize=scale})?:error("Cannot decode selected frame")
                            val crop=frame.cropRect
                            val left=(crop.left*bmp.width).toInt().coerceIn(0,bmp.width-1);val top=(crop.top*bmp.height).toInt().coerceIn(0,bmp.height-1)
                            val right=(crop.right*bmp.width).toInt().coerceIn(left+1,bmp.width);val bottom=(crop.bottom*bmp.height).toInt().coerceIn(top+1,bmp.height)
                            val pixels=IntArray((right-left)*(bottom-top));bmp.getPixels(pixels,0,right-left,left,top,right-left,bottom-top);bmp.recycle()
                            return MatchFrame.fromArgb(right-left,bottom-top,pixels)
                        }
                        val ignored=(p.base!!.ignoreRects+p.target!!.ignoreRects).map{MatchRegion(it.left.toDouble(),it.top.toDouble(),it.right.toDouble(),it.bottom.toDouble())}
                        val b=load(p.base!!);val t=load(p.target!!)
                        MatchInput(b,t,p.mode==PairMode.SAME_FRAME,ignored+FrameAlignment.letterbox(b)+FrameAlignment.letterbox(t))
                    }
                    val result=MatchPipeline.fit(inputs){step,name->state.value=FitStatus(id,true,step,name);getSystemService(NotificationManager::class.java).notify(2024,notification(name,step*10))}
                    val dir=File(filesDir,"projects/$id/results").apply{mkdirs()}
                    File(dir,"match_model.json").writeText(result.model.toJson().toString(2))
                    File(dir,"shader.glsl").writeText(MatchShader.source(result.model))
                    fun png(name:String,f:MatchFrame){val p=IntArray(f.rgb.size){i->val c=f.rgb[i];0xff000000.toInt() or ((c[0]*255+.5).toInt().coerceIn(0,255) shl 16) or ((c[1]*255+.5).toInt().coerceIn(0,255) shl 8) or (c[2]*255+.5).toInt().coerceIn(0,255)};val bmp=Bitmap.createBitmap(p,f.width,f.height,Bitmap.Config.ARGB_8888);File(dir,name).outputStream().use{bmp.compress(Bitmap.CompressFormat.PNG,100,it)};bmp.recycle()}
                    result.pairs.forEachIndexed{i,pair->
                        val pairId=complete[i].id;png("before_$pairId.png",pair.base);png("target_$pairId.png",pair.target);png("recreated_$pairId.png",pair.base.apply(result.model))
                        val heat=Bitmap.createBitmap(MatchPipeline.heatmap(pair,result.model),pair.base.width,pair.base.height,Bitmap.Config.ARGB_8888);File(dir,"heatmap_$pairId.png").outputStream().use{heat.compress(Bitmap.CompressFormat.PNG,100,it)};heat.recycle()
                    }
                    val r=result.reports.first();project.fitResult=FitResultData(File(dir,"match_model.json").absolutePath,r.fitted.mean.toFloat(),r.fitted.p95.toFloat(),System.currentTimeMillis(),r.identity.mean.toFloat(),r.identity.p95.toFloat(),r.holdout.mean.toFloat(),r.blurred.mean.toFloat(),r.aligned)
                    repo.saveProject(project);state.value=FitStatus(id,false,10,"Match complete",result=project.fitResult)
                }
            } catch(e:CancellationException){state.value=FitStatus(id,false,0,"Cancelled")} catch(e:Exception){state.value=FitStatus(id,false,0,"Fit failed",error=e.message?:e.javaClass.simpleName)}
            finally{stopForeground(STOP_FOREGROUND_REMOVE);stopSelf()}
        }
        return START_NOT_STICKY
    }
    private fun notification(text:String,progress:Int):Notification {
        val cancel=PendingIntent.getService(this,1,Intent(this,FitService::class.java).setAction(CANCEL),PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val open=PendingIntent.getActivity(this,0,Intent(this,MainActivity::class.java),PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return NotificationCompat.Builder(this,CHANNEL).setContentTitle("MOTIONSALT Colour Match").setContentText(text).setSmallIcon(android.R.drawable.ic_menu_compass).setContentIntent(open).setProgress(100,progress,false).setOngoing(true).addAction(android.R.drawable.ic_menu_close_clear_cancel,"Cancel",cancel).build()
    }
    override fun onDestroy(){scope.cancel();super.onDestroy()}
    companion object { const val CHANNEL="colour_match";const val CANCEL="com.motionsalt.ccstudio.CANCEL_FIT";val state=MutableStateFlow(FitStatus()) }
}
data class FitStatus(val projectId:String="",val running:Boolean=false,val step:Int=0,val message:String="Ready",val error:String?=null,val result:FitResultData?=null)
