package com.jayantkrish.jklol.evaluation;

import java.util.List;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.primitives.Doubles;

/**
 * A trainer for {@link MixturePredictor}s which optimizes the mixing
 * proportions between a given set of predictors. The trained predictor is a
 * probabilistic mixture of the predictors used to create this trainer, and the
 * mixing proportions are chosen to optimize the likelihood of the data passed
 * to {@link #train}.
 * 
 * <p>
 * A common use for this class is to optimize the hyperparameters of a model,
 * especially the amount of smoothing. In this case, the model is trained on a
 * training set without smoothing to get a maximum-likelihood estimate. The
 * trained model and {@link Baselines#uniform()} are given to this trainer,
 * which then optimizes the mixing proportions using a separate validation set.
 * 
 * <p>
 * Internally, this class runs a single iteration of hard EM to select the best
 * hyperparameter. Although this procedure isn't guaranteed to find the exact
 * best value, it is extremely simple and will be fairly close if the best
 * mixing proportion is close to uniform.
 * 
 * @author jayantk
 * @param <I>
 * @param <O>
 */
public class MixturePredictorTrainer<I, O> implements PredictorTrainer<I, O> {

  private final ImmutableList<Predictor<I, O>> predictors;
  private double[] predictionCounts;

  /**
   * Create a trainer which will return an optimized mixture of
   * {@code predictors}.
   * 
   * @param predictors
   */
  public MixturePredictorTrainer(List<Predictor<I, O>> predictors) {
    this.predictors = ImmutableList.copyOf(predictors);
    this.predictionCounts = new double[predictors.size()];
  }

  @Override
  public MixturePredictor<I, O> train(Iterable<Example<I, O>> trainingData) {
    for (Example<I, O> example : trainingData) {
      int bestPredictorInd = -1;
      double bestProbability = -1.0;
      for (int i = 0; i < predictors.size(); i++) {
        Predictor<I, O> predictor = predictors.get(i);
        double probability = predictor.getProbability(example.getInput(), example.getOutput());
        if (probability > bestProbability) {
          bestProbability = probability;
          bestPredictorInd = i;
        }
      }
      Preconditions.checkState(bestPredictorInd >= 0);
      Preconditions.checkState(bestProbability >= 0.0);

      predictionCounts[bestPredictorInd] += 1.0;
    }
    return new MixturePredictor<I, O>(predictors, Doubles.asList(predictionCounts));
  }
}
