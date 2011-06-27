package com.jayantkrish.jklol.models;

import com.jayantkrish.jklol.util.*;

import java.util.Collection;
import java.util.NoSuchElementException;

public class Variable<T> {

	private String name;
	private IndexedList<T> values;

	public Variable(String name, Collection<T> values) {
		this.name = name;
		this.values = new IndexedList<T>(values);
	}

	/**
	 * Get an arbitrary value v which can be assigned to this variable. Useful for
	 * initializing things that don't care about the particular value.
	 */
	public Object getArbitraryValue() {
		return 0;
	}

	/**
	 * Get the number of possible values that this variable can take on.
	 * @return
	 */
	public int numValues() {
		return values.size();
	}
	
	/**
	 * Returns true if value is a legitimate setting for this variable.
	 * @param value
	 * @return
	 */
	public boolean canTakeValue(Object value) {	
		return values.contains((T) value);
	}

	public T getValue(int index) {
		return values.get(index);
	}

	public int getValueIndex(T typedValue) {
		if (!values.contains(typedValue)) {
			throw new NoSuchElementException("Tried accessing " + typedValue + " of a closed variable class");
		}
		return values.getIndex(typedValue);
	}

	/**
	 * You must call this with appropriately typed objects (i.e., of type T).
	 * This method exists so that other parts of the framework do not have to 
	 * track the types of each variable.
	 */
	public int getValueIndexObject(Object value) {
		T typedValue = (T) value;
		return getValueIndex(typedValue);
	}

	public String toString() {
		return values.toString();
	}

	public boolean equals(Object o) {
		if (o instanceof Variable<?>) {
			Variable<? >v = (Variable<?>) o;
			return name.equals(v.name) && values.equals(v.values); 
		}
		return false;
	}
}