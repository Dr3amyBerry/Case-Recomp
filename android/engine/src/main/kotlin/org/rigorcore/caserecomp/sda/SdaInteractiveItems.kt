package org.rigorcore.caserecomp.sda

/** Conditions inspect the named step's completion flag, not whether its index was visited. */
enum class SdaStepCondition { ALWAYS, COMPLETED, NOT_COMPLETED, NEVER }
data class SdaInteractiveImage(val x:Int,val y:Int,val width:Int,val height:Int,val pixels:SdaPixelSource?,val uri:String?=null)
data class SdaInteractiveStep(val imageIndex:Int=0,val frameIndex:Int=0,val frameWidth:Int=-1,
 val interactive:Boolean=false,val frameTime:Float=.125f,val condition:SdaStepCondition=SdaStepCondition.ALWAYS,
 val item:String="",val step:String="",val name:String="")
data class SdaInteractiveItemDefinition(val name:String,val images:List<SdaInteractiveImage>,val steps:List<SdaInteractiveStep>,val initial:Int=0)
data class SdaInteractiveFrame(val image:SdaInteractiveImage,val width:Int,val height:Int,val sourceX:Int,val sourceY:Int)

/** Native 00433650/00433720/00431080: one step per update, strict time comparison, next-step gate. */
class SdaInteractiveItems(val definitions:List<SdaInteractiveItemDefinition>,checkpoint:Map<String,Any?>?=null,
 private val onCompleted:(SdaInteractiveItems,Int,SdaInteractiveStep)->Boolean={ _,_,_ -> true },
 private val onBlocked:(SdaInteractiveItems,Int,SdaInteractiveStep)->Unit={ _,_,_ -> }) {
 private data class Item(var index:Int,val elapsed:FloatArray,val completed:BooleanArray,val interactive:BooleanArray)
 private val items:List<Item>
 init {
  require(definitions.size in 1..256)
  definitions.forEach { d ->
   require(d.images.isNotEmpty() && d.steps.isNotEmpty() && d.initial in d.steps.indices)
   require(d.images.all { it.width>=0 && it.height>=0 && it.x in -32768..32768 && it.y in -32768..32768 })
   d.steps.forEach { s ->
    require(s.imageIndex in d.images.indices && s.frameIndex>=0 && (s.frameWidth==-1 || s.frameWidth>0))
    require(s.frameTime.isFinite() && s.frameTime>=0)
    val image=d.images[s.imageIndex];val fw=if(s.frameWidth==-1) image.width else s.frameWidth
    image.pixels?.let { require(fw>0 && image.height>0 && fw<=it.width && image.height<=it.height && s.frameIndex<(it.width/fw)*(it.height/image.height)) { "invalid interactive atlas frame" } }
   }
  }
  items=definitions.map { d -> Item(d.initial,FloatArray(d.steps.size),BooleanArray(d.steps.size),BooleanArray(d.steps.size) { d.steps[it].interactive }) }
  checkpoint?.let { saved ->
   val rows=saved["items"] as? List<*> ?: throw IllegalArgumentException("missing interactive items")
   require(rows.size==definitions.size)
   rows.forEachIndexed { i,row ->
    val data=row as? Map<*,*> ?: throw IllegalArgumentException("invalid interactive item")
    val d=definitions[i];require(data["name"]==d.name)
    val index=data["index"] as? Number ?: throw IllegalArgumentException("missing step index")
    require(index.toDouble()==index.toInt().toDouble() && index.toInt() in d.steps.indices)
    fun flags(key:String):BooleanArray {
     val list=data[key] as? List<*> ?: throw IllegalArgumentException("missing $key")
     require(list.size==d.steps.size && list.all { it is Boolean });return BooleanArray(list.size) { list[it] as Boolean }
    }
    val completed=flags("completed");val interactive=flags("interactive")
    val elapsed=data["elapsed"] as? List<*> ?: throw IllegalArgumentException("missing elapsed")
    require(elapsed.size==d.steps.size)
    elapsed.forEachIndexed { n,value ->
     require(value is Number);val seconds=value.toFloat();require(seconds.isFinite() && seconds>=0)
     require(!interactive[n] || d.steps[n].interactive) { "invented interactive flag" }
     items[i].elapsed[n]=seconds;items[i].completed[n]=completed[n];items[i].interactive[n]=interactive[n]
    }
    items[i].index=index.toInt()
   }
  }
 }
 fun index(item:Int)=items[item].index
 fun elapsed(item:Int)=items[item].elapsed[items[item].index]
 fun completed(item:String,step:String):Boolean {
  // Native lookups return the first matching item/step; duplicate keypad names remain ordered.
  val i=definitions.indexOfFirst { it.name==item };if(i<0) return false
  val s=definitions[i].steps.indexOfFirst { it.name==step };return s>=0 && items[i].completed[s]
 }
 fun conditionAllows(step:SdaInteractiveStep):Boolean=when(step.condition) {
  SdaStepCondition.ALWAYS -> true
  SdaStepCondition.NEVER -> false
  SdaStepCondition.COMPLETED -> completed(step.item,step.step)
  SdaStepCondition.NOT_COMPLETED -> {
   val i=definitions.indexOfFirst { it.name==step.item }
   i>=0 && definitions[i].steps.any { it.name==step.step } && !completed(step.item,step.step)
  }
 }
 /** Explicit native callbacks may move another item; this never credits campaign victory. */
 fun advanceItem(item:Int):Boolean {
  val state=items[item];if(state.index+1>=definitions[item].steps.size) return false
  state.completed[state.index]=true;state.index++;state.elapsed[state.index]=0f;return true
 }
 /** Controller-directed rewind preserves completed/interactive flags, as native callbacks do. */
 fun rewindItem(item:Int,index:Int) {
  require(index in definitions[item].steps.indices)
  items[item].index=index;items[item].elapsed[index]=0f
 }
 /** Native return animations rearm only their initial interactive step. */
 fun restartItem(item:Int,index:Int) {
  rewindItem(item,index);items[item].completed[index]=false;items[item].interactive[index]=definitions[item].steps[index].interactive
 }
 fun frame(item:Int):SdaInteractiveFrame {
  val step=definitions[item].steps[items[item].index];val image=definitions[item].images[step.imageIndex]
  val width=if(step.frameWidth==-1) image.width else step.frameWidth
  val columns=if(width>0) image.pixels?.width?.div(width) ?: 1 else 1
  return SdaInteractiveFrame(image,width,image.height,step.frameIndex%columns*width,step.frameIndex/columns*image.height)
 }
 fun hit(item:Int,x:Int,y:Int):Boolean {
  val frame=frame(item);val image=frame.image
  val px=x-image.x;val py=y-image.y
  if(px !in 0 until frame.width || py !in 0 until frame.height) return false
  return image.pixels?.getAlpha(frame.sourceX+px,frame.sourceY+py)?.let { it!=0 } ?: true
 }
 private fun finish(item:Int,move:Boolean,repeatCallback:Boolean=false) {
  val state=items[item];val old=state.index;val step=definitions[item].steps[old]
  val result=if(state.completed[old] && !repeatCallback) true else onCompleted(this,item,step)
  if(move) advanceItem(item)
  state.completed[old]=result
 }
 fun click(x:Int,y:Int,eligible:(Int)->Boolean={true}):Boolean {
  var handled=false
  definitions.indices.forEach { i ->
   val state=items[i];val old=state.index;val steps=definitions[i].steps
   if(eligible(i) && state.interactive[old] && hit(i,x,y)) {
    if(old+1<steps.size && !conditionAllows(steps[old+1])) onBlocked(this,i,steps[old])
    else { finish(i,old+1<steps.size);state.interactive[old]=false;handled=true }
   }
  }
  return handled
 }
 fun advance(seconds:Float,eligible:(Int)->Boolean={true}) {
  require(seconds.isFinite() && seconds>=0)
  require(items.all { (it.elapsed[it.index]+seconds).isFinite() })
  definitions.indices.filter(eligible).forEach { i ->
   val state=items[i];val current=state.index;val steps=definitions[i].steps;val step=steps[current]
   state.elapsed[current]+=seconds
   if(state.elapsed[current]>step.frameTime && !state.interactive[current]) {
    if(current+1<steps.size) { if(conditionAllows(steps[current+1])) finish(i,true,repeatCallback=true) }
    else if(!state.completed[current]) finish(i,false)
   }
  }
 }
 fun state():Map<String,Any?> = mapOf("items" to definitions.indices.map { i ->
  val item=items[i];mapOf("name" to definitions[i].name,"index" to item.index.toLong(),"elapsed" to item.elapsed.map { it.toDouble() },
   "completed" to item.completed.toList(),"interactive" to item.interactive.toList())
 })
}
