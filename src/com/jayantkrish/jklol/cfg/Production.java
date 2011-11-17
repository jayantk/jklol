package com.jayantkrish.jklol.cfg;

import java.util.*;

/**
 * A production is a symbol in a CFG.
 */
public class Production {

  private static Map<String, Production> cache = 
      Collections.synchronizedMap(new HashMap<String, Production>());
  private static int productionCounter = 0;

	private String displayStr;
	private int productionNumber;


	public Production(int i, String displayStr) {
		productionNumber = i;
		this.displayStr = displayStr;
	}

	public String toString() {
		return displayStr;
	}

	public int hashCode() {
		return productionNumber;
	}

	public boolean equals(Object o) {
		if (o instanceof Production) {
			Production p = (Production) o;
			return p.productionNumber == productionNumber;
		}
		return false;
	}

	public static synchronized Production getProduction(String displayStr) {
		if (cache.containsKey(displayStr)) {
			return cache.get(displayStr);
		}
		productionCounter++;
		Production p = new Production(productionCounter - 1, displayStr);
		cache.put(displayStr, p);
		return p;
	}
}