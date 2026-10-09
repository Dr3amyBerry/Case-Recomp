// Export static references and decompile a bounded dependency slice. No target code is executed.
// @category CaseRecomp.Research
import ghidra.app.script.GhidraScript;
import ghidra.app.decompiler.*;
import ghidra.program.model.listing.*;
import ghidra.program.model.symbol.*;
import ghidra.program.model.address.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class ExportNativeSlice extends GhidraScript {
    public void run() throws Exception {
        String[] args = getScriptArgs();
        if (args.length < 1) throw new IllegalArgumentException("output directory and optional comma-separated roots required");
        boolean focused = args.length > 1;
        boolean exactOnly = focused && Arrays.stream(Arrays.copyOfRange(args,1,args.length))
            .flatMap(a -> Arrays.stream(a.split(","))).allMatch(a -> a.startsWith("only:"));
        if (exactOnly && String.join(",",Arrays.copyOfRange(args,1,args.length)).split(",").length > 12)
            throw new IllegalArgumentException("exact function budget exceeded");
        File out = new File(args[0]); out.mkdirs();
        LinkedHashSet<Function> selected = new LinkedHashSet<>();
        try (PrintWriter w = writer(new File(out, "references.tsv"))) {
            w.println("kind\tname\taddress\treference\tfunction");
            SymbolIterator symbols = currentProgram.getSymbolTable().getAllSymbols(true);
            while (symbols.hasNext() && !monitor.isCancelled()) {
                Symbol s = symbols.next();
                String n = s.getName();
                if (!n.matches("(?i).*(LoadLibrary|FindResource|LoadResource|LockResource|SizeofResource|GetProcAddress|DispatchMessage|PeekMessage|RegisterClass|CreateWindow|BASS_|SDL_|XML|XUI).*")) continue;
                for (Reference ref : s.getReferences()) {
                    Function f = currentProgram.getFunctionManager().getFunctionContaining(ref.getFromAddress());
                    w.println("symbol\t" + clean(n) + "\t" + s.getAddress() + "\t" + ref.getFromAddress() + "\t" + (f == null ? "" : f.getEntryPoint()));
                    if (!focused && f != null && !f.isExternal() && selected.size()<600) selected.add(f);
                }
            }
            DataIterator data = currentProgram.getListing().getDefinedData(true);
            while (data.hasNext() && !monitor.isCancelled()) {
                Data d = data.next(); if (!d.hasStringValue()) continue;
                String value = String.valueOf(d.getValue());
                if (!value.matches("(?is).*(resources\\.dll|\\.xui|sda|mpi:|component|event|kerning|font|animation|scene).*")) continue;
                ReferenceIterator refs = currentProgram.getReferenceManager().getReferencesTo(d.getAddress());
                while (refs.hasNext()) {
                    Reference ref = refs.next();
                    Function f = currentProgram.getFunctionManager().getFunctionContaining(ref.getFromAddress());
                    w.println("string\t" + clean(value) + "\t" + d.getAddress() + "\t" + ref.getFromAddress() + "\t" + (f == null ? "" : f.getEntryPoint()));
                    if (!focused && f != null && !f.isExternal() && selected.size()<600) selected.add(f);
                }
            }
        }
        // Include direct dependencies; full export is separately bounded to 10,000 functions.
        // Vtable roots expose candidate pointer runs; adjacent tables require manual boundary checks.
        if (focused) {
            for (String root : Arrays.copyOfRange(args, 1, args.length)) for (String entry : root.split(",")) {
                if (entry.startsWith("only:")) {
                    if (!exactOnly) throw new IllegalArgumentException("only roots cannot mix with dependency modes");
                    String address=entry.substring(5);
                    if(!address.matches("[0-9a-fA-F]{8,16}")) throw new IllegalArgumentException("hex address required");
                    Function f=currentProgram.getFunctionManager().getFunctionAt(toAddr(address));
                    if(f==null || f.isExternal()) throw new IllegalArgumentException("no stored function at "+address);
                    selected.add(f);
                } else if (entry.equals("all")) {
                    FunctionIterator all=currentProgram.getFunctionManager().getFunctions(true);
                    while(all.hasNext()) {
                        Function f=all.next();
                        if (!f.isExternal()) selected.add(f);
                        if (selected.size()>10000) throw new IllegalArgumentException("full export exceeds function budget");
                    }
                } else if (entry.startsWith("vtable:")) {
                    Address start=toAddr(entry.substring(7));
                    try(PrintWriter w=writer(new File(out,"vtable-"+start+".tsv"))) {
                        w.println("slot\taddress\tfunction");
                        for(int slot=0;slot<64;slot++) {
                            Address target=toAddr(Integer.toUnsignedLong(currentProgram.getMemory().getInt(start.add(slot*4L))));
                            Function f=currentProgram.getFunctionManager().getFunctionAt(target);
                            if(f==null) break;
                            w.println(slot+"\t"+target+"\t"+clean(f.getName())); selected.add(f);
                        }
                    }
                } else if (entry.startsWith("discover:")) {
                    Address address=toAddr(entry.substring(9));
                    if(address==null) throw new IllegalArgumentException("invalid discovery address: "+entry);
                    if (currentProgram.getMemory().getBlock(address)==null ||
                        !currentProgram.getMemory().getBlock(address).isExecute())
                        throw new IllegalArgumentException("discovery root is not executable: "+address);
                    Function f=currentProgram.getFunctionManager().getFunctionContaining(address);
                    if(f!=null && !f.getEntryPoint().equals(address))
                        throw new IllegalArgumentException("discovery root overlaps existing function: "+address);
                    if(f==null) {
                        if(!disassemble(address)) throw new IllegalArgumentException("cannot disassemble "+address);
                        f=createFunction(address,null);
                    }
                    if(f==null) throw new IllegalArgumentException("cannot define function at "+address);
                    selected.add(f);
                } else {
                    Address address=toAddr(entry);
                    if(address==null) throw new IllegalArgumentException("invalid root address: "+entry);
                    Function f = currentProgram.getFunctionManager().getFunctionAt(address);
                    if (f == null) throw new IllegalArgumentException("no function at " + entry);
                    selected.add(f);
                }
            }
            for (int depth=0; !exactOnly && depth<3; depth++) {
                for (Function f:new ArrayList<>(selected)) {
                    for (Function other:f.getCalledFunctions(monitor))
                        if (!other.isExternal() && selected.size()<600) selected.add(other);
                }
            }
        }
        try (PrintWriter w = writer(new File(out,"functions.tsv"))) {
            w.println("address\tname\tsize\tcalling_convention");
            FunctionIterator all = currentProgram.getFunctionManager().getFunctions(true);
            while (all.hasNext()) {
                Function f=all.next(); w.println(f.getEntryPoint()+"\t"+clean(f.getName())+"\t"+f.getBody().getNumAddresses()+"\t"+f.getCallingConventionName());
            }
        }
        List<Function> seeds = new ArrayList<>(selected);
        if (!exactOnly) for (Function f : seeds) {
            if (selected.size() >= 600) break;
            for (Function other : f.getCalledFunctions(monitor)) if (!other.isExternal() && selected.size()<600) selected.add(other);
            for (Function other : f.getCallingFunctions(monitor)) if (!other.isExternal() && selected.size()<600) selected.add(other);
        }
        DecompInterface decomp = new DecompInterface();
        try {
            if (!decomp.openProgram(currentProgram)) throw new IOException(decomp.getLastMessage());
            try (PrintWriter calls=writer(new File(out,"calls.tsv")); PrintWriter status=writer(new File(out,"decompile-status.tsv"))) {
                calls.println("caller\tcallee\tname");status.println("address\tcompleted\terror");
                for (Function f : selected) {
                    if (monitor.isCancelled()) break;
                    for (Function other:f.getCalledFunctions(monitor)) calls.println(f.getEntryPoint()+"\t"+other.getEntryPoint()+"\t"+clean(other.getName()));
                    DecompileResults result=decomp.decompileFunction(f,15,monitor);
                    status.println(f.getEntryPoint()+"\t"+result.decompileCompleted()+"\t"+clean(result.getErrorMessage()));
                    if (result.decompileCompleted()) try(PrintWriter w=writer(new File(out,f.getEntryPoint()+".c"))) {w.print(result.getDecompiledFunction().getC());}
                }
            }
        } finally {decomp.dispose();}
        println("exported static slice: "+selected.size()+" functions to "+out);
    }
    private PrintWriter writer(File file) throws IOException {return new PrintWriter(new OutputStreamWriter(new FileOutputStream(file),StandardCharsets.UTF_8));}
    private String clean(String s) {return s==null ? "" : s.replace('\t',' ').replace('\n',' ').replace('\r',' ');}
}
