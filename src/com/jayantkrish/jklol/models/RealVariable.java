package com.jayantkrish.jklol.models;

import com.google.common.base.Preconditions;

/**
 * RealVariable represents a real-valued vector variable in a graphical model. Of course,
 * since the real numbers can't be represented on a computer, the class actually uses doubles.
 * @author jayant
 *
 */
public class RealVariable implements Variable {
	
	private int numDimensions;
	
	public RealVariable(int numDimensions) {
		Preconditions.checkArgument(numDimensions > 0);
		this.numDimensions = numDimensions;
	}
	
	public int numDimensions() {
		return numDimensions;
	}

	@Override
	public Object getArbitraryValue() {
		return 0.0;
	}
	
	@Override
	public boolean canTakeValue(Object o) {
		return o instanceof Double;
	}
}
