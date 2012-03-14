package com.jayantkrish.jklol.evaluation;

import java.util.List;

import com.google.common.base.Function;
import com.google.common.base.Preconditions;

/**
 * A predictor wraps a prediction algorithm that can be used to predict values
 * of new instances. Typically instantiated using a {@link PredictorTrainer}.
 * 
 * @param I the type of the inputVar that the prediction is based on.
 * @param O the type of the outputVar prediction.
 */
public interface Predictor<I, O> extends Function<I, O> {

  /**
   * Get the best prediction for the given input. Returns {@code null} if
   * {@code input} is invalid or no prediction can be computed for {@code input}
   * .
   */
  public O getBestPrediction(I input);

  /**
   * Gets a ranked list of the numBest predictions for the given inputVar. If
   * fewer than {@code numBest} predictions can be retrieved, then as many
   * predictions as possible are returned. In this case, the returned list may
   * contain fewer than {@code numBest} items.
   * 
   * If no prediction can be computed for {@code input}, returns an empty list.
   */
  public List<O> getBestPredictions(I input, int numBest);

  /**
   * Gets the probability of the outputVar given the inputVar.
   * 
   * If either {@code input} or {@code output} are invalid, returns 0.0.
   */
  public double getProbability(I input, O output);

  /**
   * Gets the {@code numPredictions} best predictions of this predictor on
   * {@code input}. Fewer than {@code numPredictions} predictions may be
   * returned in some cases, for example, if {@code input} is invalid or if this
   * predictor can only compute the best prediction.
   * 
   * If {@code output} is non-null, then simultaneously compute the true score
   * for {@code output}.
   * 
   * @param input
   * @param output
   * @param numPredictions
   * @return
   */
  // public Prediction<I, O> getBestPredictions(I input, O output, int numPredictions);

  /**
   * A prediction made by a {@code Predictor} on an example.
   * 
   * @author jayantk
   * @param <I>
   * @param <O>
   */
  public static class Prediction<I, O> {

    // The current training example, as an input to the classifier
    // and the correct output.
    private final I input;
    private final O output;

    // The classifier's predictions and their scores.
    private final double[] scores;
    private final List<O> predictions;

    /**
     *
     * {@code output} may be null, signifying that the true output is not known.
     */
    public Prediction(I input, O output, double[] scores, List<O> predictions) {
      this.input = Preconditions.checkNotNull(input);
      this.output = output;
      this.scores = Preconditions.checkNotNull(scores);
      this.predictions = Preconditions.checkNotNull(predictions);
      Preconditions.checkArgument(predictions.size() == scores.length);
    }
    
    public static <I, O> Prediction<I, O> create(I input, O output, double[] scores, 
        List<O> predictions) {
      return new Prediction<I, O>(input, output, scores, predictions);
    }

    public I getInput() {
      return input;
    }

    public O getOutput() {
      return output;
    }

    public double[] getScores() {
      return scores;
    }

    public List<O> getPredictions() {
      return predictions;
    }
  }
}