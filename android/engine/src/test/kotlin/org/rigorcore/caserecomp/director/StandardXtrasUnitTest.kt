package org.rigorcore.caserecomp.director

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import org.rigorcore.caserecomp.lingo.LingoBundle
import org.rigorcore.caserecomp.lingo.LingoError
import org.rigorcore.caserecomp.lingo.LingoValue
import org.rigorcore.caserecomp.lingo.LingoValue.LInt
import org.rigorcore.caserecomp.lingo.LingoValue.LList
import org.rigorcore.caserecomp.lingo.LingoValue.LPropList
import org.rigorcore.caserecomp.lingo.LingoValue.LString
import org.rigorcore.caserecomp.lingo.LingoValue.LSymbol
import org.rigorcore.caserecomp.lingo.LingoVm

class StandardXtrasUnitTest {
    private val store = MemoryStore()
    private val xtras = StandardXtras(store, castFileSize = { if (it.endsWith("01.cct")) 50_000 else -1 }, screenWidth = 1280, screenHeight = 720)
    private val vm = LingoVm(LingoBundle(emptyList(), emptyList()), xtras)
    private fun call(name: String, vararg args: LingoValue) = vm.callFunction(name, args.toList())
    private val key = LString("Software\\Studio\\Game")
    private val root = LString("HKEY_CURRENT_USER")

    @Test fun buddy_registry_strings_and_multi_strings() {
        assertEquals(LString("error"), call("baReadRegString", key, LString("Prefs"), LString("error"), root))
        assertEquals(LInt(1), call("baWriteRegString", key, LString("Prefs"), LString("[#sfxVol: 102]"), root))
        assertEquals(LString("[#sfxVol: 102]"), call("baReadRegString", key, LString("prefs"), LString("error"), root))
        val missing = call("baReadRegMulti", key, LString("Data0"), LString("error"), root) as LList
        assertEquals(listOf<LingoValue>(LString("error")), missing.items)
        call("baWriteRegMulti", key, LString("Data0"), LList(mutableListOf(LString("Ann"), LString("Bo"))), root)
        assertEquals(listOf<LingoValue>(LString("Ann"), LString("Bo")), (call("baReadRegMulti", key, LString("Data0"), LString("error"), root) as LList).items)
        call("baDeleteReg", key, LString("Prefs"), root)
        assertEquals(LString("error"), call("baReadRegString", key, LString("Prefs"), LString("error"), root))
    }

    @Test fun buddy_files_screen_and_windows() {
        assertEquals(LInt(50_000), call("baFileSize", LString("C:\\Game\\data\\01.cct")))
        assertEquals(LInt(-1), call("baFileSize", LString("C:\\Game\\data\\02.cct")))
        assertEquals(LingoValue.FALSE, call("baFileExists", LString("C:\\Game\\dist.jpg")))
        assertEquals(LInt(1280), call("baScreenInfo", LString("width")))
        assertEquals(LInt(32), call("baScreenInfo", LString("depth")))
        assertEquals(LString("normal"), call("baWindowInfo", LInt(1), LString("state")))
        assertEquals(LInt(1), call("baSetDisplay", LInt(800), LInt(600), LInt(32), LString("temp"), LInt(1)))
    }

    @Test fun fileio_reads_writes_and_rejects_mac_paths_on_windows() {
        val file = call("new", call("xtra", LString("fileio")))
        fun io(method: String, vararg args: LingoValue) = vm.callMethod(file, method, listOf(file) + args)
        io("openFile", LString("C:\\Game\\save.txt"), LInt(0))
        assertEquals(LInt(-43), call("status", file))
        io("createFile", LString("C:\\Game\\save.txt"))
        io("openFile", LString("C:\\Game\\save.txt"), LInt(0))
        io("writeString", LString("line1\rline2"))
        io("closeFile")
        io("openFile", LString("c:/game/SAVE.txt"), LInt(0))
        assertEquals(LInt(0), io("status"))
        assertEquals(LString("line1\r"), io("readLine"))
        assertEquals(LString("line2"), io("readFile"))
        assertEquals(LInt(11), io("getLength"))
        assertEquals(LString("File not found"), io("error", LInt(-43)))
        io("createFile", LString("C:\\Game\\data:pData:pPref.dat"))
        assertEquals(LInt(-37), io("status"))
        io("delete")
        io("openFile", LString("C:\\Game\\save.txt"), LInt(0))
        assertEquals(LInt(-43), io("status"))
    }

    @Test fun enhancer_and_offline_network() {
        val enhancer = vm.callMethod(call("xtra", LString("enhancer")), "new", listOf(call("xtra", LString("enhancer")), LString("key")))
        // xtra("x").new() is not a method of the reference: titles use new(xtra("x"), ...).
        assertNull(enhancer)
        val instance = call("new", call("xtra", LString("enhancer")), LString("key"))
        val modes = vm.callMethod(instance, "get_display_modes", listOf(instance)) as LList
        assertEquals(LList(mutableListOf(LInt(800), LInt(600), LInt(32))).items, (modes.items[1] as LList).items)
        assertEquals(LingoValue.Void, vm.callMethod(instance, "hide_menuanddock", listOf(instance)))
        val id = call("downloadNetThing", LString("http://example.invalid/01.cct"), LString("C:\\Game\\01.cct"))
        assertEquals(LingoValue.TRUE, call("netDone", id))
        val status = call("getStreamStatus", id) as LPropList
        assertEquals(LString("http://example.invalid/01.cct"), status.entries.first { it.first == LSymbol("URL") }.second)
        assertEquals(LString("noNetwork"), status.entries.first { it.first == LSymbol("error") }.second)
        assertThrows(LingoError::class.java) { call("new", call("xtra", LString("multiuser"))) }
        assertNull(xtras.callBuiltin(vm, "somethingElse", emptyList()))
    }
}
