package com.jayantkrish.jklol.evaluation;

import java.util.Collections;
import java.util.List;

import com.jayantkrish.jklol.inference.MarginalCalculator;
import com.jayantkrish.jklol.inference.MarginalSet;
import com.jayantkrish.jklol.inference.MaxMarginalSet;
import com.jayantkrish.jklol.models.Factor;
import com.jayantkrish.jklol.models.FactorGraph;
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.util.Assignment;

/**
 * Adapts a {@link FactorGraph} to the {@code Predictor} interface. Predictions
 * are made in terms of {@code Assignments} to the underlying factor graph. The
 * predicted assignment for a given input is the assignment with the highest
 * conditional probability given the input.
 * 
 * <p>
 * Internally, this class uses a marginal calculator to perform inference on the
 * given factor graph instance to determine the most likely predictions, etc. If
 * the marginal calculator given to this class is approximate, then so are the
 * predictions of this {@code Predictor}.
 * 
 * @author jayantk
 */
public class FactorGraphPredictor implements Predictor<Assignment, Assignment> {

  private final FactorGraph factorGraph;
  private final VariableNumMap outputVariables;
  private final MarginalCalculator marginalCalculator;
  
  // The unconditional partition function of {@code factorGraph}
  private double partitionFunction;

  /**
   * Creates a {@code FactorGraphPredictor} which makes predictions about 
   * assignments to {@code factorGraph} using {@code marginalCalculator} to
   * perform any necessary inference operations. The predicted assignments are
   * over {@code outputVariables}. 
   * 
   * @param factorGraph
   * @param marginalCalculator
   */
  public FactorGraphPredictor(FactorGraph factorGraph, VariableNumMap outputVariables,
      MarginalCalculator marginalCalculator) {
    this.factorGraph = factorGraph;
    this.outputVariables = outputVariables;
    this.marginalCalculator = marginalCalculator;
    
    partitionFunction = -1.0;
  }

  @Override
  public Assignment getBestPrediction(Assignment input) {
    MaxMarginalSet maxMarginals = marginalCalculator.computeMaxMarginals(factorGraph, input);
    return maxMarginals.getNthBestAssignment(0).subAssignment(outputVariables);
  }

  /**
   * {@inheritDoc}
   * 
   * At the moment, this method returns the output assignments with the highest
   * marginal probability. Note that this makes this method incompatible with
   * {@link #getBestPrediction()}, which returns the most likely single
   * assignment (i.e., based on a max-marginal). If {@code input} is not a valid
   * assignment to the underlying graphical model, returns the empty list.
   */
  @Override
  public List<Assignment> getBestPredictions(Assignment input, int numBest) {
    if (!factorGraph.getVariableNumMap().isValidAssignment(input)) {
      return Collections.emptyList();
    }

    MarginalSet marginals = marginalCalculator.computeMarginals(factorGraph, input);
    Factor outputVarsMarginal = marginals.getMarginal(outputVariables.getVariableNums());
    return outputVarsMarginal.getMostLikelyAssignments(numBest);
  }

  /**
   * {@inheritDoc}
   * 
   * The probability of an {@code input}/{@code output} pair is taken to be the
   * conditional probability of {@code output} given {@code input}. This method
   * conditions on {@code input}, computes marginals, and returns the marginal
   * probability of {@code output}. If either of {@code input} or {@code output}
   * are not valid assignments to the underlying factor graph, this method
   * returns 0.
   */
  @Override
  public double getProbability(Assignment input, Assignment output) {
    if (!(outputVariables.isValidAssignment(output) && factorGraph.getVariableNumMap().isValidAssignment(input))) {
      return 0.0;
    }

    double inputPartitionFunction = 0.0;
    if (input.size() == 0) {
      if (partitionFunction < 0) {
        MarginalSet inputMarginals = marginalCalculator.computeMarginals(factorGraph, input);
        partitionFunction = inputMarginals.getPartitionFunction();
      }
      inputPartitionFunction = partitionFunction;        
    } else {
      MarginalSet inputMarginals = marginalCalculator.computeMarginals(factorGraph, input);
      inputPartitionFunction = inputMarginals.getPartitionFunction();
    }
    
    MarginalSet inputOutputMarginals = marginalCalculator.computeMarginals(
        factorGraph, input.jointAssignment(output));
    return inputOutputMarginals.getPartitionFunction() / inputPartitionFunction;
  }
}
