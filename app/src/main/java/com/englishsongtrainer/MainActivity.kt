package com.englishsongtrainer

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.OpenableColumns
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

class MainActivity : Activity() {
    private val bg=Color.rgb(17,23,30)
    private val panel=Color.rgb(31,43,55)
    private val ink=Color.rgb(235,241,245)
    private val accent=Color.rgb(135,220,196)
    private val muted=Color.rgb(155,173,187)
    private val h=Handler(Looper.getMainLooper())
    private lateinit var p: ExoPlayer
    private lateinit var root: LinearLayout
    private lateinit var title: TextView
    private lateinit var status: TextView
    private lateinit var clock: TextView
    private lateinit var offsetLabel: TextView
    private lateinit var abLabel: TextView
    private lateinit var play: Button
    private lateinit var seek: SeekBar
    private lateinit var scroll: ScrollView
    private lateinit var list: LinearLayout
    private val rows=mutableListOf<TextView>()
    private var media: Uri?=null
    private var subtitle: Uri?=null
    private var cues=emptyList<Cue>()
    private var selected=-1
    private var highlight=-2
    private var offset=0L
    private var speed=1f
    private var mode=0
    private var repeats=3
    private var gap=3L
    private var count=0
    private var a: Long?=null
    private var b: Long?=null
    private var training=false
    private var pending: Runnable?=null
    private var seeking=false
    private var guard=0L
    private var favorites=mutableSetOf<String>()
    private var favoritesOnly=false
    private val prefs by lazy { getSharedPreferences("trainer",MODE_PRIVATE) }
    private fun dp(v:Int)=(v*resources.displayMetrics.density+0.5f).toInt()
    private fun duration()=p.duration.coerceAtLeast(0)
    private fun fmt(t:Long):String { val s=t.coerceAtLeast(0)/1000; return "%02d:%02d".format(s/60,s%60) }
    private fun label(s:String,size:Float=14f,color:Int=ink)=TextView(this).apply {
        text=s; textSize=size; setTextColor(color); gravity=Gravity.CENTER_VERTICAL
    }
    private fun btn(s:String,action:()->Unit)=Button(this).apply {
        text=s; isAllCaps=false; textSize=12f; minHeight=dp(42); minimumWidth=0
        setPadding(dp(2),0,dp(2),0); setOnClickListener{action()}
    }
    private fun row(vararg v:View)=LinearLayout(this).apply {
        orientation=LinearLayout.HORIZONTAL; gravity=Gravity.CENTER_VERTICAL
        v.forEach{addView(it,LinearLayout.LayoutParams(0,-2,1f))}
    }
    private fun toast(s:String)=Toast.makeText(this,s,Toast.LENGTH_SHORT).show()
    private fun choose(code:Int) {
        val i=Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE); type="*/*"
            if(code==1) putExtra(Intent.EXTRA_MIME_TYPES,arrayOf("video/*","audio/*","application/octet-stream"))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
        startActivityForResult(i,code)
    }
    private fun name(u:Uri):String=try {
        contentResolver.query(u,arrayOf(OpenableColumns.DISPLAY_NAME),null,null,null)?.use{
            if(it.moveToFirst()) it.getString(0) else null
        } ?: u.lastPathSegment ?: "本地文件"
    } catch(_:Exception){u.lastPathSegment ?: "本地文件"}

    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor=bg; window.navigationBarColor=bg
        p=ExoPlayer.Builder(this).build()
        ui()
        p.addListener(object:Player.Listener{
            override fun onPlayerError(error:PlaybackException){
                cancel(); status.text="播放失败："+error.errorCodeName
            }
            override fun onIsPlayingChanged(isPlaying:Boolean){ updatePlay() }
        })
        val u=prefs.getString("media",null)
        val sub=prefs.getString("subtitle",null)
        val pos=prefs.getLong("position",0)
        if(u!=null) {
            open(Uri.parse(u),pos)
            offset=prefs.getLong("offset",0)
            if(sub!=null) load(Uri.parse(sub))
        }
        updateOffset()
        h.post(ticker)
    }
    private fun ui(){
        root=LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL;setBackgroundColor(bg);setPadding(dp(12),dp(6),dp(12),dp(6))
        }
        setContentView(root)
        root.addView(label("ENGLISH SONG TRAINER",12f,accent))
        title=label("导入歌曲或视频",20f).apply{maxLines=1}; root.addView(title)
        root.addView(row(btn("打开媒体"){choose(1)},btn("导入歌词"){choose(2)},btn("说明"){help()}))
        val video=PlayerView(this).apply{player=p;useController=false;setBackgroundColor(Color.BLACK)}
        val videoHeight=(resources.configuration.screenHeightDp*0.23).toInt().coerceIn(100,190)
        root.addView(video,LinearLayout.LayoutParams(-1,dp(videoHeight)))
        clock=label("00:00 / 00:00",12f,muted);root.addView(clock)
        seek=SeekBar(this).apply {
            max=1000
            setOnSeekBarChangeListener(object:SeekBar.OnSeekBarChangeListener{
                override fun onStartTrackingTouch(bar:SeekBar){seeking=true;cancel()}
                override fun onProgressChanged(bar:SeekBar,progress:Int,fromUser:Boolean){
                    if(fromUser) clock.text=fmt(duration()*progress/1000)+" / "+fmt(duration())
                }
                override fun onStopTrackingTouch(bar:SeekBar){
                    p.seekTo(duration()*bar.progress/1000);seeking=false;selected=-1
                }
            })
        }
        root.addView(seek,LinearLayout.LayoutParams(-1,dp(30)))
        play=btn("播放"){toggle()}
        root.addView(row(btn("上一句"){go(-1)},btn("重听"){replay()},play,btn("下一句"){go(1)}))
        val speedBtn=btn("速度 1.0×"){}
        speedBtn.setOnClickListener{
            val v=floatArrayOf(0.5f,0.65f,0.75f,0.85f,1f,1.15f,1.25f,1.5f)
            options("变速不变调",v.map{it.toString()+"×"}.toTypedArray(),v.indexOfFirst { it == speed }){
                speed=v[it];p.playbackParameters=PlaybackParameters(speed,1f);speedBtn.text="速度 "+speed+"×"
            }
        }
        val modes=arrayOf("连续播放","句末暂停","单句循环")
        val modeBtn=btn(modes[0]){}
        modeBtn.setOnClickListener{options("训练模式",modes,mode){
            cancel();mode=it;modeBtn.text=modes[it]
            if(mode!=0 && cues.isNotEmpty() && p.isPlaying) replay()
        }}
        val countBtn=btn("循环 3次"){}
        countBtn.setOnClickListener{
            val n=intArrayOf(1,2,3,5,10,0)
            options("循环次数",n.map{if(it==0)"无限" else it.toString()+" 次"}.toTypedArray(),n.indexOf(repeats)){
                repeats=n[it];countBtn.text=if(repeats==0)"无限循环" else "循环 "+repeats+"次"
            }
        }
        val gapBtn=btn("留白 3秒"){}
        gapBtn.setOnClickListener{
            val n=longArrayOf(0,1,2,3,5,8,10)
            options("每次循环后的跟读留白",n.map{it.toString()+" 秒"}.toTypedArray(),n.indexOf(gap)){
                gap=n[it];gapBtn.text="留白 "+gap+"秒"
            }
        }
        root.addView(row(speedBtn,modeBtn,countBtn,gapBtn))
        offsetLabel=label("字幕 +0.0s",12f,muted)
        root.addView(row(btn("字幕 −0.1s"){shift(-100)},offsetLabel,btn("字幕 +0.1s"){shift(100)},btn("重置"){shift(-offset)}))
        abLabel=label("AB 未设定",12f,muted)
        root.addView(row(btn("设 A"){cancel();a=p.currentPosition;b=null;updateAB()},btn("设 B"){setB()},abLabel,
            btn("清除 AB"){cancel();a=null;b=null;updateAB()}))
        status=label("导入时间轴歌词；点击播放，长按收藏",12f,muted);root.addView(status)
        val fav=btn("全部歌词 ☆"){}
        fav.setOnClickListener{favoritesOnly=!favoritesOnly;fav.text=if(favoritesOnly)"仅收藏 ★" else "全部歌词 ☆";render()}
        root.addView(fav,LinearLayout.LayoutParams(-1,dp(42)))
        list=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL}
        scroll=ScrollView(this).apply{addView(list);isFillViewport=true}
        root.addView(scroll,LinearLayout.LayoutParams(-1,0,1f))
        render()
    }
    private fun options(title:String,v:Array<String>,i:Int,action:(Int)->Unit){
        AlertDialog.Builder(this).setTitle(title).setSingleChoiceItems(v,i){d,n->action(n);d.dismiss()}
            .setNegativeButton("取消",null).show()
    }
    private fun help(){
        AlertDialog.Builder(this).setTitle("精听训练").setMessage(
            "打开 MP4/MP3 等媒体，再导入对应的 LRC / SRT / VTT。\n点击歌词定位，选择句末暂停或循环模式。\n"+
            "留白用于模仿原唱；长按收藏。没有歌词时可以设 A、B 点练习片段。\n所有文件只在本地，不自动识别视频画面内的文字。")
            .setPositiveButton("知道了",null).show()
    }
    @Deprecated("Legacy result")
    override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?){
        super.onActivityResult(requestCode,resultCode,data)
        if(resultCode!=RESULT_OK)return
        val u=data?.data ?: return
        try{contentResolver.takePersistableUriPermission(u,Intent.FLAG_GRANT_READ_URI_PERMISSION)}catch(_:Exception){}
        if(requestCode==1){open(u,0);save()}
        if(requestCode==2)load(u)
    }
    private fun open(u:Uri,pos:Long){
        cancel();p.pause();media=u;subtitle=null;cues=emptyList();selected=-1;offset=0;a=null;b=null
        favorites=prefs.getStringSet("fav:"+u,emptySet())?.toMutableSet() ?: mutableSetOf()
        title.text=name(u);p.setMediaItem(MediaItem.fromUri(u),pos);p.prepare()
        updateOffset();updateAB();render();status.text="导入对应歌词，或设置 AB 循环"
    }
    private fun load(u:Uri){
        try{
            val raw=contentResolver.openInputStream(u)?.bufferedReader(Charsets.UTF_8)?.use{r->
                val buf=CharArray(8192);val out=StringBuilder()
                while(true){val n=r.read(buf);if(n<0)break;out.append(buf,0,n);require(out.length<=2000000){"字幕过大"}}
                out.toString()
            } ?: error("无法读取")
            val parsed=Lyrics.parse(raw);require(parsed.isNotEmpty()){"没有识别到时间轴"}
            cancel();subtitle=u;cues=parsed;selected=-1;status.text=name(u)+" · "+cues.size+" 句 · 长按收藏"
            render();save()
        }catch(e:Exception){toast("导入失败："+e.message)}
    }
    private fun key(i:Int)=subtitle.toString()+":"+cues[i].start
    private fun render(){
        list.removeAllViews();rows.clear();highlight=-2
        if(cues.isEmpty())list.addView(label("暂无歌词。导入 LRC、SRT 或 VTT 后开始练习。",16f,muted))
        cues.forEachIndexed{i,c->
            val v=label(fmt(c.start+offset)+"  "+(if(key(i) in favorites)"★ " else "")+c.text,18f).apply{
                setPadding(dp(12),dp(12),dp(8),dp(12))
                setOnClickListener{line(i)}
                setOnLongClickListener{
                    val k=key(i);if(!favorites.add(k))favorites.remove(k)
                    media?.let{prefs.edit().putStringSet("fav:"+it,favorites.toSet()).apply()}
                    render();true
                }
            }
            if(favoritesOnly && key(i) !in favorites)v.visibility=View.GONE
            rows.add(v);list.addView(v)
        }
        refresh()
    }
    private fun shift(delta:Long){cancel();offset=(offset+delta).coerceIn(-60000L,60000L);updateOffset();render();save()}
    private fun updateOffset(){offsetLabel.text="字幕 %+.1fs".format(offset/1000.0)}
    private fun updateAB(){abLabel.text=if(a==null)"AB 未设定" else "A "+fmt(a!!)+" / B "+(b?.let{fmt(it)} ?: "--")}
    private fun setB(){
        if(media==null)return
        val start=a ?: run{toast("先设置 A");return}
        val end=p.currentPosition
        if(end-start<250){toast("B 必须晚于 A 至少 0.25 秒");return}
        cancel();b=end;updateAB();count=0;training=true;seekPlay(start)
    }
    private fun cancel(){pending?.let{h.removeCallbacks(it)};pending=null;training=false;updatePlay()}
    private fun updatePlay(){if(::play.isInitialized)play.text=if(p.playWhenReady || pending!=null)"暂停" else "播放"}
    private fun seekPlay(pos:Long){guard=SystemClock.elapsedRealtime()+150;p.seekTo(pos);p.play();updatePlay()}
    private fun toggle(){
        if(media==null){toast("请先打开媒体");return}
        if(p.playWhenReady || pending!=null){cancel();p.pause();updatePlay();return}
        if(a!=null && b!=null){count=0;training=true;seekPlay(a!!);return}
        if(mode!=0 && cues.isNotEmpty()){line((if(selected>=0)selected else active()).coerceAtLeast(0));return}
        if(p.playbackState==Player.STATE_ENDED)p.seekTo(0)
        p.play();updatePlay()
    }
    private fun line(i:Int){
        if(media==null){toast("请先打开媒体");return}
        if(i !in cues.indices)return
        val (start,end)=Lyrics.range(cues,i,offset,duration())
        if(end<=start || (duration()>0 && start>=duration())){toast("该句超出媒体时间");return}
        cancel();a=null;b=null;updateAB();selected=i;count=0;training=mode!=0;seekPlay(start)
    }
    private fun active()=cues.indexOfLast{it.start<=p.currentPosition-offset}
    private fun go(delta:Int){
        if(cues.isEmpty()){cancel();p.seekTo((p.currentPosition+delta*3000).coerceIn(0,duration()));return}
        val i=if(mode!=0 && selected>=0)selected else active()
        line((i+delta).coerceIn(0,cues.lastIndex))
    }
    private fun replay(){
        if(media==null)return
        if(a!=null && b!=null){cancel();count=0;training=true;seekPlay(a!!);return}
        if(cues.isNotEmpty())line((if(mode!=0 && selected>=0)selected else active()).coerceAtLeast(0))
        else{cancel();seekPlay((p.currentPosition-3000).coerceAtLeast(0))}
    }
    private fun refresh(){
        val i=if(mode!=0 && selected>=0 && a==null)selected else active()
        if(i==highlight)return
        highlight=i
        rows.forEachIndexed{n,v->v.setTextColor(if(n==i)accent else ink);v.setBackgroundColor(if(n==i)panel else Color.TRANSPARENT)}
        rows.getOrNull(i)?.takeIf{it.visibility==View.VISIBLE}?.let{v->scroll.post{scroll.smoothScrollTo(0,(v.top-scroll.height/3).coerceAtLeast(0))}}
    }
    private fun train(){
        if(!training || pending!=null || SystemClock.elapsedRealtime()<guard ||
            (!p.isPlaying && p.playbackState!=Player.STATE_ENDED))return
        val ab=a!=null && b!=null
        val (start,end)=if(ab)a!! to b!! else if(selected in cues.indices)Lyrics.range(cues,selected,offset,duration()) else return
        if(end<=start){cancel();p.pause();updatePlay();return}
        if(p.currentPosition<end && p.playbackState!=Player.STATE_ENDED)return
        p.pause();count++
        if((!ab && mode!=2)||(repeats>0 && count>=repeats)){training=false;updatePlay();return}
        if(gap==0L)seekPlay(start) else{
            val job=Runnable{pending=null;if(training)seekPlay(start)}
            pending=job;updatePlay();h.postDelayed(job,gap*1000)
        }
    }
    private val ticker=object:Runnable{
        override fun run(){
            train()
            if(!seeking){clock.text=fmt(p.currentPosition)+" / "+fmt(duration())
                seek.progress=if(duration()>0)(p.currentPosition*1000/duration()).toInt().coerceIn(0,1000) else 0}
            refresh();h.postDelayed(this,40)
        }
    }
    private fun save(){
        prefs.edit().putString("media",media?.toString()).putString("subtitle",subtitle?.toString())
            .putLong("position",p.currentPosition.coerceAtLeast(0)).putLong("offset",offset).apply()
    }
    override fun onPause(){super.onPause();cancel();p.pause();save()}
    override fun onDestroy(){h.removeCallbacksAndMessages(null);p.release();super.onDestroy()}
}
