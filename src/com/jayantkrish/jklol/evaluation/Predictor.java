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
   * Gets the {@code numPredictions} best predictions of this predictor on
   * {@code input} as a {@code Prediction}. Fewer than {@code numPredictions}
   * predictions may be returned in some cases, for example, if {@code input} is
   * invalid or if this predictor can only compute the best prediction.
   * <p>
   * If {@code output} is non-null, then simultaneously compute the true score
   * for {@code output}.
   * 
   * @param input
   * @param output
   * @param numPredictions
   * @return
   */
  public Prediction<I, O> getBestPredictions(I input, O output, int numPredictions);

  /**
   * Equivalent to {@link #getBestPredictions} using the input and output from
   * {@code example}.
   * 
   * @param example
   * @return
   */  
  public Prediction<I, O> getBestPredictions(Example<? extends I, ? extends O> example, 
      int numPredictions);

  /**
   * Equivalent to {@link #getBestPredictions} with {@code numPredictions} set
   * to 1.
   * 
   * @param input
   * @param output
   * @return
   */
  public Prediction<I, O> getBestPrediction(I input, O output);

  /**
   * Equivalent to {@link #getBestPredictions} using the input and output from
   * {@code example} and {@code numPredictions} set to 1.
   * 
   * @param example
   * @return
   */
  public Prediction<I, O> getBestPrediction(Example<? extends I, ? extends O> example);

  /**
   * Equivalent to {@link #getBestPredictions} with {@code numPredictions} set
   * to 1 and {@code output} set to {@code null}.
   * 
   * @param input
   * @param output
   * @return
   */
  public Prediction<I, O> getBestPrediction(I input);

  /**
   * Gets the score of {@code output} given {@code input}. Higher scores mean
   * that {@code output} is a better prediction given {@code input}, though
   * different models may assign different scores. If possible, score should be
   * the log probability of {@code output} given {@code input}, marginalizing
   * out all other random variables.
   * 
   * @param input
   * @param output
   * @return
   */
  public double getScore(I input, O output);

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
    // The score of the correct output on this particular example.
    // Only valid if output != null
    private final double outputScore;

    // The classifier's predictions and their scores.
    private final double[] scores;
    private final List<O> predictions;

    /**
     * 
     * {@code output} may be null, signifying that the true output is not known.
     */
    public Prediction(I input, O output, double outputScore, double[] scores, List<O> predictions) {
      this.input = Preconditions.checkNotNull(input);
      this.output = output;
      this.outputScore = outputScore;
      this.scores = Preconditions.checkNotNull(scores);
      this.predictions = Preconditions.checkNotNull(predictions);
      Preconditions.checkArgument(predictions.size() == scores.length);
    }

    public static <I, O> Prediction<I, O> create(I input, O output, double outputScore,
        double[] scores, List<O> predictions) {
      return new Prediction<I, O>(input, output, outputScore, scores, predictions);
    }

    public I getInput() {
      return input;
    }

    public O getOutput() {
      return output;
    }

    public double getOutputScore() {
      return outputScore;
    }

    public double getOutputProbability() {
      return Math.exp(outputScore);
    }

    public double[] getScores() {
      return scores;
    }

    public List<O> getPredictions() {
      return predictions;
    }

    public O getBestPrediction() {
      if (predictions.size() > 0) {
        return predictions.get(0);
      } else {
        return null;
      }
    }
  }
}