package com.jayantkrish.jklol.boost;

import java.util.Arrays;

import com.google.common.base.Preconditions;
import com.jayantkrish.jklol.models.Factor;
import com.jayantkrish.jklol.models.TableFactor;
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.tensor.DenseTensorBuilder;
import com.jayantkrish.jklol.tensor.TensorBuilder;
import com.jayantkrish.jklol.util.Assignment;

/**
 * Functional gradient implementation for
 * {@link AveragingBoostingFamily}. This class sums up all of the
 * regression targets, making it only suitable for families where only
 * the mean target value for each outcome is need.
 * 
 * @author jayantk
 */
public class MeanFunctionalGradient implements FunctionalGradient {

  private VariableNumMap vars;
  private TensorBuilder regressionTargetSum;
  private int numTargets;

  public MeanFunctionalGradient(VariableNumMap vars, TensorBuilder regressionTargetSum,
      int numTargets) {
    this.vars = Preconditions.checkNotNull(vars);
    this.regressionTargetSum = Preconditions.checkNotNull(regressionTargetSum);
    this.numTargets = numTargets;

    Preconditions.checkArgument(Arrays.equals(vars.getVariableNumsArray(),
        regressionTargetSum.getDimensionNumbers()));
  }

  public static MeanFunctionalGradient empty(VariableNumMap vars) {
    TensorBuilder builder = new DenseTensorBuilder(vars.getVariableNumsArray(),
        vars.getVariableSizes());
    return new MeanFunctionalGradient(vars, builder, 0);
  }

  public void addExample(Factor regressionTarget, Assignment assignment) {
    Preconditions.checkArgument(assignment.size() == 0);
    regressionTargetSum.increment(regressionTarget.coerceToDiscrete().getWeights());
    numTargets++;
  }

  public Factor getRegressionTargetSum() {
    return new TableFactor(vars, regressionTargetSum.build());
  }

  public int getNumTargets() {
    return numTargets;
  }

  @Override
  public void combineExamples(FunctionalGradient other) {
    MeanFunctionalGradient factorOther = (MeanFunctionalGradient) other;
    regressionTargetSum.increment(factorOther.regressionTargetSum);
    numTargets += factorOther.numTargets;
  }
}
