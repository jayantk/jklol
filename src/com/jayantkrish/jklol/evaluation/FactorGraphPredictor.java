package com.jayantkrish.jklol.evaluation;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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

  // A cache for the partition function of the {@code factorGraph}.
  private final Map<Assignment, Double> partitionFunctionCache;
  // The number of entries to store in the cache. Storing entries is fairly
  // cheap relative to computing partition functions, hence it makes sense to
  // use a fairly large value.
  private static final int LRU_CACHE_SIZE = 1000;

  /**
   * Creates a {@code FactorGraphPredictor} which makes predictions about
   * assignments to {@code factorGraph} using {@code marginalCalculator} to
   * perform any necessary inference operations. The predicted assignments are
   * over {@code outputVariables}.
   * 
   * @param factorGraph
   * @param marginalCalculator
   */
  @SuppressWarnings("serial")
  public FactorGraphPredictor(FactorGraph factorGraph, VariableNumMap outputVariables,
      MarginalCalculator marginalCalculator) {
    this.factorGraph = factorGraph;
    this.outputVariables = outputVariables;
    this.marginalCalculator = marginalCalculator;
    
    partitionFunctionCache = Collections.synchronizedMap(
        new LinkedHashMap<Assignment, Double>(LRU_CACHE_SIZE, 0.75f, true) {
          protected boolean removeEldestEntry(Map.Entry<Assignment, Double> entry ) {
            return size() > LRU_CACHE_SIZE;
          }
        });
  }

  /**
   * Gets the {@code FactorGraph} used by {@code this} to make predictions.
   * 
   * @return
   */
  public FactorGraph getFactorGraph() {
    return factorGraph;
  }

  @Override
  public Assignment getBestPrediction(Assignment input) {
    FactorGraph conditionalFactorGraph = factorGraph.conditional(input);
    MaxMarginalSet maxMarginals = marginalCalculator.computeMaxMarginals(conditionalFactorGraph);
    return maxMarginals.getNthBestAssignment(0).intersection(outputVariables);
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
    if (!factorGraph.getVariables().isValidAssignment(input)) {
      return Collections.emptyList();
    }

    FactorGraph conditionalFactorGraph = factorGraph.conditional(input);
    MarginalSet marginals = marginalCalculator.computeMarginals(conditionalFactorGraph);
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
    if (!(outputVariables.isValidAssignment(output) && factorGraph.getVariables().isValidAssignment(input))) {
      return 0.0;
    }

    double inputPartitionFunction = 0.0;
    if (partitionFunctionCache.containsKey(input)) {
      inputPartitionFunction = partitionFunctionCache.get(input);
    } else {
      FactorGraph conditionalFactorGraph = factorGraph.conditional(input);
      MarginalSet inputMarginals = marginalCalculator.computeMarginals(conditionalFactorGraph);
      inputPartitionFunction = inputMarginals.getPartitionFunction();
      partitionFunctionCache.put(input, inputPartitionFunction);
    }

    FactorGraph conditionalFactorGraph = factorGraph.conditional(input.union(output));
    MarginalSet inputOutputMarginals = marginalCalculator.computeMarginals(conditionalFactorGraph);
    return inputOutputMarginals.getPartitionFunction() / inputPartitionFunction;
  }
}
