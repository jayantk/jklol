package com.jayantkrish.jklol.evaluation;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Random;

import com.google.common.collect.Lists;

/**
 * TestSetEvaluation evaluates a predictor by training on one data set and
 * testing on another.
 * 
 * @author jayant
 * 
 * @param <I>
 * @param <O>
 */
public class TestSetEvaluation<I, O> extends AbstractEvaluation<I, O> {

  private final Collection<Example<I, O>> trainingData;
  private final Collection<Example<I, O>> validationData;
  private final Collection<Example<I, O>> testData;

  public TestSetEvaluation(Collection<Example<I, O>> trainingData,
      Collection<Example<I, O>> validationData, Collection<Example<I, O>> testData) {
    this.trainingData = trainingData;
    this.validationData = validationData;
    this.testData = testData;
  }

  /**
   * Evaluates the loss of {@code predictor} on the test data contained in this
   * evaluation. This method is useful when training is unnecessary or more
   * complicated than simply running on the trainer on training data.
   * 
   * @param predictor
   * @param lossFunctions
   */
  public void evaluateLoss(Predictor<I, O> predictor, List<LossFunction<I, O>> lossFunctions) {
    for (Example<I, O> testDatum : testData) {
      for (LossFunction<I, O> lossFunction : lossFunctions) {
        lossFunction.accumulateLoss(predictor, testDatum.getInput(), testDatum.getOutput());
      }
    }
  }

  /**
   * Same as {@link #evaluateLoss(Predictor, List)}, using varargs.
   * 
   * @param predictor
   * @param lossFunctions
   */
  public void evaluateLoss(Predictor<I, O> predictor, LossFunction<I, O>... lossFunctions) {
    evaluateLoss(predictor, Arrays.asList(lossFunctions));
  }

  /**
   * Gets the training data used by {@code this} to train predictors.
   * 
   * @return
   */
  public Collection<Example<I, O>> getTrainingData() {
    return trainingData;
  }

  /**
   * Gets the validation data contained in {@code this}. This data is ignored
   * during training.
   * 
   * @return
   */
  public Collection<Example<I, O>> getValidationData() {
    return validationData;
  }

  /**
   * Gets the test data used by {@code this} to evaluate the loss of predictors.
   * 
   * @return
   */
  public Collection<Example<I, O>> getTestData() {
    return testData;
  }

  /**
   * {@inheritDoc}
   * 
   * This implementation ignores any validation data contained by {@code this},
   * simply training algorithms on the training data and testing on the test
   * data. If using validation data is desired, train the predictor externally
   * and pass it to {@link #evaluateLoss(Predictor, List)}.
   */
  @Override
  public void evaluateLoss(PredictorTrainer<I, O> predictorTrainer, 
      List<LossFunction<I, O>> lossFunctions) {
    Predictor<I, O> predictor = predictorTrainer.train(trainingData);
    evaluateLoss(predictor, lossFunctions);
  }

  @Override
  public String toString() {
    return "TestSetEvaluation: " + trainingData.size() + " training examples, "
        + validationData.size() + " validation examples, " + testData.size() + " test examples";
  }

  /**
   * Creates a {@code TestSetEvaluation} by partitioning {@code data} into a
   * training and a test set. {@code testFraction} is the percent of
   * {@code data} which is placed in the test set. For example, {@code 0.1}
   * places 10% of the data in the test set and the remaining 90% in the
   * training set.
   * 
   * The partitioning into training / validation / test data is pseudorandom,
   * but deterministic. Multiple calls to this method with the same {@code data}
   * argument will produce the same training / validation / test split.
   * 
   * @param dataPoints
   * @param percentHeldOut
   * @return
   */
  public static <I, O> TestSetEvaluation<I, O> createHoldOutEvaluation(
      Iterable<Example<I, O>> data, double testFraction, double validationFraction) {
    List<Example<I, O>> trainingData = Lists.newArrayList();
    List<Example<I, O>> validationData = Lists.newArrayList();
    List<Example<I, O>> testData = Lists.newArrayList();
    // Use a seeded random number generator to ensure that the partition is
    // deterministic.
    Random random = new Random(0);
    for (Example<I, O> datum : data) {
      double draw = random.nextDouble();
      if (draw < testFraction) {
        testData.add(datum);
      } else if (draw < testFraction + validationFraction) {
        validationData.add(datum);
      } else {
        trainingData.add(datum);
      }
    }
    return new TestSetEvaluation<I, O>(trainingData, validationData, testData);
  }
}
