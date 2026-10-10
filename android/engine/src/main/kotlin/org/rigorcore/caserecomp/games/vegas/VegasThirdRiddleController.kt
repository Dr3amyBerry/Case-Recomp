package org.rigorcore.caserecomp.games.vegas

import org.rigorcore.caserecomp.sda.*

/** Native interactive callbacks, fingerprint reader and keypad. Door/ending transitions remain pending. */
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
 var endingState=5;private set
 var endingTime=0f;private set
 var doorOffset=0;private set
 private var doorFraction=0f
 private var sceneAlpha=1f
 private var roomAlpha=0f
 private var paperAlpha=0f
 val sceneOpacity get()=if(endingState in setOf(8,9,10,12,0)) sceneAlpha.coerceIn(0f,1f) else 1f
 val roomOpacity get()=roomAlpha.coerceIn(0f,1f)
 val newspaperOpacity get()=paperAlpha.coerceIn(0f,1f)
 override val isSolved get()=endingState==0
 var fingerprintHeld=false;private set
 var fingerprintReturning=false;private set
 var fingerprintX=406f;private set
 var fingerprintY=243f;private set
 private var returnX=406f
 private var returnY=243f
 private var returnTime=0f
 var keypadEnabled=false;private set
 var keypadError=false;private set
 var doorOpening=false;private set
 private var errorTime=0f
 private var blinkTime=0f
 var ledsVisible=true;private set
 private val entered=IntArray(4) { 101 }
 private var inputIndex=0
 val ledFrames:List<Int> get()=entered.map { when(it) { 0->5;1->6;2->7;3->1;4->4;5->8;6->3;7->2;8->9;101->0;else->10 } }
 // Native 004313a0 rect insertion order: bottom row, middle row, top row.
 val keyBounds=listOf(SdaRiddleCell("0",656,417,29,26),SdaRiddleCell("1",684,417,29,26),SdaRiddleCell("2",713,417,27,26),
  SdaRiddleCell("3",656,394,29,23),SdaRiddleCell("4",684,394,29,23),SdaRiddleCell("5",713,394,27,23),
  SdaRiddleCell("6",659,373,29,21),SdaRiddleCell("7",684,373,29,21),SdaRiddleCell("8",713,373,27,21))
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
  if(checkpoint!=null && checkpoint["version"] in setOf(2L,2,3L,3)) {
   fun flag(key:String)=checkpoint[key] as? Boolean ?: throw IllegalArgumentException("missing $key")
   fun time(key:String):Float {
    val v=checkpoint[key] as? Number ?: throw IllegalArgumentException("missing $key")
    return v.toFloat().also { require(it.isFinite() && it>=0) }
   }
   fun coordinate(key:String):Float {
    val v=checkpoint[key] as? Number ?: throw IllegalArgumentException("missing $key")
    return v.toFloat().also { require(it.isFinite() && it in -32768f..32768f) }
   }
   fingerprintHeld=flag("fingerprintHeld");fingerprintReturning=flag("fingerprintReturning")
   fingerprintX=coordinate("fingerprintX");fingerprintY=coordinate("fingerprintY")
   returnX=coordinate("returnX");returnY=coordinate("returnY");returnTime=time("returnTime")
   keypadEnabled=flag("keypadEnabled");keypadError=flag("keypadError");doorOpening=flag("doorOpening")
   errorTime=time("errorTime");blinkTime=time("blinkTime");ledsVisible=flag("ledsVisible")
   val input=checkpoint["entered"] as? List<*> ?: throw IllegalArgumentException("missing keypad input")
   require(input.size==4);input.forEachIndexed { i,v ->
    require(v is Number && v.toDouble()==v.toInt().toDouble() && (v.toInt() in 0..8 || v.toInt() in setOf(10,101)))
    entered[i]=v.toInt()
   }
   val index=checkpoint["inputIndex"] as? Number ?: throw IllegalArgumentException("missing input index")
   require(index.toDouble()==index.toInt().toDouble() && index.toInt() in 0..3);inputIndex=index.toInt()
   require(!fingerprintHeld || !fingerprintReturning)
   require(started || (!fingerprintHeld && !fingerprintReturning && !keypadEnabled && !keypadError && !doorOpening))
  } else require(checkpoint?.get("version")==null) { "unsupported controller checkpoint version" }
  if(checkpoint!=null && checkpoint["version"] in setOf(3L,3)) {
   fun number(key:String):Number=checkpoint[key] as? Number ?: throw IllegalArgumentException("missing $key")
   fun scalar(key:String):Float=number(key).toFloat().also { require(it.isFinite() && it>=0) }
   val stage=number("endingState");require(stage.toDouble()==stage.toInt().toDouble() && stage.toInt() in setOf(0,5,6,7,8,9,10,12))
   endingState=stage.toInt();endingTime=scalar("endingTime")
   val offset=number("doorOffset");require(offset.toDouble()==offset.toInt().toDouble() && offset.toInt() in 0..60);doorOffset=offset.toInt()
   doorFraction=scalar("doorFraction");require(doorFraction<1f)
   sceneAlpha=scalar("sceneAlpha");roomAlpha=scalar("roomAlpha");paperAlpha=scalar("paperAlpha")
   require(sceneAlpha<=1f && roomAlpha<=1f && paperAlpha<=1f)
   require(doorOpening==(endingState!=5)) { "ending state contradicts door callback" }
  } else if(doorOpening) endingState=6 // Previously earned v2 unlock begins its unimplemented transition now.
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
  "fingerprint" -> !fingerprintHeld && !fingerprintReturning
  else -> true
 }
 override fun move(x:Int,y:Int):Boolean {
  if(!fingerprintHeld || x !in -32768..32768 || y !in -32768..32768) return false
  val image=items.definitions[id("fingerprint")].images.first()
  fingerprintX=(x-image.width/2).toFloat();fingerprintY=(y-image.height/2).toFloat();return true
 }
 override fun click(x:Int,y:Int):Boolean {
  if(!started || doorOpening || fingerprintReturning || keypadError) return false
  if(fingerprintHeld) {
   val image=items.definitions[id("fingerprint")].images.first()
   val cx=fingerprintX.toInt()+image.width/2;val cy=fingerprintY.toInt()+image.height/2
   if(cx in 594 until 651 && cy in 334 until 492 && items.index(id("reader"))>0) {
    items.advanceItem(id("fingerprint"));fingerprintHeld=false;return true
   }
   fingerprintHeld=false;fingerprintReturning=true;returnTime=0f;returnX=fingerprintX;returnY=fingerprintY
   return false
  }
  if(x in 651 until 770 && y in 334 until 492) {
   if(!keypadEnabled) { sounds.add("readeroffinputsfx");return false }
   val key=keyBounds.indexOfFirst { it.contains(x,y) }
   sounds.add("readerinputsfx");if(key>=0) return enterSymbol(key)
  }
  return items.click(x,y) { index -> visible(index) && items.definitions[index].name!="pokercard" }
 }
 /** Native keyboard and mouse share 004334d0; comparison is against all four live reel-code values. */
 fun enterSymbol(symbol:Int):Boolean {
  require(symbol in 0..8)
  if(!started || !keypadEnabled || keypadError || doorOpening || fingerprintHeld || fingerprintReturning) return false
  entered[inputIndex++]=symbol
  if(reelsFinished && entered.toList()==symbols) {
   items.advanceItem(id("lock"));inputIndex=2
  } else if(inputIndex==4) {
   inputIndex=0;entered.fill(10);keypadError=true;errorTime=0f;blinkTime=0f;ledsVisible=true
  }
  return true
 }
 override fun advance(seconds:Float) {
  require(seconds.isFinite() && seconds>=0)
  if(!started) return
  if(doorOpening) { advanceEnding(seconds);return }
  if(fingerprintReturning) {
   returnTime+=seconds
   if(returnTime>.5f) {
    fingerprintReturning=false;items.restartItem(id("fingerprint"),0)
    fingerprintX=406f;fingerprintY=243f
   } else {
    // 00432450 state 15: constant-speed return over .5s; rendering truncates the float position.
    fingerprintX+=(406f-returnX)*seconds*2f;fingerprintY+=(243f-returnY)*seconds*2f
   }
   return
  }
  items.advance(seconds,::visible)
  if(keypadError) {
   errorTime+=seconds;blinkTime+=seconds
   if(blinkTime>.3f) { blinkTime=0f;ledsVisible=!ledsVisible }
   if(errorTime>1.65f) { keypadError=false;errorTime=0f;entered.fill(101);ledsVisible=true }
  }
 }
 /** 00432450 states 6..12: step movement is per native update, not invented pixels/second. */
 private fun advanceEnding(seconds:Float) {
  require((endingTime+seconds).isFinite())
  when(endingState) {
   6 -> if(seconds>0f) { endingState=7;endingTime=0f;sounds.add("doorsfx") }
   7 -> {
    endingTime+=seconds
    val previousX=743+doorOffset
    val distance=doorFraction+1.875f;val pixels=distance.toInt();doorFraction=distance-pixels;doorOffset+=pixels
    if(previousX>800) { endingState=8;endingTime=0f;sceneAlpha=1f;roomAlpha=0f;sounds.add("finishsfx") }
   }
   8 -> {
    endingTime+=seconds;sceneAlpha=(sceneAlpha-seconds).coerceAtLeast(0f);roomAlpha=(roomAlpha+seconds).coerceAtMost(1f)
    if(endingTime>1f) { endingState=9;endingTime=0f;sceneAlpha=0f;roomAlpha=1f }
   }
   9 -> { endingTime+=seconds;if(endingTime>2f) { endingState=10;endingTime=0f } }
   10 -> { endingTime+=seconds;paperAlpha=(paperAlpha+seconds).coerceAtMost(1f);if(endingTime>3f) { endingState=12;endingTime=0f;paperAlpha=1f } }
   12 -> { endingTime+=seconds;if(endingTime>.7f) { endingState=0;endingTime=0f } }
  }
 }
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
   "fingerprintshouldbemoved" -> { fingerprintHeld=true;fingerprintX=406f;fingerprintY=243f }
   "keypadenabled" -> { keypadEnabled=true;inputIndex=0;entered.fill(101);sounds.add("fingerprintplacedsfx") }
   "dooropen" -> { doorOpening=true;endingState=6;endingTime=0f }
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
 override fun state():Map<String,Any?> = mapOf("version" to 3L,"rng" to rng.state,
  "endingState" to endingState,"endingTime" to endingTime.toDouble(),"doorOffset" to doorOffset,"doorFraction" to doorFraction.toDouble(),
  "sceneAlpha" to sceneAlpha.toDouble(),"roomAlpha" to roomAlpha.toDouble(),"paperAlpha" to paperAlpha.toDouble(),
  "fingerprintHeld" to fingerprintHeld,"fingerprintReturning" to fingerprintReturning,"fingerprintX" to fingerprintX.toDouble(),"fingerprintY" to fingerprintY.toDouble(),
  "returnX" to returnX.toDouble(),"returnY" to returnY.toDouble(),"returnTime" to returnTime.toDouble(),
  "keypadEnabled" to keypadEnabled,"keypadError" to keypadError,"doorOpening" to doorOpening,"entered" to entered.toList(),"inputIndex" to inputIndex,
  "errorTime" to errorTime.toDouble(),"blinkTime" to blinkTime.toDouble(),"ledsVisible" to ledsVisible,"started" to started,"reelsFinished" to reelsFinished,
  "blockedArm" to blockedArm,"symbols" to symbols,"symbolVisible" to displayed.toList(),"items" to items.state())
}
