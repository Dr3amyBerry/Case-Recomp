package org.rigorcore.caserecomp.lingo

import org.rigorcore.caserecomp.lingo.LingoValue.LFloat
import org.rigorcore.caserecomp.lingo.LingoValue.LInstance
import org.rigorcore.caserecomp.lingo.LingoValue.LInt
import org.rigorcore.caserecomp.lingo.LingoValue.LList
import org.rigorcore.caserecomp.lingo.LingoValue.LPropList
import org.rigorcore.caserecomp.lingo.LingoValue.LScriptRef
import org.rigorcore.caserecomp.lingo.LingoValue.LString
import org.rigorcore.caserecomp.lingo.LingoValue.LSymbol
import org.rigorcore.caserecomp.lingo.LingoValue.Void
import kotlin.random.Random

/**
 * Director services the VM does not implement itself (sprites, members, sound, the
 * stage, Xtras...). Every method may return null to mean "not provided here".
 */
interface LingoHost {
    fun callBuiltin(vm: LingoVm, name: String, args: List<LingoValue>): LingoValue? = null
    fun getMovieProp(name: String): LingoValue? = null
    fun setMovieProp(name: String, value: LingoValue): Boolean = false
    fun theBuiltin(name: String): LingoValue? = null
}

private class ArgList(val values: List<LingoValue>, val wantResult: Boolean)

private class Frame(val handler: LingoHandler, val receiver: LInstance?, args: List<LingoValue>) {
    val args: MutableList<LingoValue> = args.toMutableList().also { list ->
        while (list.size < handler.arguments.size) list += Void
    }
    val passedCount = args.size
    val locals: Array<LingoValue> = Array(handler.locals.size) { Void }
    val stack = ArrayList<Any>(16)
    var returnValue: LingoValue = Void
}

/**
 * Interpreter for Director 8.5 Lingo bytecode as described by the compiled-script
 * bundle. It is a clean-room implementation of the opcode semantics; game logic is
 * only ever the user's own compiled scripts loaded at runtime.
 */
class LingoVm(
    val bundle: LingoBundle,
    private val host: LingoHost = object : LingoHost {},
    val random: Random = Random.Default,
    private val stepLimit: Long = 50_000_000L,
    private val maxDepth: Int = 256,
) {
    private val globals = HashMap<String, LingoValue>()
    private var steps = 0L
    private var depth = 0
    /** `the` properties assigned that no host accepted; kept for inspection only. */
    val unhandledSets = mutableListOf<Pair<String, LingoValue>>()

    fun global(name: String): LingoValue = globals[name.lowercase()] ?: Void
    fun setGlobal(name: String, value: LingoValue) { globals[name.lowercase()] = value }
    fun clearGlobals() = globals.clear()

    /** Reset the per-entry instruction budget (call once per host event). */
    fun resetBudget() { steps = 0 }

    // ---- calls ---------------------------------------------------------------------------

    fun callHandler(handler: LingoHandler, args: List<LingoValue>, receiver: LInstance? = args.firstOrNull() as? LInstance): LingoValue {
        if (++depth > maxDepth) { depth = 0; throw LingoError("call stack too deep in ${handler.name}") }
        try {
            return execute(Frame(handler, receiver, args))
        } catch (e: LingoError) {
            if (e.lingoTrace.size < 32) e.lingoTrace += "${handler.script.name}.${handler.name}"
            throw e
        } finally {
            depth--
        }
    }

    /** Call a movie-script (global) handler by name; null when no movie script defines it. */
    fun callGlobal(name: String, args: List<LingoValue>): LingoValue? {
        for (script in bundle.movieScripts) {
            val handler = script.handler(name) ?: continue
            return callHandler(handler, args, receiver = null)
        }
        return null
    }

    /** Send a message to an object; null when it has no such handler/method. */
    fun callMethod(target: LingoValue, name: String, args: List<LingoValue>): LingoValue? = when (target) {
        is LInstance -> target.script.handler(name)?.let { callHandler(it, args, target) }
        is LScriptRef -> if (name.equals("new", true)) newInstance(target.script, args.drop(1))
            else target.script.handler(name)?.let { callHandler(it, args, null) }
        is LingoValue.LHost -> target.call(name, args.drop(1))
        else -> LingoNatives.method(this, target, name, args.drop(1))
    }

    fun newInstance(script: LingoScript, args: List<LingoValue>): LingoValue {
        val instance = LInstance(script)
        val constructor = script.handler("new") ?: return instance
        return callHandler(constructor, listOf(instance) + args, instance)
    }

    /** Resolution of an unqualified call `name(args)`, as Director's message dispatch does it. */
    fun callFunction(name: String, args: List<LingoValue>): LingoValue {
        val first = args.firstOrNull()
        if (first is LInstance && first.script.handler(name) != null) return callMethod(first, name, args)!!
        callGlobal(name, args)?.let { return it }
        LingoNatives.function(this, name, args)?.let { return it }
        host.callBuiltin(this, name, args)?.let { return it }
        if (first != null && first !is LInstance) callMethod(first, name, args)?.let { return it }
        throw LingoError("handler not defined: $name")
    }

    // ---- interpreter ---------------------------------------------------------------------

    private fun name(index: Int): String = bundle.names.getOrNull(index) ?: throw LingoError("name index $index out of range")

    private fun execute(frame: Frame): LingoValue {
        val code = frame.handler.code
        val script = frame.handler.script
        val stack = frame.stack
        fun pop(): LingoValue = (stack.removeLastOrNull() ?: throw LingoError("stack underflow in ${frame.handler.name}")) as? LingoValue
            ?: throw LingoError("expected a value on the stack")
        fun popArgs(): ArgList = stack.removeLastOrNull() as? ArgList ?: throw LingoError("expected an argument list")
        fun push(value: LingoValue) { stack += value }
        fun result(args: ArgList, value: LingoValue) { if (args.wantResult) push(value) }

        var pc = 0
        while (pc < code.size) {
            if (++steps > stepLimit) throw LingoError("instruction budget exhausted in ${script.name}/${frame.handler.name}")
            val at = pc
            val op = code[pc++].toInt() and 0xFF
            val opcode = if (op >= 0x40) 0x40 + op % 0x40 else op
            var arg = 0
            if (op >= 0xC0) {
                arg = ((code[pc].toInt() and 0xFF) shl 24) or ((code[pc + 1].toInt() and 0xFF) shl 16) or
                    ((code[pc + 2].toInt() and 0xFF) shl 8) or (code[pc + 3].toInt() and 0xFF); pc += 4
            } else if (op >= 0x80) {
                val raw = ((code[pc].toInt() and 0xFF) shl 8) or (code[pc + 1].toInt() and 0xFF); pc += 2
                arg = if (opcode == 0x41 || opcode == 0x6E) raw.toShort().toInt() else raw
            } else if (op >= 0x40) {
                val raw = code[pc++].toInt() and 0xFF
                arg = if (opcode == 0x41) raw.toByte().toInt() else raw
            }
            when (opcode) {
                0x01, 0x02 -> return frame.returnValue
                0x03 -> push(LInt(0))
                0x04 -> { val b = pop(); push(arithmetic(pop(), b, '*')) }
                0x05 -> { val b = pop(); push(arithmetic(pop(), b, '+')) }
                0x06 -> { val b = pop(); push(arithmetic(pop(), b, '-')) }
                0x07 -> { val b = pop(); push(arithmetic(pop(), b, '/')) }
                0x08 -> { val b = pop(); push(arithmetic(pop(), b, '%')) }
                0x09 -> push(when (val x = pop()) {
                    is LInt -> LInt(-x.value); is LFloat -> LFloat(-x.value)
                    else -> arithmetic(LInt(0), x, '-')
                })
                0x0A -> { val b = pop(); push(LString(pop().asText() + b.asText())) }
                0x0B -> { val b = pop(); push(LString(pop().asText() + " " + b.asText())) }
                0x0C -> { val b = pop(); push(LingoValue.bool(lingoCompare(pop(), b) < 0)) }
                0x0D -> { val b = pop(); push(LingoValue.bool(lingoCompare(pop(), b) <= 0)) }
                0x0E -> { val b = pop(); push(LingoValue.bool(!lingoEquals(pop(), b))) }
                0x0F -> { val b = pop(); push(LingoValue.bool(lingoEquals(pop(), b))) }
                0x10 -> { val b = pop(); push(LingoValue.bool(lingoCompare(pop(), b) > 0)) }
                0x11 -> { val b = pop(); push(LingoValue.bool(lingoCompare(pop(), b) >= 0)) }
                0x12 -> { val b = pop(); push(LingoValue.bool(pop().isTruthy() && b.isTruthy())) }
                0x13 -> { val b = pop(); push(LingoValue.bool(pop().isTruthy() || b.isTruthy())) }
                0x14 -> push(LingoValue.bool(!pop().isTruthy()))
                0x15 -> { val b = pop(); push(LingoValue.bool(pop().asText().contains(b.asText(), ignoreCase = true))) }
                0x16 -> { val b = pop(); push(LingoValue.bool(pop().asText().startsWith(b.asText(), ignoreCase = true))) }
                0x17 -> { val text = pop(); push(LString(LingoChunks.get(text.asText(), popChunk(::pop)))) }
                0x1E -> push(LList(popArgs().values.toMutableList()))
                0x1F -> {
                    val values = popArgs().values
                    if (values.size % 2 != 0) throw LingoError("odd property list literal")
                    push(LPropList(values.chunked(2).mapTo(mutableListOf()) { it[0] to it[1] }))
                }
                0x21 -> { val b = pop(); val a = pop(); push(b); push(a) }
                0x41, 0x6E, 0x6F -> push(LInt(arg))
                0x42 -> { val n = arg; val values = List(n) { pop() }.asReversed(); stack += ArgList(values, false) }
                0x43 -> { val n = arg; val values = List(n) { pop() }.asReversed(); stack += ArgList(values, true) }
                0x44 -> push(script.literals.getOrNull(arg) ?: throw LingoError("literal $arg out of range"))
                0x45 -> push(LSymbol(name(arg)))
                0x48, 0x49 -> push(global(name(arg)))
                0x4E, 0x4F -> setGlobal(name(arg), pop())
                0x4A -> push(frame.receiver?.let { propertyOf(it, name(arg)) } ?: Void)
                0x50 -> { val value = pop(); frame.receiver?.properties?.set(propertyKey(frame.receiver, name(arg)), value) }
                0x4B -> push(frame.args.getOrElse(arg) { Void })
                0x51 -> { val value = pop(); while (frame.args.size <= arg) frame.args += Void; frame.args[arg] = value }
                0x4C -> push(frame.locals.getOrElse(arg) { throw LingoError("local $arg out of range") })
                0x52 -> { val value = pop(); if (arg !in frame.locals.indices) throw LingoError("local $arg out of range"); frame.locals[arg] = value }
                0x53 -> pc = at + arg
                0x54 -> pc = at - arg
                0x55 -> if (!pop().isTruthy()) pc = at + arg
                0x56 -> {
                    val args = popArgs()
                    val handler = script.handlers.getOrNull(arg) ?: throw LingoError("local handler $arg out of range")
                    result(args, callHandler(handler, args.values, frame.receiver))
                }
                0x57 -> {
                    val args = popArgs()
                    val callee = name(arg)
                    if (callee.equals("return", true)) {
                        // `return` leaves the handler at once; what the compiler emits after it
                        // (a jump out of a case/if) is unreachable.
                        return args.values.firstOrNull() ?: Void
                    } else {
                        result(args, callFunction(callee, args.values))
                    }
                }
                0x59 -> {
                    val mode = PutMode.entries.getOrNull(((arg shr 4) and 0xF) - 1) ?: throw LingoError("bad put mode")
                    val ref = variableRef(arg and 0xF, frame, ::pop)
                    val value = pop()
                    ref.write(LString(LingoChunks.put(ref.read().asText(), emptyList(), value.asText(), mode)))
                }
                0x5B -> {
                    val ref = variableRef(arg, frame, ::pop)
                    ref.write(LString(LingoChunks.delete(ref.read().asText(), popChunk(::pop))))
                }
                0x5C -> push(movieProp(legacyProperty(arg, pop().toInt())))
                0x5D -> { val property = legacyProperty(arg, pop().toInt()); setMovieProp(property, pop()) }
                0x5F -> push(movieProp(name(arg)))
                0x60 -> { val value = pop(); setMovieProp(name(arg), value) }
                0x61, 0x70 -> { val target = pop(); push(getObjectProp(target, name(arg))) }
                0x62 -> { val value = pop(); setObjectProp(pop(), name(arg), value) }
                0x64 -> push(stack.getOrNull(stack.size - 1 - arg) as? LingoValue ?: throw LingoError("bad peek"))
                0x65 -> repeat(arg) { stack.removeLastOrNull() ?: throw LingoError("stack underflow in pop") }
                0x66 -> { popArgs(); push(theBuiltin(name(arg), frame)) }
                0x67 -> {
                    val args = popArgs()
                    val target = args.values.firstOrNull() ?: throw LingoError("method call without a target")
                    val method = name(arg)
                    val value = callMethod(target, method, args.values)
                        ?: throw LingoError("${target.ilkOf()} has no method $method")
                    result(args, value)
                }
                0x71 -> push(LFloat(java.lang.Float.intBitsToFloat(arg).toDouble()))
                else -> throw LingoError("unsupported opcode 0x${opcode.toString(16)} in ${script.name}/${frame.handler.name}")
            }
        }
        return frame.returnValue
    }

    private fun popChunk(pop: () -> LingoValue): List<ChunkSelector> {
        val lastLine = pop().toInt(); val firstLine = pop().toInt()
        val lastItem = pop().toInt(); val firstItem = pop().toInt()
        val lastWord = pop().toInt(); val firstWord = pop().toInt()
        val lastChar = pop().toInt(); val firstChar = pop().toInt()
        return listOfNotNull(
            ChunkSelector(ChunkKind.LINE, firstLine, lastLine).takeIf { firstLine != 0 },
            ChunkSelector(ChunkKind.ITEM, firstItem, lastItem).takeIf { firstItem != 0 },
            ChunkSelector(ChunkKind.WORD, firstWord, lastWord).takeIf { firstWord != 0 },
            ChunkSelector(ChunkKind.CHAR, firstChar, lastChar).takeIf { firstChar != 0 },
        )
    }

    private class VariableRef(val read: () -> LingoValue, val write: (LingoValue) -> Unit)

    private fun variableRef(kind: Int, frame: Frame, pop: () -> LingoValue): VariableRef {
        val id = pop()
        return when (kind) {
            1, 2 -> { val n = name(id.toInt()); VariableRef({ global(n) }, { setGlobal(n, it) }) }
            3 -> {
                val receiver = frame.receiver ?: throw LingoError("property write without an instance")
                val key = propertyKey(receiver, name(id.toInt()))
                VariableRef({ receiver.properties[key] ?: Void }, { receiver.properties[key] = it })
            }
            4 -> { val i = id.toInt(); VariableRef({ frame.args.getOrElse(i) { Void } }, { v -> while (frame.args.size <= i) frame.args += Void; frame.args[i] = v }) }
            5 -> { val i = id.toInt(); VariableRef({ frame.locals[i] }, { frame.locals[i] = it }) }
            else -> throw LingoError("unsupported variable kind $kind")
        }
    }

    private fun propertyKey(instance: LInstance, name: String): String =
        instance.properties.keys.firstOrNull { it.equals(name, ignoreCase = true) } ?: name

    private fun propertyOf(instance: LInstance, name: String): LingoValue = instance.properties[propertyKey(instance, name)] ?: Void

    private fun movieProp(name: String): LingoValue = host.getMovieProp(name) ?: Void

    private fun setMovieProp(name: String, value: LingoValue) {
        if (!host.setMovieProp(name, value)) unhandledSets += name to value
    }

    /** Name of a `the` property read or written through the legacy get/set opcodes. */
    private fun legacyProperty(type: Int, id: Int): String = when (type) {
        0x00 -> MOVIE_PROPERTIES.getOrNull(id)
        0x08 -> ANIMATION2_PROPERTIES.getOrNull(id)
        else -> null
    } ?: throw LingoError("unsupported legacy property $type/$id")

    private companion object {
        val MOVIE_PROPERTIES = listOf("floatPrecision", "mouseDownScript", "mouseUpScript", "keyDownScript", "keyUpScript", "timeoutScript")
        val ANIMATION2_PROPERTIES = listOf("", "perFrameHook", "number of castMembers", "number of menus", "number of castLibs", "number of xtras")
    }

    private fun theBuiltin(name: String, frame: Frame): LingoValue =
        if (name.equals("paramCount", true)) LInt(frame.passedCount) else host.theBuiltin(name) ?: Void

    fun getObjectProp(target: LingoValue, name: String): LingoValue = when (target) {
        is LInstance -> target.properties.entries.firstOrNull { it.key.equals(name, true) }?.value
            ?: throw LingoError("property $name not found on ${target.script.name}")
        is LingoValue.LHost -> target.getProp(name)
        else -> LingoNatives.property(target, name)
    }

    fun setObjectProp(target: LingoValue, name: String, value: LingoValue) {
        when (target) {
            is LInstance -> target.properties[propertyKey(target, name)] = value
            is LingoValue.LHost -> target.setProp(name, value)
            is LPropList -> LingoNatives.setPropValue(target, LSymbol(name), value)
            else -> throw LingoError("cannot set $name on ${target.ilkOf()}")
        }
    }
}
