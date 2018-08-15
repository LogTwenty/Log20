package ca.utoronto.dsrg.twentyqs;

import java.util.ArrayList;

/**
 * Created by xzhao on 3/23/17.
 */
public class BasicBlockTrace {
    public BasicBlockTrace() {
        occur = new ArrayList<>();
    }
    public String toString() {
        return this.occur.toString() + " " + weight;
    }

    public Integer id;
    public Integer weight;
    public ArrayList<Long> occur;
}
