package org.rigorcore.caserecomp.sda

/** Placement-riddle inputs at completed animation boundaries. Native fades/scroll timing remain pending. */
open class SdaPlacementRiddleGame(override val resourceName: String,val controllerId: String,
 val definition: SdaRiddleDefinition,val images: Map<String,SdaPixelSource>,val background: SdaPixelSource,
 val seed: Long,checkpoint: Map<String,Any?>?=null,val arrowImages: Map<String,SdaPixelSource> = emptyMap(),val captionPaper:SdaPixelSource?=null, final override val kind:String,
 val trayImages:Map<String,SdaPixelSource> = images,val placedImages:Map<String,SdaPixelSource> = images) : SdaBonusGame {
 override val rows=1
 override val cols=definition.pieces.size
 override var points=0
  set(value) { require(value==0) { "no certified riddle reward" };field=value }
 val interaction: SdaRiddleInteraction
 var pointerX: Int?=null; private set
 var pointerY: Int?=null; private set
 val backgroundY: Int get()=definition.backgroundY+interaction.board.placed.sumOf { definition.destinations.getValue(it).scrollUp }
 val caption: String? get()=definition.pieces.firstOrNull { it.hasTarget && it.placeOrder==interaction.board.currentOrder }?.let { definition.captions[it.id] }
 override val isSolved get()=interaction.board.isSolved
 init {
  require(images.keys==definition.pieces.map { it.id }.toSet()) { "missing placement-riddle images" }
  require(trayImages.keys==images.keys && placedImages.keys==images.keys) { "incomplete riddle image variants" }
  @Suppress("UNCHECKED_CAST")
  val saved=checkpoint?.let { it["interaction"] as? Map<String,Any?> ?: throw IllegalArgumentException("missing placement-riddle interaction") }
  interaction=SdaRiddleInteraction(definition.pieces,definition.required,seed,definition.tray,saved)
  if(checkpoint!=null) {
   require(checkpoint["kind"]==kind && checkpoint["resourceName"]==resourceName && checkpoint["controllerId"]==controllerId &&
    (checkpoint["points"]==0L || checkpoint["points"]==0)) { "placement-riddle checkpoint identity mismatch" }
   fun coordinate(key:String): Int? {
    val value=checkpoint[key] ?: return null
    require(value is Long || value is Int)
    val n=(value as Number).toLong();require(n in -32768..32768);return n.toInt()
   }
   pointerX=coordinate("pointerX");pointerY=coordinate("pointerY")
   require((pointerX!=null)==(pointerY!=null) && (pointerX!=null)==(interaction.board.selected!=null)) { "invalid held riddle pointer" }
  }
 }
 fun movePixel(x:Int,y:Int): Boolean {
  if(interaction.board.selected==null || x !in -32768..32768 || y !in -32768..32768) return false
  pointerX=x;pointerY=y;return true
 }
 fun arrowRect(key:String): SdaRiddleCell? {
  val arrow=definition.arrows[key] ?: return null
  val image=arrowImages.getValue(arrow.normalUri)
  return SdaRiddleCell(key,arrow.x,arrow.y,image.width,image.height)
 }
 fun arrowEnabled(key:String)=interaction.board.selected==null && !isSolved &&
  if(key=="up") interaction.firstVisible>0 else interaction.firstVisible<maxOf(0,interaction.board.available.size-4)
 override fun clickPixel(x:Int,y:Int): Boolean {
  if(isSolved) return false
  if(interaction.board.selected!=null) {
   if(!movePixel(x,y)) return false
   val placed=interaction.dropScreen(x,y,definition.backgroundX,backgroundY)
   pointerX=null;pointerY=null;return placed
  }
  for(key in listOf("up","down")) if(arrowRect(key)?.contains(x,y)==true) return interaction.scroll(if(key=="up") -1 else 1)
  val picked=interaction.pickPixel(x,y)
  if(picked) movePixel(x,y)
  return picked
 }
 override fun solve(): Unit=throw UnsupportedOperationException("riddle cannot be skipped")
 override fun state(): Map<String,Any?> = mapOf("kind" to kind,"resourceName" to resourceName,"controllerId" to controllerId,
  "points" to 0L,"interaction" to interaction.state(),"pointerX" to pointerX?.toLong(),"pointerY" to pointerY?.toLong())
}
