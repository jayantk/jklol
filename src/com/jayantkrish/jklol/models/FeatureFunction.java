package com.jayantkrish.jklol.models;

import java.util.Iterator;
import java.util.List;

import com.jayantkrish.jklol.util.Assignment;

/**
 * A FeatureFunction is a feature in the model.
 */
public interface FeatureFunction {

    /**
     * The value of the feature for a particular assignment to
     * variable values.
     */
    public double getValue(Assignment assignment);

    /**
     * An iterator over all assignments for which the feature
     * has a non-zero value.
     */
    public Iterator<Assignment> getNonzeroAssignments();

    /**
     * The varnums which this feature operates on, returned
     * in sorted order.
     */
    public List<Integer> getVarNums();
}