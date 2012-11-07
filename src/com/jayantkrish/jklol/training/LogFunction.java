package com.jayantkrish.jklol.training;

import com.jayantkrish.jklol.models.FactorGraph;
import com.jayantkrish.jklol.models.parametric.ParametricFamily;
import com.jayantkrish.jklol.models.parametric.SufficientStatistics;
import com.jayantkrish.jklol.util.Assignment;

/**
 * Logging functionality for printing stuff out during training. Also provides
 * timer functionality.
 */
public interface LogFunction {

  public void log(Assignment example, FactorGraph graph);

  public void log(int iteration, int exampleNum, Assignment example, FactorGraph graph);
  
  public void logParameters(int iteration, SufficientStatistics parameters, 
      ParametricFamily<?> family);

  public void logStatistic(int iteration, String statisticName, double value);

  public void notifyIterationStart(int iteration);

  public void notifyIterationEnd(int iteration);

  public void startTimer(String timerName);
  
  public double stopTimer(String timerName);
}