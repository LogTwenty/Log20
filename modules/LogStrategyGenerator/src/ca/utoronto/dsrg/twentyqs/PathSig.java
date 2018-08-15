package ca.utoronto.dsrg.twentyqs;

import org.apache.commons.codec.digest.DigestUtils;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

/**
 * Created by xzhao on 4/4/17.
 */
public class PathSig {
    public PathSig() {
        this.occurance = new Long(0);
        this.id = new Integer(-1);
    }
    public static String gen_sig_for_path(List<Long> path) {
        StringBuilder sb = new StringBuilder();
        for(Long k : path) {
            sb.append(k).append(",");
        }
        String ss = sb.toString();
        String s = DigestUtils.sha1Hex(ss);
        return s;
    }
    public static String gen_sig_for_path(HashMap<Integer, Long> path) {
        StringBuilder sb = new StringBuilder();
        for(Integer k : path.keySet()) {
            sb.append(k).append("-").append(path.get(k)).append(",");
        }
        String ss = sb.toString();
        String s = DigestUtils.sha1Hex(ss);
        return s;
    }

    public static String gen_sig_for_path_appearance(HashSet<Integer> path) {
        StringBuilder sb = new StringBuilder();
        for(Integer k : path) {
            sb.append(k).append(" ");
        }
        String ss = sb.toString();
        String s = DigestUtils.sha1Hex(ss);
        return s;
    }
    Integer id;
    Long occurance;
}
