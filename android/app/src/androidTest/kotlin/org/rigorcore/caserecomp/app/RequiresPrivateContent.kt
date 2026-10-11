package org.rigorcore.caserecomp.app

/** Tests explicitly invoked on a device with the owner's game files; excluded from public CI. */
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class RequiresPrivateContent
