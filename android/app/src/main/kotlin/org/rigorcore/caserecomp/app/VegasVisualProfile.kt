package org.rigorcore.caserecomp.app

import android.graphics.*
import org.rigorcore.caserecomp.sda.*

/** Vegas graph IDs and recovered 0043eec0 layouts belong to this title profile. */
class VegasVisualProfile(private val content:SdaContent):SdaVisualProfile {
 private val doc=SdaUiDocument(requireNotNull(content.read("ENVS.MSE")),content.loadStrings("ENVS.MSE"))
 private val ui=SdaResourceCanvas(doc,content)
 private val sceneRows=mutableMapOf<String,Map<List<String>,SdaXuiSet>>()
 private val photos=Paint(Paint.FILTER_BITMAP_FLAG)
 private val selected=Paint().apply { color=Color.YELLOW;style=Paint.Style.STROKE;strokeWidth=2f }
 override val returnMapRect get()=ui.rect(doc.component("mapbutton"))
 override val solveRect get()=ui.rect(doc.component("solvebutton"))
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
  ui.label(canvas,timer,doc.caption(timer),width=35)
  ui.label(canvas,timer.copy(attributes=timer.attributes+mapOf("halign" to "left")),clock?.text().orEmpty(),x=timer.number("x")+35)
  button(canvas,doc.component("pdadownpausebutton"))
  val score=doc.component("score")
  ui.label(canvas,score,doc.caption(score)+" "+String.format(java.util.Locale.US,"%,d",c?.points ?: 0))
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
  if(!paused) for(row in scene.targetPresentation()) {
   val attributes=definitions.getValue(row.objects).attributes
   val label=SdaUiNode("label",attributes+mapOf("halign" to "center","valign" to "middle"),emptyList())
   ui.label(canvas,label,row.caption,y=label.number("y")+row.index*label.number("h"),opacity=row.alpha)
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
  val underlay=doc.component(family+"underlay")
  underlay.children.filter { it.type=="image" && it.attributes["id"]==null }.forEach { ui.image(canvas,it) }
  underlay.children.filter { it.type=="label" && it.attributes["id"]==null }.forEach { ui.label(canvas,it) }
  overlay.children.filter { it.type=="label" && it.attributes["caption"] in setOf(
   "@ID_TILESWAP_HOWTOPLAY","@ID_TILEROT_HOWTOPLAY","@ID_WORDSEARCH_HOWTOPLAY","@ID_JIGSAW_HOWTOPLAY") }.forEach { ui.label(canvas,it) }
  if(campaign.bonusGame is SdaTileRotGame || campaign.bonusGame is SdaTileSwapGame) {
   ui.image(canvas,doc.component(family+"thumb_"+campaign.currentLevel.bonusImage),photographic=true)
  }
  button(canvas,doc.component("solvebutton"))
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
  for(i in 0 until cols*rows) {
   val source=swap?.tilePositions?.get(i) ?: i
   val x=ox+i%cols*w;val y=oy+i/cols*h
   canvas.save();canvas.clipRect(x,y,x+w,y+h)
   canvas.rotate((rotation?.tileRotations?.get(i) ?: 0)*90f,x+w/2f,y+h/2f)
   canvas.drawBitmap(image,Rect(source%cols*w,source/cols*h,source%cols*w+w,source/cols*h+h),Rect(x,y,x+w,y+h),photos)
   canvas.restore()
   if(swap?.selectedIndex==i) canvas.drawRect(x.toFloat(),y.toFloat(),(x+w).toFloat(),(y+h).toFloat(),selected)
  }
  return true
 }
}
