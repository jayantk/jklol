package com.jayantkrish.jklol.evaluation;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.google.common.collect.Lists;
import com.jayantkrish.jklol.inference.MarginalCalculator;
import com.jayantkrish.jklol.inference.MarginalSet;
import com.jayantkrish.jklol.inference.MaxMarginalSet;
import com.jayantkrish.jklol.models.Factor;
import com.jayantkrish.jklol.models.FactorGraph;
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.models.dynamic.DynamicAssignment;
import com.jayantkrish.jklol.models.dynamic.DynamicFactorGraph;
import com.jayantkrish.jklol.models.dynamic.VariablePattern;
import com.jayantkrish.jklol.models.dynamic.VariablePattern.VariableMatch;
import com.jayantkrish.jklol.util.Assignment;

/**
 * Adapts a {@link FactorGraph} to the {@code Predictor} interface. Predictions
 * are made in terms of {@code DynamicAssignments} to the underlying factor
 * graph. The predicted assignment for a given inputVar is the assignment with
 * the highest conditional probability given the inputVar.
 * 
 * <p>
 * Internally, this class uses a marginal calculator to perform inference on the
 * given factor graph instance to determine the most likely predictions, etc. If
 * the marginal calculator given to this class is approximate, then so are the
 * predictions of this {@code Predictor}.
 * 
 * @author jayantk
 */
public class FactorGraphPredictor implements Predictor<DynamicAssignment, DynamicAssignment> {

  private final DynamicFactorGraph factorGraph;
  private final VariablePattern outputVariablePattern;
  private final MarginalCalculator marginalCalculator;

  // A cache for the partition function of the {@code factorGraph}.
  private final Map<DynamicAssignment, Double> partitionFunctionCache;
  // The number of entries to store in the cache. Storing entries is fairly
  // cheap relative to computing partition functions, hence it makes sense to
  // use a fairly large value.
  private static final int LRU_CACHE_SIZE = 1000;

  /**
   * Creates a {@code FactorGraphPredictor} which makes predictions about
   * assignments to {@code factorGraph} using {@code marginalCalculator} to
   * perform any necessary inference operations. The predicted assignments are
   * over {@code outputVariablePattern}.
   * 
   * @param factorGraph
   * @param marginalCalculator
   */
  @SuppressWarnings("serial")
  public FactorGraphPredictor(DynamicFactorGraph factorGraph, VariablePattern outputVariablePattern,
      MarginalCalculator marginalCalculator) {
    this.factorGraph = factorGraph;
    this.outputVariablePattern = outputVariablePattern;
    this.marginalCalculator = marginalCalculator;

    partitionFunctionCache = Collections.synchronizedMap(
        new LinkedHashMap<DynamicAssignment, Double>(LRU_CACHE_SIZE, 0.75f, true) {
          protected boolean removeEldestEntry(Map.Entry<DynamicAssignment, Double> entry) {
            return size() > LRU_CACHE_SIZE;
          }
        });
  }

  /**
   * Gets the {@code FactorGraph} used by {@code this} to make predictions.
   * 
   * @return
   */
  public DynamicFactorGraph getFactorGraph() {
    return factorGraph;
  }

  @Override
  public DynamicAssignment getBestPrediction(DynamicAssignment dynamicInput) {
    // Compute max-marginals conditioned on the input
    Assignment input = factorGraph.getVariables().toAssignment(dynamicInput);
    FactorGraph conditionalFactorGraph = factorGraph.getFactorGraph(dynamicInput).conditional(input);
    MaxMarginalSet maxMarginals = marginalCalculator.computeMaxMarginals(conditionalFactorGraph);

    Assignment output = maxMarginals.getNthBestAssignment(0).intersection(
        getOutputVariables(conditionalFactorGraph, outputVariablePattern));
    return factorGraph.getVariables().toDynamicAssignment(output, conditionalFactorGraph.getAllVariables());
  }

  /**
   * {@inheritDoc}
   * 
   * At the moment, this method returns the outputVar assignments with the
   * highest marginal probability. Note that this makes this method incompatible
   * with {@link #getBestPrediction()}, which returns the most likely single
   * assignment (i.e., based on a max-marginal). If {@code inputVar} is not a
   * valid assignment to the underlying graphical model, returns the empty list.
   */
  @Override
  public List<DynamicAssignment> getBestPredictions(DynamicAssignment input, int numBest) {
    if (!factorGraph.getVariables().isValidAssignment(input)) {
      return Collections.emptyList();
    }

    FactorGraph conditionalFactorGraph = factorGraph.conditional(input);
    MarginalSet marginals = marginalCalculator.computeMarginals(conditionalFactorGraph);
    Factor outputVarsMarginal = marginals.getMarginal(
        getOutputVariables(conditionalFactorGraph, outputVariablePattern).getVariableNums());

    List<DynamicAssignment> output = Lists.newArrayList();
    for (Assignment a : outputVarsMarginal.getMostLikelyAssignments(numBest)) {
      output.add(factorGraph.getVariables().toDynamicAssignment(a, conditionalFactorGraph.getAllVariables()));
    }
    return output;
  }

  /**
   * {@inheritDoc}
   * 
   * The probability of an {@code inputVar}/{@code outputVar} pair is taken to
   * be the conditional probability of {@code outputVar} given {@code inputVar}.
   * This method conditions on {@code inputVar}, computes marginals, and returns
   * the marginal probability of {@code outputVar}. If either of
   * {@code inputVar} or {@code outputVar} are not valid assignments to the
   * underlying factor graph, this method returns 0.
   */
  @Override
  public double getProbability(DynamicAssignment input, DynamicAssignment output) {
    if (!(factorGraph.getVariables().isValidAssignment(output) && factorGraph.getVariables().isValidAssignment(input))) {
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

  private static VariableNumMap getOutputVariables(FactorGraph conditionalFactorGraph,
      VariablePattern outputPattern) {
    // Identify which variables in the factor graph should be output as
    // predictions.
    List<VariableMatch> matches = outputPattern.matchVariables(conditionalFactorGraph.getAllVariables());
    VariableNumMap outputVariables = VariableNumMap.emptyMap();
    for (VariableMatch match : matches) {
      outputVariables = outputVariables.union(match.getMatchedVariables());
    }
    return outputVariables;
  }

  /**
   * Simpler version of a {@code FactorGraphPredictor} that doesn't worry about
   * {@code DynamicAssignments}.
   * 
   * @author jayantk
   */
  public static class SimpleFactorGraphPredictor implements Predictor<Assignment, Assignment> {
    
    private FactorGraphPredictor predictor;
    
    public SimpleFactorGraphPredictor(FactorGraph factorGraph, 
        VariableNumMap outputVariables, MarginalCalculator marginalCalculator) {
      predictor = new FactorGraphPredictor(DynamicFactorGraph.fromFactorGraph(factorGraph),
          VariablePattern.fromVariableNumMap(outputVariables), marginalCalculator);
    }

    @Override
    public Assignment getBestPrediction(Assignment input) {
      return predictor.getBestPrediction(DynamicAssignment.fromAssignment(input))
          .getFixedAssignment();
    }

    @Override
    public List<Assignment> getBestPredictions(Assignment input, int numBest) {
      List<DynamicAssignment> predictions = predictor.getBestPredictions(DynamicAssignment.fromAssignment(input), numBest);
      List<Assignment> assignments = Lists.newArrayList();
      for (DynamicAssignment prediction : predictions) {
        assignments.add(prediction.getFixedAssignment());
      }
      return assignments;
    }

    @Override
    public double getProbability(Assignment input, Assignment output) {
      return predictor.getProbability(DynamicAssignment.fromAssignment(input),
          DynamicAssignment.fromAssignment(output));
    }
  }
}
