package org.rigorcore.caserecomp.sda

/** Configurable rechargeable aid. Timing belongs to the title profile. */
data class SdaHintPolicy(val initialDelay:Float,val multiplier:Float,val maximumDelay:Float) {
 init { require(initialDelay.isFinite() && initialDelay>0 && multiplier.isFinite() && multiplier>=1 && maximumDelay.isFinite() && maximumDelay>=initialDelay) }
}
class SdaHint(private val policy:SdaHintPolicy) {
 var delay=policy.initialDelay;private set
 private var elapsed=0f
 private var cooling=false
 var target:String?=null;private set
 var animationAge=0f;private set
 val ready get()=!cooling
 val progress get()=if(ready) 1f else (elapsed/delay).coerceIn(0f,1f)
 fun consume():Boolean {
  if(!ready) return false
  cooling=true;elapsed=0f;clearTarget();return true
 }
 fun reveal(id:String?) { target=id;animationAge=0f }
 fun clearTarget() { target=null;animationAge=0f }
 fun advance(seconds:Float) {
  require(seconds.isFinite() && seconds>=0)
  animationAge+=seconds
  if(!cooling) return
  elapsed=minOf(delay,elapsed+seconds)
  if(elapsed>=delay) { cooling=false;delay=minOf(policy.maximumDelay,delay*policy.multiplier) }
 }
 fun state():Map<String,Any?> = mapOf("delay" to delay,"elapsed" to elapsed,"cooling" to cooling,"target" to target,"animationAge" to animationAge)
 fun restore(state:Map<String,Any?>) {
  val d=(state["delay"] as? Number)?.toFloat() ?: throw IllegalArgumentException("missing hint delay")
  val e=(state["elapsed"] as? Number)?.toFloat() ?: throw IllegalArgumentException("missing hint elapsed")
  val age=(state["animationAge"] as? Number)?.toFloat() ?: 0f
  val pending=state["cooling"] as? Boolean ?: throw IllegalArgumentException("missing hint recharge")
  require(d.isFinite() && d in policy.initialDelay..policy.maximumDelay && e.isFinite() && e>=0 && e<=policy.maximumDelay && age.isFinite() && age>=0)
  require(!pending || e<d)
  require(state["target"]==null || state["target"] is String)
  delay=d;elapsed=e;cooling=pending;target=state["target"] as? String;animationAge=age
 }
}
