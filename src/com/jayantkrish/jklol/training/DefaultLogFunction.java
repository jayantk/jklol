package com.jayantkrish.jklol.training;

import com.jayantkrish.jklol.models.*;

import java.util.List;

/**
 * A simple default logging function.
 */ 
public class DefaultLogFunction implements LogFunction {

    private int numAssignments;

    public DefaultLogFunction(int numAssignments) {
	this.numAssignments = numAssignments;
    }

    public void log(int iteration, int exampleNum, Assignment example, FactorGraph graph) {
	System.out.println(iteration + "." + exampleNum + ": example: " + graph.assignmentToObject(example));
    }

    public void log(int iteration, int exampleNum, DiscreteFactor originalFactor, DiscreteFactor marginal, FactorGraph graph) {
	List<Assignment> mostLikely = marginal.mostLikelyAssignments(numAssignments);
	for (Assignment a : mostLikely) {
	    double prob = marginal.getUnnormalizedProbability(a) / marginal.getPartitionFunction();
	    System.out.println(prob + " " + graph.assignmentToObject(a));
	}
    }

    public void notifyIterationStart(int iteration) {
	System.out.println("*** ITERATION " + iteration + " ***");
    }

    public void notifyIterationEnd(int iteration) {}
}

