package com.jayantkrish.jklol.training;

import java.util.Collections;
import java.util.List;

import com.google.common.collect.Lists;
import com.jayantkrish.jklol.models.FactorGraph;
import com.jayantkrish.jklol.util.Assignment;

/**
 * A simple default logging function.
 */ 
public class DefaultLogFunction extends AbstractLogFunction {

  public DefaultLogFunction() { super(); }
  
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
		startTimer("iteration");
	}

	@Override
	public void notifyIterationEnd(int iteration) {
	  long elapsedTime = stopTimer("iteration");
	  System.out.println(iteration + " done. Elapsed: " + elapsedTime + " ms");
	  printTimeStatistics();
	}

  @Override
  public void logStatistic(int iteration, String statisticName, String value) {
    System.out.println(iteration + ": " + statisticName + "=" + value);
  }
  
  public void printTimeStatistics() {
    System.out.println("Elapsed time statistics:");

    List<String> timers = Lists.newArrayList(getAllTimers());
    Collections.sort(timers);
    for (String timer : timers) {
      long total = getTimerElapsedTime(timer);
      long invocations = getTimerInvocations(timer);
      double average = ((double) total) / invocations;
      System.out.println(timer + ": " +  total + " ms (" + average + " * " + invocations + ")");
    }
  }
}
