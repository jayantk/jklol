package com.jayantkrish.jklol.training;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.google.common.collect.Lists;
import com.jayantkrish.jklol.models.FactorGraph;
import com.jayantkrish.jklol.util.Assignment;

/**
 * A simple default logging function.
 */ 
public class DefaultLogFunction extends AbstractLogFunction {
  
  private final int logInterval;
  
  // Print asynchronously for speed.
  private final ExecutorService printExecutor;

  public DefaultLogFunction() {
    super();
    this.logInterval = 1; 
    this.printExecutor = Executors.newSingleThreadExecutor();
  }
  
  public DefaultLogFunction(int logInterval) { 
    super(); 
    this.logInterval = logInterval;
    this.printExecutor = Executors.newSingleThreadExecutor();
  }
  
  private void print(String toPrint) {
    printExecutor.submit(new PrintTask(toPrint));
  }
  
	@Override
	public void log(Assignment example, FactorGraph graph) {
	  print("?.?: example: " + graph.assignmentToObject(example));
	}

	@Override
	public void log(int iteration, int exampleNum, Assignment example, FactorGraph graph) {
	  if (iteration % logInterval == 0) {
	    String prob = "";
	    if (example.containsAll(graph.getVariables().getVariableNums())) {
	      prob = Double.toString(graph.getUnnormalizedLogProbability(example));
	    } 
	    print(iteration + "." + exampleNum + " " + prob + ": example: " + graph.assignmentToObject(example));
	  }
	}

	@Override
	public void notifyIterationStart(int iteration) {
	  if (iteration % logInterval == 0) {
	    print("*** ITERATION " + iteration + " ***");
	  }
		startTimer("iteration");
	}

	@Override
	public void notifyIterationEnd(int iteration) {
	  long elapsedTime = stopTimer("iteration");
	  if (iteration % logInterval == 0) {
	    print(iteration + " done. Elapsed: " + elapsedTime + " ms");
	    printTimeStatistics();
	  }
	}

  @Override
  public void logStatistic(int iteration, String statisticName, String value) {
    if (iteration % logInterval == 0) {
      print(iteration + ": " + statisticName + "=" + value);
    }
  }
  
  public void printTimeStatistics() {
    print("Elapsed time statistics:");

    List<String> timers = Lists.newArrayList(getAllTimers());
    Collections.sort(timers);
    for (String timer : timers) {
      long total = getTimerElapsedTime(timer);
      long totalSecs = total / 1000;
      long totalDecimal = total % 1000;
      long invocations = getTimerInvocations(timer);
      double average = ((double) total) / invocations;
      print(timer + ": " +  totalSecs + "." + totalDecimal + " s (" + average + " * " + invocations + ")");
    }
  }
  
  private static class PrintTask implements Runnable {
    
    private final String toPrint;
    
    public PrintTask(String toPrint) { 
      this.toPrint = toPrint;
    }

    @Override
    public void run() {
      System.out.println(toPrint);      
    }
  }
}
