package org.rigorcore.caserecomp.sda

data class SdaCaptionRun(val text:String,val verticalAdvance:Int)
/** Audited subset of 004725b0/0047285f, matching tools/sda-prototype/fonts.py. */
object SdaCaptionRuns {
 fun parse(text:String,lineHeight:Int): List<SdaCaptionRun> {
  require(lineHeight in 1..4096)
  val result=mutableListOf<SdaCaptionRun>();var offset=0;var start=0;var i=0
  fun flush(end:Int) { if(end>start) result.add(SdaCaptionRun(text.substring(start,end),offset)) }
  while(i<text.length) {
   if(text[i]!='\\') { i++;continue }
   flush(i)
   when {
    text.startsWith("\\n",i) -> { offset+=lineHeight*3/4;i+=2 }
    text.startsWith("\\sa",i) -> {
     val digits=i+3;var end=digits
     while(end<text.length && text[end] in '0'..'9') end++
     require(end-digits in 1..2) { "unsupported native vertical advance" }
     offset+=text.substring(digits,end).toInt();i=end
    }
    else -> throw IllegalArgumentException("unsupported SDA caption escape")
   }
   start=i
  }
  flush(text.length)
  require(result.size<=10) { "caption exceeds native segment count" }
  return result
 }
}
