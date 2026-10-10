package org.rigorcore.caserecomp.sda

/** Profile-supplied entry point, never inferred from a filename by the generic campaign. */
data class SdaRiddleBinding(val resource: String,val controller: String,val strings: String="STRINGS.TXT")
data class SdaRiddleArrow(val x: Int,val y: Int,val normalUri: String,val disabledUri: String)

/** First phase uses the ordered hotspots and scrolling recovered from its original controller. */
class SdaFirstRiddleGame(resourceName:String,controllerId:String,
 definition:SdaRiddleDefinition,images:Map<String,SdaPixelSource>,background:SdaPixelSource,
 seed:Long,checkpoint:Map<String,Any?>?=null,arrowImages:Map<String,SdaPixelSource> = emptyMap(),captionPaper:SdaPixelSource?=null)
 : SdaPlacementRiddleGame(resourceName,controllerId,definition,images,background,seed,checkpoint,arrowImages,captionPaper,"first_riddle") {
 companion object {
  fun load(content:SdaContent,binding:SdaRiddleBinding,seed:Long,checkpoint:Map<String,Any?>?=null): SdaFirstRiddleGame {
   val definition=SdaRiddleResources.load(content,binding.resource,binding.controller,binding.strings)
   val images=definition.imageUris.mapValues { content.decodeImage(it.value) }
   val arrows=definition.arrows.values.flatMap { listOf(it.normalUri,it.disabledUri) }.distinct().associateWith { content.decodeImage(it) }
   return SdaFirstRiddleGame(binding.resource,binding.controller,definition,images,content.decodeImage(definition.backgroundUri),seed,checkpoint,arrows,definition.captionLayout?.let { content.decodeImage(it.paperUri) })
  }
 }
}
