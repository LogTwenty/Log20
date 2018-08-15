package ca.utoronto.dsrg;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;
import java.util.Map.Entry;

import soot.Body;
import soot.BodyTransformer;
import soot.IntType;
import soot.Local;
import soot.LongType;
import soot.PackManager;
import soot.PatchingChain;
import soot.RefType;
import soot.Scene;
import soot.SootClass;
import soot.SootMethod;
import soot.Transform;
import soot.Unit;
import soot.jimple.GotoStmt;
import soot.jimple.IdentityStmt;
import soot.jimple.IfStmt;
import soot.jimple.IntConstant;
import soot.jimple.InvokeExpr;
import soot.jimple.Jimple;
import soot.jimple.ReturnStmt;
import soot.jimple.ReturnVoidStmt;
import soot.jimple.Stmt;
import soot.jimple.ThrowStmt;
import soot.toolkits.graph.Block;
import soot.toolkits.graph.BlockGraph;
import soot.toolkits.graph.BriefBlockGraph;

public class TracingInstrumentor {
	private static Object gm_methodSigToIdLock = new Object();
	private static int gm_nextMethodId = 0;
	private static HashMap<String, Integer> gm_methodSigToId = new HashMap<>();
	private static LinkedList<String> gm_bbPropStrs = new LinkedList<>();
	
	public static BlockGraph createBlockGraph (Body body) {
		return new BriefBlockGraph(body);
	}
	
	public static String getBBPropString (String methodSig, Block b) {
		// Calculate categorized number of logs in block
		int numTrace = 0;
		int numDebug = 0;
		int numInfo = 0;
		int numWarn = 0;
		int numError = 0;
		int numFatal = 0;
		for (Iterator<Unit> unitIx = b.iterator(); unitIx.hasNext(); ) {
			Unit u = unitIx.next();
			Stmt s = (Stmt)u;
			if (s.containsInvokeExpr()) {
				String invokedName = s.getInvokeExpr().getMethod().getName();
				if (invokedName.equals("trace")) ++numTrace;
				else if (invokedName.equals("debug")) ++numDebug;
				else if (invokedName.equals("info")) ++numInfo;
				else if (invokedName.equals("warn")) ++numWarn;
				else if (invokedName.equals("error")) ++numError;
				else if (invokedName.equals("fatal")) ++numFatal;
			}
		}
		
		// Create property string
		StringBuffer strBuilder = new StringBuffer();
		strBuilder.append(methodSig);
		strBuilder.append('\t');
		strBuilder.append(b.getIndexInMethod());
		strBuilder.append('\t');
		strBuilder.append(numTrace);
		strBuilder.append('\t');
		strBuilder.append(numDebug);
		strBuilder.append('\t');
		strBuilder.append(numInfo);
		strBuilder.append('\t');
		strBuilder.append(numWarn);
		strBuilder.append('\t');
		strBuilder.append(numError);
		strBuilder.append('\t');
		strBuilder.append(numFatal);
		strBuilder.append('\t');
		strBuilder.append(b.getHead().getJavaSourceStartLineNumber());
		strBuilder.append('\t');
		strBuilder.append(b.getTail().getJavaSourceStartLineNumber());
		strBuilder.append('\t');
		for (Iterator<Block> predIx = b.getPreds().iterator(); predIx.hasNext(); ) {
			Block pred = predIx.next();
			strBuilder.append(pred.getIndexInMethod());
			strBuilder.append(',');
		}
		strBuilder.append('\t');
		for (Iterator<Block> succIx = b.getPreds().iterator(); succIx.hasNext(); ) {
			Block succ = succIx.next();
			strBuilder.append(succ.getIndexInMethod());
			strBuilder.append(',');
		}
		
		return strBuilder.toString();
	}
	
	public static void main(String[] args) {
		if (args.length < 7) {
			System.err.println("Usage: java -cp <soot classpath> ca.utoronto.dsrg.SootInstrumEx " +
					"<analysis classpath> " + 
					"<package inclusion list>" +
					"<package exclusion list>" +
					"<request entry point list>" +
					"<method ID start> " +
					"<class path list> " +
					"<output dir>");
			System.exit(0);
		}
		int argIx = 0;
		String anaClassPath = args[argIx++];
		String packInclPath = args[argIx++];
		String packExclPath = args[argIx++];
		String reqEntryPointsPath = args[argIx++];
		int methodIdStart = Integer.parseInt(args[argIx++]);
		gm_nextMethodId = methodIdStart;
		String classPathPath = args[argIx++];
		String outputDir = args[argIx++];
		
		LinkedList<String> includedPackages = new LinkedList<>();
		LinkedList<String> excludedPackages = new LinkedList<>();
		final HashSet<String> reqEntryPoints = new HashSet<>();
		final HashSet<String> foundEntryPoints = new HashSet<>();
		LinkedList<String> classNames = new LinkedList<>();
		FileReader fReader;
		BufferedReader bReader;
		try {
			String line;
			
			fReader = new FileReader(packInclPath);
			bReader = new BufferedReader(fReader);
			while ((line = bReader.readLine()) != null) {
				// Skip empty or comment lines
				if (line.trim().equals("") || '#' == line.trim().charAt(0)) continue;
	
				if (line.contains("*")) {
					System.err.println("ERROR Invalid '*' in included package " + line);
					System.exit(-1);
				}
				includedPackages.add(line);
			}
			bReader.close();
			fReader.close();
			
			fReader = new FileReader(packExclPath);
			bReader = new BufferedReader(fReader);
			while ((line = bReader.readLine()) != null) {
				// Skip empty or comment lines
				if (line.trim().equals("") || '#' == line.trim().charAt(0)) continue;
	
				if (line.contains("*")) {
					System.err.println("ERROR Invalid '*' in excluded package " + line);
					System.exit(-1);
				}
				excludedPackages.add(line);
			}
			bReader.close();
			fReader.close();
			
			fReader = new FileReader(reqEntryPointsPath);
			bReader = new BufferedReader(fReader);
			while ((line = bReader.readLine()) != null) {
				// Skip empty or comment lines
				if (line.trim().equals("") || '#' == line.trim().charAt(0)) continue;
	
				reqEntryPoints.add(line);
			}
			bReader.close();
			fReader.close();
			
			fReader = new FileReader(classPathPath);
			bReader = new BufferedReader(fReader);
			while ((line = bReader.readLine()) != null) {
				// Skip empty or comment lines
				if (line.trim().equals("") || '#' == line.trim().charAt(0)) continue;
				classNames.add(line.replace('/', '.').substring(0, line.length() - 6));
			}
			bReader.close();
			fReader.close();
		} catch (FileNotFoundException fNotFoundEx) {
			System.err.println(fNotFoundEx.getMessage());
			System.exit(-1);
		} catch (IOException ioEx) {
			System.err.println(ioEx.getMessage());
			System.exit(-1);
		}
		
		// Remove classes outside scope of includes and excludes
		for (Iterator<String> classNameIx = classNames.iterator(); classNameIx.hasNext(); ) {
			String className = classNameIx.next();

			boolean matchedClass = false;
			for (Iterator<String> includedPackageIx = includedPackages.iterator(); includedPackageIx.hasNext(); ) {
				String includedPackage = includedPackageIx.next();
				if (className.startsWith(includedPackage)) {
					matchedClass = true;
					break;
				}
			}
			if (includedPackages.size() > 0 && !matchedClass) {
				classNameIx.remove();
				continue;
			}
			
			matchedClass = false;
			for (Iterator<String> excludedPackageIx = excludedPackages.iterator(); excludedPackageIx.hasNext(); ) {
				String excludedPackage = excludedPackageIx.next();
				if (className.startsWith(excludedPackage)) {
					matchedClass = true;
					break;
				}
			}
			if (matchedClass) {
				classNameIx.remove();
				continue;
			}
		}
		
		System.err.println("INFO " + classNames.size() + " classes to transform");
		
		LinkedList<String> sootArgs = new LinkedList<>();
		sootArgs.add("-allow-phantom-refs");
		sootArgs.add("-keep-line-number");
		sootArgs.add("-asm-backend");
		sootArgs.add("-cp");
		sootArgs.add(anaClassPath);
		sootArgs.add("-output-dir");
		sootArgs.add(outputDir);
		
		Transform instrumentation = new Transform("jtp.myTrans", new BodyTransformer() {
			@Override
			protected void internalTransform (Body body, String phase, Map options) {
				SootMethod method = body.getMethod();
				
				// FIXME: Workaround for StackOverflowError exception in Zookeeper after instrumentation
				if (method.getDeclaringClass().getName().equals("org.apache.zookeeper.server.quorum.QuorumPeer")) {
					return;
				}
				
				// Get or assign method ID
				String methodSig = method.getSignature();
				Integer methodId;
				synchronized(gm_methodSigToIdLock) {
					methodId = gm_methodSigToId.get(methodSig);
					if (null == methodId) {
						methodId = gm_nextMethodId;
						++gm_nextMethodId;
						gm_methodSigToId.put(methodSig, methodId);
					}
				}
				
				// Add locals
				Local currThreadRef = Jimple.v().newLocal("currThreadRef", RefType.v("java.lang.Thread"));
				body.getLocals().add(currThreadRef);
				Local currThreadId = Jimple.v().newLocal("currThreadId", LongType.v());
				body.getLocals().add(currThreadId);
				Local currThreadIdStr = Jimple.v().newLocal("currThreadIdStr", RefType.v("java.lang.String"));
				body.getLocals().add(currThreadIdStr);
				Local logPointId = Jimple.v().newLocal("logPointId", IntType.v());
				body.getLocals().add(logPointId);
				
				// Construct currThreadRef = Thread.currentThread()
				SootMethod currThreadMethod = Scene.v().getMethod("<java.lang.Thread: java.lang.Thread currentThread()>");
				InvokeExpr currThreadExpr = Jimple.v().newStaticInvokeExpr(currThreadMethod.makeRef());
				Unit setCurrThreadRef = Jimple.v().newAssignStmt(currThreadRef, currThreadExpr);
				
				// Construct currThreadId = currentThreadRef.getId()
				SootMethod getIdMethod = Scene.v().getMethod("<java.lang.Thread: long getId()>");
				InvokeExpr getIdExpr = Jimple.v().newVirtualInvokeExpr(currThreadRef, getIdMethod.makeRef());
				Unit setCurrThreadId = Jimple.v().newAssignStmt(currThreadId, getIdExpr);
				
				// Construct currThreadIdStr = Integer.toString(currThreadId)
				SootMethod longToStrMethod = Scene.v().getMethod("<java.lang.Long: java.lang.String toString(long)>");
				InvokeExpr longToStrExpr = Jimple.v().newStaticInvokeExpr(longToStrMethod.makeRef(), currThreadId);
				Unit setCurrThreadIdStr = Jimple.v().newAssignStmt(currThreadIdStr, longToStrExpr);
				
				PatchingChain<Unit> units = body.getUnits();
				
				// Add trace call to end of every BB
				SootMethod bbTraceMethod = Scene.v().getMethod(
						"<ca.utoronto.dsrg.RequestInstrumentationMultiThreadScheduler: " + 
						"void insertBasicBlockTraceToBuffer(long,int)>");
				BlockGraph blockGraph = createBlockGraph(body);
				Iterator<Block> blocksIx = blockGraph.getBlocks().iterator();
				while (blocksIx.hasNext()) {
					Block block = blocksIx.next();
					
					// Generate block properties string
					String bbPropStr = getBBPropString(methodSig, block);
					synchronized (gm_bbPropStrs) {
						gm_bbPropStrs.add(bbPropStr);
					}
					
					// Calculate log point ID
					int realLogPointId = 0;
					realLogPointId |= methodId;
					realLogPointId = (realLogPointId << 1) | 0;
					realLogPointId = (realLogPointId << 12) | block.getIndexInMethod();
					
					// Construct logPointId = <realLogPointId>
					Unit setLogPointId = Jimple.v().newAssignStmt(logPointId, IntConstant.v(realLogPointId));
					// Construct bbTrace(currThreadId, logPointId)
					Unit callBBTrace = Jimple.v().newInvokeStmt(Jimple.v().newStaticInvokeExpr(bbTraceMethod.makeRef(), 
							currThreadId, logPointId));
					
					boolean insertAfterTail = false;
					Unit tail = block.getTail();
					if (!(tail instanceof IfStmt) && 
							!(tail instanceof ThrowStmt) && 
							!(tail instanceof ReturnVoidStmt) &&
							!(tail instanceof ReturnStmt) &&
							!(tail instanceof GotoStmt))
					{
						insertAfterTail = true;
					}
					if (!insertAfterTail) {
						units.insertBefore(setLogPointId, tail);
						units.insertBefore(callBBTrace, tail);
					} else {
						units.insertAfter(callBBTrace, tail);
						units.insertAfter(setLogPointId, tail);
					}
				}
				
				// NOTE: We insert method trace calls after all blocks are instrumented since the method traces need to 
				// encapsulate the BB traces
				SootMethod beginTraceMethod;
				SootMethod endTraceMethod;
				
				if (reqEntryPoints.contains(methodSig)) {
					foundEntryPoints.add(methodSig);
					beginTraceMethod = Scene.v().getMethod(
							"<ca.utoronto.dsrg.RequestInstrumentationMultiThreadScheduler: " + 
							"void insertTopLevelMethodBeginTraceToBuffer(long,int,int)>");
					endTraceMethod = Scene.v().getMethod(
							"<ca.utoronto.dsrg.RequestInstrumentationMultiThreadScheduler: " + 
							"void insertTopLevelMethodEndTraceToBuffer(long,int)>");
				} else {
					beginTraceMethod = Scene.v().getMethod(
							"<ca.utoronto.dsrg.RequestInstrumentationMultiThreadScheduler: " + 
							"void insertMethodBeginTraceToBuffer(long,int,int)>");
					endTraceMethod = Scene.v().getMethod(
							"<ca.utoronto.dsrg.RequestInstrumentationMultiThreadScheduler: " + 
							"void insertMethodEndTraceToBuffer(long,int)>");
				}
				// Construct methodTraceBegin(currThreadId, <methodId>, <blockGraph.size()>)
				Unit callMethodTraceBegin = Jimple.v().newInvokeStmt(
						Jimple.v().newStaticInvokeExpr(beginTraceMethod.makeRef(), currThreadId, 
								IntConstant.v(methodId), IntConstant.v(blockGraph.size())));
				
				// Insert constructed units after first batch of IdentityStmts (start of method)
				Unit beforeInsertPoint = null;
				for (Iterator<Unit> unitIx = units.iterator(); unitIx.hasNext(); ) {
					Unit unit = unitIx.next();
					if (!(unit instanceof IdentityStmt)) break;
					beforeInsertPoint = unit;
				}
				if (null == beforeInsertPoint) {
					units.addFirst(callMethodTraceBegin);
					units.addFirst(setCurrThreadIdStr);
					units.addFirst(setCurrThreadId);
					units.addFirst(setCurrThreadRef);
				} else {
					units.insertAfter(callMethodTraceBegin, beforeInsertPoint);
					units.insertAfter(setCurrThreadIdStr, beforeInsertPoint);
					units.insertAfter(setCurrThreadId, beforeInsertPoint);
					units.insertAfter(setCurrThreadRef, beforeInsertPoint);
				}
				
				// Insert methodTraceEnd before every return
				// NOTE: Thrown exceptions are unhandled
				for (Iterator<Unit> unitIx = units.snapshotIterator(); unitIx.hasNext(); ) {
					Unit unit = unitIx.next();
					if (unit instanceof ReturnVoidStmt || unit instanceof ReturnStmt) {
						// Construct methodTraceEnd(currThreadId, <methodId>)
						Unit callMethodTraceEnd = Jimple.v().newInvokeStmt(
								Jimple.v().newStaticInvokeExpr(endTraceMethod.makeRef(), currThreadId, 
										IntConstant.v(methodId)));
						units.insertBefore(callMethodTraceEnd, unit);
					}
				}
			}
		});
		
		// /dev/null output stream for soot output
		PrintStream devNull = null;
		try {
			devNull = new PrintStream(new File("/dev/null"));
		} catch (FileNotFoundException fnfe) {
			System.err.println("ERROR " + fnfe.getMessage());
			System.exit(-1);
		}

//		// Run Soot for each class
//		String[] sootArgsArray = new String[sootArgs.size() + 1];
//		int i = 0;
//		for (Iterator<String> sootArgIx = sootArgs.iterator(); sootArgIx.hasNext(); ) {
//			String sootArg = sootArgIx.next();
//			sootArgsArray[i] = sootArg;
//			++i;
//		}
//		for (Iterator<String> classPathIx = classNames.iterator(); classPathIx.hasNext(); ) {
//			String classPath = classPathIx.next();
//			
//			System.err.println("DEBUG Transforming " + classPath);
//			
//			// Reset between runs to ensure classes don't influence each other
//			soot.G.reset();
//			
//			soot.G.v().out = devNull;
//			
//			// Add classes referenced by transformer to scene since they are phantom otherwise
//			Scene.v().addBasicClass("java.lang.Thread", SootClass.SIGNATURES);
//			Scene.v().addBasicClass("ca.utoronto.dsrg.RequestInstrumentationMultiThreadScheduler", 
//					SootClass.SIGNATURES);
//			PackManager.v().getPack("jtp").add(instrumentation);
//			sootArgsArray[i] = classPath;
//			soot.Main.main(sootArgsArray);
//		}
		
		
		// Add classes referenced by transformer to scene since they are phantom otherwise
		Scene.v().addBasicClass("java.lang.Thread", SootClass.SIGNATURES);
		Scene.v().addBasicClass("ca.utoronto.dsrg.RequestInstrumentationMultiThreadScheduler", 
				SootClass.SIGNATURES);
		PackManager.v().getPack("jtp").add(instrumentation);
		
		// Run Soot for each class
		String[] sootArgsArray = new String[sootArgs.size() + classNames.size()];
		int i = 0;
		for (Iterator<String> sootArgIx = sootArgs.iterator(); sootArgIx.hasNext(); ) {
			String sootArg = sootArgIx.next();
			sootArgsArray[i] = sootArg;
			++i;
		}
		for (Iterator<String> classPathIx = classNames.iterator(); classPathIx.hasNext(); ) {
			String classPath = classPathIx.next();
			
			sootArgsArray[i] = classPath;
			++i;
		}
		
		soot.G.v().out = devNull;
		
		soot.Main.main(sootArgsArray);
		
		// Print entry points found in the given classes
		for (Iterator<String> pointIx = foundEntryPoints.iterator(); pointIx.hasNext(); ) {
			String point = pointIx.next();
			System.out.println(point);
		}
		
		// Write out method signature to ID map
		try {
			BufferedWriter methodSigsWriter = new BufferedWriter(
					new OutputStreamWriter(
							new FileOutputStream("MethodSignatureMapping.log", true), "utf-8"
					)
			);
			if (gm_methodSigToId.isEmpty()) System.err.println("ERROR No method to IDs");
			for (Iterator<Entry<String, Integer>> entryIx = gm_methodSigToId.entrySet().iterator(); entryIx.hasNext(); )
			{
				Entry<String, Integer> entry = entryIx.next();
				methodSigsWriter.write("MethodSignatureHashList[" + entry.getValue() + "]:" + entry.getKey());
				methodSigsWriter.newLine();
			}
			methodSigsWriter.write(Integer.toString(gm_nextMethodId));
			methodSigsWriter.close();
		} catch (UnsupportedEncodingException uee) {
			System.err.println("ERROR " + uee.getMessage());
			System.exit(-1);
		} catch (FileNotFoundException fnfe) {
			System.err.println("ERROR " + fnfe.getMessage());
			System.exit(-1);
		} catch (IOException ioe) {
			System.err.println("ERROR " + ioe.getMessage());
			System.exit(-1);
		}
		
		// Write out block properties
		try {
			BufferedWriter bbPropsWriter = new BufferedWriter(
					new OutputStreamWriter(
							new FileOutputStream("BBProperties.log", true), "utf-8"
					)
			);
			
			for (Iterator<String> propStrIx = gm_bbPropStrs.iterator(); propStrIx.hasNext(); ) {
				String propStr = propStrIx.next();
				bbPropsWriter.write(propStr);
				bbPropsWriter.newLine();
			}
			
			bbPropsWriter.close();
		} catch (UnsupportedEncodingException uee) {
			System.err.println("ERROR " + uee.getMessage());
			System.exit(-1);
		} catch (FileNotFoundException fnfe) {
			System.err.println("ERROR " + fnfe.getMessage());
			System.exit(-1);
		} catch (IOException ie) {
			System.err.println("ERROR " + ie.getMessage());
			System.exit(-1);
		}
	}
}
