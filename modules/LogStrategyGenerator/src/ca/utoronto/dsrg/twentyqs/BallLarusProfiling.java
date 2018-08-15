package ca.utoronto.dsrg.twentyqs;

import soot.*;
import soot.shimple.Shimple;
import soot.shimple.ShimpleBody;
import soot.toolkits.graph.Block;
import soot.toolkits.graph.BriefBlockGraph;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintStream;
import java.util.*;
import java.util.stream.Collector;
import java.util.stream.Collectors;

public class BallLarusProfiling {

    // fpath:
    // class_path
    public BallLarusProfiling(List<String> classes, List<String> class_pathes, ProfileEnum type) {
        this.classes = classes;
        this.class_pathes = class_pathes;
        this.type = type;
    }
    ProfileEnum type;

    List<String> classes;
    List<String> class_pathes;

    Integer time = 0;
    HashSet<Block> visited = new HashSet<>();
    HashMap<Block, Integer> started = new HashMap<>();
    HashSet<Block> finished = new HashSet<>();

    private void insert_all_nodes_and_edges(Graph g, Block head, Block parent) {
        visited.add(head);
        time += 1;
        started.put(head, time);
        g.addNode(head);
        if(parent != null) {
            g.addEdge(parent, head, Graph.EdgeType.Tree);
        }
        for(Block succ : head.getSuccs()) {
            if(!visited.contains(succ)) {
                insert_all_nodes_and_edges(g, succ, head); // recursion
            } else if(!finished.contains(succ)) { // succ visited, but not finished
                g.addEdge(head, succ, Graph.EdgeType.Back);
            } else if(started.get(head) < started.get(succ)) {
                g.addEdge(head, succ, Graph.EdgeType.Forward);
            } else {
                g.addEdge(head, succ, Graph.EdgeType.Cross);
            }
        }
        finished.add(head);
    }

    private String getClassNamefromFuncName(String func_name) {
        // System.out.println("Analyzing" + func_name);
        int end_index = func_name.indexOf(":");
        String class_name = func_name.substring(1,end_index);
        return class_name;
    }

    HashMap<Block, Integer> blockIndex;
    HashSet<Integer> out = new HashSet<>();

    private void analyze_func(Body body) {
        // generate function body CFG
        ShimpleBody shimpBody = Shimple.v().newBody(body);
        BriefBlockGraph blockGraph = new BriefBlockGraph(shimpBody);
        blockIndex = new HashMap<>();
        List<Block> heads = blockGraph.getHeads();
        for(int i = 0; i < blockGraph.getBlocks().size(); i ++) {
            blockIndex.put(blockGraph.getBlocks().get(i), i);
        }
        for(Block head : heads) {
            Graph g = new Graph();
            visited = new HashSet<>();
            started = new HashMap<>();
            finished = new HashSet<>();
            insert_all_nodes_and_edges(g, head, null);
            g.assignValue();
            // g.dumpValue();
            List<Block> log_blk_list = null;
            if(this.type.equals(ProfileEnum.BALLLARUS)) {
                log_blk_list = g.gen_log_blk_list();
            } else if(this.type.equals(ProfileEnum.BACKEDGE)) {
                log_blk_list = g.gen_backedge_blk_list();
            }
            List<Integer> log_blk_indices = log_blk_list.stream()
                    .map(s -> blockIndex.get(s))
                    .collect(Collectors.toList());
            for(Integer i : log_blk_indices) {
                out.add(i);
            }
            // System.out.println("Logging block size: " + log_blk_list.size());
        }
    }

    public enum ProfileEnum {
        BACKEDGE,
        BALLLARUS,
    }

    /**
     * Ball-Larus profiling
     * Input: name of the function
     * Output: list of basicblocks need to log, applying the Ball-Larus profiling
     * */
    public HashSet<Integer> profileTarget(String func_name) {
        LinkedList<String> sootArgs = new LinkedList<>();
        sootArgs.add("-allow-phantom-refs");
        sootArgs.add("-keep-line-number");
        sootArgs.add("-asm-backend");
        sootArgs.add("-p");
        sootArgs.add("jb");
        sootArgs.add("use-original-names:true");
        sootArgs.add("-cp");
        sootArgs.add(class_pathes.get(0));
        Transform instrumentation = new Transform("jtp.myTrans", new BodyTransformer() {
            @Override
            protected void internalTransform (Body body, String phase, Map options) {
                SootMethod method = body.getMethod();
                if(method.getSignature().equals(func_name)) {
                    // System.err.println("In file Analyzing " + method.toString());
                    analyze_func(body);
                } else {
                    // System.err.println("Found method: " + method.getSignature());
                }
            }});
        // Run Soot for each class
        String[] sootArgsArray = new String[sootArgs.size() + 1];
        int i = 0;
        for (Iterator<String> sootArgIx = sootArgs.iterator(); sootArgIx.hasNext(); ) {
            String sootArg = sootArgIx.next();
            sootArgsArray[i] = sootArg;
            ++i;
        }
        // Reset between runs to ensure classes don't influence each other
        soot.G.reset();
        // /dev/null output stream for soot output
        PrintStream devNull = null;
        try {
            devNull = new PrintStream(new File("/dev/null"));
        } catch (FileNotFoundException fnfe) {
            System.err.println("ERROR " + fnfe.getMessage());
            System.exit(-1);
        }
        soot.G.v().out = devNull;
        // Add classes referenced by transformer to scene since they are phantom otherwise
        PackManager.v().getPack("jtp").add(instrumentation);

        sootArgsArray[i] = getClassNamefromFuncName(func_name);
        // this class will throw exception or soot:
        // Class org.apache.hadoop.hdfs.server.datanode.ReplicaInfo$ReplicaDirInfo was found in an archive,
        // but Soot doesn't support reading source files out of an archive
        // So don't process it.
        if(func_name.contains("ReplicaInfo$ReplicaDirInfo") ||
                func_name.contains("PerStoragePendingIncrementalBR")||
                func_name.contains("BlockTokenSecretManager")) {
            return out;
        }
        // System.err.println("DEBUG Transforming " + sootArgsArray[i]);
        try{
            soot.Main.main(sootArgsArray);
        } catch(Exception e) {
            System.out.println("Jar exception:" + e);
            return out;
        }
        return out;
    }
}
