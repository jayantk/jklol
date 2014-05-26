package com.jayantkrish.jklol.training;

import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.jayantkrish.jklol.models.FactorGraph;
import com.jayantkrish.jklol.models.parametric.SufficientStatistics;
import com.jayantkrish.jklol.util.Assignment;
import com.jayantkrish.jklol.util.IoUtils;

/**
 * A simple default logging function.
 */ 
public class DefaultLogFunction extends AbstractLogFunction {
  
  private final int logInterval;
  private final boolean showExamples;
  
  private final Map<String, Double> statistics;

  private final int modelSerializationInterval;
  private final String modelSerializationDir;

  // Print asynchronously for speed.
  private final ExecutorService printExecutor;

  public DefaultLogFunction() {
    super();
    this.logInterval = 1;
    this.showExamples = true;
    this.printExecutor = Executors.newSingleThreadExecutor();
    
    this.statistics = Maps.newHashMap();
    
    this.modelSerializationInterval = -1;
    this.modelSerializationDir = null;
  }

  public DefaultLogFunction(int logInterval, boolean showExamples) { 
    super();
    this.logInterval = logInterval;
    this.showExamples = showExamples;
    this.printExecutor = Executors.newSingleThreadExecutor();
    
    this.statistics = Maps.newHashMap();
    
    this.modelSerializationInterval = -1;
    this.modelSerializationDir = null;
  }

  public DefaultLogFunction(int logInterval, boolean showExamples, int modelSerializationInterval,
      String modelSerializationDir) {
    super();
    this.logInterval = logInterval;
    this.showExamples = showExamples;
    this.printExecutor = Executors.newSingleThreadExecutor();

    this.statistics = Maps.newHashMap();

    Preconditions.checkArgument(modelSerializationInterval <= 0 || modelSerializationDir != null);
    this.modelSerializationInterval = modelSerializationInterval;
    this.modelSerializationDir = modelSerializationDir;
  }

  protected void print(String toPrint) {
    System.out.println(toPrint);
    // printExecutor.submit(new PrintTask(toPrint));
  }

	@Override
	public void log(Assignment example, FactorGraph graph) {
	  if (showExamples) {
	    print("?.?: example: " + graph.assignmentToObject(example));
	  }
	}

	@Override
	public void log(int iteration, int exampleNum, Assignment example, FactorGraph graph) {
	  if (showExamples) {
	    if (iteration % logInterval == 0) {
	      String prob = "";
	      if (example.containsAll(graph.getVariables().getVariableNumsArray())) {
	        prob = Double.toString(graph.getUnnormalizedLogProbability(example));
	      } 
	      print(iteration + "." + exampleNum + " " + prob + ": example: " + graph.assignmentToObject(example));
	    }
	  }
	}
	
	@Override
	public void logMessage(Object message) {
	  print(message.toString());
	}
	
	@Override
	public void logParameters(int iteration, SufficientStatistics parameters) {
	  if (modelSerializationInterval > 0 && iteration % modelSerializationInterval == 0) {
	    String parametersFilename = modelSerializationDir + File.separator + "parameters_"
	        + iteration + ".ser";
	    IoUtils.serializeObjectToFile(parameters, parametersFilename);
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
	  double elapsedTime = stopTimer("iteration");
	  if (iteration % logInterval == 0) {
	    print(iteration + " done. Elapsed: " + elapsedTime + " ms");
	    printTimeStatistics();
	  }
	}

  @Override
  public void logStatistic(int iteration, String statisticName, double value) {
    if (iteration % logInterval == 0) {
      print(iteration + ": " + statisticName + "=" + value);
    }
    statistics.put(statisticName, value);
  }

  public double getLastStatisticValue(String statisticName) {
    return statistics.get(statisticName);
  }

  public void printTimeStatistics() {
    print("Elapsed time statistics:");

    List<String> timers = Lists.newArrayList(getAllTimers());
    Collections.sort(timers);
    for (String timer : timers) {
      double total = (double) getTimerElapsedTime(timer);
      long invocations = getTimerInvocations(timer);
      double average = total / invocations;
      print(String.format("%s: %.3f sec (%.3f ms * %d)", timer, (total / 1000), average, invocations));
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
