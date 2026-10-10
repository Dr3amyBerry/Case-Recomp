package org.rigorcore.caserecomp.games.vegas

import org.rigorcore.caserecomp.sda.*

/** Confirmed native 00433a60/004341c0 callbacks. Fingerprint/keypad/final transitions remain pending. */
class VegasThirdRiddleController(definitions:List<SdaInteractiveItemDefinition>,seed:Long,checkpoint:Map<String,Any?>?=null):SdaInteractiveController {
 override val items:SdaInteractiveItems
 private val rng:SdaRng
 private val displayedSymbols=IntArray(4) { 101 }
 val symbols:List<Int> get()=displayedSymbols.toList()
 private val displayed=BooleanArray(4)
 fun symbolVisible(index:Int)=displayed[index]
 override var started=false;private set
 var reelsFinished=false;private set
 var blockedArm=false;private set
 override val isSolved=false // Native door/ending sequence has not been implemented; no synthetic victory.
 private val sounds=mutableListOf<String>()
 override fun drainSounds():List<String> = sounds.toList().also { sounds.clear() }
 init {
  fun savedLong(key:String,fallback:Long):Long {
   if(checkpoint==null) return fallback
   val n=checkpoint[key] as? Number ?: throw IllegalArgumentException("missing $key")
   require(n.toDouble()==n.toLong().toDouble());return n.toLong()
  }
  rng=SdaRng(savedLong("rng",seed))
  if(checkpoint!=null) {
   fun flag(key:String)=checkpoint[key] as? Boolean ?: throw IllegalArgumentException("missing $key")
   started=flag("started");reelsFinished=flag("reelsFinished");blockedArm=flag("blockedArm")
   val symbols=checkpoint["symbols"] as? List<*> ?: throw IllegalArgumentException("missing symbols")
   require(symbols.size==4)
   symbols.forEachIndexed { i,value ->
    require(value is Number && value.toDouble()==value.toInt().toDouble() && (value.toInt() in 0..8 || value.toInt()==101))
    displayedSymbols[i]=value.toInt()
   }
   val visible=checkpoint["symbolVisible"] as? List<*> ?: throw IllegalArgumentException("missing symbol visibility")
   require(visible.size==4 && visible.all { it is Boolean });visible.forEachIndexed { i,value -> displayed[i]=value as Boolean }
   require(started || (!reelsFinished && !blockedArm && displayedSymbols.all { it==101 } && displayed.none { it }))
  }
  @Suppress("UNCHECKED_CAST")
  val saved=checkpoint?.let { it["items"] as? Map<String,Any?> ?: throw IllegalArgumentException("missing item checkpoint") }
  items=SdaInteractiveItems(definitions,saved,::completed,::blocked)
  if(!started) require(items.state()==SdaInteractiveItems(definitions).state()) { "interactive progress before acknowledgement" }
  require(listOf("slotarm","slotarmblockedanimation","coin","slotmachine").all { name -> definitions.any { it.name==name } })
 }
 companion object {
  fun loadGame(content:SdaContent,seed:Long,checkpoint:Map<String,Any?>?=null):SdaInteractiveRiddleGame {
   @Suppress("UNCHECKED_CAST")
   val saved=checkpoint?.let { it["controller"] as? Map<String,Any?> ?: throw IllegalArgumentException("missing controller checkpoint") }
   return SdaInteractiveRiddleGame("ENVS.MSE","thirdriddle",VegasThirdRiddleController(
    SdaInteractiveResources.load(content,"ENVS.MSE","thirdriddleelementscontainer"),seed,saved),checkpoint)
  }
 }
 private fun id(name:String)=items.definitions.indexOfFirst { it.name==name }.also { require(it>=0) { "missing native item $name" } }
 override fun start() { started=true }
 override fun visible(index:Int):Boolean=when(items.definitions[index].name) {
  "slotarm" -> !blockedArm
  "slotarmblockedanimation" -> blockedArm
  else -> true
 }
 override fun click(x:Int,y:Int):Boolean=started && items.click(x,y) { index ->
  // Do not enter an unimplemented native mode and silently credit it.
  visible(index) && items.definitions[index].name !in setOf("fingerprint","pokercard")
 }
 override fun advance(seconds:Float) { require(seconds.isFinite() && seconds>=0);if(started) items.advance(seconds,::visible) }
 private fun blocked(machine:SdaInteractiveItems,index:Int,step:SdaInteractiveStep) {
  if(step.name=="slotarmblockedclick") {
   machine.advanceItem(id("slotarmblockedanimation"));blockedArm=true;sounds.add("slotarmnotfoundsfx")
  }
 }
 private fun completed(machine:SdaInteractiveItems,index:Int,step:SdaInteractiveStep):Boolean {
  when(step.name) {
   "hammerreleasedstep" -> sounds.add("hamerreleasedsfx")
   "hourglassbegindrop" -> sounds.add("hourglassrotatesfx")
   "breakhourglass" -> sounds.add("hourglassbreaksfx")
   "leveroff" -> sounds.add("leveronsfx")
   "coinempty" -> { machine.advanceItem(id("slotarm"));sounds.add("coinsfx") }
   "slotmachineoff" -> {
    listOf(7,6,4,0).forEachIndexed { i,symbol -> displayedSymbols[i]=symbol;displayed[i]=true }
    sounds.add("slotmachineonsfx")
   }
   "reelspinstarted" -> { displayed.fill(false);sounds.add("reelspinsfx") }
   "displayreelspi1code","displayreelspi2code","displayreelspi3code","displayreelspi4code" -> {
    val reel=step.name[14].digitToInt()-1
    displayedSymbols[reel]=(rng.nextScaled()*9.0).toInt();displayed[reel]=true
    if(reel==3) reelsFinished=true
   }
   "slotarmblockedanimationcomplete" -> {
    machine.rewindItem(id("slotarmblockedanimation"),0);blockedArm=false;return false
   }
  }
  return true
 }
 override fun state():Map<String,Any?> = mapOf("rng" to rng.state,"started" to started,"reelsFinished" to reelsFinished,
  "blockedArm" to blockedArm,"symbols" to symbols,"symbolVisible" to displayed.toList(),"items" to items.state())
}
