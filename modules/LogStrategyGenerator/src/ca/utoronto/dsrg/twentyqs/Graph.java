package ca.utoronto.dsrg.twentyqs;

import fj.test.Bool;
import soot.toolkits.graph.Block;

import java.util.*;

public class Graph {
    public Graph() {
        global_map = new HashMap<>();
        nodeList = new ArrayList<>();
        edgeList = new ArrayList<>();
    }
    public Node addNode(Block b) {
        Node n = new Node();
        n.b = b;
        n.succ = new ArrayList<>();
        n.prev = new ArrayList<>();
        nodeList.add(n);
        global_map.put(b, n);
        return n;
    }
    // add an edge from b1 -> b2
    public void addEdge(Block b1, Block b2, EdgeType type) {
        Node n1 = global_map.get(b1);
        Node n2 = global_map.get(b2);
        n1.succ.add(n2);
        n2.prev.add(n1);
        edgeList.add(new Edge(n1, n2, type)); // by default everything is not backedge
    }
    public Edge findEdge(Node n1, Node n2) {
        for(int i = 0; i < edgeList.size(); i ++) {
            Edge e = edgeList.get(i);
            if(e.from.equals(n1) && e.to.equals(n2)) {
                return e;
            }
        }
        return null;
    }
    public HashMap<Block, Node> global_map;
    public List<Node> nodeList; // nodeList is stored in topological order
    public List<Edge> edgeList;
    public void assignValue() {
        for(int i = nodeList.size() - 1; i >= 0; i --) {
            Node n = nodeList.get(i);
            if(n.succ.size() == 0) { // leaf vertex
                n.numPaths = 1;
            } else {
                n.numPaths = 0;
                for(Node succ : n.succ) {
                    Edge e = findEdge(n, succ);
                    e.val = n.numPaths;
                    n.numPaths = n.numPaths + succ.numPaths;
                }
            }
        }
    }
    public void dumpValue() {
        for(Edge e : edgeList) {
            System.out.println("Edge type: " + e.type + ", value: " + e.val);
        }
    }

    public List<Block> gen_backedge_blk_list() {
        List<Block> out = new ArrayList<>();
        HashSet<Block> set = new HashSet<>();
        for(Edge e : edgeList) {
            if(e.type.equals(EdgeType.Back)) {
                set.add(e.from.b);
            }
        }
        for(Block b : set) {
            out.add(b);
        }
        return out;
    }

    public List<Block> gen_log_blk_list() {
        List<Block> out = new ArrayList<>();
        HashSet<Block> set = new HashSet<>();
        for(Edge e : edgeList) {
            if(e.val != 0) {
                set.add(e.from.b);
            }else if(e.type.equals(EdgeType.Back)) {
                set.add(e.from.b);
            }
        }
        for(Block b : set) {
            out.add(b);
        }
        return out;
    }
    public class Node {
        public Block b;
        public List<Node> succ;
        public List<Node> prev;
        public Integer numPaths = 0;
    }
    public enum EdgeType {
        Tree,
        Back,
        Cross,
        Forward
    }
    public class Edge {
        public Edge(Node from, Node to, EdgeType type) {
            this.from = from;
            this.to = to;
            this.type = type;
            this.val = 0;
        }
        public Node from;
        public Node to;
        public EdgeType type;
        public Integer val;
    }
}

