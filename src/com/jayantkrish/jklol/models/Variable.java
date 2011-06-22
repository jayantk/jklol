package com.jayantkrish.jklol.models;

import com.jayantkrish.jklol.util.*;

import java.util.Collection;
import java.util.NoSuchElementException;

public class Variable<T> {

    private String name;
    private IndexedList<T> values;
    private boolean openValueClass;

    public Variable(String name) {
	this.name = name;
	this.values = new IndexedList<T>();
	openValueClass = false;
    }

    public Variable(String name, Collection<T> values) {
	this.name = name;
	this.values = new IndexedList<T>(values);
	openValueClass = false;
    }

    /**
     * Get the index of an arbitrary value in this factor. Useful for
     * initializing things that don't care about the particular value.
     */
    public int getArbitraryValueIndex() {
	return 0;
    }

    public int numValues() {
	return values.size();
    }

    public void addValue(T value) {
	values.add(value);
    }

    public T getValue(int index) {
	return values.get(index);
    }

    public int getValueIndex(T typedValue) {
	if (!values.contains(typedValue)) {
	    if (!openValueClass) {
		throw new NoSuchElementException("Tried accessing " + typedValue + " of a closed variable class");
	    }
	    values.add(typedValue);
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
}