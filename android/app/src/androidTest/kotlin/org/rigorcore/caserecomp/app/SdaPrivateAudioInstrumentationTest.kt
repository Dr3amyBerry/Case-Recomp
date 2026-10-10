package org.rigorcore.caserecomp.app

import android.media.MediaPlayer
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.rigorcore.caserecomp.sda.*
import java.io.File

/** Codec/reference test with authentic private audio. Not an audible or campaign UI acceptance test. */
@RunWith(AndroidJUnit4::class)
class SdaPrivateAudioInstrumentationTest {
 @Test fun original_options_volume_preview_cancel_save_and_reopen() {
  val instrumentation=InstrumentationRegistry.getInstrumentation();val context=instrumentation.targetContext
  val path=InstrumentationRegistry.getArguments().getString("privateAudioPackage")
  assumeTrue(path!=null && File(path).isFile)
  val display=InstrumentationRegistry.getArguments().getString("visualDisplayId")?.toInt() ?: 0
  val checkpoint=context.getSharedPreferences("case-recomp-sda",0)
  val keys=listOf("active_campaign_checkpoint","session-checkpoint");val before=keys.associateWith { checkpoint.getString(it,null) }
  val prefs=context.getSharedPreferences("case-recomp-vegas-options",0)
  val settingsBefore=prefs.all.toMap()
  assertTrue(prefs.edit().putInt("music",50).putInt("effects",75).commit())
  fun launch()=androidx.test.core.app.ActivityScenario.launch<SdaLauncherActivity>(
   android.content.Intent(context,SdaLauncherActivity::class.java).putExtra("private_sda_package",path),
   android.app.ActivityOptions.makeBasic().setLaunchDisplayId(display).toBundle())
  lateinit var audio:SdaAudioSession
  lateinit var menu:SdaResourceMenuView
  fun touch(action:Int,x:Float,y:Float) {
   val scale=minOf(menu.width/800f,menu.height/600f)
   val event=android.view.MotionEvent.obtain(0,android.os.SystemClock.uptimeMillis(),action,
    (menu.width-800*scale)/2+x*scale,(menu.height-600*scale)/2+y*scale,0)
   try { assertTrue(menu.dispatchTouchEvent(event)) } finally { event.recycle() }
  }
  fun button(value:Int) {
   val rect=menu.buttonBounds(menu.buttons.single { it.number("value")==value })
   touch(android.view.MotionEvent.ACTION_DOWN,rect.centerX().toFloat(),rect.centerY().toFloat())
   touch(android.view.MotionEvent.ACTION_UP,rect.centerX().toFloat(),rect.centerY().toFloat())
  }
  fun bind(activity:SdaLauncherActivity) {
   audio=SdaLauncherActivity::class.java.getDeclaredField("audioSession").apply { isAccessible=true }.get(activity) as SdaAudioSession
   val dialog=SdaLauncherActivity::class.java.getDeclaredField("hostMenu").apply { isAccessible=true }.get(activity) as android.app.Dialog
   menu=dialog.window!!.decorView.findViewWithTag("sda-resource-menu")
   assertEquals(display,activity.display!!.displayId)
  }
  try {
   launch().use { scenario ->
    scenario.onActivity { bind(it);button(30);assertEquals("mainoptionsdlg",menu.screenId) }
    // Real Android gestures: drag captured knobs beyond both track ends; values must clamp.
    scenario.onActivity {
     touch(android.view.MotionEvent.ACTION_DOWN,555f,184f)
     touch(android.view.MotionEvent.ACTION_MOVE,390f,184f)
     touch(android.view.MotionEvent.ACTION_UP,390f,184f)
     assertEquals(0,audio.musicVolume)
     touch(android.view.MotionEvent.ACTION_DOWN,586f,229f)
     touch(android.view.MotionEvent.ACTION_MOVE,720f,229f)
     touch(android.view.MotionEvent.ACTION_UP,720f,229f)
     assertEquals(100,audio.effectsVolume)
     button(36);assertEquals("mainmenuunderlay",menu.screenId)
     assertEquals(50,audio.musicVolume);assertEquals(75,audio.effectsVolume)
     assertEquals(50,prefs.getInt("music",-1));assertEquals(75,prefs.getInt("effects",-1))
     button(30)
     touch(android.view.MotionEvent.ACTION_DOWN,555f,184f)
     touch(android.view.MotionEvent.ACTION_MOVE,390f,184f)
     touch(android.view.MotionEvent.ACTION_UP,390f,184f)
     touch(android.view.MotionEvent.ACTION_DOWN,586f,229f)
     touch(android.view.MotionEvent.ACTION_MOVE,390f,229f)
     touch(android.view.MotionEvent.ACTION_UP,390f,229f)
     assertEquals(0,audio.musicVolume);assertEquals(0,audio.effectsVolume)
     button(31);assertEquals("mainmenuunderlay",menu.screenId)
     assertEquals(0,prefs.getInt("music",-1));assertEquals(0,prefs.getInt("effects",-1))
     button(299)
     fun find(view:android.view.View):SdaGameView? {
      if(view is SdaGameView) return view
      if(view is android.view.ViewGroup) for(i in 0 until view.childCount) find(view.getChildAt(i))?.let { return it }
      return null
     }
     val game=checkNotNull(find(it.window.decorView))
     val rect=checkNotNull(game.visuals).menuRect(checkNotNull(game.campaign))
     val scale=minOf(game.width/800f,game.height/600f)
     for(action in listOf(android.view.MotionEvent.ACTION_DOWN,android.view.MotionEvent.ACTION_UP)) {
      val event=android.view.MotionEvent.obtain(0,0,action,(game.width-800*scale)/2+rect.centerX()*scale,(game.height-600*scale)/2+rect.centerY()*scale,0)
      try { assertTrue(game.dispatchTouchEvent(event)) } finally { event.recycle() }
     }
     bind(it);assertEquals("menudlg2",menu.screenId);button(34)
     assertEquals("mainoptionsdlgeyespy",menu.screenId)
     val slider=menu.sliders.single { it.number("typevalue")==1 }
     val knob=menu.sliderKnobBounds(slider)
     touch(android.view.MotionEvent.ACTION_DOWN,knob.centerX().toFloat(),knob.centerY().toFloat())
     touch(android.view.MotionEvent.ACTION_MOVE,720f,knob.centerY().toFloat());assertEquals(100,audio.musicVolume)
     touch(android.view.MotionEvent.ACTION_CANCEL,720f,knob.centerY().toFloat());assertEquals(0,audio.musicVolume)
     button(220);assertEquals("menudlg2",menu.screenId)
     button(34)
     val next=menu.sliderKnobBounds(menu.sliders.single { it.number("typevalue")==1 })
     touch(android.view.MotionEvent.ACTION_DOWN,next.centerX().toFloat(),next.centerY().toFloat())
     touch(android.view.MotionEvent.ACTION_MOVE,720f,next.centerY().toFloat())
     touch(android.view.MotionEvent.ACTION_UP,720f,next.centerY().toFloat());assertEquals(100,audio.musicVolume)
     val dialog=SdaLauncherActivity::class.java.getDeclaredField("hostMenu").apply { isAccessible=true }.get(it) as android.app.Dialog
     dialog.cancel()
    }
    instrumentation.waitForIdleSync()
    scenario.onActivity { assertEquals("closing options cancels preview",0,audio.musicVolume) }
   }
   launch().use { scenario ->
    scenario.onActivity { bind(it);assertEquals(0,audio.musicVolume);assertEquals(0,audio.effectsVolume);button(30) }
    instrumentation.waitForIdleSync();android.os.SystemClock.sleep(150)
    lateinit var window:android.view.Window;lateinit var bitmap:android.graphics.Bitmap
    scenario.onActivity { activity ->
     val dialog=SdaLauncherActivity::class.java.getDeclaredField("hostMenu").apply { isAccessible=true }.get(activity) as android.app.Dialog
     window=dialog.window!!;bitmap=android.graphics.Bitmap.createBitmap(window.decorView.width,window.decorView.height,android.graphics.Bitmap.Config.ARGB_8888)
    }
    val done=java.util.concurrent.CountDownLatch(1);var status=-1
    android.view.PixelCopy.request(window,bitmap,{ status=it;done.countDown() },android.os.Handler(android.os.Looper.getMainLooper()))
    assertTrue(done.await(10,java.util.concurrent.TimeUnit.SECONDS));assertEquals(android.view.PixelCopy.SUCCESS,status)
    File(context.getExternalFilesDir(null),"vegas-options-volume.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) };bitmap.recycle()
   }
  } finally {
   val edit=prefs.edit().clear()
   for((key,value) in settingsBefore) when(value) {
    is Int -> edit.putInt(key,value);is Boolean -> edit.putBoolean(key,value);is String -> edit.putString(key,value)
    is Long -> edit.putLong(key,value);is Float -> edit.putFloat(key,value)
    is Set<*> -> edit.putStringSet(key,value.filterIsInstance<String>().toSet())
   }
   assertTrue(edit.commit())
   val save=checkpoint.edit();for(key in keys) before[key]?.let { save.putString(key,it) } ?: save.remove(key)
   assertTrue(save.commit())
   assertEquals(settingsBefore,prefs.all)
  }
 }

 @Test fun menu_audio_plays_pauses_resumes_and_releases() {
  val instrumentation=InstrumentationRegistry.getInstrumentation();val context=instrumentation.targetContext
  val path=InstrumentationRegistry.getArguments().getString("privateAudioPackage")
  assumeTrue(path!=null && File(path).isFile)
  val display=InstrumentationRegistry.getArguments().getString("visualDisplayId")?.toInt() ?: 0
  val prefs=context.getSharedPreferences("case-recomp-sda",android.content.Context.MODE_PRIVATE)
  val keys=listOf("active_campaign_checkpoint","session-checkpoint");val before=keys.associateWith { prefs.getString(it,null) }
  fun sessionFiles()=context.cacheDir.listFiles().orEmpty().filter { it.name.startsWith("sda-audio-session-") }.map { it.name }.toSet()
  val filesBefore=sessionFiles()
  lateinit var audio:SdaAudioSession
  try {
   val intent=android.content.Intent(context,SdaLauncherActivity::class.java).putExtra(SdaLauncherActivity.EXTRA_PACKAGE_PATH,path)
   androidx.test.core.app.ActivityScenario.launch<SdaLauncherActivity>(intent,android.app.ActivityOptions.makeBasic().setLaunchDisplayId(display).toBundle()).use { scenario ->
    scenario.onActivity { activity ->
     val field=SdaLauncherActivity::class.java.getDeclaredField("audioSession").apply { isAccessible=true }
     audio=field.get(activity) as SdaAudioSession
     audio.setMusicVolume(-20);assertEquals(0,audio.musicVolume)
     audio.setEffectsVolume(120);assertEquals(100,audio.effectsVolume)
     audio.setEffectsVolume(0)
    }
    val deadline=android.os.SystemClock.uptimeMillis()+5000
    while(!audio.isMusicPlaying && android.os.SystemClock.uptimeMillis()<deadline) android.os.SystemClock.sleep(30)
    assertTrue("actual menu music must start",audio.isMusicPlaying)
    // ActivityScenario's foreign-process helper cannot stop this secondary-display activity.
    // The real catalogue in the same task/display gives actual onPause/onStop/onResume.
    val helperClass=HomeActivity::class.java.name
    val monitor=instrumentation.addMonitor(helperClass,null,false)
    var helper:android.app.Activity?=null
    try {
     scenario.onActivity { activity ->
      val helperIntent=android.content.Intent(activity,HomeActivity::class.java)
      activity.startActivity(helperIntent,android.app.ActivityOptions.makeBasic().setLaunchDisplayId(display).toBundle())
     }
     helper=monitor.waitForActivityWithTimeout(3000)
     assertNotNull("same-display lifecycle helper starts",helper)
     assertEquals(display,helper!!.display!!.displayId)
     val stopDeadline=android.os.SystemClock.uptimeMillis()+3000
     while(scenario.state!=androidx.lifecycle.Lifecycle.State.CREATED && android.os.SystemClock.uptimeMillis()<stopDeadline) android.os.SystemClock.sleep(20)
     assertEquals(androidx.lifecycle.Lifecycle.State.CREATED,scenario.state)
     assertFalse(audio.isMusicPlaying)
    } finally {
     helper?.let { activity -> instrumentation.runOnMainSync { activity.finish() } }
     instrumentation.removeMonitor(monitor)
    }
    val resumeDeadline=android.os.SystemClock.uptimeMillis()+3000
    while(scenario.state!=androidx.lifecycle.Lifecycle.State.RESUMED && android.os.SystemClock.uptimeMillis()<resumeDeadline) android.os.SystemClock.sleep(20)
    assertEquals(androidx.lifecycle.Lifecycle.State.RESUMED,scenario.state)
    assertTrue("menu music must resume",audio.isMusicPlaying)
    scenario.onActivity { activity ->
     val field=SdaLauncherActivity::class.java.getDeclaredField("hostMenu").apply { isAccessible=true }
     val dialog=field.get(activity) as android.app.Dialog
     val menu=dialog.window!!.decorView.findViewWithTag<SdaResourceMenuView>("sda-resource-menu")
     val rect=menu.buttonBounds(menu.buttons.single { it.number("value")==200 })
     val scale=minOf(menu.width/800f,menu.height/600f)
     val x=(menu.width-800*scale)/2+rect.centerX()*scale;val y=(menu.height-600*scale)/2+rect.centerY()*scale
     for(action in listOf(android.view.MotionEvent.ACTION_DOWN,android.view.MotionEvent.ACTION_UP)) {
      val event=android.view.MotionEvent.obtain(0,0,action,x,y,0)
      try { assertTrue(menu.dispatchTouchEvent(event)) } finally { event.recycle() }
     }
    }
    val effectsDeadline=android.os.SystemClock.uptimeMillis()+3000
    while(audio.effectsStarted==0 && android.os.SystemClock.uptimeMillis()<effectsDeadline) android.os.SystemClock.sleep(20)
    assertTrue("original button sfx must start through UI input",audio.effectsStarted>0)
   }
   assertTrue(audio.isClosed);assertFalse(audio.isMusicPlaying)
   assertEquals(0,audio.activePlayers)
   assertEquals(0,audio.cachedFiles)
   assertEquals("owned audio files removed without touching existing files",filesBefore,sessionFiles())
  } finally {
   val edit=prefs.edit();for(key in keys) before[key]?.let { edit.putString(key,it) } ?: edit.remove(key)
   assertTrue(edit.commit())
   for(key in keys) assertEquals(before[key],prefs.getString(key,null))
  }
 }

 @Test fun packaged_music_and_effect_decode_and_play_on_android() {
  val context=InstrumentationRegistry.getInstrumentation().targetContext
  val path=InstrumentationRegistry.getArguments().getString("privateAudioPackage")
  assumeTrue(path!=null && File(path).isFile)
  val evidence=org.json.JSONArray()
  SdaContent.open(File(path!!),SdaImageDecoder { null }).use { content ->
   for(resource in listOf("mainmenu.ogg","eyespy1.ogg","eyespy2.ogg","minigametheme.ogg","ButtonClick2.ogg")) {
    val data=checkNotNull(content.read(resource)) { "missing packaged audio: $resource" }
    assertEquals("OggS",String(data,0,4,Charsets.US_ASCII))
    val file=File.createTempFile("sda-audio-reference-",".ogg",context.cacheDir)
    val player=MediaPlayer()
    try {
     file.writeBytes(data)
     file.inputStream().use { player.setDataSource(it.fd) }
     player.setVolume(0f,0f) // Validate actual playback without playing test audio through the user's speakers.
     player.prepare()
     assertTrue("decoded duration for $resource",player.duration>0)
     player.start();assertTrue("actual Android player starts $resource",player.isPlaying)
     player.pause();assertFalse(player.isPlaying)
     evidence.put(org.json.JSONObject().put("resource",resource).put("bytes",data.size).put("durationMs",player.duration).put("codecPrepared",true).put("silentPlayback",true))
    } finally { player.release();check(file.delete()) { "failed to remove test-owned cache file" } }
   }
  }
  File(context.getExternalFilesDir(null),"vegas-audio-reference.json").writeText(evidence.toString(2))
 }
}
