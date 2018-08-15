package ca.utoronto.dsrg;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.Iterator;
import java.util.LinkedList;

import soot.Body;
import soot.G;
import soot.Scene;
import soot.SootClass;
import soot.SootMethod;
import soot.options.Options;
import soot.toolkits.graph.Block;
import soot.toolkits.graph.BlockGraph;

public class TracingBBPrinter {
	private static String[] gm_parseMethodSig (String methodSig) {
		int colonPos = methodSig.indexOf(':');
		int openParenthesesPos = methodSig.indexOf('(');
		int methodNameStartPos = methodSig.indexOf(' ', colonPos + 2) + 1;

		String classSig = methodSig.substring(1, colonPos);
		String methodName = methodSig.substring(methodNameStartPos, openParenthesesPos);
		String methodSubSig = methodSig.substring(colonPos + 2, methodSig.length() - 1);

		String[] sigParts = new String[3];
		int sigPartIx = 0;
		sigParts[sigPartIx++] = classSig;
		sigParts[sigPartIx++] = methodSubSig;
		sigParts[sigPartIx++] = methodName;
		return sigParts;
	}
	
	private static boolean gm_writeBBs (String anaClassPath, String methodSig, String outputDir) {
		// TODO: Ensure overloaded methods don't overwrite each other
		String[] sigParts = gm_parseMethodSig(methodSig);
		int sigPartIx = 0;
		String classSig = sigParts[sigPartIx++];
		String methodSubSig = sigParts[sigPartIx++];
		String methodName = sigParts[sigPartIx++];
		String outFileName = classSig + '.' + methodName;
		String blkFilePath = outputDir + "/" + outFileName + ".bbs";

		G.reset();
		Options.v().set_allow_phantom_refs(true);
		Options.v().set_keep_line_number(true);
		Options.v().setPhaseOption("jb", "use-original-names:true");
		Options.v().set_soot_classpath(anaClassPath);
		
		Options.v().classes().add(classSig);
		Scene.v().loadNecessaryClasses();
		SootClass c = Scene.v().getSootClass(classSig);
		SootMethod m = null;
		try {
			m = c.getMethod(methodSubSig);
		} catch (Exception e) {
			System.err.println("ERROR Can't find method " + methodSig);
			System.exit(-1);
		}
		m.retrieveActiveBody();
		Body body = m.getActiveBody();

		try {
			BufferedWriter blksWriter = new BufferedWriter(
					new OutputStreamWriter(
							new FileOutputStream(blkFilePath), "utf-8"
					)
			);
		
			BlockGraph cfg = TracingInstrumentor.createBlockGraph(body);;
			Iterator<Block> blockIx = cfg.iterator();
			while (blockIx.hasNext()) {
				Block b = blockIx.next();

				blksWriter.write("Lines: " + Integer.toString(b.getHead().getJavaSourceStartLineNumber()) + "-" + 
						Integer.toString(b.getTail().getJavaSourceStartLineNumber()));
				blksWriter.newLine();
				blksWriter.write(b.toString());
				blksWriter.newLine();
			}

			blksWriter.close();
		} catch (IOException ioEx) {
			System.err.println("ERROR " + ioEx.getMessage());
			return false;
		}
		
		return true;
	}
	
	public static void main(String[] args) {
		if (args.length < 3) {
			System.out.println("Usage: java -cp <classpath> ca.dsrg.utoronto.TracingBBPrint " + 
					"<analysis classpath> " +
					"<method signature list>" + 
					"<output dir>");
			System.exit(0);
		}
		String anaClassPath = args[0];
		String methodSigsListPath = args[1];
		String outputDir = args[2];
		
		// Read method signatures
		final LinkedList<String> methodSigs = new LinkedList<String>();
		try {
			// Create readers
			FileReader fReader = new FileReader(methodSigsListPath);
			BufferedReader bReader = new BufferedReader(fReader);

			String line;
			while ((line = bReader.readLine()) != null) {
				// Skip empty or comment lines
				if (line.equals("") || '#' == line.trim().charAt(0)) continue;

				methodSigs.add(line);
			}

			bReader.close();
			fReader.close();
		} catch (FileNotFoundException fnfe) {
			System.err.println("ERROR " + fnfe.getMessage());
			System.exit(-1);
		} catch (IOException ioe) {
			System.err.println("ERROR " + ioe.getMessage());
			System.exit(-1);
		}
		
		for (Iterator<String> methodSigIx = methodSigs.iterator(); methodSigIx.hasNext(); ) {
			String methodSig = methodSigIx.next();
			if (!gm_writeBBs(anaClassPath, methodSig, outputDir)) System.exit(-1);
		}
		
	}

}
