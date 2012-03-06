package com.jayantkrish.jklol.models;

import com.jayantkrish.jklol.models.FactorGraphProtos.VariableProto;

/**
 * A Variable represents a random variable which can take on some set of Object values.
 * Typing in Java doesn't handle dynamically-generated lists of types particularly well,
 * so Variables always operate on Objects. However, it is good practice to check that 
 * an Object is within the domain of a Variable by using the canTakeValue() method. 
 * 
 * @author jayant
 *
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
