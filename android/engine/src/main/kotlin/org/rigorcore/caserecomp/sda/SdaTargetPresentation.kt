package org.rigorcore.caserecomp.sda

/** Read-only presentation of existing target rows; never changes completion or scoring. */
data class SdaTargetCaption(val index:Int,val objects:List<String>,val caption:String,val alpha:Float)
fun SdaScene.targetPresentation():List<SdaTargetCaption> = activeSets.mapIndexedNotNull { index,ids ->
 val row=rows[ids] ?: return@mapIndexedNotNull null
 if(row.removed || row.alpha<=0f) return@mapIndexedNotNull null
 val choices=captions[ids].orEmpty()
 val foundCount=ids.count { objects[it]?.found==true }
 val caption=choices.getOrNull(minOf(foundCount,choices.lastIndex)) ?: return@mapIndexedNotNull null
 SdaTargetCaption(index,ids,caption,row.alpha)
}
