package ca.utoronto.dsrg;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;

import soot.Body;
import soot.BodyTransformer;
import soot.PackManager;
import soot.SootMethod;
import soot.Transform;

public class MethodSigsPrinter {
	public static void main (String[] args) {
		if (args.length < 2) {
			System.err.println("Usage: java -cp <soot classpath> ca.utoronto.dsrg.MethodSigsPrinter " +
					"<analysis classpath> " + 
					"<class path list> ");
			System.exit(0);
		}
		int argIx = 0;
		String anaClassPath = args[argIx++];
		String classPathPath = args[argIx++];
		
		LinkedList<String> classNames = new LinkedList<>();
		FileReader fReader;
		BufferedReader bReader;
		try {
			String line;
			
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
		
		LinkedList<String> sootArgs = new LinkedList<>();
		sootArgs.add("-allow-phantom-refs");
		sootArgs.add("-asm-backend");
		sootArgs.add("-cp");
		sootArgs.add(anaClassPath);
		
		Transform instrumentation = new Transform("jtp.myTrans", new BodyTransformer() {
			@Override
			protected void internalTransform (Body body, String phase, Map options) {
				SootMethod method = body.getMethod();
				
				System.out.println(method.getSignature());
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
			
			// Reset between runs to ensure classes don't influence each other
			soot.G.reset();
			
			soot.G.v().out = devNull;
			
			// Add classes referenced by transformer to scene since they are phantom otherwise
			PackManager.v().getPack("jtp").add(instrumentation);
			sootArgsArray[i] = classPath;
			soot.Main.main(sootArgsArray);
		}
	}
}
