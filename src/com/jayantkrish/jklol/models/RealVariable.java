package com.jayantkrish.jklol.models;

import com.google.common.base.Preconditions;
import com.jayantkrish.jklol.tensor.SparseTensor;

/**
 * RealVariable represents a real-valued vector variable in a graphical model. 
 * 
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
		SparseTensor value = SparseTensor.empty(new int[]{0}, new int[] {numDimensions});
		return value;
	}
	
	@Override
	public boolean canTakeValue(Object o) {
		return o instanceof SparseTensor && 
		    ((SparseTensor) o).getDimensionNumbers().length == 1;
	}
}
