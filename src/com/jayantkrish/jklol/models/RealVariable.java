package com.jayantkrish.jklol.models;

/**
 * RealVariable represents a real-valued variable in a graphical model. Of course,
 * since the real numbers can't be represented on a computer, the class actually uses doubles.
 * @author jayant
 *
 */
public class RealVariable implements Variable {

	@Override
	public Object getArbitraryValue() {
		return 0.0;
	}
	
	@Override
	public boolean canTakeValue(Object o) {
		return o instanceof Double;
	}
}
