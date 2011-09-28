package com.jayantkrish.jklol.training;

import com.jayantkrish.jklol.models.FactorGraph;
import com.jayantkrish.jklol.util.Assignment;

/**
 * A {@link LogFunction} which doesn't log anything.
 * 
 * @author jayantk
 */
public class NullLogFunction implements LogFunction {

  @Override
  public void log(Assignment example, FactorGraph graph) {}

  @Override
  public void log(int iteration, int exampleNum, Assignment example, FactorGraph graph) {}
  
  @Override
  public void logStatistic(int iteration, String statisticName, String value) {}

  @Override
  public void notifyIterationStart(int iteration) {}

  @Override
  public void notifyIterationEnd(int iteration) {}
}
