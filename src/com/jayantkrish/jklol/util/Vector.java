package com.jayantkrish.jklol.util;

import java.util.Arrays;

import com.google.common.base.Preconditions;

/**
 * A Vector in the math sense.
 * @author jayant
 *
 */
public class Vector {

	double[] values;
	
	public Vector(double[] values) {
		this.values = values;
	}

	/**
	 * Copy constructor.
	 * @param vector
	 */
	public Vector(Vector vector) {
		this.values = Arrays.copyOf(vector.values, vector.values.length);
	}

	/**
	 * The dimensionality of the vector.
	 * @return
	 */
	public int numDimensions() {
		return values.length;
	}
	
	/**
	 * The value of the given index of the vector.  
	 * @param index
	 * @return
	 */
	public double get(int index) {
		Preconditions.checkArgument(index >= 0);
		Preconditions.checkArgument(index < values.length);
		return values[index];
	}
	
	/**
	 * Increments the value of this vector. The resulting value of index i
	 * is equal to this.get(i) + other.get(i). other must have the same number
	 * of dimensions as this vector. 
	 * @param other
	 * @return
	 */
	public void addTo(Vector other) {
		Preconditions.checkNotNull(other);
		Preconditions.checkArgument(numDimensions() == other.numDimensions());
		for (int i = 0; i < other.numDimensions(); i++) {
			values[i] += other.get(i);
		}
	}
	
	/**
	 * Increments the value of a particular entry of this vector.
	 * @param index
	 * @param value
	 */
	public void addTo(int index, double value) {
		Preconditions.checkArgument(index >= 0 && index < numDimensions());
		values[index] += value;
	}

	/**
	 * Returns a constant-valued vector. 
	 * @param numDimensions The number of dimensions  
	 * @param value The value of each entry.
	 * @return
	 */
	public static Vector constantVector(int numDimensions, double value) {
		double[] valueArray = new double[numDimensions];
		for (int i = 0; i < numDimensions; i++) {
			valueArray[i] = value;
		}
		return new Vector(valueArray);
	}	
}
