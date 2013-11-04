package com.jayantkrish.jklol.inference;

import java.util.Arrays;

import com.google.common.base.Preconditions;
import com.jayantkrish.jklol.models.Factor;
import com.jayantkrish.jklol.models.TableFactor;
import com.jayantkrish.jklol.tensor.SparseTensor;
import com.jayantkrish.jklol.tensor.Tensor;

/**
 * Beam search pruning strategy that discards assignments
 * whose probability is less than some fraction of the maximum
 * probability assignment.
 * 
 * @author jayantk
 */
public class BeamPruningStrategy implements PruningStrategy {
  private static final long serialVersionUID = 1L;

  private final double minProbabilityRatio;

  public BeamPruningStrategy(double minProbabilityRatio) {
    this.minProbabilityRatio = minProbabilityRatio;
    Preconditions.checkArgument(minProbabilityRatio >= 0.0 && minProbabilityRatio <= 1.0);
  }

  @Override
  public Factor apply(Factor factor) {
    Tensor weights = factor.coerceToDiscrete().getWeights();
    long[] bestKey = weights.getLargestValues(1);
    double bestValue = weights.get(bestKey[0]);
    double threshold = bestValue * minProbabilityRatio;
    
    long[] newKeyNums = new long[weights.size()];
    double[] newValues = new double[weights.size()];
    double[] values = weights.getValues();
    int numValues = values.length;
    int numFilled = 0;
    for (int i = 0; i < numValues; i++) {
      if (values[i] >= threshold) {
        newValues[numFilled] = values[i];
        newKeyNums[numFilled] = weights.indexToKeyNum(i);
        numFilled++;
      }
    }
    
    long[] newKeyNumsCopy = Arrays.copyOf(newKeyNums, numFilled);
    double[] newValuesCopy = Arrays.copyOf(newValues, numFilled);
    Tensor newWeights = new SparseTensor(weights.getDimensionNumbers(), weights.getDimensionSizes(),
        newKeyNumsCopy, newValuesCopy);

    return new TableFactor(factor.getVars(), newWeights);
  }
}
