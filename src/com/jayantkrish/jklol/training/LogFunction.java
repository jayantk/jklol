package com.jayantkrish.jklol.training;

import com.jayantkrish.jklol.models.FactorGraph;
import com.jayantkrish.jklol.models.parametric.SufficientStatistics;
import com.jayantkrish.jklol.util.Assignment;

/**
 * Logging functionality for printing stuff out during training. Also provides
 * timer functionality.
 */
public interface LogFunction {

  public void log(Assignment example, FactorGraph graph);

  public void log(long iteration, int exampleNum, Assignment example, FactorGraph graph);
  
  public void logMessage(Object message); 
  
  public void logParameters(long iteration, SufficientStatistics parameters);

  public void logStatistic(long iteration, String statisticName, double value);

  public void notifyIterationStart(long iteration);

  public void notifyIterationEnd(long iteration);

  public void startTimer(String timerName);
  
  public double stopTimer(String timerName);
}