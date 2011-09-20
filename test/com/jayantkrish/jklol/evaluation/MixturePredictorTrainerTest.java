package com.jayantkrish.jklol.evaluation;

import java.util.List;

import junit.framework.TestCase;

import com.google.common.collect.ImmutableList;
import com.jayantkrish.jklol.testing.ExampleUtils;

/**
 * Unit tests for {@link MixturePredictorTrainer}.
 * 
 * @author jayantk
 */
public class MixturePredictorTrainerTest extends TestCase {

  private String[][] training = new String[][] {
      {"1", "true"}, {"2", "false"}, {"3", "false"},
      {"4", "true"}, {"5", "true"}
  };
  private List<Example<String, String>> trainingExamples;
  
  private Predictor<String, String> firstPredictor;
  private Predictor<String, String> secondPredictor;
  private MixturePredictorTrainer<String, String> trainer;
  
  public void setUp() {
    trainingExamples = ExampleUtils.exampleArrayToList(training);

    firstPredictor = Predictors.constant("true");
    secondPredictor = Predictors.constant("false");
    List<Predictor<String, String>> predictors = ImmutableList.of(firstPredictor, secondPredictor);
    trainer = new MixturePredictorTrainer<String, String>(predictors);
  }

  public void testTrain() {
    MixturePredictor<String, String> predictor = trainer.train(trainingExamples);
    
    assertEquals(0.6, predictor.getProbability("1", "true"));
    assertEquals(0.4, predictor.getProbability("1", "false"));
    assertEquals(0.6, predictor.getProbability("foo", "true"));
    assertEquals(0.4, predictor.getProbability("foo", "false"));
    
    assertEquals(0.6, predictor.getMixingProportions().get(0));
    assertEquals(0.4, predictor.getMixingProportions().get(1));
  }
}
