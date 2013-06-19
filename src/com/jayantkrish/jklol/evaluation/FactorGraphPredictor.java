package com.jayantkrish.jklol.evaluation;

import java.util.Collections;
import java.util.List;

import com.google.common.collect.Lists;
import com.jayantkrish.jklol.inference.MarginalCalculator;
import com.jayantkrish.jklol.inference.MarginalCalculator.ZeroProbabilityError;
import com.jayantkrish.jklol.inference.MarginalSet;
import com.jayantkrish.jklol.inference.MaxMarginalSet;
import com.jayantkrish.jklol.models.FactorGraph;
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.models.dynamic.DynamicAssignment;
import com.jayantkrish.jklol.models.dynamic.DynamicFactorGraph;
import com.jayantkrish.jklol.models.dynamic.VariablePattern;
import com.jayantkrish.jklol.models.dynamic.VariablePattern.VariableMatch;
import com.jayantkrish.jklol.models.dynamic.WrapperVariablePattern;
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
public class FactorGraphPredictor extends AbstractPredictor<DynamicAssignment, DynamicAssignment> {

  private final DynamicFactorGraph factorGraph;
  private final VariablePattern outputVariablePattern;
  private final MarginalCalculator marginalCalculator;

  /**
   * Creates a {@code FactorGraphPredictor} which makes predictions about
   * assignments to {@code factorGraph} using {@code marginalCalculator} to
   * perform any necessary inference operations. The predicted assignments are
   * over {@code outputVariablePattern}.
   * 
   * @param factorGraph
   * @param marginalCalculator
   */
  public FactorGraphPredictor(DynamicFactorGraph factorGraph, VariablePattern outputVariablePattern,
      MarginalCalculator marginalCalculator) {
    this.factorGraph = factorGraph;
    this.outputVariablePattern = outputVariablePattern;
    this.marginalCalculator = marginalCalculator;
  }

  /**
   * Gets the {@code FactorGraph} used by {@code this} to make predictions.
   * 
   * @return
   */
  public DynamicFactorGraph getFactorGraph() {
    return factorGraph;
  }

  /**
   * {@inheritDoc}
   * 
   * When this predictor has hidden variables (i.e., variables not included in
   * either the input or output), the returned {@code Prediction} has slightly
   * inconsistent scores for the predictions vs. the output. The score for the
   * output is its marginal probability given the input (i.e., summing over all
   * assignments that are supersets of the output). The score for a prediction
   * is the max-marginal probability given the input (i.e., maximizing over the
   * superset assignments). Without hidden variables, these two probabilities
   * are equivalent.
   */
  @Override
  public Prediction<DynamicAssignment, DynamicAssignment> getBestPredictions(DynamicAssignment dynamicInput,
      DynamicAssignment dynamicOutput, int numPredictions) {
    if (!factorGraph.getVariables().isValidAssignment(dynamicInput)) {
      // Cannot make predictions
      return Prediction.create(dynamicInput, dynamicOutput, Double.NEGATIVE_INFINITY, new double[0],
          Collections.<DynamicAssignment> emptyList());
    }
    // Compute max-marginals conditioned on the input
    Assignment input = factorGraph.getVariables().toAssignment(dynamicInput);
    FactorGraph conditionalFactorGraph = factorGraph.getFactorGraph(dynamicInput).conditional(input);
    MaxMarginalSet maxMarginals = marginalCalculator.computeMaxMarginals(conditionalFactorGraph);

    List<Assignment> bestAssignments = Lists.newArrayList();
    try {
      for (int i = 0; i < numPredictions; i++) {
        bestAssignments.add(maxMarginals.getNthBestAssignment(i));
      }
    } catch (ZeroProbabilityError e) {
      // Occurs if all outputs have zero probability under the given assignment.
      // Safely ignored (setting bestAssignments to the empty list).
    }

    // Need marginals in order to compute the true partition function.
    double logPartitionFunction = Double.NEGATIVE_INFINITY;
    try {
      MarginalSet marginals = marginalCalculator.computeMarginals(conditionalFactorGraph);
      logPartitionFunction = marginals.getLogPartitionFunction();
    } catch (ZeroProbabilityError e) {
      // If this occurs, the factor graph assigns zero probability 
      // to everything given dynamicInput.
      return Prediction.create(dynamicInput, dynamicOutput, Double.NEGATIVE_INFINITY, 
          new double[0], Collections.<DynamicAssignment>emptyList());
    }
    
    List<DynamicAssignment> predictedOutputs = Lists.newArrayList();
    double[] scores = new double[bestAssignments.size()];
    for (int i = 0; i < bestAssignments.size(); i++) {
      Assignment bestAssignment = bestAssignments.get(i);
      scores[i] = conditionalFactorGraph.getUnnormalizedLogProbability(bestAssignment) - logPartitionFunction;

      Assignment outputAssignment = bestAssignment.intersection(
          getOutputVariables(conditionalFactorGraph, outputVariablePattern));
      predictedOutputs.add(factorGraph.getVariables().toDynamicAssignment(outputAssignment,
          conditionalFactorGraph.getAllVariables()));
    }

    double outputScore = Double.NEGATIVE_INFINITY;
    if (dynamicOutput != null) {
      DynamicAssignment dynamicInputAndOutput = dynamicInput.union(dynamicOutput); 
      if (factorGraph.getVariables().isValidAssignment(dynamicInputAndOutput)) {
        Assignment inputAndOutput = factorGraph.getVariables().toAssignment(dynamicInputAndOutput);
        FactorGraph jointConditioned = factorGraph.getFactorGraph(dynamicInputAndOutput)
            .conditional(inputAndOutput);
        
        try {
          MarginalSet conditionedMarginals = marginalCalculator.computeMarginals(jointConditioned);
          outputScore = conditionedMarginals.getLogPartitionFunction() - logPartitionFunction;
        } catch (ZeroProbabilityError e) {
          // outputScore is already set as if output had zero probability.
        }
      }
    }

    return Prediction.create(dynamicInput, dynamicOutput, outputScore, scores, predictedOutputs);
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
  public static class SimpleFactorGraphPredictor extends AbstractPredictor<Assignment, Assignment> {

    private FactorGraphPredictor predictor;

    public SimpleFactorGraphPredictor(FactorGraph factorGraph,
        VariableNumMap outputVariables, MarginalCalculator marginalCalculator) {
      predictor = new FactorGraphPredictor(DynamicFactorGraph.fromFactorGraph(factorGraph),
          new WrapperVariablePattern(outputVariables), marginalCalculator);
    }

    public Prediction<Assignment, Assignment> getBestPredictions(Assignment input,
        Assignment output, int numPredictions) {
      DynamicAssignment dynamicOutput = output == null ? null : DynamicAssignment.fromAssignment(output);
      Prediction<DynamicAssignment, DynamicAssignment> best = predictor.getBestPredictions(
          DynamicAssignment.fromAssignment(input), dynamicOutput,
          numPredictions);

      List<Assignment> predictions = Lists.newArrayList();
      for (DynamicAssignment dynamic : best.getPredictions()) {
        predictions.add(dynamic.getFixedAssignment());
      }
      return Prediction.create(input, output, best.getOutputScore(), best.getScores(), predictions);
    }
  }
}
