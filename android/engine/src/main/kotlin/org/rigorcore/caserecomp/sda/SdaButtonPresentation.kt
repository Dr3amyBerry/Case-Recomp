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
