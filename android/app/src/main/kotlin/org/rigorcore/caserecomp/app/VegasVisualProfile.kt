package org.rigorcore.caserecomp.app

import android.graphics.*
import org.rigorcore.caserecomp.sda.*

/** Vegas graph IDs and recovered 0043eec0 layouts belong to this title profile. */
class VegasVisualProfile(private val content:SdaContent):SdaVisualProfile {
 private val doc=SdaUiDocument(requireNotNull(content.read("ENVS.MSE")),content.loadStrings("ENVS.MSE"))
 private val ui=SdaResourceCanvas(doc,content)
 fun bonusTimeReward(remainingSeconds:Float):Int = VegasScoreRules.bonusTimeReward(remainingSeconds)
 private val musicState=VegasMusicState()
 private val musicTracks=doc.nodes("musictrack").associate { it.attributes.getValue("name") to it.attributes.getValue("audiostream") }
 private val musicRequests=musicTracks.mapValues { SdaMusicRequest(it.value,true) }
 override fun campaignMusic(phase:SdaCampaignPhase):SdaMusicRequest? {
  val mode=when(phase) {
   SdaCampaignPhase.MAP,SdaCampaignPhase.SCENE,SdaCampaignPhase.SCENE_COMPLETE,SdaCampaignPhase.OBJECTS_COMPLETE -> 2
   SdaCampaignPhase.BONUS,SdaCampaignPhase.LEVEL_COMPLETE -> 3
   else -> 0 // Finale music is not mapped: XUI has no gamefinish stream.
  }
  val name=musicState.select(mode) ?: return null
  return musicRequests[name]
 }
 override fun audioSession(context:android.content.Context):SdaAudioSession {
  val sliders=doc.component("mainoptionsdlg").children.filter { it.type=="slider" }
  val prefs=context.getSharedPreferences("case-recomp-vegas-options",android.content.Context.MODE_PRIVATE)
  return SdaAudioSession(context,content,doc,prefs.getInt("music",sliders.single { it.number("typevalue")==1 }.number("value")),prefs.getInt("effects",sliders.single { it.number("typevalue")==2 }.number("value")))
 }
 override fun sceneFeedback(found:Boolean):String? =
  doc.component("eyespypauseunderlay").attributes[if(found) "foundsfx" else "notfoundsfx"]
 override fun collectibleLimit(kind:String):Int = if(kind in setOf("key","chip")) 25 else 0
 override fun collectibleSound(kind:String):String? {
  val attribute=when(kind) { "key" -> "keybonussfx";"chip" -> "chipbonussfx";else -> return null }
  return doc.component("pdacontrol").attributes[attribute]
 }
 override fun collectibleDialog(context:android.content.Context,kind:String,onConfirm:()->Unit):SdaResourceMenuView? {
  val reference=when(kind) { "key" -> "foundthekeydialog";"chip" -> "chipfirstfounddialog";else -> return null }
  val original=doc.component(doc.component(reference).attributes.getValue("overlay"))
  val panel=original.children.single { it.type=="dialogimg" }
  val container=original.copy(attributes=original.attributes+mapOf("x" to ((800-panel.number("w"))/2).toString(),"y" to ((600-panel.number("h"))/2).toString()))
  val confirm=original.children.single { it.type=="allbutton" }.number("value")
  return SdaResourceMenuView(context,ui,container,listOf("mpi_diag_tleft","mpi_diag_tmid","mpi_diag_tright",
   "mpi_diag_left","mpi_diag_mid","mpi_diag_right","mpi_diag_bleft","mpi_diag_bmid","mpi_diag_bright"),800,600) { action ->
   if(action==confirm) onConfirm()
  }
 }
 override fun hintPolicy()=SdaHintPolicy(20f,1.5f,67.5f) // 00426f70 / 00427050.
 override fun immediateHintRecharge(context:android.content.Context)=context.getSharedPreferences("case-recomp-vegas-options",0).getBoolean("rapidhints",false)
 override fun hintRect(campaign:SdaCampaign):Rect? =
  if(campaign.phase==SdaCampaignPhase.SCENE && campaign.hint?.ready==true) ui.rect(doc.component("hintbutton")) else null
 override fun hintSound():String?=doc.component("hintbutton").attributes["sfx"]
 // Native resource IDs, visibility and dialog action adapters remain title-specific.
 override fun menuView(context:android.content.Context,campaign:SdaCampaign?,entry:SdaMenuEntry,audio:SdaAudioSession?,onAction:(SdaMenuAction)->Unit):SdaResourceMenuView {
  val textures=listOf("mpi_diag_tleft","mpi_diag_tmid","mpi_diag_tright","mpi_diag_left","mpi_diag_mid",
   "mpi_diag_right","mpi_diag_bleft","mpi_diag_bmid","mpi_diag_bright")
  val playerRepository=PrivateSdaRepository(context).apply { configureCampaignSlots(content.gameId) }
  val activePlayer=playerRepository.getActiveProfile()
  val portrait="img_mm_id"+activePlayer.avatar.takeIf { it in setOf("generic","male","female") }.orEmpty().ifEmpty { "generic" }
  val main=doc.component("mainmenuunderlay").let { node ->
   node.copy(children=node.children.filter { child ->
    val id=child.attributes["id"]
    when(child.type) {
     "image" -> id==null || id in setOf(portrait,"imagelock")
     "label" -> id !in setOf("recover","unlocked","unlimited")
     "allbutton" -> id !in setOf("unlimitedbtn","unlimitedspotbtn","disunlimitedspotbtn")
     "quitbutton" -> true
     else -> false
    }
   })
  }
  val namedMain=main.copy(children=main.children.map { if(it.attributes["id"]=="playername") it.copy(attributes=it.attributes+mapOf("caption" to activePlayer.name)) else it })
  val campaignFinished=campaign?.phase==SdaCampaignPhase.CAMPAIGN_COMPLETE
  val finalMain=if(campaignFinished) namedMain.copy(children=namedMain.children.map { node ->
   if(node.type=="allbutton" && node.number("value")==299) node.copy(attributes=node.attributes+mapOf("disabled" to "true")) else node
  }) else namedMain
  val backdrop=main.children.filter { it.type=="image" && it.attributes["id"]==null }
  val prefs=context.getSharedPreferences("case-recomp-vegas-options",android.content.Context.MODE_PRIVATE)
  var optionsReturn="mainmenuunderlay"
  var optionsBefore:Pair<Int,Int>?=null
  var rapidBefore:Boolean?=null
  var helpReturn=if(entry==SdaMenuEntry.MAIN) "mainmenuunderlay" else "menudlg2"
  lateinit var view:SdaResourceMenuView
  var mainBackdrop=entry!=SdaMenuEntry.PAUSE
  fun show(id:String) {
   if(id=="mainmenuunderlay") { mainBackdrop=true;musicState.select(1);musicTracks["mainmenu"]?.let { audio?.playMusic(it,loop=true) } }
   if(id=="menudlg2") mainBackdrop=false
   view.show(if(id=="mainmenuunderlay") finalMain else doc.component(id),if(mainBackdrop && id!="mainmenuunderlay") backdrop else emptyList())
  }
  var chosenPlayer=activePlayer.id
  var chosenAvatar="generic"
  fun showPlayers() {
   val all=playerRepository.profileStorage.listProfiles()
   val original=doc.component("selectplayer")
   // Original delete button is enabled only for an existing selected row.
   val node=original.copy(children=original.children.filterNot { it.type=="label" && it.attributes["caption"]=="@ID_PLAYER_MSG1" }.map { child ->
    if(child.type=="allbutton" && child.number("value")==2) child.copy(attributes=child.attributes+mapOf("disabled" to (all.none { it.id==chosenPlayer }).toString())) else child
   })
   view.show(node,backdrop)
   view.setListRows(all.map { player -> SdaMenuListRow(player.id,player.name,original.children.single { it.type=="listbox" }.attributes[player.avatar] ?: original.children.single { it.type=="listbox" }.attributes["generic"]) }+SdaMenuListRow("",doc.resolve("@ID_CLICKTOCREATEPLAYER")),chosenPlayer)
  }
  fun showNewPlayer(error:String?=null) {
   val original=doc.component("newplayer")
   val flattened=original.children.flatMap { if(it.type=="radiobutton") it.children else listOf(it) }
   val node=original.copy(children=flattened.filterNot { it.type=="label" && it.attributes["caption"] in setOf("@ID_PLAYER_MSG3","@ID_PLAYER_MSG4") && it.attributes["caption"]!=error }.map { child ->
    if(child.type=="label" && child.attributes["caption"] in setOf("@ID_PLAYER_MSG3","@ID_PLAYER_MSG4")) child.copy(attributes=child.attributes+mapOf("fitwidth" to "true")) else child
   })
   view.show(node,backdrop)
   for(check in view.checkboxes) view.setCheckboxValue(check,check.attributes["id"]==when(chosenAvatar) { "male" -> "malecheck";"female" -> "femalecheck";else -> "gencheck" })
  }
  fun selectPlayer(id:String) { view.requestedProfileId=id;onAction(SdaMenuAction.SWITCH_PROFILE) }
  fun cancelOptions() {
   optionsBefore?.let { (music,effects) -> audio?.setMusicVolume(music);audio?.setEffectsVolume(effects) };optionsBefore=null
   rapidBefore?.let { campaign?.hintRechargeImmediately=it };rapidBefore=null
  }
  fun openOptions(from:String) {
   optionsReturn=from;optionsBefore=(audio?.musicVolume ?: prefs.getInt("music",50)) to (audio?.effectsVolume ?: prefs.getInt("effects",75))
   rapidBefore=campaign?.hintRechargeImmediately ?: prefs.getBoolean("rapidhints",false)
   show(if(from=="menudlg2") "mainoptionsdlgeyespy" else "mainoptionsdlg")
   // Native checkbox order: fullscreen, rapid hints, relaxed mode, hardware acceleration.
   val original=doc.component(view.screenId)
   // Measured Spanish Windows reference: template-matched outer corners, not XUI's base size.
   // Native runtime resizing algorithm is not yet recovered; keep this edition calibration here.
   val spanish=doc.resolve("@ID_OPTIONS_DIALOG1").uppercase(java.util.Locale.ROOT)=="OPCIONES DEL JUEGO"
   val node=if(spanish) original.copy(attributes=original.attributes+mapOf("x" to "114","y" to "85"),
    children=original.children.map { if(it.type=="dialogimg") it.copy(attributes=it.attributes+mapOf("w" to "573")) else it }) else original
   val rapid=node.children.filter { it.type=="checkbox" }[1]
   view.show(node.copy(children=node.children.map {
    if((it.type=="checkbox" && it!=rapid) || (it.type=="label" && it.attributes["caption"] in setOf("@ID_OPTIONS_FSCREEN","@ID_OPTIONS_RELAXED","@ID_OPTIONS_HACC"))) it.copy(attributes=it.attributes+mapOf("disabled" to "true")) else it
   }),if(mainBackdrop) backdrop else emptyList())
   view.sliders.forEach { view.setSliderValue(it,if(it.number("typevalue")==1) optionsBefore!!.first else optionsBefore!!.second) }
   view.setCheckboxValue(view.checkboxes[1],checkNotNull(rapidBefore))
  }
  view=SdaResourceMenuView(context,ui,finalMain,textures,800,600) { value ->
   val screen=view.screenId
   when {
    screen=="mainmenuunderlay" -> when(value) {
     6 -> { chosenPlayer=activePlayer.id;showPlayers() }
     299 -> onAction(SdaMenuAction.RESUME)
     -1 -> onAction(SdaMenuAction.RETURN_TO_CATALOGUE)
     200 -> { helpReturn=screen;show("mainoverlaydlg") }
     30 -> openOptions(screen)
     else -> onAction(SdaMenuAction.UNAVAILABLE)
    }
    screen=="deletedlg" -> when(value) {
     8 -> showPlayers()
     5 -> { view.requestedProfileId=chosenPlayer;onAction(SdaMenuAction.DELETE_PROFILE) }
    }
    screen=="selectplayer" -> when(value) {
     2 -> playerRepository.profileStorage.getProfile(chosenPlayer)?.let { player ->
      val node=doc.component("deletedlg")
      view.show(node.copy(children=node.children.map { child ->
       if(child.type=="label" && child.attributes["caption"]=="\"Player1\"") child.copy(attributes=child.attributes+mapOf("caption" to "\"${player.name}\"")) else child
      }),backdrop)
     }
     3 -> if(chosenPlayer.isNotBlank()) selectPlayer(chosenPlayer)
     4 -> show("mainmenuunderlay")
     62 -> view.scrollList(1)
     63 -> view.scrollList(-1)
    }
    screen=="newplayer" -> when(value) {
     9 -> showPlayers()
     1 -> {
      val name=view.editText.trim()
      val error=when {
       name.isBlank() -> "@ID_PLAYER_MSG4"
       playerRepository.profileStorage.listProfiles().any { it.name.equals(name,ignoreCase=true) } -> "@ID_PLAYER_MSG3"
       else -> null
      }
      if(error!=null) showNewPlayer(error) else {
       val player=SdaProfile("pi_"+java.util.UUID.randomUUID().toString().replace("-",""),name,avatar=chosenAvatar)
       if(playerRepository.profileStorage.saveProfile(player)) selectPlayer(player.id) else onAction(SdaMenuAction.UNAVAILABLE)
      }
     }
    }
    screen=="menudlg2" -> when(value) {
     215 -> onAction(SdaMenuAction.RESUME)
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
      val rapid=view.checkboxValue(view.checkboxes[1])
      prefs.edit().putInt("music",music).putInt("effects",effects).putBoolean("rapidhints",rapid).apply()
      campaign?.hintRechargeImmediately=rapid
      optionsBefore=null;rapidBefore=null;show(optionsReturn)
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
  view.onEditChanged={ if(view.screenId=="newplayer") showNewPlayer() }
  view.onListSelection={ id ->
   if(view.screenId=="selectplayer") {
    if(id.isEmpty()) { chosenAvatar="generic";view.setEditText("");showNewPlayer() } else chosenPlayer=id
   }
  }
  view.onCheckboxValue={ node,value ->
   if(view.screenId=="newplayer") {
    chosenAvatar=when(node.attributes["id"]) { "malecheck" -> "male";"femalecheck" -> "female";else -> "generic" }
    for(check in view.checkboxes) view.setCheckboxValue(check,check==node)
   } else if(node==view.checkboxes.getOrNull(1)) campaign?.hintRechargeImmediately=value
  }
  view.onSoundEffect={ audio?.playEffect(it) }
  if(entry==SdaMenuEntry.PLAYER_SELECTION) { show("mainmenuunderlay");showPlayers() } else show(if(entry==SdaMenuEntry.MAIN) "mainmenuunderlay" else "menudlg2")
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
 fun drawCollectionMeters(canvas:Canvas,keys:Int,chips:Int) {
  for((id,top) in listOf("keyfull" to VegasCollectionMeterRules.keyCropTop(keys),"chipsfull" to VegasCollectionMeterRules.chipCropTop(chips))) {
   // The chip component starts with a zero-height image until its first count update.
   if(id=="chipsfull" && chips==0) continue
   val node=doc.component(id);val image=ui.bitmap(node.attributes.getValue("tex"))
   val width=minOf(node.number("w",image.width),image.width)
   if(top>=image.height) continue
   val source=Rect(0,top,width,image.height)
   val destination=Rect(node.number("x"),node.number("y")+top,node.number("x")+width,node.number("y")+image.height)
   canvas.drawBitmap(image,source,destination,null)
  }
 }
 private fun base(canvas:Canvas,c:SdaCampaign?,clock:SdaClock?) {
  doc.component("pdacontrol").children.filter { it.type=="image" && it.attributes["id"]==null }.forEach { ui.image(canvas,it) }
  val timer=doc.component("clock")
  // User-requested Android layout: one centered clock line, separated from score.
  compactInfo(canvas,timer,doc.caption(timer)+" "+clock?.text().orEmpty(),timer.number("y")-5)
  if(c!=null) {
   drawCollectionMeters(canvas,c.collectedCount("key"),c.collectedCount("chip"))
   for((kind,id) in listOf("key" to "keyscollected","chip" to "chipscollected")) {
    val label=doc.component(id)
    ui.label(canvas,label,c.collectedCount(kind).toString()+doc.caption(label))
   }
  }
  button(canvas,doc.component("pdadownmenubutton"))
  button(canvas,doc.component("pdadownpausebutton"))
  val score=doc.component("score")
  // Explicit user adaptation: caption above a smaller, centered numeric row.
  compactInfo(canvas,score,doc.caption(score),score.number("y")-6)
  compactInfo(canvas,score,String.format(java.util.Locale.US,"%,d",c?.points ?: 0),score.number("y")+5,scale=.80f)
 }
 private fun compactInfo(canvas:Canvas,node:SdaUiNode,text:String,y:Int,scale:Float=.80f) {
  // Requested compact header: leave the level and objective instructions untouched.
  canvas.save()
  canvas.scale(scale,scale,node.number("x")+node.number("w")/2f,y+node.number("h")/2f)
  ui.label(canvas,node.copy(attributes=node.attributes+mapOf("halign" to "center")),text,y=y,clipToBounds=false)
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
  if(!paused && campaign?.phase==SdaCampaignPhase.SCENE) campaign.hint?.let { hint ->
   val node=doc.component("hintbutton");val bounds=ui.rect(node)
   if(hint.ready) button(canvas,node) else {
    canvas.saveLayerAlpha(RectF(bounds),40);ui.button(canvas,node);canvas.restore()
    canvas.save();canvas.clipRect(bounds.left,bounds.top,bounds.left+(bounds.width()*hint.progress).toInt(),bounds.bottom)
    ui.button(canvas,node);canvas.restore()
   }
   hint.target?.let(scene.objects::get)?.takeIf { !it.found && !it.hidden }?.let { sprite ->
    val animation=doc.component("eyespyanimhint");val bitmap=ui.bitmap(animation.attributes.getValue("tex"))
    val w=animation.number("framew");val h=animation.number("frameh")
    val cols=bitmap.width/w;val frames=cols*(bitmap.height/h);val frame=(hint.animationAge/.14f).toInt()
    if(cols>0 && frame in 0 until frames) {
     val x=sprite.x+(sprite.image.width-w)/2;val y=sprite.y+(sprite.image.height-h)/2
     canvas.save();canvas.clipRect(ui.rect(doc.component("pdacontrol")).right,0,800,600)
     canvas.drawBitmap(bitmap,Rect(frame%cols*w,frame/cols*h,frame%cols*w+w,frame/cols*h+h),Rect(x,y,x+w,y+h),null);canvas.restore()
    }
   }
  }
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
 override fun interactiveRiddle(content:SdaContent,seed:Long,checkpoint:Map<String,Any?>?) =
  org.rigorcore.caserecomp.games.vegas.VegasThirdRiddleController.loadGame(content,seed,checkpoint)
 override fun interactiveRiddleSound(game:SdaInteractiveRiddleGame,key:String)=doc.component(game.controllerId).attributes[key]
 override fun drawCampaignComplete(canvas:Canvas,campaign:SdaCampaign):Boolean {
  ui.image(canvas,doc.component("thirdriddlenewspaper"));return true
 }
 override fun interactiveRiddleOpacity(game:SdaInteractiveRiddleGame)=(game.controller as org.rigorcore.caserecomp.games.vegas.VegasThirdRiddleController).sceneOpacity
 override fun interactiveRiddleItemOffset(game:SdaInteractiveRiddleGame,index:Int):Pair<Int,Int> {
  val controller=game.controller as org.rigorcore.caserecomp.games.vegas.VegasThirdRiddleController
  return (if(controller.items.definitions[index].name=="lock") controller.doorOffset else 0) to 0
 }
 private fun layer(canvas:Canvas,opacity:Float,draw:()->Unit) {
  canvas.saveLayerAlpha(null,(opacity*255).toInt().coerceIn(0,255));try { draw() } finally { canvas.restore() }
 }
 override fun drawInteractiveRiddleBase(canvas:Canvas,campaign:SdaCampaign,game:SdaInteractiveRiddleGame) {
  val controller=doc.component(game.controllerId)
  val native=game.controller as org.rigorcore.caserecomp.games.vegas.VegasThirdRiddleController
  layer(canvas,native.sceneOpacity) {
  base(canvas,campaign,campaign.clock)
  val visible=setOf("riddlebackground3","thirdriddleemptyclock","thirdriddlehourhand","thirdriddleminutehand","thirdriddleemptypda","thirdriddledoor")
  controller.children.filter { it.type=="image" && it.attributes["id"] in visible }.forEach {
   ui.image(canvas,it,x=it.number("x")+if(it.attributes["id"]=="thirdriddledoor") native.doorOffset else 0)
  }
  controller.children.filter { it.type=="label" }.forEach { ui.label(canvas,it) }
  }
 }
 override fun drawInteractiveRiddleOverlay(canvas:Canvas,game:SdaInteractiveRiddleGame) {
  val controller=game.controller as? org.rigorcore.caserecomp.games.vegas.VegasThirdRiddleController ?: return
  layer(canvas,controller.sceneOpacity) {
  val slot=doc.component("riddle3slotdisplay")
  val textures=slot.children.first { it.type=="container" }.children.filter { it.type=="image" }
  slot.children.filter { it.type=="image" }.forEachIndexed { i,node ->
   if(controller.symbolVisible(i)) ui.image(canvas,node.copy(attributes=node.attributes+mapOf("tex" to textures[controller.symbols[i]].attributes.getValue("tex"))))
  }
  if(controller.keypadEnabled && controller.ledsVisible) {
   val led=doc.component("riddle3leddisplay")
   led.children.filter { it.type=="image" }.forEachIndexed { i,node ->
    val frame=controller.ledFrames[i]
    if(frame!=0) {
     val atlas=ui.bitmap(node.attributes.getValue("tex"))
     ui.spriteFrame(canvas,atlas,doc.component(game.controllerId).number("leddisplaywidth"),atlas.height,frame,
      led.number("x")+node.number("x"),led.number("y")+node.number("y"))
    }
   }
  }
  if(controller.fingerprintHeld || controller.fingerprintReturning)
   ui.image(canvas,doc.component("riddle3fingerprintimage"),controller.fingerprintX.toInt(),controller.fingerprintY.toInt())
  }
  if(controller.roomOpacity>0) layer(canvas,controller.roomOpacity) { ui.image(canvas,doc.component("thirdriddlemoneyroom")) }
  if(controller.newspaperOpacity>0) layer(canvas,controller.newspaperOpacity) { ui.image(canvas,doc.component("thirdriddlenewspaper")) }
 }
 override fun riddleDialog(context:android.content.Context,campaign:SdaCampaign,onConfirm:()->Unit):android.view.View? {
  val game=campaign.bonusGame
  val controllerId=when(game) {
   is SdaPlacementRiddleGame -> game.controllerId
   is SdaInteractiveRiddleGame -> game.controllerId
   else -> return null
  }
  val controller=doc.component(controllerId)
  val starting=when(game) {
   is SdaSecondRiddleGame -> !game.started
   is SdaInteractiveRiddleGame -> !game.controller.started
   else -> false
  }
  if(!starting && !game.isSolved) return null
  val key=if(game is SdaInteractiveRiddleGame) { if(starting) "startdialog" else "completeddialog" }
   else if(starting) "startdialogcontainer" else "completedialog"
  val original=doc.component(controller.attributes.getValue(key))
  // Only the actual campaign points are available; native total breakdown and rank remain unverified.
  var finalLine=0
  val container=if(game is SdaInteractiveRiddleGame && !starting) original.copy(children=original.children.map { child ->
   if(child.type=="label" && child.attributes["caption"].isNullOrEmpty()) {
    finalLine++
    if(finalLine==2) child.copy(attributes=child.attributes+mapOf("caption" to (doc.resolve(controller.attributes.getValue("totalscorecaption"))+" "+String.format(java.util.Locale.US,"%,d",campaign.points)))) else child
   } else child
  }) else original
  val expected=when(game) {
   is SdaInteractiveRiddleGame -> if(starting) 1018 else 1019
   is SdaSecondRiddleGame -> if(starting) 1016 else 1017
   else -> 1015
  }
  return SdaResourceMenuView(context,ui,container,listOf("mpi_diag_tleft","mpi_diag_tmid","mpi_diag_tright",
   "mpi_diag_left","mpi_diag_mid","mpi_diag_right","mpi_diag_bleft","mpi_diag_bmid","mpi_diag_bright"),800,600) { action ->
   if(action==expected) onConfirm()
  }
 }
 override fun drawRiddlePlaced(canvas:Canvas,game:SdaPlacementRiddleGame,id:String):Boolean {
  if(game !is SdaSecondRiddleGame) return false
  val image=game.placedImages.getValue(id).nativeImage as? Bitmap ?: return false
  val destination=game.definition.destinations.getValue(id)
  // 00451360, via dimensions 00489221 and frame selector 004893f3.
  when(id) {
   "finale2hourglass" -> ui.spriteFrame(canvas,image,142,261,1,186,134,destination.finalAlpha)
   "finale2slotarm" -> ui.spriteFrame(canvas,image,90,293,0,game.definition.backgroundX+destination.x.toInt(),game.backgroundY+destination.y.toInt(),destination.finalAlpha)
   else -> return false
  }
  return true
 }
 override fun drawRiddleDecorations(canvas:Canvas,game:SdaPlacementRiddleGame) {
  if(game !is SdaSecondRiddleGame) return
  val controller=doc.component(game.controllerId)
  val bound=setOf("backgroundimage","paper","indicator").mapNotNull { controller.attributes[it] }.toSet()
  val hidden=if("finale2cup" in game.interaction.board.placed) setOf(controller.attributes["lefttray"],controller.attributes["finale2righttrayimage"]) else emptySet()
  controller.children.filter { it.type=="image" && it.attributes["id"] !in bound && it.attributes["id"] !in hidden && !it.attributes["tex"].isNullOrEmpty() }
   .forEach { ui.image(canvas,it) }
 }
 override fun drawRiddleBase(canvas:Canvas,campaign:SdaCampaign,game:SdaPlacementRiddleGame) {
  // The riddle controller explicitly binds pdacontrol; its tray draws above this frame.
  base(canvas,campaign,campaign.clock)
  if(game is SdaSecondRiddleGame) doc.component(game.controllerId).children.filter { it.type=="image" && it.attributes["id"] in setOf("secondriddleemptypda","secondriddleemptypda2") }.forEach { ui.image(canvas,it) }
  doc.component("puzzletext").children.filter { it.type=="image" }.forEach { ui.image(canvas,it) }
 }
 override fun drawRiddleCaption(canvas:Canvas,game:SdaPlacementRiddleGame):Boolean {
  val controller=doc.component(game.controllerId)
  val paper=doc.component(controller.attributes.getValue("paper"))
  val label=doc.component(controller.attributes.getValue("riddlelabel"))
  game.caption?.let { caption ->
   ui.image(canvas,paper)
   ui.label(canvas,label,caption)
  }
  return true
 }
 // Original allobjectspickeddialog/action 334 (00412300), not the post-bonus wincontainer.
 // Center the native 400x250 dialog in the logical viewport; keep child coordinates unchanged.
 private fun objectsCompleteOrigin():Pair<Int,Int> {
  val panel=doc.component("allobjectspickeddialog").children.single { it.type=="dialogimg" }
  return (800-panel.number("w"))/2 to (600-panel.number("h"))/2
 }
 override fun objectsCompleteRect():Rect {
  val node=doc.component("allobjectspickeddialog").children.single { it.type=="allbutton" && it.number("value")==334 }
  val (x,y)=objectsCompleteOrigin()
  val width=doc.component("allobjectspickeddialog").children.single { it.type=="dialogimg" }.number("w")
  return ui.rect(node,width).apply { offset(x,y) }
 }
 override fun drawObjectsComplete(canvas:Canvas):Boolean {
  val container=doc.component("allobjectspickeddialog")
  val panel=container.children.single { it.type=="dialogimg" }
  val (x,y)=objectsCompleteOrigin()
  canvas.save();canvas.translate(x.toFloat(),y.toFloat())
  ui.tiledPanel(canvas,listOf("mpi_diag_tleft","mpi_diag_tmid","mpi_diag_tright",
   "mpi_diag_left","mpi_diag_mid","mpi_diag_right","mpi_diag_bleft","mpi_diag_bmid","mpi_diag_bright"),Rect(0,0,panel.number("w"),panel.number("h")))
  container.children.forEach { node -> when(node.type) {
   "image" -> ui.image(canvas,node)
   "label" -> ui.label(canvas,node)
   "allbutton" -> ui.button(canvas,node,parentWidth=panel.number("w"))
  } }
  canvas.restore()
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
