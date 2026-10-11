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
 override fun introView(context:android.content.Context,onDone:()->Unit):android.view.View? =
  if(content.read("GAMESTART.XUI")!=null) VegasIntroView(context,content,onDone) else null
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
  }.also { it.openDialogSound=::dialogSound }
 }
 /** XUI leavedialog bindings: the sound played when their overlay container opens. */
 private fun dialogSound(container:String):String? =
  doc.nodes("leavedialog").firstOrNull { it.attributes["overlay"]==container }?.attributes?.get("opendialogsfx")
 override fun unlimitedDialog(context:android.content.Context,kind:String,onConfirm:()->Unit):SdaResourceMenuView? {
  val id=when(kind) { "scenecomplete" -> "allobjectspickeddialog";"finished" -> "finishedunlimitedmodecontainer";else -> return null }
  val original=doc.component(id);val panel=original.children.single { it.type=="dialogimg" }
  val container=if(original.attributes["x"]!=null) original else
   original.copy(attributes=original.attributes+mapOf("x" to ((800-panel.number("w"))/2).toString(),"y" to ((600-panel.number("h"))/2).toString()))
  val confirm=original.children.single { it.type=="allbutton" }.number("value")
  return SdaResourceMenuView(context,ui,container,listOf("mpi_diag_tleft","mpi_diag_tmid","mpi_diag_tright",
   "mpi_diag_left","mpi_diag_mid","mpi_diag_right","mpi_diag_bleft","mpi_diag_bmid","mpi_diag_bright"),800,600) { action ->
   if(action==confirm) onConfirm()
  }.also { it.openDialogSound=::dialogSound }
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
  playerName=activePlayer.name
  scorePrefs=context.getSharedPreferences("case-recomp-vegas-scores",android.content.Context.MODE_PRIVATE)
  val portrait="img_mm_id"+activePlayer.avatar.takeIf { it in setOf("generic","male","female") }.orEmpty().ifEmpty { "generic" }
  // Native main menu choices: continue/start fader by progress, locked/unlocked fader by the 25 keys and 25 chips.
  // Unlimited search unlocks with 25 keys and 25 chips or a solved heist (ID_INST_DLG_8).
  val status=investigationStatus
  val played=status?.played ?: (campaign!=null && (campaign.levelIndex>0 || campaign.points>0 || campaign.completedObjects>0))
  val unlimitedOpen=status?.let { (it.keys>=25 && it.chips>=25) || it.solved } ?: false
  val faders=setOf(if(played) "levelsplayed" else "levelsstart",if(unlimitedOpen) "unlimitedstart" else "unlimitedlocked")
  val main=doc.component("mainmenuunderlay").let { node ->
   val unusedFaderLabels=node.children.filter { it.type=="imagefader" && it.attributes["id"] !in faders }.mapNotNull { it.attributes["label"] }.toSet()
   node.copy(children=node.children.filter { child ->
    val id=child.attributes["id"]
    when(child.type) {
     "image" -> id==null || id==portrait || (id=="imagelock" && !unlimitedOpen)
     "imagefader" -> id in faders
     "label" -> id!="unlimited" && id !in unusedFaderLabels
     "allbutton" -> id !in setOf(if(unlimitedOpen) "disunlimitedbtn" else "unlimitedbtn","unlimitedspotbtn","disunlimitedspotbtn")
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
  fun showScores() {
   val rows=highScores()
   scoreFirst=scoreFirst.coerceIn(0,maxOf(0,rows.size-10))
   val node=doc.component("highscoredialog")
   view.show(node.copy(children=node.children.map { child ->
    when(child.attributes["id"]) {
     "downarrowbutton" -> child.copy(attributes=child.attributes+mapOf("disabled" to (scoreFirst==0).toString()))
     "uparrowbutton" -> child.copy(attributes=child.attributes+mapOf("disabled" to (scoreFirst+10>=rows.size).toString()))
     else -> child
    }
   }))
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
     299 -> onAction(if(campaign?.unlimited==true) SdaMenuAction.RESUME_INVESTIGATION else SdaMenuAction.RESUME)
     326 -> when {
      campaign?.unlimited==true && !campaign.unlimitedFinished -> onAction(SdaMenuAction.RESUME)
      status?.unlimitedFinished==true || campaign?.unlimitedFinished==true -> {
       val node=doc.component("restartunlimited");val panel=node.children.single { it.type=="dialogimg" }
       view.show(node.copy(attributes=node.attributes+mapOf("x" to ((800-panel.number("w"))/2).toString(),"y" to ((600-panel.number("h"))/2).toString())),backdrop)
      }
      else -> onAction(SdaMenuAction.START_UNLIMITED)
     }
     -1 -> onAction(SdaMenuAction.RETURN_TO_CATALOGUE)
     200 -> { helpReturn=screen;show("mainoverlaydlg") }
     30 -> openOptions(screen)
     312 -> { scoreFirst=0;showScores() }
     else -> onAction(SdaMenuAction.UNAVAILABLE)
    }
    screen=="restartunlimited" -> when(value) {
     332 -> onAction(SdaMenuAction.RESTART_UNLIMITED)
     333 -> show("mainmenuunderlay")
    }
    screen=="highscoredialog" -> when(value) {
     315 -> show("mainmenuunderlay")
     313 -> { scoreFirst-=1;showScores() }
     314 -> { scoreFirst+=1;showScores() }
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
  view.openDialogSound=::dialogSound
  view.onDrawOverlay={ canvas,dt ->
   when(view.screenId) {
    "mainmenuunderlay" -> logoHighlight.draw(canvas,dt)
    "highscoredialog" -> { drawHighScores(canvas,scoreFirst);false }
    else -> false
   }
  }
  logoHighlight.shown()
  if(entry==SdaMenuEntry.PLAYER_SELECTION) { show("mainmenuunderlay");showPlayers() } else show(if(entry==SdaMenuEntry.MAIN) "mainmenuunderlay" else "menudlg2")
  return view
 }
 private val sceneRows=mutableMapOf<String,Map<List<String>,SdaXuiSet>>()
 private var playerName=""
 private var scoreFirst=0
 private var scorePrefs:android.content.SharedPreferences?=null
 /** HIGHSCORE.MSE default rows plus this device's finished campaigns, best score first. */
 private fun highScores():List<SdaUiNode> {
  val defaults=content.read("HIGHSCORE.MSE")?.let { SdaUiDocument(it,emptyMap()).nodes("hscorerow") }.orEmpty()
  val played=scorePrefs?.getStringSet("rows",emptySet()).orEmpty().mapNotNull { row ->
   runCatching { org.json.JSONObject(row).let { o -> SdaUiNode("hscorerow",o.keys().asSequence().associateWith { o.getString(it) },emptyList()) } }.getOrNull()
  }
  return (defaults+played).sortedByDescending { it.attributes["score"]?.toLongOrNull() ?: 0L }
 }
 override fun campaignCompleted(context:android.content.Context,campaign:SdaCampaign) {
  val prefs=context.getSharedPreferences("case-recomp-vegas-scores",android.content.Context.MODE_PRIVATE)
  val player=PrivateSdaRepository(context).apply { configureCampaignSlots(content.gameId) }.getActiveProfile()
  val keys=campaign.collectedCount("key");val chips=campaign.collectedCount("chip")
  bestRank=VegasRankRules.displayed(bestRank,campaign.points,keys,chips)
  val row=org.json.JSONObject(mapOf("name" to player.name,"score" to campaign.points.toString(),"rank" to bestRank.toString(),
   "time" to campaign.totalElapsed.toLong().toString(),"keys" to keys.toString(),"chips" to chips.toString(),"fingerprints" to "0",
   "gender" to player.avatar,"id" to player.id+":"+System.currentTimeMillis())).toString()
  prefs.edit().putStringSet("rows",prefs.getStringSet("rows",emptySet()).orEmpty()+row).apply()
 }
 /** mpi:hscoretable (0042fd00 rows of 31 px, ten visible; 0042f050 column widths 129/119/70/94/67/68/81). */
 private fun drawHighScores(canvas:Canvas,first:Int) {
  val table=doc.nodes("hscoretable").single();val x0=table.number("x");val y0=table.number("y")
  val font=table.attributes.getValue("font")
  val widths=listOf(129,119,70,94,67,68,81);val gaps=listOf(130,120,71,95,68,69,82)
  for((row,entry) in highScores().drop(first).take(10).withIndex()) {
   val a=entry.attributes
   // Score and time exceed SdaUiNode.number's coordinate range.
   val seconds=a["time"]?.toLongOrNull() ?: 0L
   val cells=listOf(a["name"].orEmpty(),String.format(java.util.Locale.US,"%,d",a["score"]?.toLongOrNull() ?: 0L),entry.number("rank").toString(),
    String.format(java.util.Locale.US,"%02d:%02d:%02d",seconds/3600,seconds/60%60,seconds%60),entry.number("keys").toString(),entry.number("chips").toString(),entry.number("fingerprints").toString())
   var x=x0
   for((column,text) in cells.withIndex()) {
    ui.label(canvas,SdaUiNode("label",mapOf("font" to font,"halign" to "center","valign" to "middle"),emptyList()),text,x=x,y=y0+row*31,width=widths[column],height=31)
    x+=gaps[column]
   }
  }
 }
 private var bestRank=1
 /** Map footer (mpi:playermapdisplay): player, total score, clues found and rank (004189c0 ranks). */
 private fun playerMapDisplay(canvas:Canvas,campaign:SdaCampaign) {
  val display=doc.component("playermapdisplay");val ox=display.number("x");val oy=display.number("y")
  fun put(node:SdaUiNode,text:String) { ui.label(canvas,node,text,x=ox+node.number("x"),y=oy+node.number("y")) }
  val labels=display.children.filter { it.type=="label" }
  labels.firstOrNull { it.attributes["id"]=="playernamedisplay" }?.let { put(it,investigationStatus?.player ?: playerName) }
  labels.firstOrNull { it.attributes["id"]=="totalscoredisplay" }?.let {
   // The unlimited map footer keeps showing the heist score, as observed on the original.
   if(campaign.unlimited) put(it,doc.resolve(display.attributes.getValue("unlimitedscorecaption"))+String.format(java.util.Locale.US,"%,d",investigationStatus?.points ?: 0))
   else put(it,doc.resolve(display.attributes.getValue("totalscore"))+String.format(java.util.Locale.US,"%,d",campaign.points))
  }
  display.children.firstOrNull { it.attributes["id"]=="playermapdisplay_default" }?.children?.filter { it.type=="label" }?.let { clue ->
   clue.getOrNull(0)?.let { put(it,doc.resolve(display.attributes.getValue("clues"))) }
   clue.getOrNull(1)?.let { put(it,if(campaign.unlimited) "${investigationStatus?.clues ?: 0}/25" else "${campaign.levelIndex}/${campaign.levels.size}") }
  }
  labels.firstOrNull { it.attributes["caption"]=="@ID_HSCORE_RANK" }?.let { put(it,doc.caption(it)) }
  bestRank=VegasRankRules.displayed(bestRank,campaign.points,campaign.collectedCount("key"),campaign.collectedCount("chip"))
  labels.firstOrNull { it.attributes["id"]=="playerrankdisplay" }?.let { put(it,doc.caption(doc.component("rank$bestRank"))) }
 }
 /** Main-menu logo glint (0043dc60/0043e1b0/0043df90): 30 s idle (27 s pre-elapsed on the first menu of a
  *  run), then a 2 s sweep at 0.5*268 px/s over 268 px with alpha (134-|centre-x|)/134*0.85+0.15. */
 private inner class LogoHighlight {
  private var first=true;private var state=1;private var timer=27f;private var x=0f;private var direction=1f
  fun shown() { if(first) first=false else { state=1;timer=0f } }
  fun draw(canvas:Canvas,dt:Float):Boolean {
   val node=doc.component("mainmenulogoanim_image");val start=node.number("x").toFloat()
   timer+=dt
   if(state==1) { x=start;direction=1f;if(timer>30f) { timer=0f;state=2 };return true }
   x+=dt*.5f*direction*268f
   if(x>start+268f) { direction=-direction;x=start+268f }
   if(x<start) { direction=-direction;x=start }
   if(timer>2f) { state=1;timer=0f }
   val alpha=(134f-kotlin.math.abs(start+134f-x))/134f*.85f+.15f
   val paint=Paint(Paint.FILTER_BITMAP_FLAG).apply { this.alpha=(alpha.coerceIn(0f,1f)*255).toInt() }
   canvas.drawBitmap(ui.bitmap(node.attributes.getValue("tex")),x,node.number("y").toFloat(),paint)
   return true
  }
 }
 private val logoHighlight=LogoHighlight()
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
 override var investigationStatus:SdaInvestigationStatus?=null
 // Unlimited map pages, observed on the original: 9, 7 and 9 locations in UNLIMITED.XUI order.
 private var mapPage=0
 private fun unlimitedPages(c:SdaCampaign):List<List<String>> {
  val scenes=c.currentLevel.scenes;val sizes=if(scenes.size==25) listOf(9,7,9) else scenes.chunked(9).map { it.size }
  var start=0;return sizes.map { n -> scenes.subList(start,start+n).also { start+=n } }
 }
 private fun pageScenes(c:SdaCampaign):List<String> {
  if(!c.unlimited) return c.currentLevel.scenes
  if(c.unlimitedFinished) return emptyList()
  val pages=unlimitedPages(c);mapPage=mapPage.coerceIn(0,pages.lastIndex);return pages[mapPage]
 }
 private fun cards(c:SdaCampaign):List<Pair<SdaUiNode,Pair<Int,Int>>> {
  val wanted=pageScenes(c);if(wanted.isEmpty()) return emptyList()
  // Investigation cards keep the XUI order; unlimited pages follow UNLIMITED.XUI, as on the original.
  val listed=doc.component("mapunderlay").children.filter { it.type=="scenebutton" && it.attributes["name"] in wanted }
  val nodes=if(c.unlimited) listed.sortedBy { wanted.indexOf(it.attributes["name"]) } else listed
  require(nodes.size==wanted.size && nodes.map { it.attributes["name"] }.distinct().size==nodes.size)
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
 fun drawCollectionMeters(canvas:Canvas,keys:Int,chips:Int) = drawCollectionMeters(canvas,keys.toFloat(),chips.toFloat())
 /** Fractional counts interpolate the native crop rows while a meter fills. */
 fun drawCollectionMeters(canvas:Canvas,keys:Float,chips:Float) {
  fun crop(count:Float,rule:(Int)->Int):Int {
   val low=count.toInt().coerceIn(0,25);val high=minOf(25,low+1);val f=(count-low).coerceIn(0f,1f)
   return Math.round(rule(low)+(rule(high)-rule(low))*f)
  }
  for((id,top) in listOf("keyfull" to crop(keys,VegasCollectionMeterRules::keyCropTop),"chipsfull" to crop(chips,VegasCollectionMeterRules::chipCropTop))) {
   // The chip component starts with a zero-height image until its first count update.
   if(id=="chipsfull" && chips<=0f) continue
   val node=doc.component(id);val image=ui.bitmap(node.attributes.getValue("tex"))
   val width=minOf(node.number("w",image.width),image.width)
   if(top>=image.height) continue
   val source=Rect(0,top,width,image.height)
   val destination=Rect(node.number("x"),node.number("y")+top,node.number("x")+width,node.number("y")+image.height)
   canvas.drawBitmap(image,source,destination,null)
  }
 }
 // Native pickup state machine (0043a0f0/00439f50/0043a4f0): click arms a 1.1 s pulse whose scale bounces
 // between 0.8 and 1.2 at 3.333*0.4 per second; then 25 frames fade alpha by 0.04 while moving to (75,520);
 // arrival (00443ea0) bumps the PDA count and cross-fades the small indicator over 1.5 s (00444400).
 // Native updates run about every 40 ms (Frida dt sample on the original: 30-49 ms, mode 40 ms), so a
 // per-update step is 0.04 s here; the gradual meter fill is an Android addition.
 private class Flight(val kind:String,val bitmap:Bitmap,val x:Int,val y:Int) {
  var timer=1.1f;var scale=1f;var velocity=1f;var frame=-1;var frameTime=0f
 }
 private val flights=mutableListOf<Flight>()
 private val shownCounts=mutableMapOf<String,Float>()
 private val meterTargets=mutableMapOf<String,Int>()
 private val indicatorFade=mutableMapOf<String,Float>()
 private var effectsScene:String?=null
 private val nativeFrameSeconds=.04f;private val meterFillSeconds=.5f;private val indicatorFadeSeconds=1.5f
 override fun collectiblePicked(campaign:SdaCampaign,item:SdaSceneCollectible,x:Int,y:Int):Boolean {
  val bitmap=item.sprite.image.nativeImage as? Bitmap ?: return false
  flights+=Flight(item.definition.kind,bitmap,item.sprite.x,item.sprite.y)
  // 004251f0/00421da0/004539c0: sparkles centred on the item and "+ 10,000" at the click.
  successBurst(item.sprite.x+item.sprite.image.width/2,item.sprite.y+item.sprite.image.height/2)
  floatingPoints+=FloatingPoints("+ 10,000",x,y)
  return true
 }
 override fun collectiblePickSound():String? = doc.component("eyespypauseunderlay").attributes["keypickedsfx"]
 override fun meterFillSound():String? = doc.component("eyespypauseunderlay").attributes["itemfillsfx"]
 // Rank-up (004162e0/004188a0/00444080/00444510): when the score lifts the rank during play, the PDA swaps
 // "OBJ. RESTANTES" for newranklabel. Its counter starts at 10 and drops each time a 0.5 s fade reaches
 // alpha 0 or 1 (first event at once, alpha 1), so it blinks for 4.5 s; rankupsfx plays on the alpha-1 event
 // seen with counter 2 (t=4.0 s); rankupdlg shows "MPI n - rank" and the total score meanwhile.
 private var rankCampaign:SdaCampaign?=null
 private var rankUpTime=-1f;private var rankUpRank=0
 private val pendingSounds=mutableListOf<String>()
 override fun drainEffectSounds():List<String> = pendingSounds.toList().also { pendingSounds.clear() }
 private fun checkRankUp(campaign:SdaCampaign) {
  val rank=VegasRankRules.displayed(bestRank,campaign.points,campaign.collectedCount("key"),campaign.collectedCount("chip"))
  if(rankCampaign!==campaign) { rankCampaign=campaign;bestRank=rank;return }
  if(rank>bestRank) { bestRank=rank;rankUpRank=rank;rankUpTime=0f }
 }
 private fun advanceRankUp(seconds:Float) {
  if(rankUpTime<0f) return
  val before=rankUpTime;rankUpTime+=seconds
  if(before<4f && rankUpTime>=4f) doc.component("pdacontrol").attributes["rankupsfx"]?.let(pendingSounds::add)
  if(rankUpTime>=4.5f) rankUpTime=-1f
 }
 private fun drawRankUp(canvas:Canvas,campaign:SdaCampaign):Boolean {
  if(rankUpTime<0f) return false
  val event=(rankUpTime/.5f).toInt();val f=(rankUpTime/.5f-event).coerceIn(0f,1f)
  val alpha=if(event%2==0) 1f-f else f
  ui.label(canvas,doc.component("newranklabel"),opacity=alpha.coerceIn(0f,1f))
  val dialog=doc.component("rankupdlg");val ox=dialog.number("x");val oy=dialog.number("y")
  for(label in dialog.children.filter { it.type=="label" }) {
   val text=when(label.attributes["id"]) {
    "lblrank2" -> "MPI $rankUpRank - "+doc.caption(doc.component("rank$rankUpRank"))
    "lblrank3" -> doc.caption(label)+" "+String.format(java.util.Locale.US,"%,d",campaign.points)
    else -> doc.caption(label)
   }
   ui.label(canvas,label,text,x=ox+label.number("x"),y=oy+label.number("y"))
  }
  return true
 }
 override fun advanceEffects(seconds:Float):List<String> {
  advanceRankUp(seconds)
  val landed=mutableListOf<String>()
  for(f in flights.toList()) {
   if(f.frame<0) {
    f.timer-=seconds
    f.scale+=seconds*3.3333333f*f.velocity*.4f
    if(f.scale>1.2f) { f.scale=1.2f;f.velocity=-f.velocity }
    if(f.scale<.8f) { f.scale=.8f;f.velocity=-f.velocity }
    if(f.timer<=0f) { f.scale=1f;f.frame=0 }
   } else {
    f.frameTime+=seconds
    while(f.frameTime>=nativeFrameSeconds && f.frame<=25) { f.frameTime-=nativeFrameSeconds;f.frame++ }
    if(f.frame>25) { flights.remove(f);indicatorFade[f.kind]=0f;landed+=f.kind }
   }
  }
  for(kind in indicatorFade.keys.toList()) indicatorFade[kind]=indicatorFade.getValue(kind)+seconds
  bursts.forEach { it.age+=seconds };floatingPoints.forEach { it.age+=seconds }
  for((kind,target) in meterTargets) {
   val shown=shownCounts[kind] ?: target.toFloat()
   shownCounts[kind]=if(shown>target) target.toFloat() else minOf(target.toFloat(),shown+seconds/meterFillSeconds)
  }
  return landed
 }
 private fun meterCount(c:SdaCampaign,kind:String):Float {
  val target=c.collectedCount(kind)-flights.count { it.kind==kind }
  meterTargets[kind]=target
  return (shownCounts[kind] ?: target.toFloat().also { shownCounts[kind]=it }).coerceAtMost(target.toFloat())
 }
 // Scene click feedback from the eyespy area (00422870 binds eyespyoverlaymain's animations; 0.0675 s
 // frames, hint 0.1075 s): a found object sparkles animpda centred on its PDA caption (00421fd0), a miss
 // plays animwrong centred at the click offset by (-3,+5) (00422200). Points text (00453600/0042e750/
 // 0042e860): a 200x20 box at the click, rising 2 px per 40 ms update, alpha fading linearly over 3.0 s.
 private class Burst(val node:SdaUiNode,var x:Int,var y:Int,val target:String?=null,var age:Float=0f)
 private class FloatingPoints(val text:String,val x:Int,val y:Int,var age:Float=0f)
 private fun successBurst(cx:Int,cy:Int) {
  val n=doc.component("eyespyanimsucess")
  bursts+=Burst(n,cx-n.number("framew")/2,cy-n.number("frameh")/2)
 }
 private val bursts=mutableListOf<Burst>()
 private val floatingPoints=mutableListOf<FloatingPoints>()
 private val burstFrameSeconds=.0675f;private val pointsSeconds=3f;private val pointsRisePerSecond=2f/.04f
 private fun eyespyAnimation(texture:String)=doc.component("eyespyoverlaymain").let { root ->
  fun all(n:SdaUiNode):List<SdaUiNode> = listOf(n)+n.children.flatMap(::all)
  all(root).first { it.type=="eyespyanimation" && it.attributes["tex"]==texture }
 }
 private fun frames(n:SdaUiNode):Int { val b=ui.bitmap(n.attributes.getValue("tex"));return (b.width/n.number("framew"))*(b.height/n.number("frameh")) }
 override fun sceneFound(sprite:SdaSprite,gain:Int,x:Int,y:Int) {
  // Observed on the original: big sparkles centred on the object (00422440) plus the PDA caption sparkle
  // (00421fd0; position resolved against the caption row on the next HUD draw).
  successBurst(sprite.x+sprite.image.width/2,sprite.y+sprite.image.height/2)
  bursts+=Burst(eyespyAnimation("animpda"),0,0,sprite.identity)
  // 00453430 lines: "+ 5,000"; a fast find adds the speed-bonus caption and "++ <bonus>".
  floatingPoints+=FloatingPoints("+ 5,000",x,y)
  if(gain>5000) {
   floatingPoints+=FloatingPoints(doc.resolve(doc.component("score").attributes.getValue("speedbonusstring")),x+10,y+21)
   floatingPoints+=FloatingPoints("++ "+String.format(java.util.Locale.US,"%,d",gain-5000),x+30,y+42)
  }
 }
 override fun hintPenaltyPoints() { floatingPoints+=FloatingPoints("- 7,500",30,377) } // 00453950
 override fun sceneMiss(x:Int,y:Int,penalty:Boolean):String? {
  val n=eyespyAnimation("animwrong")
  bursts+=Burst(n,x-3-n.number("framew")/2,y+5-n.number("frameh")/2)
  if(!penalty) return null
  // 00453830: five misses inside 2 s cost 2,500 points with penaltysfx and a "-2,500" points text.
  floatingPoints+=FloatingPoints("- 2,500",x,y)
  return doc.component("score").attributes["penaltysfx"]
 }
 private fun placeCaptionBursts(slot:Int,objects:List<String>,label:SdaUiNode) {
  for(b in bursts) if(b.target!=null && b.target in objects && b.x==0 && b.y==0) {
   b.x=label.number("x")+label.number("w")/2-b.node.number("framew")/2
   b.y=label.number("y")+slot*label.number("h")+label.number("h")/2-b.node.number("frameh")/2
  }
 }
 private fun drawSceneFeedback(canvas:Canvas) {
  for(b in bursts.toList()) {
   val n=b.node;val frame=(b.age/burstFrameSeconds).toInt()
   if(frame>=frames(n)) { bursts.remove(b);continue }
   if(b.target!=null && b.x==0 && b.y==0) continue
   ui.spriteFrame(canvas,ui.bitmap(n.attributes.getValue("tex")),n.number("framew"),n.number("frameh"),frame,b.x,b.y)
  }
  val node=doc.component("eyespypoints")
  for(p in floatingPoints.toList()) {
   if(p.age>=pointsSeconds) { floatingPoints.remove(p);continue }
   val opacity=1f-p.age/pointsSeconds
   // Observed: the text starts at the click (left aligned in its 200x20 box).
   ui.label(canvas,node.copy(attributes=node.attributes+mapOf("halign" to "left","valign" to "middle")),p.text,
    x=p.x,y=(p.y-pointsRisePerSecond*p.age).toInt(),width=200,height=20,opacity=opacity.coerceIn(0f,1f),clipToBounds=false)
  }
 }
 private fun bonusFamily(game:SdaBonusGame)=when(game) {
  is SdaTileRotGame -> "tilerotgameunderlay";is SdaTileSwapGame -> "tilegameunderlay"
  is SdaWordSearchGame -> "wordsearchgameunderlay";is SdaJigsawGame -> "jigsawgameunderlay";else -> null
 }
 override fun bonusSoundState(game:SdaBonusGame):Triple<String,Int,Boolean>? {
  bonusFamily(game) ?: return null
  val solved=when(game) {
   is SdaTileRotGame -> game.tileRotations.count { it==0 }
   is SdaTileSwapGame -> game.tilePositions.withIndex().count { (i,p) -> i==p }
   is SdaWordSearchGame -> game.foundWords.size
   is SdaJigsawGame -> game.interaction.board.placed.size
   else -> 0
  }
  return Triple(game.state().toString(),solved,game.isSolved)
 }
 /** XUI minigame sounds: clickedsfx on an accepted press, foundsfx/placedsfx when a unit is solved,
  *  and the completed window's sfx (levelwinsfx) when the board becomes solved. */
 override fun bonusSound(game:SdaBonusGame,before:Triple<String,Int,Boolean>,after:Triple<String,Int,Boolean>,pressed:Boolean):List<String> {
  val node=doc.component(bonusFamily(game) ?: return emptyList()).attributes
  val sounds=mutableListOf<String>()
  if(after.second>before.second) (node["placedsfx"] ?: node["foundsfx"])?.let(sounds::add)
  else if(pressed && after.first!=before.first) node["clickedsfx"]?.let(sounds::add)
  if(after.third && !before.third) doc.component("tilegamewon").attributes["sfx"]?.let(sounds::add)
  return sounds
 }
 override fun drawEffects(canvas:Canvas) {
  drawSceneFeedback(canvas)
  val paint=Paint(Paint.FILTER_BITMAP_FLAG)
  for(f in flights) {
   val w=f.bitmap.width.toFloat();val h=f.bitmap.height.toFloat()
   if(f.frame<0) {
    paint.alpha=255
    canvas.save();canvas.scale(f.scale,f.scale,f.x+w/2,f.y+h/2);canvas.drawBitmap(f.bitmap,f.x.toFloat(),f.y.toFloat(),paint);canvas.restore()
   } else {
    val t=f.frame.coerceAtMost(25)/25f
    paint.alpha=Math.round(255*(25-f.frame).coerceAtLeast(0)*.04f)
    canvas.drawBitmap(f.bitmap,f.x+(75-f.x)*t,f.y+(520-f.y)*t,paint)
   }
  }
 }
 private fun sceneIndicators(canvas:Canvas,campaign:SdaCampaign,scene:SdaScene) {
  if(effectsScene!=scene.name) { effectsScene=scene.name;flights.clear();indicatorFade.clear();bursts.clear();floatingPoints.clear() }
  // Observed on the original: the gold small key/chip shows while this scene's item is still hidden; once it
  // reaches the PDA the gold icon fades out over 1.5 s (00444400) to the blue disabled art.
  for((kind,id) in listOf("key" to "smallkey","chip" to "smallchip")) {
   val pending=scene.collectibles.any { it.definition.kind==kind && campaign.collectibleAvailable(it) } || flights.any { it.kind==kind }
   val fade=indicatorFade[kind]
   ui.image(canvas,doc.component(id+"disabled"))
   if(pending) ui.image(canvas,doc.component(id))
   else if(fade!=null && fade<indicatorFadeSeconds) { canvas.saveLayerAlpha(null,Math.round(255*(1-fade/indicatorFadeSeconds)));ui.image(canvas,doc.component(id));canvas.restore() }
  }
 }
 private fun base(canvas:Canvas,c:SdaCampaign?,clock:SdaClock?) {
  doc.component("pdacontrol").children.filter { it.type=="image" && it.attributes["id"]==null }.forEach { ui.image(canvas,it) }
  val timer=doc.component("clock")
  // User-requested Android layout: one centered clock line, separated from score.
  compactInfo(canvas,timer,doc.caption(timer)+" "+clock?.text(unlimited=c?.unlimited==true).orEmpty(),timer.number("y")-5)
  // PDA captions above the two collection meters.
  doc.component("pdacontrol").children.filter { it.type=="label" && it.attributes["caption"] in setOf("@ID_KEYS","@ID_CHIPS") }.forEach { ui.label(canvas,it) }
  if(c!=null) {
   // The unlimited search shows the investigation's keys and chips, as observed on the original.
   val status=investigationStatus.takeIf { c.unlimited }
   if(status!=null) drawCollectionMeters(canvas,status.keys,status.chips)
   else drawCollectionMeters(canvas,meterCount(c,"key"),meterCount(c,"chip"))
   for((kind,id) in listOf("key" to "keyscollected","chip" to "chipscollected")) {
    val label=doc.component(id)
    val count=if(status!=null) (if(kind=="key") status.keys else status.chips) else c.collectedCount(kind)-flights.count { it.kind==kind }
    ui.label(canvas,label,count.toString()+doc.caption(label))
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
  playerMapDisplay(canvas,campaign)
  // The original map keeps the PDA's HINT and MAP buttons on screen.
  ui.button(canvas,doc.component("hintbutton"));ui.button(canvas,doc.component("mapbutton"))
  if(campaign.unlimited) ui.label(canvas,doc.component("unlimitedmodemapscreencaption"))
  else ui.label(canvas,doc.component("mapunderlay").children.single { it.type=="label" && it.attributes["id"]=="mapscreencaption" },doc.resolve(campaign.currentLevel.title))
  for((node,pos) in cards(campaign)) {
   val (x,y)=pos;val a=node.attributes
   canvas.drawBitmap(ui.bitmap(a.getValue("texscene")),x+13f,y+11f,photos)
   val frame=ui.bitmap(a.getValue("texnormal"))
   val src=Rect(0,0,minOf(node.number("w"),frame.width),minOf(node.number("h"),frame.height))
   canvas.drawBitmap(frame,src,Rect(x,y,x+src.width(),y+src.height()),null)
   ui.label(canvas,node,doc.caption(node),x+19,y+115,node.number("w")-19,node.number("h")-115)
   val count=if(campaign.unlimited) campaign.sceneRemaining(a.getValue("name")) else campaign.scenes[a.getValue("name")]?.remainingCaptions()?.size ?: 10
   ui.label(canvas,node.copy(attributes=a+mapOf("halign" to "center","font" to a.getValue("fontitems"))),count.toString(),x+160,y+97,22,20)
  }
  if(campaign.unlimited) {
   // Unlimited map PDA text, total counter and page arrows (dim at either end) as on the original.
   doc.component("map_pdatext_container").children.forEach { if(it.type=="image") ui.image(canvas,it) else if(it.type=="label") ui.label(canvas,it) }
   val total=doc.component("totalitems");ui.label(canvas,total,doc.caption(total)+" "+campaign.remainingObjects)
   val last=unlimitedPages(campaign).lastIndex
   for((id,enabled) in listOf("mapleftarrowbutton" to (mapPage>0 && !campaign.unlimitedFinished),"maprightarrowbutton" to (mapPage<last && !campaign.unlimitedFinished))) {
    val node=doc.component(id)
    if(enabled) button(canvas,node) else { canvas.saveLayerAlpha(RectF(ui.rect(node)),110);ui.button(canvas,node);canvas.restore() }
   }
  } else {
   doc.component("maptext").children.forEach { if(it.type=="image") ui.image(canvas,it) else if(it.type=="label") ui.label(canvas,it) }
   levelLabel(canvas,campaign)
   // Original map and scenes read "OBJ. RESTANTES".
   val total=doc.component("itemstofind");ui.label(canvas,total,doc.caption(total)+" "+campaign.remainingObjects)
  }
 }
 override fun sceneAt(campaign:SdaCampaign,x:Int,y:Int):String? {
  if(campaign.unlimited && !campaign.unlimitedFinished) {
   val last=unlimitedPages(campaign).lastIndex
   if(ui.rect(doc.component("mapleftarrowbutton")).contains(x,y)) { if(mapPage>0) mapPage--;return null }
   if(ui.rect(doc.component("maprightarrowbutton")).contains(x,y)) { if(mapPage<last) mapPage++;return null }
  }
  return cardAt(campaign,x,y)
 }
 private fun cardAt(campaign:SdaCampaign,x:Int,y:Int):String?=cards(campaign).firstOrNull { (n,p) -> Rect(p.first,p.second,p.first+n.number("w"),p.second+n.number("h")).contains(x,y) }?.first?.attributes?.get("name")
 override fun drawHud(canvas:Canvas,campaign:SdaCampaign?,scene:SdaScene,clock:SdaClock?,paused:Boolean) {
  base(canvas,campaign,campaign?.clock ?: clock)
  if(campaign?.unlimited==true) {
   // eyespyunlimitedtext: the scene name replaces the level number in unlimited scenes.
   val name=doc.component("mapunderlay").children.firstOrNull { it.type=="scenebutton" && it.attributes["name"]==campaign.currentSceneName }?.let(doc::caption).orEmpty()
   ui.label(canvas,doc.component("scenenamelabel"),name)
  } else campaign?.let { levelLabel(canvas,it) }
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
    val cols=bitmap.width/w;val frames=cols*(bitmap.height/h);val frame=(hint.animationAge/.1075f).toInt() // 00422870: animhint frame time
    if(cols>0 && frame in 0 until frames) {
     val x=sprite.x+(sprite.image.width-w)/2;val y=sprite.y+(sprite.image.height-h)/2
     canvas.save();canvas.clipRect(ui.rect(doc.component("pdacontrol")).right,0,800,600)
     canvas.drawBitmap(bitmap,Rect(frame%cols*w,frame/cols*h,frame%cols*w+w,frame/cols*h+h),Rect(x,y,x+w,y+h),null);canvas.restore()
    }
   }
  }
  doc.component(if(campaign?.unlimited==true) "eyespyunlimitedtext" else "eyespytext").children.filter { it.type=="label" && it.attributes["id"]!="scenenamelabel" }.forEach { ui.label(canvas,it) }
  // Scene indicators: lit while this scene still holds an uncollected key/chip, dimmed otherwise.
  if(campaign!=null && !campaign.unlimited) sceneIndicators(canvas,campaign,scene)
  val definitions=sceneRows.getOrPut(scene.name) {
   SdaXui.parse(requireNotNull(content.read(scene.name))).targetSets.associateBy { it.objects }
  }
  // The original hides objective captions while its pause overlay is active.
  if(!paused) for((slot,row) in scene.targetPresentation().withIndex()) {
   val attributes=definitions.getValue(row.objects).attributes
   val label=SdaUiNode("label",attributes+mapOf("halign" to "center","valign" to "middle"),emptyList())
   placeCaptionBursts(slot,row.objects,label)
   ui.label(canvas,label,row.caption,y=label.number("y")+slot*label.number("h"),opacity=row.alpha)
  }
  campaign?.let(::checkRankUp)
  if(campaign?.unlimited==true) {
   val sceneItems=doc.component("sceneitems");ui.label(canvas,sceneItems,doc.caption(sceneItems)+" "+campaign.sceneRemaining(requireNotNull(campaign.currentSceneName)))
   val total=doc.component("totalitems");ui.label(canvas,total,doc.caption(total)+" "+campaign.remainingObjects)
  } else if(campaign==null || !drawRankUp(canvas,campaign)) {
   val total=doc.component("itemstofind")
   ui.label(canvas,total,doc.caption(total)+" "+(campaign?.remainingObjects ?: scene.remainingCaptions().size))
  }
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
  }.also { it.openDialogSound=::dialogSound }
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
