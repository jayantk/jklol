package com.jayantkrish.jklol.models;

import com.jayantkrish.jklol.models.VariableProtos.VariableProto;

/**
 * A random variable that takes on some set of Object values.
 * 
 * @author jayant
 */
public interface Variable {

	/**
	 * Get an arbitrary value which can be assigned to this variable. Useful for
	 * initializing things that don't care about the particular value.
	 */
	public Object getArbitraryValue();

	/**
	 * Returns true if value can be legitimately assigned to this variable.
	 */
	public boolean canTakeValue(Object value);
	
	/**
	 * Serializes {@code this} into a protocol buffer.
	 *  
	 * @return
	 */
	public VariableProto toProto();
}
