package com.jayantkrish.jklol.evaluation;

import java.util.Arrays;

import junit.framework.TestCase;

import com.jayantkrish.jklol.inference.InferenceTestCases;
import com.jayantkrish.jklol.inference.JunctionTree;
import com.jayantkrish.jklol.models.FactorGraph;
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.util.Assignment;
import com.jayantkrish.jklol.util.Converters;

/**
 * Unit tests for {@link FactorGraphPredictor}.
 * 
 * @author jayantk
 */
public class FactorGraphPredictorTest extends TestCase {
  
  private FactorGraph factorGraph;
  private VariableNumMap inputVars, outputVars;
  private Predictor<Assignment, Assignment> predictor;
  private Predictor<String, String> wrappedPredictor;
  
  private VariableNumMap densityVars; 
  private Predictor<Assignment, Assignment> densityPredictor;
  
  
  public void setUp() {
    factorGraph = InferenceTestCases.basicFactorGraph();
    outputVars = factorGraph.getVariables().getVariablesByName(Arrays.asList("Var4"));
    inputVars = factorGraph.getVariables().getVariablesByName(Arrays.asList("Var2"));
    predictor = new FactorGraphPredictor(factorGraph, outputVars, new JunctionTree()); 
  
    wrappedPredictor = new ForwardingPredictor<String, String, Assignment, Assignment>(
        predictor, 
        Converters.wrapWithCast(Converters.wrapSingletonList(inputVars.getOutcomeToAssignmentConverter()), String.class),
        Converters.wrapWithCast(Converters.wrapSingletonList(outputVars.getOutcomeToAssignmentConverter()), String.class));
    
    densityVars = factorGraph.getVariables().getVariablesByName(Arrays.asList("Var0", "Var2"));
    densityPredictor = new FactorGraphPredictor(factorGraph, densityVars, new JunctionTree());
  }
  
  public void testGetBestPrediction() {
    Assignment expected = outputVars.outcomeToAssignment(Arrays.asList("F"));
    Assignment actual = predictor.getBestPrediction(
        inputVars.outcomeToAssignment(Arrays.asList("T")));
    assertEquals(expected, actual);
    expected = outputVars.outcomeToAssignment(Arrays.asList("U"));
    actual = predictor.getBestPrediction(
        inputVars.outcomeToAssignment(Arrays.asList("F")));
    assertEquals(expected, actual);
  }
  
  public void testGetProbability() {
    assertEquals(15.0 / 25.0, predictor.getProbability(
        inputVars.outcomeToAssignment(Arrays.asList("T")),
        outputVars.outcomeToAssignment(Arrays.asList("F"))));
    assertEquals(10.0 / 25.0, predictor.getProbability(
        inputVars.outcomeToAssignment(Arrays.asList("T")),
        outputVars.outcomeToAssignment(Arrays.asList("U"))));
    assertEquals(0.0 / 25.0, predictor.getProbability(
        inputVars.outcomeToAssignment(Arrays.asList("T")),
        outputVars.outcomeToAssignment(Arrays.asList("T"))));
    assertEquals(18.0 / 18.0, predictor.getProbability(
        inputVars.outcomeToAssignment(Arrays.asList("F")),
        outputVars.outcomeToAssignment(Arrays.asList("U"))));
    assertEquals(0.0 / 43.0, predictor.getProbability(
        inputVars.outcomeToAssignment(Arrays.asList("F")),
        outputVars.outcomeToAssignment(Arrays.asList("T"))));
    
    assertEquals(25.0 / 43.0, densityPredictor.getProbability(
        Assignment.EMPTY, densityVars.outcomeToAssignment(Arrays.asList("T", "T"))));
    assertEquals(6.0 / 43.0, densityPredictor.getProbability(
        Assignment.EMPTY, densityVars.outcomeToAssignment(Arrays.asList("T", "F"))));    
  }
  
  public void testGetBestPredictionWrapped() {
    assertEquals("F", wrappedPredictor.getBestPrediction("T"));
    assertEquals("U", wrappedPredictor.getBestPrediction("F"));
  }
      
  public void testGetBestPredictionsWrapped() {
    assertEquals("F", wrappedPredictor.getBestPredictions("T", 2).get(0));
    assertEquals("U", wrappedPredictor.getBestPredictions("T", 2).get(1));
    assertEquals("U", wrappedPredictor.getBestPredictions("F", 1).get(0));
    assertTrue(wrappedPredictor.getBestPredictions("INVALID", 1).isEmpty());
  }
  
  public void testGetProbabilityWrapped() {
    assertEquals(15.0 / 25.0, wrappedPredictor.getProbability("T", "F"));
    assertEquals(10.0 / 25.0, wrappedPredictor.getProbability("T", "U"));
    assertEquals(0.0 / 25.0, wrappedPredictor.getProbability("T", "T"));
    assertEquals(18.0 / 18.0, wrappedPredictor.getProbability("F", "U"));
    assertEquals(0.0 / 18.0, wrappedPredictor.getProbability("F", "T"));
    assertEquals(0.0 / 18.0, wrappedPredictor.getProbability("NOT", "VALID"));
    assertEquals(0.0 / 18.0, wrappedPredictor.getProbability("NOT", "T"));
    assertEquals(0.0 / 18.0, wrappedPredictor.getProbability("T", "VALID"));
  }
}
