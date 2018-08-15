package ca.utoronto.dsrg.twentyqs;

import java.math.BigInteger;
import java.util.*;

public class Trace {
    public List<BasicBlockTrace> bb_matrix;
    public Integer path_cnt;
    public Integer total_path_cnt;
    public List<Long> path_latency;
    public List<HashSet<Integer>> appearance_paths;
    public List<Double> probab_matrix_appearance;
    public List<Double> probab_matrix; // probability of each path
    public int threshold;
    public Double total_entropy;
    public Double scale_factor;
    private Double one_log_overhead;
    public Trace() {
        sethash = new HashMap<>();
        one_log_overhead = 20.0;
    }
    //
    public HashMap<String, DistinguishedSet> sethash;

    public void sort_bbs_by_weight() {
        // calculate weight for each bb
        for(BasicBlockTrace bb : bb_matrix) {
            double weight = 0;
            for(int i = 0; i < bb.occur.size(); i ++) { // for each path
                weight += bb.occur.get(i) * probab_matrix.get(i);
//                if(bb.id == 3363) {
//                    if(bb.occur.get(i) !=0 && probab_matrix.get(i) != 0) {
//                        System.out.println("Found: " + bb.occur.get(i) + " with: " + probab_matrix.get(i) + " index:" + i);
//                    }
//                }
            }
            Utils.assert_exit(weight == 0, "Weight of a basicblock is not allowed to be zero");

            bb.weight = new Double(weight * this.scale_factor).intValue();
//            if(bb.id == 3363) {
//                System.out.println("3363 BB original weight: " + weight);
//                System.out.println("3363 BB weight: " + bb.weight);
//            }
//            if(bb.weight == 0){
//                System.out.println("BB " + bb.id + " weight: " + (weight * total_path_cnt));
//                System.out.println("Scale factor: " + this.scale_factor);
//                System.out.println("Weight: "  + bb.weight);
//            }
        }
        // we suppose the threshold has already been scaled
        // sort bbs by weight
        Collections.sort(bb_matrix, new Comparator<BasicBlockTrace>() {
            @Override
            public int compare(BasicBlockTrace o1, BasicBlockTrace o2) {
                // Intentional: Reverse order for this demo
                return o1.weight.compareTo(o2.weight);
            }
        });
    }

    public String cal_type;

    public LogStrategyCell gen_log_strategy_dp() {
        if(threshold == 0) {
            for(BasicBlockTrace bb : bb_matrix) {
                threshold += bb.weight;
            }
        }
        Integer n = bb_matrix.size();
        //Integer n = 25;
        LogStrategyCell[][] f = new LogStrategyCell[n][threshold+1];
        System.out.println("Calculating: " + (n-1) + ", threshold:" + threshold);
        for(int i = 0; i < n; i ++) {
            for(int j = 0; j <= threshold; j ++) {
                f[i][j] = new LogStrategyCell();
            }
        }
        int x = n - 1;
        System.out.println("Type: " + this.cal_type);
        cal_f(f, x, threshold); // use basicblock 0 - (n-1)
        cal_translate(f[x][threshold]);

//        String final_key = Utils.gen_key(f[x][threshold].strategy);
//        DistinguishedSet final_dis = sethash.get(final_key);
//        System.out.println("We have this many distinguished set:" + final_dis.map.size());
//        for(String k : final_dis.map.keySet()) {
//            System.out.println("Paths: " + final_dis.map.get(k));
//        }


        // calculate average path length
        Long[] path_lengths = new Long[path_cnt];
        for(int i = 0; i < bb_matrix.size(); i ++) {
            for(int j = 0; j < path_cnt; j ++) {
                Long occr = bb_matrix.get(i).occur.get(j);
                if(path_lengths[j] == null) {
                    path_lengths[j] = new Long(0);
                }
                path_lengths[j] += occr;
            }
        }
        all_path_avg_length = 0.0;
        for(int i = 0; i < path_cnt; i ++) {
            all_path_avg_length += path_lengths[i] * probab_matrix.get(i);
        }

        // calculate average weight
        cal_overhead(f[x][threshold]);
        return f[x][threshold];
    }
    public Double all_path_avg_length;

    private void cal_translate2(LogStrategyCell c) {
        List<Integer> r = new ArrayList<>();
        for(Integer s : c.strategy) {
            r.add(bb_matrix.get(s).id);
        }
        for(int i = 0; i < r.size(); i ++) {
            if(r.get(i) <= 6) {
                r.set(i, r.get(i) + 2);
            } else {
                r.set(i, 10);
            }
        }
        c.translated_strategy = r;
        return;
    }

    private void cal_translate(LogStrategyCell c) {
        List<Integer> r = new ArrayList<>();
        for(Integer s : c.strategy) {
            r.add(bb_matrix.get(s).id);
        }
        c.translated_strategy = r;
        return;
    }

    private void cal_overhead(LogStrategyCell c) {
        Long[] occurances = new Long[path_cnt];
        for(int k = 0; k < c.strategy.size(); k ++) {
            int logbb_id = c.strategy.get(k);
            for(int j = 0; j < path_cnt; j ++) {
                Long occr = bb_matrix.get(logbb_id).occur.get(j);
                if(occurances[j] == null) {
                    occurances[j] = new Long(0);
                }
                occurances[j] += occr;
            }
        }
        Double avg_weight = new Double(0.0);
        if(c.strategy.size() != 0){
            for(int i = 0; i < path_cnt; i ++) {
                avg_weight += occurances[i] * probab_matrix.get(i);
            }
        }
        c.overhead = avg_weight;
    }

    private void cal_f(LogStrategyCell[][] f, int n, int threshold) {
        System.out.println("Calculation: " + n + ", " + threshold);
        if(n == 0) {
            if(bb_matrix.get(n).weight <= threshold) {
                // System.out.println("N: "  + n + ", threshold:" + threshold + ", weight: " + bb_matrix.get(n).weight);
                if(!sethash.containsKey(Utils.gen_key(f[n][threshold].strategy))) { // may need to calculate empty strategy first
                    if(this.cal_type.equals("Appearance")) {
                        f[n][threshold].entrophy = cal_entropy_for_dp_appearance(f[n][threshold], -1); // calculate entrophy with logging block n
                    } else {
                        f[n][threshold].entrophy = cal_entrophy_for_dp(f[n][threshold], -1); // calculate entrophy with logging block n
                    }
                }
                if(this.cal_type.equals("Appearance")) {
                    f[n][threshold].entrophy = cal_entropy_for_dp_appearance(f[n][threshold], n); // calculate entrophy with logging block n
                } else {
                    f[n][threshold].entrophy = cal_entrophy_for_dp(f[n][threshold], n); // calculate entrophy with logging block n
                }
                f[n][threshold].strategy.add(n);
            } else {
                if(this.cal_type.equals("Appearance")) {
                    f[n][threshold].entrophy = cal_entropy_for_dp_appearance(f[n][threshold], -1); // calculate entrophy with logging block n
                } else {
                    f[n][threshold].entrophy = cal_entrophy_for_dp(f[n][threshold], -1); // calculate entrophy with logging block n
                }
            }
            return;
        }

        if(f[n-1][threshold].entrophy == -1.0) {
            cal_f(f, n-1, threshold);
        }

        double x1 = f[n-1][threshold].entrophy;
        double x2 = -1;
        int new_threshold = threshold - bb_matrix.get(n).weight;
        if(new_threshold >= 0 &&
                f[n-1][new_threshold].entrophy == -1) {
            cal_f(f, n-1, new_threshold); // In this case we need to calculate n-1 with new_threshold
        } else {
            // System.out.println("Avoiding second choice, weight: " + bb_matrix.get(n).weight + ", new_threshold " + new_threshold);
        }
        if(new_threshold >= 0) {
            // System.out.println("Calling site 3 with "  + n + "," + threshold);
            double new_entrophy = 0.0;
            if(this.cal_type.equals("Appearance")) {
                new_entrophy = cal_entropy_for_dp_appearance(f[n-1][new_threshold], n); // calculate entrophy with logging block n
            } else {
                new_entrophy = cal_entrophy_for_dp(f[n-1][new_threshold], n); // calculate entrophy with logging block n
            }
            x2 = new_entrophy;
        }

//        if(n == 752) {
//            System.out.println("Weight: " + bb_matrix.get(n).weight + " , id: " + bb_matrix.get(n).id);
//            if(new_threshold >= 0) {
//                System.out.println("f[" + n + ", " + threshold + "] = min( f[" + (n-1)  + ","+ threshold+ "]=: " + x1 +
//                        ", f[" + (n-1) + ","+  new_threshold +"] =: (without n: " +
//                        f[n-1][new_threshold].entrophy +", with n:" + x2 + "))");
//        } else {
//                System.out.println("f[" + n + ", " + threshold + "] = min( f[" + (n-1)  + ","+ threshold+ "]=: " + x1 +
//                    ", f[" + (n-1) + ","+  new_threshold +"] =: ( N/A" + ", " + x2 +  "))");
//        }
//        }
        // here we have equal in this condition because in same situation we prefer smaller bb :)
        if (x1 <= x2 || new_threshold < 0) { // We'd better not use basicblock n
            f[n][threshold].strategy = new ArrayList<>(f[n-1][threshold].strategy); // copy old strategy
            // System.out.println("Without n Strategy: " + f[n][threshold].strategy);
            f[n][threshold].entrophy = x1;
            return;
        } else { // we should use basicblock n under this threshold
            f[n][threshold].strategy = new ArrayList<>(f[n-1][new_threshold].strategy); // copy old strategy
            f[n][threshold].strategy.add(n);
            // System.out.println("With n Strategy: " + f[n][threshold].strategy);
            f[n][threshold].entrophy = x2;
            return;
        }
    }

    private double cal_entropy_for_dp_appearance(LogStrategyCell c, int n) {
        if (n == -1) {
            // for each path, calculate its probability
            double x = 0.0;
            for(int i =0; i < probab_matrix_appearance.size(); i ++) {
                x += probab_matrix_appearance.get(i) * Math.log(probab_matrix_appearance.get(i)) / Math.log(2);
            }
            Utils.assert_exit(c.strategy.size() != 0, "When n equals to -1, the size of strategy is not zero!" + c.strategy + " Exiting.");
            if(!sethash.containsKey(Utils.gen_key(c.strategy))) {
                DistinguishedSet dset = new DistinguishedSet();
                dset.map.put("", new ArrayList<Integer>());
                for(int i = 0; i < probab_matrix_appearance.size(); i ++) {
                    dset.map.get("").add(i);
                }
                dset.entrophy = x * (-1);
                c.entrophy = dset.entrophy;
                // System.out.println("Putting " + Utils.gen_key(c.strategy) + " into sethash ... entropy: " + dset.entrophy);
                sethash.put(Utils.gen_key(c.strategy), dset);
            }
            this.total_entropy = x * (-1);
            return x * (-1);
        } else {
            // calculate the distinguished set of c + n
            // sethash: key: logging strategy, value: distinguished set and entrophy
            Utils.assert_exit(!sethash.containsKey(Utils.gen_key(c.strategy)), "Sethash does not contain a " + Utils.gen_key(c.strategy) + ". Exiting.");
            DistinguishedSet old_ds = sethash.get(Utils.gen_key(c.strategy));

            DistinguishedSet ds = cal_distinguishedset_with_oldset_appearance(c, old_ds, n);
            return ds.entrophy;
        }
    }

    private DistinguishedSet cal_distinguishedset_with_oldset_appearance(LogStrategyCell old_strategy, DistinguishedSet old_set, Integer n) {

        List<Integer> new_strategy = new ArrayList<>(old_strategy.strategy);
        new_strategy.add(n);
        if(sethash.containsKey(Utils.gen_key(new_strategy))) { // already calculated
            DistinguishedSet ds = sethash.get(Utils.gen_key(new_strategy));
//            System.out.println("[Memorized] Under set: " + new_strategy + " Distinguished set: " + ds);
            return ds;
        }
//        System.out.println("old_strategy: " + old_strategy + ", contained in sethash? " + sethash.containsKey(Utils.gen_key(old_strategy.strategy))
//                + ", entropy:" + old_strategy.entrophy);
        DistinguishedSet new_set = new DistinguishedSet();

        for(String k : old_set.map.keySet()) {
            List<Integer> s = old_set.map.get(k); // set of paths (appearance)
            for(Integer id : s) {
                // count how many n's are there in path[id]
                HashSet<Integer> h = this.appearance_paths.get(id); //path[#id]
                String new_sig;
                Integer before_sort_id = bb_matrix.get(n).id;
                if(h.contains(before_sort_id)) { // n here is the SORTED id
                    new_sig = k + "," + n;
                } else {
                    new_sig = new String(k);
                }
                if(!new_set.map.containsKey(new_sig)) {
                    new_set.map.put(new_sig, new ArrayList<Integer>());
                }
                new_set.map.get(new_sig).add(id);
            }
        }
        double r = cal_entrophy_with_distinguished_set_appearance(new_set);
        new_set.entrophy = r;
        // System.out.println("Under set: " + new_strategy  + " Distinguished set: " + new_set);
        sethash.put(Utils.gen_key(new_strategy), new_set); // remember it!
        return new_set;
    }

    private double cal_entrophy_with_distinguished_set_appearance(DistinguishedSet set) {
        double r = 0.0;
        for(String s : set.map.keySet()) {
            List<Integer> paths = set.map.get(s);
            double total = 0.0;
            // System.out.println("Key:" + s);
            for(int i = 0; i < paths.size(); i ++) {
                total += probab_matrix_appearance.get(paths.get(i));
            }
            // System.out.println("Total:" + total);
            for(int i = 0; i < paths.size(); i ++) {
                double p = probab_matrix_appearance.get(paths.get(i)) / total;
                r += probab_matrix_appearance.get(paths.get(i)) * Math.log(p) / Math.log(2);
                // System.out.println("Part:" + (p * Math.log(p) / Math.log(2)));
            }
        }
        // System.out.println("R: " + r);
        return r * (-1);
    }

    // calculate the final entrophy of log strategy C and log n
    private double cal_entrophy_for_dp(LogStrategyCell c, int n) {
        // if n equals to -1, calculate total entrophy without any logs
        if (n == -1) {
            // for each path, calculate its probability
            double x = 0.0;
            for(int i =0; i < probab_matrix.size(); i ++) {
                x += probab_matrix.get(i) * Math.log(probab_matrix.get(i)) / Math.log(2);
            }
            Utils.assert_exit(c.strategy.size() != 0, "When n equals to -1, the size of strategy is not zero!" + c.strategy + " Exiting.");
            if(!sethash.containsKey(Utils.gen_key(c.strategy))) {
                DistinguishedSet dset = new DistinguishedSet();
                dset.map.put("", new ArrayList<Integer>());
                for(int i = 0; i < probab_matrix.size(); i ++) {
                    dset.map.get("").add(i);
                }
                dset.entrophy = x * (-1);
                c.entrophy = dset.entrophy;
                // System.out.println("Putting " + Utils.gen_key(c.strategy) + " into sethash ... entropy: " + dset.entrophy);
                sethash.put(Utils.gen_key(c.strategy), dset);
            }
            this.total_entropy = x * (-1);
            return x * (-1);
        } else {
            // calculate the distinguished set of c + n
            // sethash: key: logging strategy, value: distinguished set and entrophy
            Utils.assert_exit(!sethash.containsKey(Utils.gen_key(c.strategy)), "Sethash does not contain a " + Utils.gen_key(c.strategy) + ". Exiting.");
            DistinguishedSet old_ds = sethash.get(Utils.gen_key(c.strategy));

            DistinguishedSet ds = cal_distinguishedset_with_oldset(c, old_ds, n);
            return ds.entrophy;
        }
    }

    // calculate distinguished set from scratch
    private double cal_entrophy_with_logstrategycell_deprecated(List<Integer> strategy) {
        List<Integer> new_set = strategy;
        if(sethash.containsKey(Utils.gen_key(new_set))) { // already calculated
            DistinguishedSet ds = sethash.get(Utils.gen_key(new_set));
            // System.out.println("[Memorized] Under set: " + new_set + " Distinguished set: " + ds);
            return ds.entrophy;
        } else {
            // calculate distinguised set for new_set
            DistinguishedSet s = new DistinguishedSet();
            for(int i = 0; i < probab_matrix.size(); i ++) { // for each path calculate its signature
                String sig = new String();
                for(int j = 0; j < bb_matrix.size(); j ++) {
                    if(new_set.contains(j)) {
                        sig = sig + "," + j + "-" + bb_matrix.get(j).occur.get(i);
                    }
                }
                if(!s.map.containsKey(sig)) {
                    s.map.put(sig, new ArrayList<Integer>());
                }
                s.map.get(sig).add(i);
            }

            double r = cal_entrophy_with_distinguished_set(s);
            s.entrophy = r;
            // System.out.println("Under set: " + new_set + " Distinguished set: " + s);
            sethash.put(Utils.gen_key(new_set), s);
            return r;
        }
    }

    // Calculate distinguished set incrementally
    private DistinguishedSet cal_distinguishedset_with_oldset(LogStrategyCell old_strategy, DistinguishedSet old_set, Integer n) {

        List<Integer> new_strategy = new ArrayList<>(old_strategy.strategy);
        new_strategy.add(n);
        if(sethash.containsKey(Utils.gen_key(new_strategy))) { // already calculated
            DistinguishedSet ds = sethash.get(Utils.gen_key(new_strategy));
//            System.out.println("[Memorized] Under set: " + new_strategy + " Distinguished set: " + ds);
            return ds;
        }
//        System.out.println("old_strategy: " + old_strategy + ", contained in sethash? " + sethash.containsKey(Utils.gen_key(old_strategy.strategy))
//                + ", entropy:" + old_strategy.entrophy);
        DistinguishedSet new_set = new DistinguishedSet();
        for(String k : old_set.map.keySet()) {
            List<Integer> s = old_set.map.get(k);
            for(Integer id : s) {
                // count how many n's are there in path[id]
                Long count = bb_matrix.get(n).occur.get(id);
                String new_sig = k + "," + n  + "-" + count;
                if(!new_set.map.containsKey(new_sig)) {
                    new_set.map.put(new_sig, new ArrayList<Integer>());
                }
                new_set.map.get(new_sig).add(id);
            }
        }
        double r = 0.0;
        if(this.cal_type == "Appearance"){
            r = cal_entrophy_with_distinguished_set_appearance(new_set);
        } else {
            r = cal_entrophy_with_distinguished_set(new_set);
        }
        new_set.entrophy = r;
        // System.out.println("Under set: " + new_strategy  + " Distinguished set: " + new_set);
        sethash.put(Utils.gen_key(new_strategy), new_set); // remember it!
        return new_set;
    }

    private double cal_entrophy_with_distinguished_set(DistinguishedSet set) {
        double r = 0.0;
        for(String s : set.map.keySet()) {
            List<Integer> paths = set.map.get(s);
            double total = 0.0;
            // System.out.println("Key:" + s);
            for(int i = 0; i < paths.size(); i ++) {
                total += probab_matrix.get(paths.get(i));
            }
            // System.out.println("Total:" + total);
            for(int i = 0; i < paths.size(); i ++) {
                double p = probab_matrix.get(paths.get(i)) / total;
                r += probab_matrix.get(paths.get(i)) * Math.log(p) / Math.log(2);
                // System.out.println("Part:" + (p * Math.log(p) / Math.log(2)));
            }
        }
        // System.out.println("R: " + r);
        return r * (-1);
    }

    public LogStrategyCell gen_length_log(int length, int threshold) {
        // Generate log strategy with length
        LogStrategyCell best_solution = null;
        double entropy = -1.0;
        LogStrategyCell cc = new LogStrategyCell();
        if(this.cal_type.equals("Appearance")) {
            this.total_entropy = cal_entropy_for_dp_appearance(cc, -1);
        } else {
            this.total_entropy = cal_entrophy_for_dp(cc, -1);
        }
        for(int j = 0; j < bb_matrix.size(); j ++) {
            LogStrategyCell cell = new LogStrategyCell();
            cell.strategy.add(j);
            if(threshold != 0) {
                int strategy_weight = bb_matrix.get(j).weight;
                if(strategy_weight > threshold) {
                    continue; // check next basic block
                }
            }
            cal_entropy_with_logstrategycell(cell);
            double e = cell.entrophy;
            if(entropy == -1 || e < entropy) {
                entropy = e;
                best_solution = cell;
                best_solution.entrophy = entropy;
            }
        }
        List<Integer> r = new ArrayList<>();
        for(Integer s : best_solution.strategy) { // convert to id that before the sort
            r.add(bb_matrix.get(s).id);
        }
        best_solution.translated_strategy = r;
        best_solution.entrophy = entropy;
        return best_solution;
    }

    // enumerate all possibilities - dfs
    // get the smallest entropy strategy within threshold
    // length is the number of basicblocks that eliminates the most entropy
    public LogStrategyCell gen_log_strategy_enumerate() {
        int bb_cnt = bb_matrix.size();
        BigInteger all_cnt = BigInteger.ONE;
        // all_cnt = all_cnt.shiftLeft(bb_cnt);
        all_cnt = all_cnt.shiftLeft(25);
        System.out.println("bb_cnt: " + bb_cnt + " " +
                "Total number of placments: " + all_cnt);
        LogStrategyCell best_solution = null;
        double entrophy = -1.0;
        for(BigInteger i = BigInteger.ZERO; i.compareTo(all_cnt) != 0; i = i.add(BigInteger.ONE)) {
            LogStrategyCell cell = new LogStrategyCell();
            int cost = 0;
            boolean over_budget = false;
            for(int j = 0; j < bb_cnt; j ++) {
                if((i.shiftRight(j).and(BigInteger.ONE).intValue()) == 1) {
                    cell.strategy.add(j);
                    cost += this.bb_matrix.get(j).weight;
                    if (cost > threshold) {
                        over_budget = true;
                        break;
                    }
                }
            }
            if(over_budget) {
                continue;
            }
            // get entrophy for the cell
            cal_entropy_with_logstrategycell(cell);
            double e = cell.entrophy;
            if (entrophy == -1.0 || e < entrophy) {
                entrophy = e;
                best_solution = cell;
            }
        }
        List<Integer> r = new ArrayList<>();
        for(Integer s : best_solution.strategy) { // convert to id that before the sort
            r.add(bb_matrix.get(s).id);
        }
        best_solution.translated_strategy = r;
        best_solution.entrophy = entrophy;
        return best_solution;
    }

    public Double cal_overhead_with_logstrategycell(LogStrategyCell c) {
        // weight of a strategy: for each path, calculate pi * (# of bbs in this path)
        Double avg_length = 0.0;
        for(int i = 0; i < probab_matrix.size(); i ++) {
            Long length = new Long(0);
            for(int j = 0; j < c.strategy.size(); j ++) {
                length += bb_matrix.get(c.strategy.get(j)).occur.get(i);
            }
            avg_length += length.doubleValue() * probab_matrix.get(i);
        }
        c.overhead = avg_length;
        return avg_length;
    }


    public Double cal_plain_entropy() {
        if(this.cal_type.equals("Appearance")) {
            this.total_entropy = cal_entropy_for_dp_appearance(new LogStrategyCell(), -1);
        } else {
            this.total_entropy = cal_entrophy_for_dp(new LogStrategyCell(), -1);
        }
        return this.total_entropy;
    }

    private double cal_entrophy_with_logstrategycell_appearance(List<Integer> strategy) {
        List<Integer> new_set = strategy;
        if(sethash.containsKey(Utils.gen_key(new_set))) { // already calculated
            DistinguishedSet ds = sethash.get(Utils.gen_key(new_set));
            // System.out.println("[Memorized] Under set: " + new_set + " Distinguished set: " + ds);
            return ds.entrophy;
        } else {
            // calculate distinguised set for new_set
            DistinguishedSet s = new DistinguishedSet();
            for(int i = 0; i < appearance_paths.size(); i ++) { // for each path calculate its signature
                String sig = new String();
                for(int j = 0; j < new_set.size(); j ++) {
                    Integer id = bb_matrix.get(new_set.get(j)).id;
                    if(appearance_paths.get(i).contains(id)) {
                        sig = sig + "," + new_set.get(j);
                    }
                }
                if(!s.map.containsKey(sig)) {
                    s.map.put(sig, new ArrayList<Integer>());
                }
                s.map.get(sig).add(i);
            }

            double r = cal_entrophy_with_distinguished_set_appearance(s);
            s.entrophy = r;
            // System.out.println("Under set: " + new_set + " Distinguished set: " + s);
            sethash.put(Utils.gen_key(new_set), s);
            return r;
        }
    }

    public void cal_entropy_with_logstrategycell(LogStrategyCell c) {
        double e = 0.0;
        if(this.cal_type.equals("Appearance")){
            e = cal_entrophy_with_logstrategycell_appearance(c.strategy);
        } else {
            e = cal_entrophy_with_logstrategycell_deprecated(c.strategy);
        }
        c.entrophy = e;
        return;
    }

    public void print_distinguished_set(LogStrategyCell c) {
        DistinguishedSet ds = sethash.get(Utils.gen_key(c.strategy));
        System.out.println(ds.map);
    }
}
