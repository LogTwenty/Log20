package ca.utoronto.dsrg.twentyqs;

import java.util.List;

/**
 * Created by xzhao on 3/24/17.
 */
public class Utils {

    public static String gen_key(List<Integer> list) {
        return list.toString();
    }

    public static void assert_exit(boolean t, String desc) {
        if(t) {
            System.out.println(desc);
            System.exit(1);
        } else {
            return;
        }
    }
}
