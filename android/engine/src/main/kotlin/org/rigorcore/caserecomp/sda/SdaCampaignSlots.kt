package org.rigorcore.caserecomp.sda

import org.rigorcore.caserecomp.MiniJson
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** Private, atomic campaign slots. Namespaces and player selection are provided by callers. */
class SdaCampaignSlots(private val directory:File) {
 private val safe=Regex("[A-Za-z0-9][A-Za-z0-9_-]{0,79}")
 private fun file(namespace:String,player:String):File? =
  if(safe.matches(namespace) && safe.matches(player)) File(File(directory,namespace),"$player.json") else null
 @Synchronized fun load(namespace:String,player:String):String? {
  val path=file(namespace,player) ?: return null
  return if(path.isFile) runCatching { path.readText(Charsets.UTF_8) }.getOrNull() else null
 }
 @Synchronized fun save(namespace:String,player:String,json:String):Boolean {
  val destination=file(namespace,player) ?: return false
  if(runCatching { MiniJson.parse(json) is Map<*,*> }.getOrDefault(false)!=true) return false
  return runCatching {
   check(destination.parentFile.isDirectory || destination.parentFile.mkdirs())
   val stage=File.createTempFile("campaign-",".partial",destination.parentFile)
   try {
    java.io.FileOutputStream(stage).use { it.write(json.toByteArray(Charsets.UTF_8));it.fd.sync() }
    try { Files.move(stage.toPath(),destination.toPath(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING) }
    catch(e:java.nio.file.AtomicMoveNotSupportedException) { Files.move(stage.toPath(),destination.toPath(),StandardCopyOption.REPLACE_EXISTING) }
   } finally { stage.delete() }
   true
  }.getOrDefault(false)
 }
}
