package com.jayantkrish.jklol.training;

import com.jayantkrish.jklol.models.FactorGraph;
import com.jayantkrish.jklol.util.Assignment;

/**
 * A simple default logging function.
 */ 
public class DefaultLogFunction implements LogFunction {

	@Override
	public void log(Assignment example, FactorGraph graph) {
	  System.out.println("?.?: example: " + graph.assignmentToObject(example));
	}

	@Override
	public void log(int iteration, int exampleNum, Assignment example, FactorGraph graph) {
		System.out.println(iteration + "." + exampleNum + ": example: " + graph.assignmentToObject(example));
	}

	@Override
	public void notifyIterationStart(int iteration) {
		System.out.println("*** ITERATION " + iteration + " ***");
	}

	@Override
	public void notifyIterationEnd(int iteration) {}

  @Override
  public void logStatistic(int iteration, String statisticName, String value) {
    System.out.println(iteration + ": " + statisticName + "=" + value);
  }
}
