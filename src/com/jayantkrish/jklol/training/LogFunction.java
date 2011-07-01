package com.jayantkrish.jklol.training;

import com.jayantkrish.jklol.models.Factor;
import com.jayantkrish.jklol.models.FactorGraph;
import com.jayantkrish.jklol.util.Assignment;

/**
 * Logging functionality for printing stuff out during training.
 */
public interface LogFunction {

	public void log(int iteration, int exampleNum, Assignment example, FactorGraph graph);

	public void log(int iteration, int exampleNum, Factor originalFactor, Factor marginal, FactorGraph graph);

	public void notifyIterationStart(int iteration);

	public void notifyIterationEnd(int iteration);

}