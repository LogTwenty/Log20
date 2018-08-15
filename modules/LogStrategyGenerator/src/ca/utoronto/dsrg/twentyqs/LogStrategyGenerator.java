package ca.utoronto.dsrg.twentyqs;

import java.io.*;
import java.nio.charset.Charset;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LogStrategyGenerator {

    // Return Map from String <Methodid-block-id, occurance> to Integer
    private static HashMap<String, Long> parse_hdfs_trace_line(String line) {
        if(line.length() == 0) {
            return new HashMap<>();
        }
        String[] array = line.split(",");
        String func_id = array[0];
        HashMap<String, Long> r = new HashMap<>();
        for(int i = 1; i < array.length; i += 2) {
            if(array[i].length() == 0) {
                return new HashMap<>();
            }
            String bb_id = func_id + "-" + (i-1) / 2; // we ignore the basicblock latency
            if(array[i].contains("[0-9]")) {
                continue; // ignore invalid
            }
            if(Long.parseLong(array[i]) != 0) { // Ignore Zero Weight Basicblock
                r.put(bb_id, Long.parseLong(array[i]));
            }
        }
        return r;
    }

    private static Long parse_hdfs_func_latency(String line) {
        String[] array = line.split(",");
        Long r = new Long(0);
        for(int i = 2; i < array.length; i += 2) {
            if(array[i].length() == 0) {
                return new Long(0);
            }
            if(array[i].contains("[0-9]")) {
                continue; // ignore invalid
            }
            Long l = Long.parseLong(array[i]);
            r = r + l / 1000; // we use microseconds
        }
        return r;
    }

    private static List<BasicBlockTrace> bbs;
    private static HashMap<String, Integer> bb_map; // map from bb string to unsorted bb index
    private static HashMap<Integer, String> bb_reverse_map; // map from unsorted bb index to bb string


    private static  void reset_bb_occurance(HashMap<String, Long> bb_occurance) {
        for(String k : bb_occurance.keySet()) {
            bb_occurance.put(k, new Long(0));
        }
    }

    private static void process_traceline(HashMap<Integer, Long> cur_path,
                                          HashMap<String, PathSig> path_rm_reptitive,
                                          HashMap<String, PathSig> path_appearance_reptitive,
                                          List<Long> path_latency,
                                          Long cur_path_latency,
                                          List<HashMap<Integer, Long>> path_map,
                                          List<HashSet<Integer>> appearance_paths,
                                          HashSet<Integer> cur_path_occurance) {
        String sig = PathSig.gen_sig_for_path(cur_path);
        if(path_rm_reptitive.containsKey(sig)) {
            PathSig ps = path_rm_reptitive.get(sig);
            path_latency.set(ps.id, path_latency.get(ps.id) + cur_path_latency);
            ps.occurance += 1;
        } else {
            // we have found a new path, so bookkeeping it
            path_map.add(cur_path);
            path_latency.add(cur_path_latency);
            path_rm_reptitive.put(sig, new PathSig());
            path_rm_reptitive.get(sig).id = path_map.size() - 1;
            path_rm_reptitive.get(sig).occurance = new Long(1);
        }
        // calculate same thing for appearance
        String sig_appear = PathSig.gen_sig_for_path_appearance(cur_path_occurance);
        if(path_appearance_reptitive.containsKey(sig_appear)) {
            path_appearance_reptitive.get(sig_appear).occurance += 1;
        } else {
            appearance_paths.add(cur_path_occurance);
            path_appearance_reptitive.put(sig_appear, new PathSig());
            path_appearance_reptitive.get(sig_appear).id = appearance_paths.size() - 1;
            path_appearance_reptitive.get(sig_appear).occurance = new Long(1);
        }

    }

    private static Trace get_trace(String test_file) throws IOException {
        String line;
        InputStream fis = null;
        try {
            fis = new FileInputStream(test_file);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        InputStreamReader isr = new InputStreamReader(fis, Charset.forName("UTF-8"));
        BufferedReader br = new BufferedReader(isr);
        bb_map = new HashMap<>(); // map from method-location id to integer-id
        HashMap<String, Long> bb_occurance = new HashMap<>(); // map from method-location id to occurance
        bb_reverse_map = new HashMap<>(); // map from integer-id to method-location id
        List<HashMap<Integer, Long>> path_map = new ArrayList<>(); // each path is a set of <BB,BB-freq> tuples
        List<HashSet<Integer>> appearance_paths = new ArrayList<>(); // each path is set of <BB>s
        List<Long> path_latency = new ArrayList<>(); // latency of each path, indexed by id in path_map
        HashMap<String, PathSig> path_rm_reptitive = new HashMap<>();
        HashMap<String, PathSig> path_appearance_reptitive = new HashMap<>();

        Integer blk_cnt = 0;
        HashMap<Integer, Long> cur_path = null;
        HashSet<Integer> cur_path_occurance = null;
        Long cur_path_latency = new Long(0);

        int total_path_cnt = 0;
        int total_line_cnt = 0;
        while((line = br.readLine()) != null) {
            total_line_cnt += 1;
            String cur_line = null;
            if(line.contains("***NEW TRACE***")) {
                // System.out.println("Found new trace! " + total_line_cnt);
                reset_bb_occurance(bb_occurance);
                continue;
            }
            if(line.contains("***New Request***")) {
                int x = line.indexOf("***New Request***");
                line = line.substring(x);
                // generate sig for cur_path and check if it appeared before
                total_path_cnt += 1;
                if(cur_path != null) {
                    process_traceline( cur_path,
                             path_rm_reptitive,
                             path_appearance_reptitive,
                             path_latency,
                             cur_path_latency,
                             path_map,
                             appearance_paths,
                             cur_path_occurance);
                }
                cur_path = new HashMap<>();
                cur_path_occurance = new HashSet<>();
                cur_path_latency = new Long(0);
                cur_line = line.replaceAll("\\*\\*\\*New Request\\*\\*\\*", "");
            } else {
                cur_line = line;
            }

            // System.out.println(line);
            if(line.length() == 0) {
                continue;
            }
            cur_path_latency += parse_hdfs_func_latency(line);
            HashMap<String, Long> r = parse_hdfs_trace_line(cur_line);
            for(String k : r.keySet()) { // k here is method-location id
                if(!bb_map.containsKey(k)) {
                    bb_map.put(k, bb_map.size());
                }
                if(!bb_occurance.containsKey(k)) {
                    bb_occurance.put(k, new Long(0));
                }
                Integer key = bb_map.get(k); // Get Basicblock Integer Id
                bb_reverse_map.put(key, k);
                Long old_occurance = bb_occurance.get(k);
                Long occurance = r.get(k);
//                Utils.assert_exit(occurance < old_occurance,
//                        "Line: " + total_line_cnt + ": New bb record is smaller than old one: " + k + ": " + old_occurance + ", " + occurance);
                if (occurance < old_occurance) {
                    bb_occurance.put(k, new Long(0));
                    old_occurance = new Long(0);
                }
                if(occurance - old_occurance != 0) {
                    cur_path_occurance.add(key);
                    cur_path.put(key, new Long(occurance - old_occurance));
                }
                bb_occurance.put(k, occurance); // up-to-date BasicBlock occurance
            }
        }
        // add the leftover of cur_path into path_map
        if(cur_path != null) {
            process_traceline( cur_path,
                    path_rm_reptitive,
                    path_appearance_reptitive,
                    path_latency,
                    cur_path_latency,
                    path_map,
                    appearance_paths,
                    cur_path_occurance);
        }
        blk_cnt = bb_map.size();
        Utils.assert_exit((path_map.size() != path_rm_reptitive.size()),
                "The size of path_map and path_rm_reptitive is not equal!");

        // prepare algorithm inputs
        List<Double> probabs = new ArrayList<>();

        List<Double> path_appear_probabs = new ArrayList<>();

        bbs = new ArrayList<>();
        int path_cnt = path_rm_reptitive.size();

        for(String sig : path_rm_reptitive.keySet()) {
            PathSig ps = path_rm_reptitive.get(sig);
            probabs.add(new Double(ps.occurance) / new Double(total_path_cnt));
        }
        for(String sig : path_appearance_reptitive.keySet()) {
            PathSig ps = path_appearance_reptitive.get(sig);
            path_appear_probabs.add(new Double(ps.occurance) / new Double(total_path_cnt));
            // path_appear_probabs.add(new Double(1.0) / new Double(appearance_paths.size()));
        }

        System.out.println("BB Cnt:" + blk_cnt);
        for(int i = 0; i < blk_cnt; i ++) {
            bbs.add(new BasicBlockTrace());
            bbs.get(i).id = i;
            for(int j = 0; j < path_cnt; j ++) {
                if(path_map.get(j).containsKey(new Integer(i))) {
                    bbs.get(i).occur.add(path_map.get(j).get(i));
                } else {
                    bbs.get(i).occur.add(new Long(0));
                }
            }
        }

        // System.out.println("Probab matrix size: " + probabs.size());
        // System.out.println(probabs);
        System.out.println(path_appear_probabs);


        System.out.println("Total number paths: " + total_path_cnt);

        // System.out.println("Path latency:" + path_latency);
        // System.out.println("BB matrix: " + bbs);

        Trace t = new Trace();
        t.path_cnt = path_cnt;
        t.path_latency = path_latency;
        t.total_path_cnt = total_path_cnt;
        t.bb_matrix = bbs;
        t.probab_matrix = probabs;
        t.scale_factor = t.total_path_cnt.doubleValue(); // set scale factor
        t.appearance_paths = appearance_paths;
        t.probab_matrix_appearance = path_appear_probabs;
        t.sort_bbs_by_weight();
        // ID before sort: 3363, id after sort: 3454
//        System.out.println("3454 bb occurs: " + bbs.get(3454).occur);
//        System.out.println("3454 bb weight: " + bbs.get(3454).weight);

        Integer bb_id = bb_map.get("2533-0");
        Long a = new Long(0);
        Long length = new Long(0);
        Long avg_length_all = new Long(0);
        for(String sig : path_rm_reptitive.keySet()) {
            Integer id = path_rm_reptitive.get(sig).id;
            Long occur = path_rm_reptitive.get(sig).occurance;
            if(path_map.get(id).keySet().contains(bb_id)) {
                for(Integer k : path_map.get(id).keySet()) {
                    length += path_map.get(id).get(k).longValue() * occur;
                }
                a += occur;
            }
            for(Integer k : path_map.get(id).keySet()) {
                avg_length_all += path_map.get(id).get(k).longValue() * occur;
            }
        }
        avg_length_all = avg_length_all / total_path_cnt;
//        System.out.println("Number of paths containing " +  bb_id + " : " + a);
//        System.out.println("Avg lengths of paths containing " +  bb_id + " : " + (length.doubleValue() / a.doubleValue()));
        System.out.println("number of paths:" + path_map.size());
        System.out.println("Avg length of paths:" + avg_length_all);
        System.out.println("number of BBs:" + bbs.size());
        System.out.println("number of paths in appearance:" + appearance_paths.size());
        return t;
    }

    private static void dump_paths(Trace t) {
        List<String> paths = new ArrayList<>();
        for(int i = 0; i < t.path_cnt; i ++) {
            StringBuilder sb = new StringBuilder();
            for(int j = 0; j < t.bb_matrix.size(); j ++) {
                sb.append(t.bb_matrix.get(j).occur.get(i)).append(" ");
            }
            paths.add(sb.toString());
        }
        for(String s : paths) {
            System.out.println(s);
        }
    }

    private static void run_hdfs_trace(String test_file, Double threshold) throws IOException {
        // readin
        long startTime = System.currentTimeMillis();
        Trace t = get_trace(test_file);

        // --------------------- Start the algorithm -------------------------------------------
        long stage1 = System.currentTimeMillis();
        System.out.println("[Stage1] Readin time: " + (stage1 - startTime));
        Double avg_latency = 0.0;
        for(int i = 0; i < t.path_cnt; i ++) {
            avg_latency += t.path_latency.get(i) * t.probab_matrix.get(i);
        }
        System.out.println("Threshold: " + threshold);
        LogStrategyCell best_solution = gen_log_strategy(t, threshold);

        Double[] entropies = new Double[best_solution.strategy.size()];
        for(int i = 0; i < best_solution.strategy.size(); i ++) {
            Integer p = best_solution.strategy.get(i);
            double dd = cal_entropy_with_logblk(t, p);
            entropies[i] = dd;
        }

        // add all nodes with backedges to eliminate extra entropy cuzed by loop
        // add_backedge_nodes(best_solution);

        t.cal_overhead_with_logstrategycell(best_solution);
        t.cal_entropy_with_logstrategycell(best_solution);
        System.out.println("Final choice: " + best_solution + ", with entrophy: " + best_solution.entrophy
                + ", total entropy: " + t.total_entropy + ", path avg length: " + best_solution.overhead
                + ", total avg length: " + t.all_path_avg_length);

        for(int i = 0; i < best_solution.strategy.size(); i ++) {
            Integer id_after_sort = best_solution.strategy.get(i);
            Integer id_before_sort = best_solution.translated_strategy.get(i);
            System.out.println(bb_reverse_map.get(id_before_sort) + ", Weight: " + bbs.get(id_after_sort).weight
            + ", Entropy: " + entropies[i]);
        }
        long stage2 = System.currentTimeMillis();

        System.out.println("[Stage2] Processing time: " + (stage2 - stage1));

        // --------------------  Start Integration with Kirk's Code ----------------------------
//        LogStrategyCell best_solution_with_variable = gen_log_var_strategy(best_solution);
//        long stage3 = System.currentTimeMillis();
//        t.cal_entropy_with_logstrategycell(best_solution_wi  th_variable);
//        System.out.println("[Stage3] After variables, best solution: " + best_solution_with_variable + " Entropy: " +best_solution_with_variable.entrophy);
//        System.out.println("[Stage3] Integrating with LogEnhancer Done! time: " + (stage3 - stage2));

    }

    private static void add_backedge_nodes(LogStrategyCell strategy) {
        String hdfs_method_file = test_home + "/MethodSignatureMapping.log";
        Set<String> bbs = bb_map.keySet();
        HashSet<String> func_ids = new HashSet<>();
        for(String bb : bbs) {
            int f = bb.indexOf("-");
            func_ids.add(bb.substring(0, f));
        }
        List<String> method_names = read_file_to_arraystr(hdfs_method_file);
        HashMap<Integer, String> method_id_to_names = gen_method_id_to_names(method_names);
        List<String> classes = read_file_to_arraystr(test_home+"/file.txt");
        List<String> class_pathes = read_file_to_arraystr(test_home + "/class.txt");
        BallLarusProfiling larus_prof = new BallLarusProfiling(classes, class_pathes,
                BallLarusProfiling.ProfileEnum.BACKEDGE);

        System.out.println("Find out size: " + func_ids.size());
        for(String ff : func_ids) {
            String fname = method_id_to_names.get(Integer.parseInt(ff));
            HashSet<Integer> should_log = larus_prof.profileTarget(fname);
            for(Integer bb : should_log) {
                String bb_id = ff + "-" + bb;
                Integer unsorted_bb_id = bb_map.get(bb_id);
                if(unsorted_bb_id != null) {
                    Integer sorted_bb_id = find_id_after_sort(unsorted_bb_id);
                    strategy.strategy.add(sorted_bb_id);
                    strategy.translated_strategy.add(unsorted_bb_id);
                }
            }
        }
    }

    private static double cal_entropy_with_logblk(Trace t, Integer blk) {
        LogStrategyCell c = new LogStrategyCell();
        c.strategy.add(blk);
        t.cal_entropy_with_logstrategycell(c);
        return c.entrophy;
    }

    private static LogStrategyCell gen_log_strategy(Trace t, Double threshold) {
        t.threshold = new Double(threshold * t.scale_factor).intValue();
        // t.cal_type = "Count";
        t.cal_type = "Appearance";
        if(algo_type.equals("dp")) {
            LogStrategyCell best_solution = t.gen_log_strategy_dp();
            return best_solution;
        }
        if(algo_type.equals("enumerate")) {
            LogStrategyCell best_solution = t.gen_log_strategy_enumerate();
            return best_solution;
        }
        System.exit(1);
        return null;
    }

    private static Integer find_id_after_sort(Integer id_before_sort) {
        for(int i = 0; i < bbs.size(); i ++) {
            if(bbs.get(i).id.equals(id_before_sort)) {
                return i;
            }
        }
        return -1;
    }

    private static String algo_type = "dp";

    private static LogStrategyCell gen_log_var_strategy(LogStrategyCell best_solution) {
        String hdfs_method_file = test_home + "/MethodSignatureMapping.log";
        // read method file ids into a list
        List<String> method_names = read_file_to_arraystr(hdfs_method_file);
        HashMap<Integer, String> method_id_to_names = gen_method_id_to_names(method_names);

        // reduce on m_id
        HashMap<Integer, List<Integer>> log_func_array = new HashMap<>();
        for(int i = 0; i < best_solution.strategy.size(); i ++) {
            Integer id_before_sort = best_solution.translated_strategy.get(i);
            String bb_origin_id = bb_reverse_map.get(id_before_sort);
            // System.out.println("ID: " + bb_origin_id);
            String pattern = "([0-9]+)-([0-9]+)";
            Matcher m = Pattern.compile(pattern).matcher(bb_origin_id);
            if(m.find()) {
                Integer m_id = Integer.parseInt(m.group(1));
                Integer b_id = Integer.parseInt(m.group(2));
                if(!log_func_array.containsKey(m_id)) {
                    log_func_array.put(m_id,new LinkedList<Integer>());
                }
                log_func_array.get(m_id).add(b_id);
            }
        }
        LogStrategyCell best_solution_with_variable = new LogStrategyCell(best_solution);
        for(Integer k : log_func_array.keySet()) {
            List<Integer> loggedBlockIds = log_func_array.get(k);
            String m_name = method_id_to_names.get(k);
            LinkedList<Integer> disambiguatedBlockIds = new LinkedList<>();
            // System.out.println("[Log Variable]Calculating " + m_name + " with BBs: " + loggedBlockIds);
            LoggableVarFinder.getDisambiguatedBlockIds(m_name,
                    loggedBlockIds, disambiguatedBlockIds);
            for(Integer new_bb : disambiguatedBlockIds) {
                String bb_method_id = k + "-" + new_bb;
                Integer bb_int_id = bb_map.get(bb_method_id);
                if(bb_int_id != null) {
                    for(int i = 0; i < bbs.size(); i ++) { // do exhaused search, should optimize here
                        if(bbs.get(i).id.equals(bb_int_id)) {
                            best_solution_with_variable.strategy.add(i);
                            best_solution_with_variable.translated_strategy.add(bb_int_id);
                        }
                    }
                }
            }
            // System.out.println("Disambuated Blocks: " + disambiguatedBlockIds);
        }
        return best_solution_with_variable;
    }


    private static List<String> read_file_to_arraystr(String filename) {
        List<String> r = new ArrayList<>();
        InputStream fis = null;
        try {
            fis = new FileInputStream(filename);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        InputStreamReader isr = new InputStreamReader(fis, Charset.forName("UTF-8"));
        BufferedReader br = new BufferedReader(isr);
        String line;
        try {
            while((line = br.readLine()) != null) {
                r.add(line);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return r;
    }

    private static void run_mock_traces(String test_file) throws IOException {
        String line;
        InputStream fis = null;
        try {
            fis = new FileInputStream(test_file);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        InputStreamReader isr = new InputStreamReader(fis, Charset.forName("UTF-8"));
        BufferedReader br = new BufferedReader(isr);
        List<Double> probabs = new ArrayList<>();
        List<List<Long>> paths = new ArrayList<>();
        List<BasicBlockTrace> bbs = new ArrayList<>();
        int i = 0;
        int blk_cnt = 0, path_cnt = 0;
        int total_path_cnt = 0;
        int threshold = 0;
        List<Long> cur_path;
        HashMap<String, Integer> path_dict = new HashMap<>();
        while((line = br.readLine()) != null) {
            if (i == 0) {
                total_path_cnt = Integer.parseInt(line);
            } else if (i == 1) {
                blk_cnt = Integer.parseInt(line);
            } else if (i == 2) {
                threshold = Integer.parseInt(line);
            } else {
                cur_path = new ArrayList<Long>();
                String[] splitted = line.split(" ");
                Double occurance = 0.0;
                for(int j = 0; j < splitted.length; j ++) {
                    if (j == splitted.length - 1) {
                        occurance = Double.parseDouble(splitted[j]);
                    } else {
                        cur_path.add(Long.parseLong(splitted[j]));
                    }
                }
                String sig = PathSig.gen_sig_for_path(cur_path);
                if(path_dict.containsKey(sig)) {
                    Integer pid = path_dict.get(sig);
                    probabs.set(pid, probabs.get(pid) + occurance);
                } else {
                    probabs.add(occurance);
                    paths.add(new ArrayList<>(cur_path));
                    path_dict.put(sig, path_cnt);
                    path_cnt += 1;
                }
            }
            i += 1;
        }
        for(i = 0; i < path_cnt; i ++) {
            probabs.set(i, probabs.get(i) / new Double(total_path_cnt));
        }
        for(i = 0; i < blk_cnt; i ++) {
            bbs.add(new BasicBlockTrace());
            bbs.get(i).id = i;
            for(int j = 0; j < path_cnt; j ++) {
                bbs.get(i).occur.add(paths.get(j).get(i));
            }
        }
        System.out.println("Paths: " + paths);

        System.out.println("Probabs: " + probabs);
        Trace t = new Trace();
        t.path_cnt = path_cnt;
        t.total_path_cnt = total_path_cnt;
        t.bb_matrix = bbs;
        t.probab_matrix = probabs;
        t.threshold = threshold;
        t.sort_bbs_by_weight();
        System.out.println(t.bb_matrix);
        System.out.println("Threshold: " + t.threshold);
        LogStrategyCell best_solution = t.gen_log_strategy_dp();
        // LogStrategyCell best_solution = t.gen_log_strategy_enumerate();
        System.out.println("Final choice: " + best_solution.translated_strategy);
    }

    public static String test_home;

    public static void parse_existing_logs(String trace_file, String log_file) throws IOException {
        Trace t = get_trace(trace_file);

        String hdfs_method_file = test_home + "/MethodSignatureMapping.log";
        List<String> method_names = read_file_to_arraystr(hdfs_method_file);
        HashMap<String, Integer> method_names_to_id = gen_method_names_to_id(method_names);
        HashMap<Integer, String> method_id_to_name = gen_method_id_to_names(method_names);
        HashMap<Integer, List<Integer>> existing_logs = new HashMap<>();
        List<String> lines = read_file_to_arraystr(log_file);

        LogStrategyCell default_log = new LogStrategyCell();
        LogStrategyCell debug_log = new LogStrategyCell();
        LogStrategyCell trace_log = new LogStrategyCell();

        HashSet<Integer> default_log_bb_ids = new HashSet<>();
        HashSet<Integer> debug_log_bb_ids = new HashSet<>();
        HashSet<Integer> trace_log_bb_ids = new HashSet<>();

        for(int i = 1; i < lines.size(); i ++) {
            String[] splits = lines.get(i).split("\t");
            String method_name = splits[0];
            String pattern = "<(.+): (.+) (.+)>";
            Matcher m = Pattern.compile(pattern).matcher(method_name);
            if(m.find()) {
                String m_class = m.group(1);
                String m_ret = m.group(2);
                String m_def = m.group(3);
                /// System.out.println(m_class + "-" + m_ret + "-" + m_def);
                String m_fullname = "<" + m_class + ": " + m_ret + " " + m_def + ">";
                if(method_names_to_id.containsKey(m_fullname)) {
                    // System.out.println("Found method name: " + m_fullname);

                    Integer mid = method_names_to_id.get(m_fullname);
                    String blk_id = mid + "-" + splits[1];
                    // System.out.println("Looking for " + blk_id);
                    // get # of default logs in this bb
                    // get Id in BBs
                    if(bb_map.containsKey(blk_id)) { // this log is printed in the trace, this blk_id is before sort
                        // System.out.println("Found bb: " + blk_id);
                        Integer blk_intid = bb_map.get(blk_id);
                        Integer start_num = Integer.parseInt(splits[8]);
                        Integer end_num = Integer.parseInt(splits[9]);
                        existing_logs.put(blk_intid, new ArrayList<>());
                        existing_logs.get(blk_intid).add(start_num);
                        existing_logs.get(blk_intid).add(end_num);
                        // put the blk id in the logging strategy
                        Integer default_log_cnt = get_default_log_cnt(splits);
                        Integer debug_log_cnt = get_debug_log_cnt(splits);
                        Integer trace_log_cnt = get_trace_log_cnt(splits);
                        if(default_log_cnt != 0) {
                            default_log_bb_ids.add(blk_intid);
                        }
                        if(debug_log_cnt != 0) {
                            debug_log_bb_ids.add(blk_intid);
                        }
                        if(trace_log_cnt != 0) {
                            trace_log_bb_ids.add(blk_intid);
                        }
//                        if(m_fullname.contains("writeBlock")) {
//                            System.out.println("Found BB!" + blk_id + " log size: " + trace_log_cnt);
//                        }
                    } else {
//                        if(m_fullname.contains("writeBlock")) {
//                        System.out.println("Not Found BB!" + blk_id);
//                        }
                    }
                }
            }
        }

        System.out.println("INFO size: " +default_log_bb_ids.size());
        for(int i = 0; i < t.bb_matrix.size(); i ++) {
            BasicBlockTrace bb = t.bb_matrix.get(i);
            Integer id_before_sort = bb.id;
            if(default_log_bb_ids.contains(id_before_sort)) {
                default_log.strategy.add(i);
                default_log.translated_strategy.add(id_before_sort);
            }
            if(debug_log_bb_ids.contains(id_before_sort)) {
                debug_log.strategy.add(i);
                debug_log.translated_strategy.add(id_before_sort);
            }
            if(trace_log_bb_ids.contains(id_before_sort)) {
                trace_log.strategy.add(i);
                trace_log.translated_strategy.add(id_before_sort);
            }
        }
        // System.out.println("New Trace size: " + trace_log.strategy.size());
        Double[] entropies = new Double[trace_log.strategy.size()];
        t.cal_type = "Appearance";
        for(int i = 0; i < default_log.strategy.size(); i ++) {
            Integer p = default_log.strategy.get(i);
            double dd = cal_entropy_with_logblk(t, p);
            entropies[i] = dd;
        }
        for(int i = 0; i < default_log.strategy.size(); i ++) {
            Integer id_after_sort = default_log.strategy.get(i);
            Integer id_before_sort = default_log.translated_strategy.get(i);
            String bb_id = bb_reverse_map.get(id_before_sort);
            String method_name = method_id_to_name.get(Integer.parseInt(bb_id.substring(0, bb_id.indexOf("-"))));
            System.out.println("ID before sort: " + id_before_sort + ", id after sort: " + id_after_sort);
            System.out.println(bb_reverse_map.get(id_before_sort) + ", Weight: " + bbs.get(id_after_sort).weight
                    + " Name: " + method_name
                    + " Srcbegin: " + existing_logs.get(id_before_sort).get(0)
                    + " Srcend: " + existing_logs.get(id_before_sort).get(1) + " Entropy: " + entropies[i]);
        }
        t.cal_type = "Appearance";
        // t.cal_type = "Count";
        t.cal_entropy_with_logstrategycell(default_log);
        t.cal_entropy_with_logstrategycell(debug_log);
        t.cal_entropy_with_logstrategycell(trace_log);
        t.cal_overhead_with_logstrategycell(default_log);
        t.cal_overhead_with_logstrategycell(debug_log);
        t.cal_overhead_with_logstrategycell(trace_log);

        HashSet<String> ps = gen_patterns(t, default_log);
        System.out.println("Default: " + ps.size());
        System.out.println(ps);

        ps = gen_patterns(t, debug_log);
        System.out.println("Debug: " + ps.size());
        System.out.println(ps);

        ps = gen_patterns(t, trace_log);
        System.out.println("Trace: " + ps.size());
        System.out.println(ps);

        String final_key = Utils.gen_key(default_log.strategy);
        DistinguishedSet final_dis = t.sethash.get(final_key);
        System.out.println("We have this many distinguished set:" + final_dis.map.size());
        for(String k : final_dis.map.keySet()) {
            System.out.println("Paths: " + final_dis.map.get(k));
        }

//        default_log = gen_log_var_strategy(default_log);
//        debug_log = gen_log_var_strategy(debug_log);
//        trace_log = gen_log_var_strategy(trace_log);

        t.cal_plain_entropy();
        t.cal_entropy_with_logstrategycell(default_log);
        t.cal_entropy_with_logstrategycell(debug_log);
        t.cal_entropy_with_logstrategycell(trace_log);

        System.out.println("Total entropy: " + t.total_entropy);
        System.out.println(translate_strategy(default_log));
        System.out.println(default_log + " - Entropy:" + default_log.entrophy + " - AvgLength: " + default_log.overhead);
        System.out.println(translate_strategy(debug_log));
        System.out.println(debug_log + " - Entropy:" + debug_log.entrophy + " - AvgLength: " + debug_log.overhead);
        System.out.println(translate_strategy(trace_log));
        System.out.println(trace_log + " - Entropy:" + trace_log.entrophy + " - AvgLength: " + trace_log.overhead);
    }

    private static HashSet<String> gen_patterns(Trace t, LogStrategyCell c) {
        HashSet<String> patterns = new HashSet<>();
        for(int p = 0; p < t.path_cnt; p ++) {
            String s = "Pattern:";
            for(int k = 0; k < c.strategy.size(); k ++) {
                s = s + t.bb_matrix.get(c.strategy.get(k)).occur.get(p) + " ";
            }
            patterns.add(s);
        }
        return patterns;
    }

    private static List<String> translate_strategy(LogStrategyCell c) {
        List<String> s = new ArrayList<>();
        for(Integer bb : c.translated_strategy) {
            String bb_id = bb_reverse_map.get(bb);
            s.add(bb_id);
        }
        return s;
    }

    private static Integer get_default_log_cnt(String[] split) {
        Integer total = 0;
        // 2:Trace, 3:Debug, 4:Info, 5:Warn, 6:Error, 7:Fatal
        for(int i = 4; i < 8; i ++) {
            total += Integer.parseInt(split[i]);
        }
        return total;
    }

    private static Integer get_debug_log_cnt(String[] split) {
        Integer total = 0;
        for(int i = 3; i < 8; i ++) {
            total += Integer.parseInt(split[i]);
        }
        return total;
    }

    private static Integer get_trace_log_cnt(String[] split) {
        // sum up everything
        Integer total = 0;
        for(int i = 2; i < 8; i ++) {
            total += Integer.parseInt(split[i]);
        }
        return total;
    }

    private static HashMap<String, Integer> gen_method_names_to_id(List<String> method_names) {
        HashMap<String, Integer> method_names_to_id = new HashMap<>();
        for(int i = 0; i < method_names.size(); i ++) {
            String pattern = "MethodSignatureHashList\\[([0-9]+)\\]:(.*)$";
            Pattern r = Pattern.compile(pattern);
            Matcher m = r.matcher(method_names.get(i));
            if(m.find()) {
                Integer m_id = Integer.parseInt(m.group(1));
                String m_name = m.group(2);
                method_names_to_id.put(m_name, m_id);
            }
        }
        return method_names_to_id;
    }

    private static HashMap<Integer, String> gen_method_id_to_names(List<String> method_names) {
        HashMap<Integer, String> method_id_to_names = new HashMap<>();
        for(int i = 0; i < method_names.size(); i ++) {
            String pattern = "MethodSignatureHashList\\[([0-9]+)\\]:(.*)$";
            Pattern r = Pattern.compile(pattern);
            Matcher m = r.matcher(method_names.get(i));
            if(m.find()) {
                Integer m_id = Integer.parseInt(m.group(1));
                String m_name = m.group(2);
                method_id_to_names.put(m_id, m_name);
            }
        }
        return method_id_to_names;
    }

    private static LogStrategyCell gen_strategy_from_larus_profiling(String ff, HashSet<Integer> should_log) {
        LogStrategyCell out = new LogStrategyCell();
        for(Integer bb : should_log) {
            String bb_id = ff + "-" + bb;
            Integer unsorted_bb_id = bb_map.get(bb_id);
            if(unsorted_bb_id!= null) {
                System.out.println("Unsorted: " + unsorted_bb_id);
                Integer sorted_bb_id = find_id_after_sort(unsorted_bb_id);
                out.strategy.add(sorted_bb_id);
                out.translated_strategy.add(unsorted_bb_id);
            }
        }
        return out;
    }

    private static void run_ball_larus(String trace_file) {
        try {
            Trace t = get_trace(trace_file);
            t.cal_type = "Appearance";
            String hdfs_method_file = test_home + "/MethodSignatureMapping.log";
            Set<String> bbs = bb_map.keySet();
            HashSet<String> func_ids = new HashSet<>();
            for(String bb : bbs) {
                int f = bb.indexOf("-");
                func_ids.add(bb.substring(0, f));
            }
            List<String> method_names = read_file_to_arraystr(hdfs_method_file);
            HashMap<Integer, String> method_id_to_names = gen_method_id_to_names(method_names);
            List<String> classes = read_file_to_arraystr(test_home+"/file.txt");
            List<String> class_pathes = read_file_to_arraystr(test_home + "/class.txt");
            BallLarusProfiling larus_prof = new BallLarusProfiling(classes, class_pathes, BallLarusProfiling.ProfileEnum.BALLLARUS);

            LogStrategyCell out = new LogStrategyCell();
            System.out.println("Find out size: " + func_ids.size());
            for(String ff : func_ids) {
                String fname = method_id_to_names.get(Integer.parseInt(ff));
                HashSet<Integer> should_log = larus_prof.profileTarget(fname);
                for(Integer bb : should_log) {
                    String bb_id = ff + "-" + bb;
                    Integer unsorted_bb_id = bb_map.get(bb_id);
                    if(unsorted_bb_id != null) {
                        Integer sorted_bb_id = find_id_after_sort(unsorted_bb_id);
                        out.strategy.add(sorted_bb_id);
                        out.translated_strategy.add(unsorted_bb_id);
                    }
                }
            }
            // Test for Ball-Larus code
            // String func_name = "<org.apache.hadoop.hdfs.server.datanode.DataXceiver: void writeResponse(org.apache.hadoop.hdfs.protocol.proto.DataTransferProtos$Status,java.lang.String,java.io.OutputStream)>";
            // String func_name = "<org.apache.hadoop.hdfs.server.datanode.DataXceiver: void writeBlock(org.apache.hadoop.hdfs.protocol.ExtendedBlock,org.apache.hadoop.fs.StorageType,org.apache.hadoop.security.token.Token,java.lang.String,org.apache.hadoop.hdfs.protocol.DatanodeInfo[],org.apache.hadoop.fs.StorageType[],org.apache.hadoop.hdfs.protocol.DatanodeInfo,org.apache.hadoop.hdfs.protocol.datatransfer.BlockConstructionStage,int,long,long,long,org.apache.hadoop.util.DataChecksum,org.apache.hadoop.hdfs.server.datanode.CachingStrategy,boolean,boolean,boolean[],java.lang.String,java.lang.String[])>";
            // LogStrategyCell out = gen_strategy_from_larus_profiling(func_name, larus_prof.profileTarget(func_name));
            // out.strategy should be complete now
            t.cal_overhead_with_logstrategycell(out);
            t.cal_entropy_with_logstrategycell(out);
            System.out.println("Ball-Larus tracing entropy: " + out.entrophy + " , with overhead: " + out.overhead
                    + ", total avg length: " + t.all_path_avg_length);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) throws IOException {
        // HDFS: + "/data/20qstest/hdfs-test5"
        // HBase: + + "/data/20qstest/hbase-test4"
        // test_home = System.getProperty("user.home") + "/data/20qstest/yarn-test1";
        test_home = System.getProperty("user.home") + "/data/20qstest/hdfs-test5";
        String mode = "testcase";
        Double threshold = 0.0;
        for(int i = 0; i < args.length; i ++) {
            if(args[i].equals("--exist-check")) { // --exist-check CHECK-FILE-PATH
                mode = "exist-check";
            }
            if(args[i].equals("--threshold")) {
                threshold = Double.parseDouble(args[i+1]);
            }
            if(args[i].equals("--test-home")) {
                test_home = args[i+1];
            }
            if(args[i].equals("--balllarus")) {
                mode = "ball-larus";
            }
            if(args[i].equals("--dp")) {
                algo_type = "dp";
            }
            if(args[i].equals("--enumerate")) {
                algo_type = "enumerate";
            }
        }
        String test_file = test_home + "/trace.txt";
        String exist_file = test_home + "/BBProperties.log";
        if(mode.equals("exist-check")) {
            System.out.println("Parsing " + exist_file); // Check the entropy of existing logs
            parse_existing_logs(test_file, exist_file);
        } else if (mode.equals("testcase")) {
            run_hdfs_trace(test_file, threshold);
        } else if (mode.equals("ball-larus")) {
            run_ball_larus(test_file);
        }
        System.out.println("Maximum memory usage: " + (Runtime.getRuntime().maxMemory() / 1024 / 1024));
    }
}


