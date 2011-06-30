package com.jayantkrish.jklol.models.loglinear;

import java.util.Set;

import com.jayantkrish.jklol.models.Factor;

/**
 * A LogLinearFactor represents a probability distribution as the exponential of a weighted sum of
 * feature functions.
 */
public interface LogLinearFactor extends Factor {

	/**
	 * Add a new feature to this factor. 
	 * @param feature
	 */
	public abstract void addFeature(FeatureFunction feature);

	/**
	 * Get the set of features that determine this factor's probability distribution. 
	 * @param feature
	 */
	public abstract Set<FeatureFunction> getFeatures();

}