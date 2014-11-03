package com.jayantkrish.jklol.evaluation;

import java.util.Map;
import java.util.Set;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.jayantkrish.jklol.util.DefaultHashMap;

/**
 * Common baseline prediction methods.
 * 
 * @author jayant
 */
public class Baselines {

  /**
   * Gets a {@code PredictorTrainer} which "trains" a predictor to predict the
   * uniform distribution over all outputs observed during training.
   * 
   * @return
   */
  public static <I, O> PredictorTrainer<I, O> uniform() {
    return new UniformPredictorTrainer<I, O>();
  }

  /**
   * Gets a {@code PredictorTrainer} which returns a predictor that puts all of
   * its probability mass on {@code outputValue}. This predictor is returned
   * regardless of the training data given to the trainer.
   * 
   * @param outputValue
   * @return
   */
  public static <I, O> PredictorTrainer<I, O> constant(O outputValue) {
    return new WrapperTrainer<I, O>(Predictors.<I, O>constant(outputValue));
  }

  /**
   * Gets a predictor trainer which predicts the outputVar labels according to
   * their empirical frequency in the training set. Hence, the best prediction
   * for all inputs is the most frequent label in the training set.
   * 
   * @return
   */
  public static <I, O> PredictorTrainer<I, O> mostFrequentLabel() {
    return new MostFrequentLabel<I, O>();
  }

  /**
   * A trainer which returns a predictor that predicts the uniform distribution
   * over all observed outputs.
   * 
   * @author jayantk
   */
  private static class UniformPredictorTrainer<I, O> implements PredictorTrainer<I, O> {

    @Override
    public Predictor<I, O> train(Iterable<Example<I, O>> trainingData) {
      Set<O> observedOutputs = Sets.newHashSet();
      for (Example<I, O> datum : trainingData) {
        observedOutputs.add(datum.getOutput());
      }

      Map<O, Double> predictionProbabilities = Maps.newHashMap();
      for (O output : observedOutputs) {
        predictionProbabilities.put(output, 1.0 / observedOutputs.size());
      }

      return new ConstantPredictor<I, O>(predictionProbabilities);
    }
  }

  /**
   * Trains a predictor which predicts the most frequent label in the training
   * data, and estimates label probabilities independently of the inputVar data
   * point.
   * 
   * @author jayant
   * 
   * @param <I>
   * @param <O>
   */
  private static class MostFrequentLabel<I, O> implements PredictorTrainer<I, O> {

    @Override
    public Predictor<I, O> train(Iterable<Example<I, O>> trainingData) {
      DefaultHashMap<O, Double> outputCounts = new DefaultHashMap<O, Double>(0.0);
      double total = 0.0;
      for (Example<I, O> datum : trainingData) {
        outputCounts.put(datum.getOutput(), outputCounts.get(datum.getOutput()) + 1.0);
        total++;
      }

      for (O key : outputCounts.keySet()) {
        outputCounts.put(key, outputCounts.get(key) / total);
      }
      return new ConstantPredictor<I, O>(outputCounts.getBaseMap());
    }
  }

  /**
   * A trainer that always returns a given predictor, regardless of the training
   * data.
   * 
   * @param <I>
   * @param <O>
   * @author jayantk
   */
  private static class WrapperTrainer<I, O> implements PredictorTrainer<I, O> {
    private final Predictor<I, O> predictor;

    public WrapperTrainer(Predictor<I, O> predictor) {
      this.predictor = predictor;
    }

    @Override
    public Predictor<I, O> train(Iterable<Example<I, O>> trainingData) {
      return predictor;
    }
  }
}
