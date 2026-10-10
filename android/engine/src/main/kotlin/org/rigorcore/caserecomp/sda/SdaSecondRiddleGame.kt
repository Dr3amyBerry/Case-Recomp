package org.rigorcore.caserecomp.sda

/** Second phase: eight unordered original placements, separate PDA/target images, no invented clock. */
class SdaSecondRiddleGame private constructor(resourceName:String,controllerId:String,
 definition:SdaRiddleDefinition,images:Map<String,SdaPixelSource>,background:SdaPixelSource,
 seed:Long,checkpoint:Map<String,Any?>?,arrows:Map<String,SdaPixelSource>,paper:SdaPixelSource?,
 tray:Map<String,SdaPixelSource>,placed:Map<String,SdaPixelSource>)
 : SdaPlacementRiddleGame(resourceName,controllerId,definition,images,background,seed,checkpoint,arrows,paper,"second_riddle",tray,placed) {
 var started:Boolean=checkpoint?.let { require(it["started"] is Boolean) { "missing second-riddle start acknowledgement" };it["started"] as Boolean } ?: false
  private set
 init { require(started || (interaction.board.placed.isEmpty() && interaction.board.selected==null)) { "placements before start acknowledgement" } }
 fun start() { started=true }
 override fun clickPixel(x:Int,y:Int)=started && super.clickPixel(x,y)
 override fun state():Map<String,Any?> = super.state()+mapOf("started" to started)
 companion object {
  fun load(content:SdaContent,binding:SdaRiddleBinding,seed:Long,checkpoint:Map<String,Any?>?=null):SdaSecondRiddleGame {
   val d=SdaRiddleResources.loadSecond(content,binding.resource,binding.controller,binding.strings)
   val arrows=d.arrows.values.flatMap { listOf(it.normalUri,it.disabledUri) }.distinct().associateWith(content::decodeImage)
   return SdaSecondRiddleGame(binding.resource,binding.controller,d,d.imageUris.mapValues { content.decodeImage(it.value) },
    content.decodeImage(d.backgroundUri),seed,checkpoint,arrows,d.captionLayout?.let { content.decodeImage(it.paperUri) },
    d.trayImageUris.mapValues { content.decodeImage(it.value) },d.targetImageUris.mapValues { content.decodeImage(it.value) })
  }
 }
}
