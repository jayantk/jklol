package com.jayantkrish.jklol.cli;

import java.util.Arrays;
import java.util.List;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.primitives.Ints;
import com.jayantkrish.jklol.evaluation.Example;
import com.jayantkrish.jklol.inference.JunctionTree;
import com.jayantkrish.jklol.models.DiscreteVariable;
import com.jayantkrish.jklol.models.TableFactor;
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.models.dynamic.DynamicAssignment;
import com.jayantkrish.jklol.models.dynamic.DynamicFactorGraph;
import com.jayantkrish.jklol.models.dynamic.DynamicVariableSet;
import com.jayantkrish.jklol.models.dynamic.VariableNamePattern;
import com.jayantkrish.jklol.models.loglinear.DiscreteLogLinearFactor;
import com.jayantkrish.jklol.models.parametric.ParametricFactorGraph;
import com.jayantkrish.jklol.models.parametric.ParametricFactorGraphBuilder;
import com.jayantkrish.jklol.models.parametric.SufficientStatistics;
import com.jayantkrish.jklol.training.GradientOracle;
import com.jayantkrish.jklol.training.LoglikelihoodOracle;
import com.jayantkrish.jklol.training.MaxMarginOracle;
import com.jayantkrish.jklol.training.StochasticGradientTrainer;
import com.jayantkrish.jklol.util.Assignment;
import com.jayantkrish.jklol.util.StringUtils;

public class ModelUtils {

  public static final String PLATE_NAME = "plate";
  public static final String INPUT_NAME = "x";
  public static final String OUTPUT_NAME = "y";

  /**
   * Constructs a sequence model from the lines of a file containing features of
   * the emission distribution.
   * 
   * @param emissionFeatureLines
   * @return
   */
  public static ParametricFactorGraph buildSequenceModel(Iterable<String> emissionFeatureLines) {
    // Read in the possible values of each variable.
    List<String> words = StringUtils.readColumnFromDelimitedLines(emissionFeatureLines, 0, ",");
    List<String> labels = StringUtils.readColumnFromDelimitedLines(emissionFeatureLines, 1, ",");
    List<String> emissionFeatures = StringUtils.readColumnFromDelimitedLines(emissionFeatureLines, 2, ",");
    // Create dictionaries for each variable's values.
    DiscreteVariable wordType = new DiscreteVariable("word", words);
    DiscreteVariable labelType = new DiscreteVariable("label", labels);
    DiscreteVariable emissionFeatureType = new DiscreteVariable("emissionFeature", emissionFeatures);

    // Create a dynamic factor graph with a single plate replicating
    // the input/output variables.
    ParametricFactorGraphBuilder builder = new ParametricFactorGraphBuilder();
    builder.addPlate(PLATE_NAME, new VariableNumMap(Ints.asList(1, 2),
        Arrays.asList(INPUT_NAME, OUTPUT_NAME), Arrays.asList(wordType, labelType)), 10000);
    String inputPattern = PLATE_NAME + "/?(0)/" + INPUT_NAME;
    String outputPattern = PLATE_NAME + "/?(0)/" + OUTPUT_NAME;
    String nextOutputPattern = PLATE_NAME + "/?(1)/" + OUTPUT_NAME;
    VariableNumMap plateVars = new VariableNumMap(Ints.asList(1, 2),
        Arrays.asList(inputPattern, outputPattern), Arrays.asList(wordType, labelType));

    // Read in the emission features (for the word/label weights).
    VariableNumMap x = plateVars.getVariablesByName(inputPattern);
    VariableNumMap y = plateVars.getVariablesByName(outputPattern);
    VariableNumMap emissionFeatureVar = VariableNumMap.singleton(0, "emissionFeature", emissionFeatureType);
    TableFactor emissionFeatureFactor = TableFactor.fromDelimitedFile(
        Arrays.asList(x, y, emissionFeatureVar), emissionFeatureLines,
        ",", false);

    // Add a parametric factor for the word/label weights
    DiscreteLogLinearFactor emissionFactor = new DiscreteLogLinearFactor(x.union(y), emissionFeatureVar,
        emissionFeatureFactor);
    builder.addFactor("wordLabelFactor", emissionFactor,
        VariableNamePattern.fromTemplateVariables(plateVars, VariableNumMap.emptyMap()));

    // Create a factor connecting adjacent labels
    VariableNumMap adjacentVars = new VariableNumMap(Ints.asList(0, 1),
        Arrays.asList(outputPattern, nextOutputPattern), Arrays.asList(labelType, labelType));
    builder.addFactor("transition", DiscreteLogLinearFactor.createIndicatorFactor(adjacentVars),
        VariableNamePattern.fromTemplateVariables(adjacentVars, VariableNumMap.emptyMap()));

    return builder.build();
  }
}