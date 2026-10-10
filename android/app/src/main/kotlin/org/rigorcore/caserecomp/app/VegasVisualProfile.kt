package org.rigorcore.caserecomp.app

import android.graphics.*
import org.rigorcore.caserecomp.sda.*

/** Vegas graph IDs and recovered 0043eec0 layouts belong to this title profile. */
class VegasVisualProfile(private val content:SdaContent):SdaVisualProfile {
 private val doc=SdaUiDocument(requireNotNull(content.read("ENVS.MSE")),content.loadStrings("ENVS.MSE"))
 private val ui=SdaResourceCanvas(doc,content)
 override fun audioSession(context:android.content.Context):SdaAudioSession {
  val sliders=doc.component("mainoptionsdlg").children.filter { it.type=="slider" }
  val prefs=context.getSharedPreferences("case-recomp-vegas-options",android.content.Context.MODE_PRIVATE)
  return SdaAudioSession(context,content,doc,prefs.getInt("music",sliders.single { it.number("typevalue")==1 }.number("value")),prefs.getInt("effects",sliders.single { it.number("typevalue")==2 }.number("value")))
 }
 // Native resource IDs, visibility and dialog action adapters remain title-specific.
 override fun menuView(context:android.content.Context,campaign:SdaCampaign?,entry:SdaMenuEntry,audio:SdaAudioSession?,onAction:(SdaMenuAction)->Unit):SdaResourceMenuView {
  val textures=listOf("mpi_diag_tleft","mpi_diag_tmid","mpi_diag_tright","mpi_diag_left","mpi_diag_mid",
   "mpi_diag_right","mpi_diag_bleft","mpi_diag_bmid","mpi_diag_bright")
  val main=doc.component("mainmenuunderlay").let { node ->
   node.copy(children=node.children.filter { child ->
    val id=child.attributes["id"]
    when(child.type) {
     "image" -> id==null || id in setOf("img_mm_idgeneric","imagelock")
     "label" -> id !in setOf("recover","unlocked","unlimited")
     "allbutton" -> id !in setOf("unlimitedbtn","unlimitedspotbtn","disunlimitedspotbtn")
     "quitbutton" -> true
     else -> false
    }
   })
  }
  val backdrop=main.children.filter { it.type=="image" && it.attributes["id"]==null }
  val prefs=context.getSharedPreferences("case-recomp-vegas-options",android.content.Context.MODE_PRIVATE)
  var optionsReturn="mainmenuunderlay"
  var optionsBefore:Pair<Int,Int>?=null
  var helpReturn=if(entry==SdaMenuEntry.MAIN) "mainmenuunderlay" else "menudlg2"
  lateinit var view:SdaResourceMenuView
  var mainBackdrop=entry==SdaMenuEntry.MAIN
  fun show(id:String) {
   if(id=="mainmenuunderlay") { mainBackdrop=true;audio?.playMusic("mainmenutrack") }
   if(id=="menudlg2") mainBackdrop=false
   view.show(if(id=="mainmenuunderlay") main else doc.component(id),if(mainBackdrop && id!="mainmenuunderlay") backdrop else emptyList())
  }
  fun cancelOptions() {
   optionsBefore?.let { (music,effects) -> audio?.setMusicVolume(music);audio?.setEffectsVolume(effects) };optionsBefore=null
  }
  fun openOptions(from:String) {
   optionsReturn=from;optionsBefore=(audio?.musicVolume ?: prefs.getInt("music",50)) to (audio?.effectsVolume ?: prefs.getInt("effects",75))
   show(if(from=="menudlg2") "mainoptionsdlgeyespy" else "mainoptionsdlg")
   // These four desktop/gameplay checkbox contracts are not recovered yet: visibly disabled.
   val original=doc.component(view.screenId)
   // Measured Spanish Windows reference: template-matched outer corners, not XUI's base size.
   // Native runtime resizing algorithm is not yet recovered; keep this edition calibration here.
   val spanish=doc.resolve("@ID_OPTIONS_DIALOG1").uppercase(java.util.Locale.ROOT)=="OPCIONES DEL JUEGO"
   val node=if(spanish) original.copy(attributes=original.attributes+mapOf("x" to "114","y" to "85"),
    children=original.children.map { if(it.type=="dialogimg") it.copy(attributes=it.attributes+mapOf("w" to "573")) else it }) else original
   view.show(node.copy(children=node.children.map {
    if(it.type=="checkbox" || (it.type=="label" && it.attributes["caption"] in setOf("@ID_OPTIONS_FSCREEN","@ID_OPTIONS_HINTS","@ID_OPTIONS_RELAXED","@ID_OPTIONS_HACC"))) it.copy(attributes=it.attributes+mapOf("disabled" to "true")) else it
   }),if(mainBackdrop) backdrop else emptyList())
   view.sliders.forEach { view.setSliderValue(it,if(it.number("typevalue")==1) optionsBefore!!.first else optionsBefore!!.second) }
  }
  view=SdaResourceMenuView(context,ui,main,textures,800,600) { value ->
   val screen=view.screenId
   when {
    screen=="mainmenuunderlay" -> when(value) {
     299 -> { audio?.stopMusic();onAction(SdaMenuAction.RESUME) }
     -1 -> onAction(SdaMenuAction.RETURN_TO_CATALOGUE)
     200 -> { helpReturn=screen;show("mainoverlaydlg") }
     30 -> openOptions(screen)
     else -> onAction(SdaMenuAction.UNAVAILABLE)
    }
    screen=="menudlg2" -> when(value) {
     215 -> { audio?.stopMusic();onAction(SdaMenuAction.RESUME) }
     80 -> show("mainmenuunderlay")
     34 -> openOptions(screen)
     209 -> {
      helpReturn=screen
      val family=when(campaign?.bonusGame) { is SdaTileRotGame -> "tilerotgame";is SdaTileSwapGame -> "tilegame"
       is SdaWordSearchGame -> "wordsearchgame";is SdaJigsawGame -> "jigsawgame";else -> null }
      show(if(family==null) "mainoverlaydlg" else family+"instructionsdlgoverlay")
     }
    }
    screen in setOf("mainoptionsdlg","mainoptionsdlgeyespy") -> when(value) {
     36,220 -> { cancelOptions();show(optionsReturn) }
     31,35 -> {
      val music=view.sliders.single { it.number("typevalue")==1 }.let(view::sliderValue)
      val effects=view.sliders.single { it.number("typevalue")==2 }.let(view::sliderValue)
      prefs.edit().putInt("music",music).putInt("effects",effects).apply()
      optionsBefore=null;show(optionsReturn)
     }
     else -> onAction(SdaMenuAction.UNAVAILABLE)
    }
    screen.startsWith("mainoverlaydlg") -> when(value) {
     15 -> show("mainoverlaydlg2");16 -> show("mainoverlaydlg");17,20 -> show("mainoverlaydlg3")
     18 -> show("mainoverlaydlg2");19 -> show("mainoverlaydlg4")
     in 201..204 -> show(helpReturn)
     else -> onAction(SdaMenuAction.UNAVAILABLE)
    }
    value in 1025..1028 -> show(helpReturn)
    else -> onAction(SdaMenuAction.UNAVAILABLE)
   }
  }
  view.onSliderValue={ node,value -> if(node.number("typevalue")==1) audio?.setMusicVolume(value) else audio?.setEffectsVolume(value) }
  view.onClose={ cancelOptions() }
  view.onSoundEffect={ audio?.playEffect(it) }
  show(if(entry==SdaMenuEntry.MAIN) "mainmenuunderlay" else "menudlg2")
  return view
 }
 private val sceneRows=mutableMapOf<String,Map<List<String>,SdaXuiSet>>()
 private val photos=Paint(Paint.FILTER_BITMAP_FLAG)
 private val tileCanvases=mutableMapOf<String,SdaResourceCanvas>()
 override val returnMapRect get()=ui.rect(doc.component("mapbutton"))
 override val solveRect get()=ui.rect(doc.component("solvebutton"))
 // 0046ac50 registers slot 2; 00410300 hides it on bonus entry.
 override fun bonusSolveRect(campaign:SdaCampaign):Rect? = null
 private val layouts=listOf(
  listOf(378 to 211),listOf(278 to 211,477 to 211),listOf(379 to 133,282 to 293,480 to 293),
  listOf(278 to 131,477 to 131,278 to 291,477 to 291),
  listOf(179 to 131,377 to 131,575 to 131,278 to 291,477 to 291),
  listOf(179 to 131,377 to 131,575 to 131,179 to 291,377 to 291,575 to 291),
  listOf(278 to 51,477 to 51,179 to 211,377 to 211,575 to 211,278 to 371,477 to 371),
  listOf(179 to 51,377 to 51,575 to 51,179 to 211,377 to 211,575 to 211,278 to 371,477 to 371),
  listOf(179 to 51,377 to 51,575 to 51,179 to 211,377 to 211,575 to 211,179 to 371,377 to 371,575 to 371))
 private fun cards(c:SdaCampaign):List<Pair<SdaUiNode,Pair<Int,Int>>> {
  val nodes=doc.component("mapunderlay").children.filter { it.type=="scenebutton" && it.attributes["name"] in c.currentLevel.scenes }
  require(nodes.size==c.currentLevel.scenes.size && nodes.map { it.attributes["name"] }.distinct().size==nodes.size)
  require(nodes.size in 1..9)
  return nodes.zip(layouts[nodes.size-1])
 }
 private var pointerPosition:Pair<Int,Int>?=null
 private var pointerPressed=false
 override fun pointer(x:Int?,y:Int?,pressed:Boolean) {
  pointerPosition=if(x!=null && y!=null) x to y else null;pointerPressed=pressed
 }
 private fun buttonState(node:SdaUiNode):SdaButtonState {
  val p=pointerPosition ?: return SdaButtonState.NORMAL
  return if(ui.rect(node).contains(p.first,p.second)) {
   if(pointerPressed) SdaButtonState.PRESSED else SdaButtonState.HOVER
  } else SdaButtonState.NORMAL
 }
 private fun button(canvas:Canvas,node:SdaUiNode)=ui.button(canvas,node,state=buttonState(node))
 override fun menuRect(campaign:SdaCampaign):Rect = ui.rect(doc.component("pdadownmenubutton"))
 override fun pauseRect(campaign:SdaCampaign):Rect? =
  if(campaign.phase in setOf(SdaCampaignPhase.SCENE,SdaCampaignPhase.SCENE_COMPLETE)) ui.rect(doc.component("pdadownpausebutton")) else null
 override fun drawPause(canvas:Canvas) {
  val button=doc.component("pdadownpausebutton")
  for(node in doc.component(button.attributes.getValue("overlay")).children) when(node.type) {
   "frame" -> ui.frame(canvas,node)
   "label" -> ui.label(canvas,node)
  }
 }
 private fun base(canvas:Canvas,c:SdaCampaign?,clock:SdaClock?) {
  doc.component("pdacontrol").children.filter { it.type=="image" && it.attributes["id"]==null }.forEach { ui.image(canvas,it) }
  val timer=doc.component("clock")
  // User-requested Android layout: one centered clock line, separated from score.
  compactInfo(canvas,timer,doc.caption(timer)+" "+clock?.text().orEmpty(),timer.number("y")-5)
  button(canvas,doc.component("pdadownmenubutton"))
  button(canvas,doc.component("pdadownpausebutton"))
  val score=doc.component("score")
  // Explicit user adaptation: caption above a smaller, centered numeric row.
  compactInfo(canvas,score,doc.caption(score),score.number("y")-6)
  compactInfo(canvas,score,String.format(java.util.Locale.US,"%,d",c?.points ?: 0),score.number("y")+5)
 }
 private fun compactInfo(canvas:Canvas,node:SdaUiNode,text:String,y:Int) {
  // Requested compact header: leave the level and objective instructions untouched.
  canvas.save()
  canvas.scale(.70f,.70f,node.number("x")+node.number("w")/2f,y+node.number("h")/2f)
  ui.label(canvas,node,text,y=y,clipToBounds=false)
  canvas.restore()
 }
 private fun levelLabel(canvas:Canvas,campaign:SdaCampaign) {
  // 004433d0 slot 0 formats the original caption as "%s: %d".
  val label=doc.component("cluelabel")
  ui.label(canvas,label,doc.caption(label)+": "+campaign.currentLevel.clue)
 }
 override fun drawMap(canvas:Canvas,campaign:SdaCampaign) {
  // Native 0043f580 completes the map crossfade onto goldbackground; render that stable state.
  val background=doc.component("mapunderlay").attributes.getValue("goldbackground")
  ui.image(canvas,doc.component(background),photographic=true);base(canvas,campaign,campaign.clock)
  ui.label(canvas,doc.component("mapunderlay").children.single { it.type=="label" && it.attributes["id"]=="mapscreencaption" },doc.resolve(campaign.currentLevel.title))
  for((node,pos) in cards(campaign)) {
   val (x,y)=pos;val a=node.attributes
   canvas.drawBitmap(ui.bitmap(a.getValue("texscene")),x+13f,y+11f,photos)
   val frame=ui.bitmap(a.getValue("texnormal"))
   val src=Rect(0,0,minOf(node.number("w"),frame.width),minOf(node.number("h"),frame.height))
   canvas.drawBitmap(frame,src,Rect(x,y,x+src.width(),y+src.height()),null)
   ui.label(canvas,node,doc.caption(node),x+19,y+115,node.number("w")-19,node.number("h")-115)
   ui.label(canvas,node.copy(attributes=a+mapOf("halign" to "center","font" to a.getValue("fontitems"))),(campaign.scenes[a.getValue("name")]?.remainingCaptions()?.size ?: 10).toString(),x+160,y+97,22,20)
  }
  doc.component("maptext").children.forEach { if(it.type=="image") ui.image(canvas,it) else if(it.type=="label") ui.label(canvas,it) }
  levelLabel(canvas,campaign)
  val total=doc.component("totalitems");ui.label(canvas,total,doc.caption(total)+" "+campaign.remainingObjects)
 }
 override fun sceneAt(campaign:SdaCampaign,x:Int,y:Int):String?=cards(campaign).firstOrNull { (n,p) -> Rect(p.first,p.second,p.first+n.number("w"),p.second+n.number("h")).contains(x,y) }?.first?.attributes?.get("name")
 override fun drawHud(canvas:Canvas,campaign:SdaCampaign?,scene:SdaScene,clock:SdaClock?,paused:Boolean) {
  base(canvas,campaign,campaign?.clock ?: clock)
  campaign?.let { levelLabel(canvas,it) }
  doc.component("eyespytext").children.filter { it.type=="label" }.forEach { ui.label(canvas,it) }
  val definitions=sceneRows.getOrPut(scene.name) {
   SdaXui.parse(requireNotNull(content.read(scene.name))).targetSets.associateBy { it.objects }
  }
  // The original hides objective captions while its pause overlay is active.
  if(!paused) for((slot,row) in scene.targetPresentation().withIndex()) {
   val attributes=definitions.getValue(row.objects).attributes
   val label=SdaUiNode("label",attributes+mapOf("halign" to "center","valign" to "middle"),emptyList())
   ui.label(canvas,label,row.caption,y=label.number("y")+slot*label.number("h"),opacity=row.alpha)
  }
  val total=doc.component("totalitems")
  ui.label(canvas,total,doc.caption(total)+" "+(campaign?.remainingObjects ?: scene.remainingCaptions().size))
  button(canvas,doc.component("mapbutton"))
 }
 override fun drawBonusBase(canvas:Canvas,campaign:SdaCampaign) {
  val family=when(campaign.bonusGame) {
   is SdaTileRotGame -> "tilerotgame"
   is SdaTileSwapGame -> "tilegame"
   is SdaWordSearchGame -> "wordsearchgame"
   is SdaJigsawGame -> "jigsawgame"
   else -> return
  }
  val overlay=doc.component(family+"overlaymain")
  // Anonymous overlay images are the shared native frame, not puzzle variants.
  overlay.children.filter { it.type=="image" && it.attributes["id"]==null }.forEach { ui.image(canvas,it,photographic=true) }
  base(canvas,campaign,campaign.clock)
  if(family=="jigsawgame") {
   ui.image(canvas,doc.component("jigsawemptypdaimage"))
   ui.image(canvas,doc.component("jigsawemptybottompdaimage"))
  }
  val underlay=doc.component(family+"underlay")
  underlay.children.filter { it.type=="image" && it.attributes["id"]==null }.forEach { ui.image(canvas,it) }
  underlay.children.filter { it.type=="label" && it.attributes["id"]==null }.forEach { ui.label(canvas,it) }
  overlay.children.filter { it.type=="label" && it.attributes["caption"] in setOf(
   "@ID_TILESWAP_HOWTOPLAY","@ID_TILEROT_HOWTOPLAY","@ID_WORDSEARCH_HOWTOPLAY","@ID_JIGSAW_HOWTOPLAY") }.forEach { ui.label(canvas,it) }
  if(campaign.bonusGame is SdaTileRotGame || campaign.bonusGame is SdaTileSwapGame) {
   ui.image(canvas,doc.component(family+"thumb_"+campaign.currentLevel.bonusImage),photographic=true)
  }
 }
 override fun drawRiddleBase(canvas:Canvas,campaign:SdaCampaign,game:SdaFirstRiddleGame) {
  // The riddle controller explicitly binds pdacontrol; its tray draws above this frame.
  base(canvas,campaign,campaign.clock)
  doc.component("puzzletext").children.filter { it.type=="image" }.forEach { ui.image(canvas,it) }
 }
 override fun drawRiddleCaption(canvas:Canvas,game:SdaFirstRiddleGame):Boolean {
  val controller=doc.component(game.controllerId)
  val paper=doc.component(controller.attributes.getValue("paper"))
  val label=doc.component(controller.attributes.getValue("riddlelabel"))
  game.caption?.let { caption ->
   ui.image(canvas,paper)
   ui.label(canvas,label,caption)
  }
  return true
 }
 override fun levelCompleteRect(campaign:SdaCampaign):Rect = ui.rect(doc.component("minigamecompletedokbutton"))
 override fun drawLevelComplete(canvas:Canvas,campaign:SdaCampaign):Boolean {
  drawBonusBase(canvas,campaign)
  val window=doc.component("tilegamewon")
  window.children.forEach { node -> when(node.type) {
   "image" -> ui.image(canvas,node)
   "label" -> ui.label(canvas,node)
  } }
  // 004429d0 selects matching image/label children; 00410240 passes clue - 1.
  val index=campaign.currentLevel.clue-1
  ui.image(canvas,doc.component("polaroidimages").children[index],photographic=true)
  ui.label(canvas,doc.component("polaroidlabels").children[index])
  button(canvas,doc.component("minigamecompletedokbutton"))
  return true
 }
 private val wordCanvases=mutableMapOf<String,SdaResourceCanvas>()
 override fun drawWordSearch(canvas:Canvas,campaign:SdaCampaign):Boolean {
  val game=campaign.bonusGame as? SdaWordSearchGame ?: return false
  val resource=campaign.currentLevel.bonus
  if(resource !in wordCanvases) wordCanvases.clear() // retain only the current bonus atlas set
  val letters=wordCanvases.getOrPut(resource) {
   SdaResourceCanvas(SdaUiDocument(requireNotNull(content.read(resource)),content.loadStrings(resource)),content)
  }
  val control=letters.document.component("wordsearchgametiles")
  val retired=game.foundWords.flatMap { game.board.placements.getValue(it) }.toSet()
  val selected=game.selectedCells
  for(cell in 0 until game.rows*game.cols) {
   val row=cell/game.cols;val col=cell%game.cols
   val x=game.originX+col*game.cellWidth;val y=game.originY+row*game.cellHeight
   val state=if(cell in retired) "locked" else if(cell in selected) "selected" else "normal"
   val bitmap=letters.bitmap(control.attributes.getValue(state))
   canvas.drawBitmap(bitmap,null,Rect(x,y,x+game.cellWidth,y+game.cellHeight),null)
   canvas.save();canvas.clipRect(x,y,x+game.cellWidth,y+game.cellHeight)
   // 0045c0e0 chooses font index 0; 00461d80 uses center -5 and top alignment.
   letters.atlasText(canvas,control.attributes.getValue("font1"),game.board.displayGrid[row][col].toString(),x+game.cellWidth/2-5,y,1,3)
   canvas.restore()
  }
  game.words.forEachIndexed { index,word ->
   val label=doc.component("wslabel$index")
   ui.label(canvas,label,word,y=label.number("y")+index*label.number("h"))
  }
  return true
 }
 override fun drawTileBonus(canvas:Canvas,campaign:SdaCampaign):Boolean {
  val game=campaign.bonusGame
  val rotation=game as? SdaTileRotGame;val swap=game as? SdaTileSwapGame
  if(rotation==null && swap==null) return false
  val node=doc.component((if(rotation!=null) "tilerotgame_" else "tilegame_")+campaign.currentLevel.bonusImage)
  val image=ui.bitmap(node.attributes.getValue("tex"));val cols=rotation?.cols ?: swap!!.cols;val rows=rotation?.rows ?: swap!!.rows
  val w=612/cols;val h=408/rows;val ox=node.number("x");val oy=node.number("y")
  val resource=campaign.currentLevel.bonus
  if(resource !in tileCanvases) tileCanvases.clear()
  val tiles=tileCanvases.getOrPut(resource) {
   SdaResourceCanvas(SdaUiDocument(requireNotNull(content.read(resource)),content.loadStrings(resource)),content)
  }
  val emboss=tiles.document.component("overlay0")
  val shadow=if(rotation!=null) tiles.document.component("shadow0") else null
  val selection=tiles.document.component(if(swap!=null) "tile_selected" else "tile_select")
  // 00457500 / 00459400 keep the full source photo active behind generated tiles.
  // Retired pieces reveal that photo through the original dark frame, never a black hole.
  ui.image(canvas,node,photographic=true)
  val backdrop=tiles.document.component("tile_background_overlay")
  tiles.frame(canvas,backdrop.copy(attributes=backdrop.attributes+mapOf("x" to ox.toString(),"y" to oy.toString())))
  // XUI shadows form a separate layer below all tiles and emboss overlays.
  shadow?.let { layer ->
   for(i in 0 until cols*rows) if(rotation!!.lockedTiles[i].not()) {
    tiles.image(canvas,layer,x=ox+i%cols*w-6,y=oy+i/cols*h-4)
   }
  }
  for(i in 0 until cols*rows) {
   // 0045a780 / 00458e30 remove both the tile and its relief when retired.
   if(rotation?.lockedTiles?.get(i)==true || swap?.lockedTiles?.get(i)==true) continue
   val source=swap?.tilePositions?.get(i) ?: i
   val x=ox+i%cols*w;val y=oy+i/cols*h
   // 00459400 places rotation relief at native (-6,-4); swap uses tile origin.
   val reliefX=x+if(rotation!=null) -6 else 0
   val reliefY=y+if(rotation!=null) -4 else 0
   canvas.save();canvas.clipRect(x,y,x+w,y+h)
   canvas.rotate((rotation?.tileRotations?.get(i) ?: 0)*90f,x+w/2f,y+h/2f)
   canvas.drawBitmap(image,Rect(source%cols*w,source/cols*h,source%cols*w+w,source/cols*h+h),Rect(x,y,x+w,y+h),photos)
   canvas.restore()
   tiles.image(canvas,emboss,x=reliefX,y=reliefY)
   if(swap?.selectedIndex==i) tiles.image(canvas,selection,x=x,y=y)
  }
  return true
 }
}
