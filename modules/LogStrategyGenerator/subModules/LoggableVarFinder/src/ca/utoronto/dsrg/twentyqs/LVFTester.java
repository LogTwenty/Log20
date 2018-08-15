package ca.utoronto.dsrg.twentyqs;

import java.util.LinkedList;

public class LVFTester {

	public static void main(String[] args) {
		LinkedList<Integer> loggedBlockIds = new LinkedList<>();
		loggedBlockIds.add(0);
		LinkedList<Integer> disambiguatedBlockIds = new LinkedList<>();
		LoggableVarFinder.getDisambiguatedBlockIds("org.apache.hadoop.io.Text$1.initialValue()",
				loggedBlockIds, disambiguatedBlockIds);
		
		System.out.println(disambiguatedBlockIds.toString());
	}
}