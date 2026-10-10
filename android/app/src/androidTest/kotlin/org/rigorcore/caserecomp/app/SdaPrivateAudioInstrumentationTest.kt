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
