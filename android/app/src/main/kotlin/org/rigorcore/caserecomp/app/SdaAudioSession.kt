package org.rigorcore.caserecomp.app

import android.content.Context
import android.media.MediaPlayer
import android.util.Log
import org.rigorcore.caserecomp.sda.*
import java.io.File

/** Resource-driven Android audio; no commercial IDs or rules. Owned cache files live only for this session. */
class SdaAudioSession(private val context:Context,private val content:SdaContent,private val document:SdaUiDocument,
 musicDefault:Int,effectsDefault:Int):AutoCloseable {
 private class Slot(val player:MediaPlayer,val music:Boolean,var ready:Boolean=false)
 private val slots=mutableSetOf<Slot>()
 private val files=mutableMapOf<String,File>()
 private var music:Slot?=null
 private var musicId:String?=null
 private var paused=false
 var isClosed=false;private set
 var musicVolume=musicDefault.coerceIn(0,100);private set
 var effectsVolume=effectsDefault.coerceIn(0,100);private set
 var effectsStarted=0;private set
 val activePlayers get()=slots.size
 val cachedFiles get()=files.size
 val isMusicPlaying get()=music?.let { it.ready && runCatching { it.player.isPlaying }.getOrDefault(false) } ?: false
 fun setMusicVolume(value:Int) { musicVolume=value.coerceIn(0,100);slots.filter { it.music && it.ready }.forEach(::volume) }
 fun setEffectsVolume(value:Int) { effectsVolume=value.coerceIn(0,100);slots.filter { !it.music && it.ready }.forEach(::volume) }
 private fun volume(slot:Slot) {
  val gain=(if(slot.music) musicVolume else effectsVolume)/100f
  slot.player.setVolume(gain,gain)
 }
 fun playMusic(id:String) {
  if(isClosed || musicId==id) return
  stopMusic();musicId=id
  music=load("audiostream",id,true)
  if(music==null) musicId=null
 }
 fun stopMusic() { music?.let(::release);music=null;musicId=null }
 fun playEffect(id:String) {
  if(isClosed || paused) return
  // Bound simultaneous decoders during repeated taps.
  if(slots.count { !it.music }>=8) return
  load("sfx",id,false)
 }
 private fun load(type:String,id:String,isMusic:Boolean):Slot? {
  val node=document.nodes(type).singleOrNull { it.attributes["id"]==id } ?: return null
  val uri=node.attributes["uri"] ?: return null
  var slot:Slot?=null
  try {
   val file=files[uri] ?: run {
    val data=content.read(uri)
    if(data==null) { Log.w("CaseRecompSdaAudio","Package lacks audio $uri");return null }
    val created=File.createTempFile("sda-audio-session-",".bin",context.cacheDir)
    try { created.writeBytes(data) } catch(t:Throwable) { created.delete();throw t }
    files[uri]=created;created
   }
   val player=MediaPlayer();val entry=Slot(player,isMusic);slot=entry;slots.add(entry)
   file.inputStream().use { player.setDataSource(it.fd) }
   player.isLooping=node.attributes["loop"]=="true"
   player.setOnPreparedListener {
    if(isClosed || entry !in slots) return@setOnPreparedListener
    entry.ready=true;volume(entry)
    if(!paused) { player.start();if(!isMusic) effectsStarted++ }
    else if(!isMusic) release(entry)
   }
   player.setOnCompletionListener { release(entry) }
   player.setOnErrorListener { _,_,_ -> release(entry);true }
   player.prepareAsync();return entry
  } catch(t:Exception) {
   slot?.let(::release);Log.w("CaseRecompSdaAudio","Cannot play $uri",t);return null
  }
 }
 private fun release(slot:Slot) {
  if(!slots.remove(slot)) return
  slot.player.setOnPreparedListener(null);slot.player.setOnCompletionListener(null);slot.player.setOnErrorListener(null)
  slot.player.release()
  if(music===slot) { music=null;musicId=null }
 }
 fun pause() {
  paused=true
  slots.filter { !it.music }.toList().forEach(::release)
  music?.takeIf { it.ready && it.player.isPlaying }?.player?.pause()
 }
 fun resume() { if(isClosed) return;paused=false;music?.takeIf { it.ready }?.player?.start() }
 override fun close() {
  if(isClosed) return
  isClosed=true;slots.toList().forEach(::release);music=null;musicId=null
  files.values.forEach { if(!it.delete()) Log.w("CaseRecompSdaAudio","Cannot remove owned audio cache") };files.clear()
 }
}
