package com.jayantkrish.jklol.evaluation;

import java.util.Arrays;

import junit.framework.TestCase;

import com.jayantkrish.jklol.evaluation.FactorGraphPredictor.SimpleFactorGraphPredictor;
import com.jayantkrish.jklol.evaluation.Predictor.Prediction;
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
  private SimpleFactorGraphPredictor predictor;
  private Predictor<String, String> wrappedPredictor;
  
  private VariableNumMap densityVars; 
  private Predictor<Assignment, Assignment> densityPredictor;
  
  
  public void setUp() {
    factorGraph = InferenceTestCases.basicFactorGraph();
    outputVars = factorGraph.getVariables().getVariablesByName(Arrays.asList("Var4"));
    inputVars = factorGraph.getVariables().getVariablesByName(Arrays.asList("Var2"));
    predictor = new SimpleFactorGraphPredictor(factorGraph, outputVars, 
        new JunctionTree()); 
  
    wrappedPredictor = new ForwardingPredictor<String, String, Assignment, Assignment>(
        predictor, 
        Converters.wrapWithCast(Converters.wrapSingletonList(inputVars.getOutcomeToAssignmentConverter()), String.class),
        Converters.wrapWithCast(Converters.wrapSingletonList(outputVars.getOutcomeToAssignmentConverter()), String.class));
    
    densityVars = factorGraph.getVariables().getVariablesByName(Arrays.asList("Var0", "Var2"));
    densityPredictor = new SimpleFactorGraphPredictor(factorGraph, densityVars, 
        new JunctionTree());
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
  
  public void testGetBestPredictionObject() {
    Prediction<Assignment, Assignment> prediction = predictor.getBestPredictionObject(
        inputVars.outcomeArrayToAssignment("T"), outputVars.outcomeArrayToAssignment("U"));
    
    assertEquals(inputVars.outcomeArrayToAssignment("T"), prediction.getInput());
    assertEquals(outputVars.outcomeArrayToAssignment("U"), prediction.getOutput());
    assertEquals(1, prediction.getPredictions().size());
    assertEquals(outputVars.outcomeArrayToAssignment("F"), prediction.getPredictions().get(0));
    assertEquals(1, prediction.getScores().length);
    assertEquals(Math.log(9.0), prediction.getScores()[0], .00001);
    
    prediction = predictor.getBestPredictionObject(
        inputVars.outcomeArrayToAssignment("bashash"), outputVars.outcomeArrayToAssignment("U"));
    assertEquals(inputVars.outcomeArrayToAssignment("bashash"), prediction.getInput());
    assertEquals(outputVars.outcomeArrayToAssignment("U"), prediction.getOutput());
    assertEquals(0, prediction.getPredictions().size());
    assertEquals(0, prediction.getScores().length);
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
    assertEquals(null, wrappedPredictor.getBestPrediction("INVALID"));
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
    assertEquals(0.0 / 18.0, wrappedPredictor.getProbability("T", "INVALID"));
  }
}
