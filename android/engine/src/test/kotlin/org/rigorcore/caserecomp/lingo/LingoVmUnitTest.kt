package org.rigorcore.caserecomp.lingo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.rigorcore.caserecomp.lingo.LingoValue.LInt
import org.rigorcore.caserecomp.lingo.LingoValue.LList
import org.rigorcore.caserecomp.lingo.LingoValue.LPropList
import org.rigorcore.caserecomp.lingo.LingoValue.LString
import org.rigorcore.caserecomp.lingo.LingoValue.LSymbol
import kotlin.random.Random

/** Tiny assembler for synthetic Lingo bytecode with forward/backward labels. */
private class Asm(private val names: MutableList<String>) {
    private sealed interface Item
    private class Op(val code: Int, val arg: Int?, val wide: Boolean = false) : Item
    private class Jump(val code: Int, val label: String) : Item
    private class Label(val name: String) : Item
    private val items = mutableListOf<Item>()

    fun name(n: String): Int = names.indexOf(n).takeIf { it >= 0 } ?: names.size.also { names += n }
    fun op(code: Int) = apply { items += Op(code, null) }
    fun op(code: Int, arg: Int) = apply { items += Op(code, arg) }
    fun named(code: Int, n: String) = op(code, name(n))
    fun int(value: Int) = apply { items += Op(0x41, value, wide = value !in -128..127) }
    fun jump(code: Int, label: String) = apply { items += Jump(code, label) }
    fun label(n: String) = apply { items += Label(n) }

    private fun size(item: Item) = when (item) {
        is Label -> 0
        is Jump -> 3
        is Op -> if (item.arg == null) 1 else if (item.wide || item.arg > 255) 3 else 2
    }

    fun bytes(): ByteArray {
        val positions = mutableMapOf<String, Int>()
        var pos = 0
        for (item in items) { if (item is Label) positions[item.name] = pos; pos += size(item) }
        val out = java.io.ByteArrayOutputStream()
        pos = 0
        for (item in items) {
            when (item) {
                is Label -> Unit
                is Op -> when {
                    item.arg == null -> out.write(item.code)
                    item.wide || item.arg > 255 -> { out.write(item.code + 0x40); out.write((item.arg shr 8) and 0xFF); out.write(item.arg and 0xFF) }
                    else -> { out.write(item.code); out.write(item.arg and 0xFF) }
                }
                is Jump -> {
                    val target = positions.getValue(item.label)
                    val offset = if (item.code == 0x54) pos - target else target - pos
                    out.write(item.code + 0x40); out.write((offset shr 8) and 0xFF); out.write(offset and 0xFF)
                }
            }
            pos += size(item)
        }
        return out.toByteArray()
    }
}

class LingoVmUnitTest {
    private val names = mutableListOf<String>()
    private fun asm() = Asm(names)
    private fun Asm.ret(argCount: Int = 1) = op(0x43, argCount).named(0x57, "return").op(0x01)

    private fun handler(name: String, args: List<String>, locals: List<String> = emptyList(), code: Asm) =
        LingoHandler(name, args, locals, code.bytes())

    private fun vm(vararg scripts: LingoScript, host: LingoHost = object : LingoHost {}, limit: Long = 1_000_000) =
        LingoVm(LingoBundle(names, scripts.toList()), host, Random(7), limit)

    private fun movie(vararg handlers: LingoHandler, literals: List<LingoValue> = emptyList()) =
        LingoScript("movie", 1, ScriptType.MOVIE, emptyList(), emptyList(), literals, handlers.toList())

    @Test fun arithmetic_and_return() {
        // on area w, h: return w * h + 3
        val f = handler("area", listOf("w", "h"), code = asm().op(0x4B, 0).op(0x4B, 1).op(0x04).int(3).op(0x05).ret())
        assertEquals(LInt(23), vm(movie(f)).callGlobal("area", listOf(LInt(4), LInt(5))))
        // float promotion and integer division
        val g = handler("g", listOf("a"), code = asm().op(0x4B, 0).int(2).op(0x07).ret())
        assertEquals(LInt(3), vm(movie(g)).callGlobal("g", listOf(LInt(7))))
        assertEquals(LingoValue.LFloat(3.5), vm(movie(g)).callGlobal("g", listOf(LingoValue.LFloat(7.0))))
    }

    @Test fun if_else_and_repeat_loop() {
        // on sign x: if x < 0 then return -1 else return 1
        val sign = handler("sign", listOf("x"), code = asm()
            .op(0x4B, 0).op(0x03).op(0x0C).jump(0x55, "else")
            .int(-1).ret().jump(0x53, "end")
            .label("else").int(1).ret().label("end").op(0x01))
        val v = vm(movie(sign))
        assertEquals(LInt(-1), v.callGlobal("sign", listOf(LInt(-5))))
        assertEquals(LInt(1), v.callGlobal("sign", listOf(LInt(5))))

        // on sum n: total = 0; repeat with i = 1 to n: total = total + i; return total
        val sum = handler("sum", listOf("n"), listOf("total", "i"), asm()
            .op(0x03).op(0x52, 0).int(1).op(0x52, 1)
            .label("top").op(0x4C, 1).op(0x4B, 0).op(0x0D).jump(0x55, "done")
            .op(0x4C, 0).op(0x4C, 1).op(0x05).op(0x52, 0)
            .op(0x4C, 1).int(1).op(0x05).op(0x52, 1).jump(0x54, "top")
            .label("done").op(0x4C, 0).ret())
        assertEquals(LInt(55), vm(movie(sum)).callGlobal("sum", listOf(LInt(10))))
    }

    @Test fun strings_chunks_and_put() {
        val literals = listOf(LString("Hunts"), LString("ville"), LString("Case-Files!"), LString("?"))
        // return "Hunts" & "ville" && "Hunts"
        val join = handler("join", emptyList(), code = asm().op(0x44, 0).op(0x44, 1).op(0x0A).op(0x44, 0).op(0x0B).ret())
        fun Asm.noWordItemLine() = op(0x03).op(0x03).op(0x03).op(0x03).op(0x03).op(0x03)
        // s = "Case-Files!"; delete char s.length of s; put "?" after s; return [char 1 to 4 of s, s]
        val chunk = handler("chunk", emptyList(), listOf("s"), asm()
            .op(0x44, 2).op(0x52, 0)
            .op(0x4C, 0).op(0x43, 1).named(0x57, "length").op(0x03).noWordItemLine()   // char s.length (single)
            .op(0x41, 0).op(0x5B, 5)                                                   // delete ... of local 0
            .op(0x44, 3).op(0x41, 0).op(0x59, 0x25)                                    // put "?" after local 0
            .int(1).int(4).noWordItemLine().op(0x4C, 0).op(0x17)                       // char 1 to 4 of s
            .op(0x4C, 0).op(0x43, 2).op(0x1E).ret())
        val v = vm(movie(join, chunk, literals = literals))
        assertEquals(LString("Huntsville Hunts"), v.callGlobal("join", emptyList()))
        assertEquals(listOf<LingoValue>(LString("Case"), LString("Case-Files?")), (v.callGlobal("chunk", emptyList()) as LList).items)
        assertEquals("Case-Files?", LingoChunks.put(LingoChunks.delete("Case-Files!", listOf(ChunkSelector(ChunkKind.CHAR, 11, 11))),
            emptyList(), "?", PutMode.AFTER))
    }

    @Test fun lists_and_property_lists() {
        // l = [10, 20]; l.add(30); l[2] = 5; return [l.count, l[2], getAt(l, 3)]
        val lists = handler("lists", emptyList(), listOf("l"), asm()
            .int(10).int(20).op(0x43, 2).op(0x1E).op(0x52, 0)
            .op(0x4C, 0).int(30).op(0x42, 2).named(0x67, "add")
            .op(0x4C, 0).int(2).int(5).op(0x42, 3).named(0x67, "setAt")
            .op(0x4C, 0).named(0x61, "count")
            .op(0x4C, 0).int(2).op(0x43, 2).named(0x67, "getAt")
            .op(0x4C, 0).int(3).op(0x43, 2).named(0x57, "getAt")
            .op(0x43, 3).op(0x1E).ret())
        val result = vm(movie(lists)).callGlobal("lists", emptyList()) as LList
        assertEquals(listOf(LInt(3), LInt(5), LInt(30)), result.items)

        // p = [#a: 1]; p.addProp(#b, 2); return [p.b, p.getaProp(#zz), p.findPos(#A)]
        val props = handler("props", emptyList(), listOf("p"), asm()
            .named(0x45, "a").int(1).op(0x43, 2).op(0x1F).op(0x52, 0)
            .op(0x4C, 0).named(0x45, "b").int(2).op(0x42, 3).named(0x67, "addProp")
            .op(0x4C, 0).named(0x61, "b")
            .op(0x4C, 0).named(0x45, "zz").op(0x43, 2).named(0x67, "getaProp")
            .op(0x4C, 0).named(0x45, "A").op(0x43, 2).named(0x67, "findPos")
            .op(0x43, 3).op(0x1E).ret())
        val p = vm(movie(props)).callGlobal("props", emptyList()) as LList
        assertEquals(listOf(LInt(2), LingoValue.Void, LInt(1)), p.items)
    }

    @Test fun parent_script_instances_and_globals() {
        // parent "counter": property pCount; on new me: pCount = 0; return me; on bump me, n: pCount = pCount + n
        val newH = handler("new", listOf("me"), code = asm().op(0x03).named(0x50, "pCount").op(0x4B, 0).ret())
        val bump = handler("bump", listOf("me", "n"), code = asm().named(0x4A, "pCount").op(0x4B, 1).op(0x05).named(0x50, "pCount").op(0x01))
        val counter = LingoScript("counter", 2, ScriptType.PARENT, listOf("pCount"), emptyList(), listOf(LString("counter")), listOf(newH, bump))
        // on main: global gC; gC = new(script "counter"); bump(gC, 4); gC.bump(3); return [gC.pCount, the paramCount]
        val main = handler("main", listOf("unused"), code = asm()
            .op(0x44, 0).op(0x43, 1).named(0x57, "script").op(0x43, 1).named(0x57, "new").named(0x4F, "gC")
            .named(0x49, "gC").int(4).op(0x42, 2).named(0x57, "bump")
            .named(0x49, "gC").int(3).op(0x42, 2).named(0x67, "bump")
            .named(0x49, "gC").named(0x61, "pCount").op(0x42, 0).named(0x66, "paramCount").op(0x43, 2).op(0x1E).ret())
        val v = vm(movie(main, literals = listOf(LString("counter"))), counter)
        val out = v.callGlobal("main", listOf(LInt(9))) as LList
        assertEquals(listOf(LInt(7), LInt(1)), out.items)
        assertTrue(v.global("GC") is LingoValue.LInstance)
    }

    @Test fun case_statement_with_peek_and_pop() {
        // case x of 1: return #one; 2: return #two; otherwise: return #other
        val f = handler("pick", listOf("x"), code = asm()
            .op(0x4B, 0)
            .op(0x64, 0).int(1).op(0x0F).jump(0x55, "c2").op(0x65, 1).named(0x45, "one").ret().jump(0x53, "end")
            .label("c2").op(0x64, 0).int(2).op(0x0F).jump(0x55, "other").op(0x65, 1).named(0x45, "two").ret().jump(0x53, "end")
            .label("other").op(0x65, 1).named(0x45, "other").ret()
            .label("end").op(0x01))
        val v = vm(movie(f))
        assertEquals(LSymbol("one"), v.callGlobal("pick", listOf(LInt(1))))
        assertEquals(LSymbol("two"), v.callGlobal("pick", listOf(LInt(2))))
        assertEquals(LSymbol("other"), v.callGlobal("pick", listOf(LInt(9))))
    }

    @Test fun host_builtins_movie_props_and_value_parser() {
        val host = object : LingoHost {
            var stored: LingoValue = LingoValue.Void
            override fun callBuiltin(vm: LingoVm, name: String, args: List<LingoValue>) =
                if (name == "sprite") LString("sprite ${args[0].asText()}") else null
            override fun getMovieProp(name: String) = if (name == "milliSeconds") LInt(1234) else null
            override fun setMovieProp(name: String, value: LingoValue): Boolean { stored = value; return true }
        }
        val f = handler("f", emptyList(), code = asm()
            .int(5).op(0x43, 1).named(0x57, "sprite").named(0x5F, "milliSeconds").int(8).named(0x60, "floatPrecision")
            .op(0x43, 2).op(0x1E).ret())
        val out = vm(movie(f), host = host).callGlobal("f", emptyList()) as LList
        assertEquals(listOf(LString("sprite 5"), LInt(1234)), out.items)
        assertEquals(LInt(8), host.stored)

        val parsed = LingoLiteralParser.parseOrVoid("[[7, [37, [1, 1]], [49, 1]], #sfxVol: 102]")
        assertEquals(LingoValue.Void, parsed)
        val save = LingoLiteralParser.parseOrVoid("[[7,[37,[1,1]],[49,1]],1,922000]") as LList
        assertEquals(LInt(922000), save.items[2])
        val prefs = LingoLiteralParser.parseOrVoid("[#sfxVol: 102, #tutorial: 0, #name: \"Dream\", #r: rect(0, 0, 8, 6)]") as LPropList
        assertEquals(LString("Dream"), prefs.entries[2].second)
        assertEquals(LingoValue.LRect(LInt(0), LInt(0), LInt(8), LInt(6)), prefs.entries[3].second)
        assertEquals(LingoValue.LFloat(-1.5e-3), LingoLiteralParser.parseOrVoid(" -1.5e-3 "))
    }

    @Test fun errors_and_instruction_budget() {
        val loop = handler("spin", emptyList(), code = asm().label("top").jump(0x54, "top"))
        assertTrue(runCatching { vm(movie(loop), limit = 1000).callGlobal("spin", emptyList()) }.exceptionOrNull() is LingoError)
        val missing = handler("m", emptyList(), code = asm().op(0x42, 0).named(0x57, "noSuchHandler").op(0x01))
        assertTrue(runCatching { vm(movie(missing)).callGlobal("m", emptyList()) }.exceptionOrNull() is LingoError)
        val divide = handler("d", emptyList(), code = asm().int(1).op(0x03).op(0x07).ret())
        assertTrue(runCatching { vm(movie(divide)).callGlobal("d", emptyList()) }.exceptionOrNull() is LingoError)
        assertEquals(null, vm(movie()).callGlobal("absent", emptyList()))
    }

    @Test fun bundle_json_round_trip() {
        val json = """{"format":"case-recomp-lingo-bundle","version":1,"names":["f","return"],
            "scripts":[{"context_index":1,"script_number":0,"properties":[],"globals":[],
            "literals":[{"type":"int","value":-2},{"type":"float","value":0.5},{"type":"string","value":"x"}],
            "handlers":[{"name":"f","arguments":[],"locals":[],"globals":[],"bytecode":"RABDAVcBAQ=="}],
            "member":{"number":3,"name":"Main","script_type":"movie"}}],"source_sha256":"0","notice":""}"""
        val bundle = LingoBundle.parse(json)
        assertEquals(ScriptType.MOVIE, bundle.scripts.single().type)
        assertEquals(bundle.scripts.single(), bundle.scriptNamed("main"))
        assertEquals(LInt(-2), LingoVm(bundle).callGlobal("f", emptyList()))
        assertTrue(runCatching { LingoBundle.parse("""{"format":"other","version":1}""") }.isFailure)
    }
}
