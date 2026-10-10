package org.rigorcore.caserecomp.sda

/** Native SDA button states 0..4; bindings remain data from the UI document. */
enum class SdaButtonState(val textureKey:String,val fontKey:String) {
 NORMAL("texnormal","fontnormal"), HOVER("texhover","fonthover"),
 PRESSED("texpushed","fontpushed"), SELECTED("texhover","fonthover"), DISABLED("texdisabled","fontdisabled")
}
data class SdaButtonPresentation(val texture:String?,val font:String?,val captionX:Int,val captionY:Int)
fun SdaUiNode.buttonPresentation(state:SdaButtonState):SdaButtonPresentation = SdaButtonPresentation(
 attributes[state.textureKey],attributes[state.fontKey] ?: attributes["font"],
 number("globalcaptionoffsetx")+if(state==SdaButtonState.PRESSED) number("captionoffsetx") else 0,
 number("globalcaptionoffsety")+if(state==SdaButtonState.PRESSED) number("captionoffsety") else 0)


/** Native center attribute: parent width / 2 minus control width / 2; preserve y. */
fun SdaUiNode.buttonX(parentWidth:Int?,buttonWidth:Int):Int =
 if(attributes["center"]=="true" && parentWidth!=null) parentWidth/2-buttonWidth/2 else number("x")
