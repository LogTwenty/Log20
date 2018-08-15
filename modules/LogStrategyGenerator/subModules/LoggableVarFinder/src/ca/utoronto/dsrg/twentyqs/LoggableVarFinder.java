package ca.utoronto.dsrg.twentyqs;

import soot.*;
import soot.jimple.BinopExpr;
import soot.jimple.Constant;
import soot.jimple.IfStmt;
import soot.jimple.InvokeExpr;
import soot.options.Options;
import soot.shimple.Shimple;
import soot.shimple.ShimpleBody;
import soot.toolkits.graph.*;
import soot.toolkits.scalar.GuaranteedDefs;

import java.io.*;
import java.util.*;

public final class LoggableVarFinder {
	static private HashMap<String, ArrayList<LogDisambiguation>> gm_methodSigToDisambiguations = new HashMap<>();

	private LoggableVarFinder () {}

	static private void m_findBranchVarsBetweenBlocks (BlockGraph blockGraph, Block blockA, Block blockB,
			HashSet<Value> branchVars)
	{
		// BFS from dominated to dominator
		HashSet<Block> visitedBlocks = new HashSet<>();
		LinkedList<Block> bfsQueue = new LinkedList<>();
		bfsQueue.addAll(blockGraph.getPredsOf(blockB));
		while (!bfsQueue.isEmpty()) {
			Block queued = bfsQueue.pop();
			if (visitedBlocks.contains(queued)) continue;
			visitedBlocks.add(queued);

			Unit tail = queued.getTail();
			if (tail instanceof IfStmt) {
				IfStmt ifStmt = (IfStmt)tail;
				BinopExpr binopExpr = (BinopExpr)ifStmt.getCondition();

				Value op1 = binopExpr.getOp1();
				if (!(op1 instanceof Constant)) {
					if (op1 instanceof InvokeExpr) {
						System.err.println("ERROR " + op1.toString() + " is not a variable");
					} else {
						branchVars.add(op1);
					}
				}
				Value op2 = binopExpr.getOp2();
				if (!(op2 instanceof Constant)) {
					if (op2 instanceof InvokeExpr) {
						System.err.println("ERROR " + op2.toString() + " is not a variable");
					} else {
						branchVars.add(op2);
					}
				}
			}

			if (!blockA.equals(queued)) {
				bfsQueue.addAll(blockGraph.getPredsOf(queued));
			}
		}
	}

	static private ArrayList<LogDisambiguation> m_calcDismabiguations (Body body) {
		ShimpleBody shimpBody = Shimple.v().newBody(body);
		BriefBlockGraph blockGraph = new BriefBlockGraph(shimpBody);
		DominatorTree<Block> domTree = new DominatorTree<>(new MHGDominatorsFinder<Block>(blockGraph));
		DominatorTree<Block> postDomTree = new DominatorTree<>(new MHGPostDominatorsFinder<Block>(blockGraph));
		GuaranteedDefs guaranteedDefs = new GuaranteedDefs(new BriefUnitGraph(shimpBody));

		ArrayList<LogDisambiguation> blockDisambiguations = new ArrayList<>(blockGraph.size());
		for (Iterator<Block> blockIx = blockGraph.iterator(); blockIx.hasNext(); ) {
			Block block = blockIx.next();
			HashSet<Value> headDefs = new HashSet<>(guaranteedDefs.getGuaranteedDefs(block.getHead()));
			HashSet<Value> tailDefs = new HashSet<>(guaranteedDefs.getGuaranteedDefs(block.getTail()));

			LogDisambiguation disambiguation = new LogDisambiguation();
			blockDisambiguations.add(block.getIndexInMethod(), disambiguation);

			// Find all blocks that can be disambiguated by logging variables in this block
			DominatorNode<Block> blockDode = domTree.getDode(block);
			for (Iterator<DominatorNode<Block>> dominatedDodeIx = domTree.getChildrenOf(blockDode).iterator();
					dominatedDodeIx.hasNext(); )
			{
				DominatorNode<Block> dominatedDode = dominatedDodeIx.next();
				Block dominatedBlock = dominatedDode.getGode();

				// Find variables in all branches between block and dominated block
				HashSet<Value> branchVars = new HashSet<>();
				m_findBranchVarsBetweenBlocks(blockGraph, block, dominatedBlock, branchVars);

				// Determine if all variables are alive in block
				if (tailDefs.containsAll(branchVars)) {
					disambiguation.blockIds.add(dominatedBlock.getIndexInMethod());
					for (Iterator<Value> branchVarIx = branchVars.iterator(); branchVarIx.hasNext(); ) {
						Value branchVar = branchVarIx.next();
						disambiguation.varNames.add(branchVar.toString());
					}
				}
			}

			DominatorNode<Block> postBlockDode = postDomTree.getDode(block);
			for (Iterator<DominatorNode<Block>> dominatedDodeIx = postDomTree.getChildrenOf(postBlockDode).iterator();
					dominatedDodeIx.hasNext(); )
			{
				DominatorNode<Block> dominatedDode = dominatedDodeIx.next();
				Block dominatedBlock = dominatedDode.getGode();

				// Find common dominator
				boolean commonDominatorFound = false;
				DominatorNode<Block> parentDode = domTree.getParentOf(blockDode);
				while (null != parentDode) {
					if (domTree.isDominatorOf(parentDode, blockDode) &&
							domTree.isDominatorOf(parentDode, dominatedDode))
					{
						commonDominatorFound = true;
						break;
					}

					parentDode = domTree.getParentOf(parentDode);
				}
				if (!commonDominatorFound) continue;
				Block commonDomBlock = (Block)parentDode.getGode();

				// Find variables in all branches between block and dominated block
				HashSet<Value> branchVars = new HashSet<>();
				m_findBranchVarsBetweenBlocks(blockGraph, commonDomBlock, dominatedBlock, branchVars);

				// Determine if all variables are alive in block
				if (headDefs.containsAll(branchVars)) {
					disambiguation.postDomBlockIds.add(dominatedBlock.getIndexInMethod());
					for (Iterator<Value> branchVarIx = branchVars.iterator(); branchVarIx.hasNext(); ) {
						Value branchVar = branchVarIx.next();
						disambiguation.postDomVarNames.add(branchVar.toString());
					}
				}
			}
		}
		return blockDisambiguations;
	}

	static private ArrayList<LogDisambiguation> m_calcDisambiguations (String methodSig) {
		// Reset Soot state
		G.reset();
		Options.v().set_allow_phantom_refs(true);
		Options.v().setPhaseOption("jb", "use-original-names:true");
		Options.v().set_soot_classpath(System.getProperty("java.class.path"));

		// Get class signature
		int colonPos = methodSig.indexOf(':');
		String classSig = methodSig.substring(1, colonPos);

		// Load class
		Options.v().classes().add(classSig);
		Scene.v().loadNecessaryClasses();

		// Load method
		SootMethod m = Scene.v().getMethod(methodSig);
		if (null == m) {
			System.err.println("ERROR: Couldn't find method " + methodSig);
		}

		if (m.isConcrete()) m.retrieveActiveBody();
		else return null;

		ArrayList<LogDisambiguation> blockDisambiguations = m_calcDismabiguations(m.getActiveBody());
		gm_methodSigToDisambiguations.put(methodSig, blockDisambiguations);

		return blockDisambiguations;
	}

	static public void getDisambiguatedBlockIds (String methodSig, List<Integer> loggedBlockIds,
			List<Integer> disambiguatedBlockIds)
	{
		ArrayList<LogDisambiguation> blockIdToLogDisambiguation = gm_methodSigToDisambiguations.get(methodSig);
		if (null == blockIdToLogDisambiguation) {
			blockIdToLogDisambiguation = m_calcDisambiguations(methodSig);
			if (null == blockIdToLogDisambiguation) return;
		}

		for (Iterator<Integer> loggedBlockIdIx = loggedBlockIds.iterator(); loggedBlockIdIx.hasNext(); ) {
			int loggedBlockId = loggedBlockIdIx.next();
			LogDisambiguation disambiguation = blockIdToLogDisambiguation.get(loggedBlockId);
			disambiguatedBlockIds.addAll(disambiguation.blockIds);
			disambiguatedBlockIds.addAll(disambiguation.postDomBlockIds);
		}
	}

	static public void main (String args[]) {
		if (args.length < 5) {
			System.err.println("Usage: java -cp <soot classpath> ca.utoronto.dsrg.LoggableVarFinder " +
					"<analysis classpath> " +
					"<package inclusion list>" +
					"<package exclusion list>" +
					"<class path list> " +
					"<output file>");
			System.exit(0);
		}
		int argIx = 0;
		String anaClassPath = args[argIx++];
		String packInclPath = args[argIx++];
		String packExclPath = args[argIx++];
		String classPathPath = args[argIx++];
		String outputFile = args[argIx++];

		HashSet<String> includedPackages = new HashSet<>();
		HashSet<String> excludedPackages = new HashSet<>();
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

		final LinkedList<String> bbPropStrs = new LinkedList<>();

		LinkedList<String> sootArgs = new LinkedList<>();
		sootArgs.add("-allow-phantom-refs");
		sootArgs.add("-keep-line-number");
		sootArgs.add("-asm-backend");
		sootArgs.add("-p");
		sootArgs.add("jb");
		sootArgs.add("use-original-names:true");
		sootArgs.add("-cp");
		sootArgs.add(anaClassPath);

		// Add transformation to apply to every function
		Transform instrumentation = new Transform("jtp.myTrans", new BodyTransformer() {
			@Override
			protected void internalTransform (Body body, String phase, Map options) {
				SootMethod method = body.getMethod();

				ArrayList<LogDisambiguation> disambiguations = m_calcDismabiguations(body);
				for (int blockIx = 0; blockIx < disambiguations.size(); ++blockIx) {
					LogDisambiguation disambiguation = disambiguations.get(blockIx);

					HashSet<Integer> blockIds = new HashSet<>();
					blockIds.addAll(disambiguation.blockIds);
					blockIds.addAll(disambiguation.postDomBlockIds);
					HashSet<String> varNames = new HashSet<>();
					varNames.addAll(disambiguation.varNames);
					varNames.addAll(disambiguation.postDomVarNames);

					StringBuffer stringBuf = new StringBuffer();
					stringBuf.append(method.getSignature());
					stringBuf.append('\t');
					stringBuf.append(blockIx);
					stringBuf.append('\t');
					stringBuf.append(disambiguation.blockIds);
					stringBuf.append('\t');
					stringBuf.append(disambiguation.varNames);
					stringBuf.append('\t');
					stringBuf.append(disambiguation.postDomBlockIds);
					stringBuf.append('\t');
					stringBuf.append(disambiguation.postDomVarNames);
					stringBuf.append('\t');
					stringBuf.append(Integer.toString(blockIds.size()));
					stringBuf.append('\t');
					stringBuf.append(Integer.toString(varNames.size()));

					synchronized (bbPropStrs) {
						bbPropStrs.add(stringBuf.toString());
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

		// Run Soot for each class
		String[] sootArgsArray = new String[sootArgs.size() + 1];
		int i = 0;
		for (Iterator<String> sootArgIx = sootArgs.iterator(); sootArgIx.hasNext(); ) {
			String sootArg = sootArgIx.next();
			sootArgsArray[i] = sootArg;
			++i;
		}
		for (Iterator<String> classPathIx = classNames.iterator(); classPathIx.hasNext(); ) {
			String classPath = classPathIx.next();

			System.err.println("DEBUG Transforming " + classPath);

			// Reset between runs to ensure classes don't influence each other
			soot.G.reset();

			soot.G.v().out = devNull;

			// Add classes referenced by transformer to scene since they are phantom otherwise
			PackManager.v().getPack("jtp").add(instrumentation);
			sootArgsArray[i] = classPath;
			soot.Main.main(sootArgsArray);
		}

		// Write out block properties
		try {
			BufferedWriter bbPropsWriter = new BufferedWriter(
					new OutputStreamWriter(
							new FileOutputStream(outputFile, true), "utf-8"
					)
			);

			for (Iterator<String> propStrIx = bbPropStrs.iterator(); propStrIx.hasNext(); ) {
				String propStr = propStrIx.next();
				bbPropsWriter.write(propStr);
				bbPropsWriter.newLine();
			}

			bbPropsWriter.close();
		} catch (UnsupportedEncodingException uee) {
			System.err.println("ERROR " + uee.getMessage());
		} catch (FileNotFoundException fnfe) {
			System.err.println("ERROR " + fnfe.getMessage());
		} catch (IOException ie) {
			System.err.println("ERROR " + ie.getMessage());
		}
	}
}

class LogDisambiguation {
	public HashSet<Integer> blockIds;
	public HashSet<String> varNames;
	public HashSet<Integer> postDomBlockIds;
	public HashSet<String> postDomVarNames;

	LogDisambiguation () {
		blockIds = new HashSet<>();
		varNames = new HashSet<>();
		postDomBlockIds = new HashSet<>();
		postDomVarNames = new HashSet<>();
	}
}