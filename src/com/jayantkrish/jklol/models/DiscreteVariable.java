package com.jayantkrish.jklol.models;

import com.jayantkrish.jklol.util.*;

import java.util.Collection;
import java.util.NoSuchElementException;

/**
 * Represents a discrete random variable type which takes on one value from a 
 * finite set of values. Variables are immutable.
 * 
 * @author jayant
 */
public class DiscreteVariable implements Variable {

	private String name;
	private IndexedList<Object> values;

	public DiscreteVariable(String name, Collection<? extends Object> values) {
		this.name = name;
		this.values = new IndexedList<Object>(values);
	}

	@Override
	public Object getArbitraryValue() {
		return values.get(0);
	}

	@Override 
	public boolean canTakeValue(Object value) {	
		return values.contains(value);
	}

	/**
	 * Get the number of possible values that this variable can take on.
	 * @return
	 */
	public int numValues() {
		return values.size();
	}

	/**
	 * Get the value of this enum with the passed-in index. 
	 * @param index
	 * @return
	 */
	public Object getValue(int index) {
		return values.get(index);
	}

	/**
	 * Get an integer index which represents the passed in value. Throws a NoSuchElement 
	 * exception if value is not among the set of values this variable can be assigned.     
	 */
	public int getValueIndex(Object value) {
		if (!values.contains(value)) {
			throw new NoSuchElementException("Tried accessing " + value + " of an enumerated variable type");
		}
		return values.getIndex(value);
	}

	public String toString() {
		return name;
	}

	public boolean equals(Object o) {
		if (o instanceof DiscreteVariable) {
			DiscreteVariable v = (DiscreteVariable) o;
			return name.equals(v.name) && values.equals(v.values); 
		}
		return false;
	}
}