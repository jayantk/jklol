package com.jayantkrish.jklol.evaluation;

import java.util.Collections;
import java.util.List;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Ordering;
import com.jayantkrish.jklol.evaluation.Predictor.Prediction;

/**
 * Implementations of some common loss functions.
 */
public class LossFunctions {

  /**
   * Convenience method for instantiating an {@link Accuracy} loss function.
   * 
   * @param <I>
   * @param <O>
   * @return
   */
  public static <I, O> Accuracy<I, O> newAccuracy() {
    return new Accuracy<I, O>();
  }

  /**
   * Convenience method for instantiating a {@link PrecisionRecall} loss
   * function.
   * 
   * @param <I>
   * @return
   */
  public static <I> PrecisionRecall<I> newPrecisionRecall() {
    return new PrecisionRecall<I>();
  }

  /**
   * Convenience method for instantiating a {@link Loglikelihood} loss function.
   * 
   * @param <I>
   * @param <O>
   * @return
   */
  public static <I, O> Loglikelihood<I, O> newLoglikelihood() {
    return new Loglikelihood<I, O>();
  }

  private LossFunctions() {
    // Prevent instantiation.
  }

  /**
   * Accuracy is the zero-one loss, which assigns a "loss" of 1 when the
   * prediction is {@code equals()} to the actual, and 0 otherwise.
   * 
   * @param O - the type of the prediction
   */
  public static class Accuracy<I, O> implements LossFunction<I, O> {

    private int correct;
    private int total;

    public Accuracy() {
      correct = 0;
      total = 0;
    }

    @Override
    public void accumulateLoss(Prediction<I, O> prediction) {
      Preconditions.checkNotNull(prediction);
      Preconditions.checkNotNull(prediction.getOutput());

      total += 1;
      if (prediction.getPredictions().size() > 0) {
        O predictedOutput = prediction.getPredictions().get(0);
        correct += prediction.getOutput().equals(predictedOutput) ? 1.0 : 0.0;
      }
    }

    public double getAccuracy() {
      return ((double) correct) / total;
    }

    public int getCount() {
      return total;
    }
  }

  /**
   * Measures precision, recall and accuracy.
   */
  public static class PrecisionRecall<I> implements LossFunction<I, Boolean> {

    private int truePositives;
    private int trueNegatives;
    private int falsePositives;
    private int falseNegatives;

    // Store scores in order to generate precision/recall curves. 
    private List<Double> truePositiveScores;
    private List<Double> falsePositiveScores;

    public PrecisionRecall() {
      truePositives = 0;
      trueNegatives = 0;
      falsePositives = 0;
      falseNegatives = 0;

      truePositiveScores = Lists.newArrayList();
      falsePositiveScores = Lists.newArrayList();
    }
    
    public PrecisionRecall(int truePositives, int trueNegatives, int falsePositives, 
        int falseNegatives) {
      this.truePositives = truePositives;
      this.trueNegatives = trueNegatives;
      this.falsePositives = falsePositives;
      this.falseNegatives = falseNegatives;
      
      this.truePositiveScores = null;
      this.falsePositiveScores = null;
    }

    public void accumulatePrediction(Boolean prediction, Boolean actual, double score) {
      if (prediction == null) {
        // No prediction was made. Count the prediction as incorrect.
        prediction = !actual;
      }

      if (actual && prediction) {
        truePositives++;
        truePositiveScores.add(score);
      } else if (actual && !prediction) {
        falseNegatives++;
      } else if (!actual && prediction) {
        falsePositives++;
        falsePositiveScores.add(score);
      } else {
        trueNegatives++;
      }
    }

    @Override
    public void accumulateLoss(Prediction<I, Boolean> prediction) {
      Preconditions.checkNotNull(prediction);
      Preconditions.checkNotNull(prediction.getOutput());

      if (prediction.getPredictions().size() > 0) {
        accumulatePrediction(prediction.getPredictions().get(0), prediction.getOutput(),
            prediction.getScores()[0]);
      } else {
        accumulatePrediction(null, prediction.getOutput(), Double.NEGATIVE_INFINITY);
      }
    }

    public double getPrecision() {
      return ((double) truePositives) / (truePositives + falsePositives);
    }

    public double getRecall() {
      return ((double) truePositives) / (truePositives + falseNegatives);
    }

    public double getAccuracy() {
      return ((double) truePositives + trueNegatives) / (truePositives + trueNegatives + falsePositives + falseNegatives);
    }

    public int getNumPositivePredictions() {
      return truePositives + falsePositives;
    }

    /**
     * Gets the number of actual positive instances (i.e., instances with
     * positive labels) in the test set.
     * 
     * @return
     */
    public int getNumPositiveInstances() {
      return truePositives + falseNegatives;
    }

    public int getNumInstances() {
      return truePositives + trueNegatives + falsePositives + falseNegatives;
    }

    public void getPrecisionRecallCurve(List<Double> precisions, List<Double> recalls) {
      Collections.sort(truePositiveScores, Ordering.<Double> natural().reverse());
      Collections.sort(falsePositiveScores, Ordering.<Double> natural().reverse());

      int tpIndex = 0, fpIndex = 0;
      int numTps = truePositiveScores.size();
      int numFps = falsePositiveScores.size();
      double totalPositiveInstances = getNumPositiveInstances();
      double curTps = 0;
      double curFps = 0;
      while (tpIndex < numTps && fpIndex < numFps) {
        if (truePositiveScores.get(tpIndex) > falsePositiveScores.get(fpIndex)) {
          curTps++;
          tpIndex++;
        } else if (truePositiveScores.get(tpIndex) < falsePositiveScores.get(fpIndex)) {
          curFps++;
          fpIndex++;
        } else {
          // Equal.
          curTps++;
          curFps++;
          tpIndex++;
          fpIndex++;
        }

        precisions.add(curTps / (curTps + curFps));
        recalls.add(curTps / totalPositiveInstances);
      }

      // Finish off whichever list remains.
      while (tpIndex < numTps) {
        curTps++;
        tpIndex++;

        precisions.add(curTps / (curTps + curFps));
        recalls.add(curTps / totalPositiveInstances);
      }

      while (fpIndex < numFps) {
        curFps++;
        fpIndex++;
        precisions.add(curTps / (curTps + curFps));
        recalls.add(curTps / totalPositiveInstances);
      }
    }

    @Override
    public String toString() {
      int totalPositives = (truePositives + falsePositives);
      int totalGoldPositives = (truePositives + falseNegatives);
      return "precision: " + getPrecision() + " (" + truePositives + "/" + totalPositives +
          "), recall: " + getRecall() + " (" + truePositives + "/" + totalGoldPositives + "), tp: "
          + truePositives + ", fp: " + falsePositives + ", tn: " + trueNegatives + ", fn: "
          + falseNegatives;
    }
  }

  /**
   * evaluates the loglikelihood of a predictor's predictions, using the natural
   * log.
   * 
   * <p>
   * If an example has zero probability, the loglikelihood of the predictions is
   * negative infinity. However, this class also computes the loglikelihood
   * ignoring zeros and allows the user to retrieve the number of examples with
   * zero probability.
   */
  public static class Loglikelihood<I, O> implements LossFunction<I, O> {

    private double loglikelihood;
    // Used to compute the variance of the estimator.
    private double sumSquaredLoglikelihood;
    private int numExamples;
    private int numZeroProbabilityExamples;

    public Loglikelihood() {
      loglikelihood = 0.0;
      sumSquaredLoglikelihood = 0.0;
      numExamples = 0;
      numZeroProbabilityExamples = 0;
    }

    @Override
    public void accumulateLoss(Prediction<I, O> prediction) {
      Preconditions.checkNotNull(prediction);
      Preconditions.checkNotNull(prediction.getOutput());

      double logProb = prediction.getOutputScore();
      if (logProb != Double.NEGATIVE_INFINITY) {
        loglikelihood += logProb;
        sumSquaredLoglikelihood += logProb * logProb;
        numExamples += 1;
      } else {
        numZeroProbabilityExamples++;
      }
    }

    /**
     * Gets the average loglikelihood of the accumulated predictions.
     * 
     * @return
     */
    public double getAverageLoglikelihood() {
      if (numZeroProbabilityExamples > 0) {
        return Double.NEGATIVE_INFINITY;
      }
      return getAverageLoglikelihoodIgnoreZeros();
    }

    /**
     * Gets the variance of the average loglikelihood estimator (as returned by
     * {@link #getAverageLoglikelihood()}). This quantity should be used for
     * statistical significance tests (e.g., t-tests). Testing for significant
     * differences in perplexity should be performed using average
     * loglikelihood, as average loglikelihood is normally distributed (by the
     * central limit theorem).
     * 
     * @return
     */
    public double getLoglikelihoodVariance() {
      if (numZeroProbabilityExamples > 0) {
        return Double.POSITIVE_INFINITY;
      }
      return getLoglikelihoodVarianceIgnoreZeros();
    }

    /**
     * Gets the average loglikelihood of the accumulated predictions, ignoring
     * any predictions which had zero probability. This quantity is not a
     * valuable metric for reporting in papers, etc., but may be useful while
     * developing an algorithm.
     * 
     * @return
     */
    public double getAverageLoglikelihoodIgnoreZeros() {
      return loglikelihood / numExamples;
    }

    /**
     * Gets the variance of the average loglikelihood, ignoring zeros. As with
     * {@link #getAverageLoglikelihoodIgnoreZeros()}, this quantity is intended
     * to be used during algorithm development, but not reported in
     * publications.
     * 
     * @return
     */
    public double getLoglikelihoodVarianceIgnoreZeros() {
      return ((sumSquaredLoglikelihood / numExamples) - Math.pow(getAverageLoglikelihoodIgnoreZeros(), 2)) / numExamples;
    }

    /**
     * Gets the perplexity of the accumulated predictions.
     * 
     * @return
     */
    public double getPerplexity() {
      return Math.exp(-1 * getAverageLoglikelihood());
    }

    /**
     * Gets the perplexity of the accumulated predictions, ignoring any
     * predictions which had zero probability.
     * 
     * @return
     */
    public double getPerplexityIgnoreZeros() {
      return Math.exp(-1 * getAverageLoglikelihoodIgnoreZeros());
    }

    /**
     * Gets the number of examples which had zero probability.
     * 
     * @return
     */
    public int getNumZeroProbabilityExamples() {
      return numZeroProbabilityExamples;
    }
  }
}
