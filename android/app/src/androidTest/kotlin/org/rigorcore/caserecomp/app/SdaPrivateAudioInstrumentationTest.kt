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
