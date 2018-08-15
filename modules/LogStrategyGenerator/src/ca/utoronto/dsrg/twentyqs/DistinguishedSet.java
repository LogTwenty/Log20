package ca.utoronto.dsrg.twentyqs;

import java.util.HashMap;
import java.util.List;

/**
 * Created by xzhao on 3/20/17.
 */
// map each
public class DistinguishedSet {
    DistinguishedSet() {
        map = new HashMap<>();
        entrophy = -1.0;
    }
    HashMap<String, List<Integer>> map; // key:
    public String toString() {
        return (map.toString() + ", entrophy: " + entrophy);
    }
    double entrophy;
}
