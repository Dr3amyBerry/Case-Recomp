package org.rigorcore.caserecomp.sda

/** Atlas primitives recovered at 0047e078/0047da4b/00472124; fonts.py is the independent reference. */
data class SdaGlyphRun(val start:Int,val width:Int)
data class SdaGlyphKern(val first:Int,val second:Int,val spacing:Float)
data class SdaGlyphPlacement(val run:SdaGlyphRun,val x:Int,val y:Int)
class SdaAtlasFont(val image:SdaPixelSource,characters:String,private val spacing:Float=1f,
 private val spaceWidth:Int=image.height/2,private val baseline:Int=0,private val kerns:List<SdaGlyphKern> = emptyList()) {
 val runs:List<SdaGlyphRun>
 private val explicit=characters.isNotEmpty()
 private val indices=characters.withIndex().associate { it.value to it.index }
 init {
  require(characters.length<=256 && spacing.isFinite() && spacing>=0 && spaceWidth>=0)
  val found=mutableListOf<SdaGlyphRun>();var start:Int?=null
  for(x in 0 until image.width) {
   val ink=(0 until image.height).any { image.getAlpha(x,it)>4 }
   if(ink && start==null) start=x
   else if(!ink && start!=null) { found.add(SdaGlyphRun(start,x-start));start=null;if(found.size==256) break }
  }
  runs=found
 }
 fun glyph(char:Char):SdaGlyphRun?=runs.getOrNull(if(explicit) indices[char] ?: -1 else char.code-33)
 fun advance(char:Char):Int=((if(char==' ') spaceWidth else glyph(char)?.width ?: 0).toDouble()*spacing.toDouble()).toInt()
 fun measure(text:String):Int=text.sumOf(::advance)
 fun layout(text:String,x:Int,y:Int,halign:Int=0,valign:Int=0):List<SdaGlyphPlacement> {
  require(halign in 0..2 && valign in 0..3)
  val top=y-when(valign) { 0 -> image.height;1 -> image.height-baseline;2 -> image.height/2;else -> 0 }
  return SdaCaptionRuns.parse(text,image.height).flatMap { segment ->
   val width=measure(segment.text);var pen=x-when(halign) { 1 -> width/2;2 -> width;else -> 0 }
   segment.text.mapIndexedNotNull { index,char ->
    val placed=if(char==' ') null else glyph(char)?.let { SdaGlyphPlacement(it,pen,top+segment.verticalAdvance) }
    pen+=advance(char)
    if(index+1<segment.text.length) pen+=kerns.firstOrNull { it.first==(char.code and 255) && it.second==(segment.text[index+1].code and 255) }?.spacing?.toInt() ?: 0
    placed
   }
  }
 }
}
