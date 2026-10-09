// Bounded listing from an existing Ghidra database. No imports or program mutations.
// @category CaseRecomp.Research
import ghidra.app.script.GhidraScript;
import ghidra.program.model.listing.*;
import ghidra.program.model.address.*;
import ghidra.program.model.symbol.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
public class ExportStoredInstructions extends GhidraScript {
 public void run() throws Exception {
  String[] args=getScriptArgs();
  if(args.length<2) throw new IllegalArgumentException("output directory and function roots required");
  File out=new File(args[0]); out.mkdirs();
  String[] roots=String.join(",", Arrays.copyOfRange(args,1,args.length)).split(",");
  if(roots.length>12) throw new IllegalArgumentException("root budget exceeded");
  for(String root:roots) {
   Function f=currentProgram.getFunctionManager().getFunctionAt(toAddr(root));
   if(f==null) throw new IllegalArgumentException("missing function "+root);
   Set<Address> constants=new LinkedHashSet<>();
   try(PrintWriter w=new PrintWriter(new OutputStreamWriter(new FileOutputStream(new File(out,root+".asm.txt")),StandardCharsets.UTF_8))) {
    InstructionIterator iter=currentProgram.getListing().getInstructions(f.getBody(),true);
    int count=0;
    while(iter.hasNext()) {
     if(++count>3000) throw new IllegalArgumentException("instruction budget exceeded");
     Instruction inst=iter.next(); w.println(inst.getAddress()+"\t"+inst);
     for(Reference ref:inst.getReferencesFrom()) {
      Address addr=ref.getToAddress();
      if(ref.getReferenceType().isData() && addr.isMemoryAddress() && currentProgram.getMemory().contains(addr)) constants.add(addr);
     }
    }
    for(Address addr:constants) {
     if(currentProgram.getMemory().getBlock(addr).isExecute()) continue;
     byte[] bytes=new byte[16]; int size=currentProgram.getMemory().getBytes(addr,bytes);
     StringBuilder hex=new StringBuilder();
     for(int i=0;i<size;i++) hex.append(String.format("%02x",bytes[i]&255));
     w.println("DATA\t"+addr+"\t"+hex);
    }
    println("exported stored instructions: "+root+" count="+count);
   }
  }
 }
}
