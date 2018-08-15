package ca.utoronto.dsrg.twentyqs;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by xzhao on 3/19/17.
 */
public class LogStrategyCell {
    public LogStrategyCell(LogStrategyCell lc) {
        this.entrophy = lc.entrophy;
        if(lc.overhead != null) {
            this.overhead = new Double(lc.overhead);
        }
        this.strategy = new ArrayList<>(lc.strategy);
        this.translated_strategy = new ArrayList<>(lc.translated_strategy);
    }
    public LogStrategyCell() {
        strategy = new ArrayList<>();
        translated_strategy = new ArrayList<>();
        entrophy = -1.0;
    }
    public String toString() {
        return strategy.toString();
    }
    public double entrophy;
    public Double overhead;
    public List<Integer> strategy;
    public List<Integer> translated_strategy; // unsorted id
    // distinguished set
}
