package com.jayantkrish.jklol.cvsm;

import com.google.common.base.Preconditions;
import com.jayantkrish.jklol.models.parametric.SufficientStatistics;
import com.jayantkrish.jklol.tensor.SparseTensor;
import com.jayantkrish.jklol.tensor.Tensor;
import com.jayantkrish.jklol.training.GradientOracle;
import com.jayantkrish.jklol.training.LogFunction;

public class CvsmLoglikelihoodOracle implements GradientOracle<Cvsm, CvsmExample> {
  
  private final CvsmFamily family;
  private final boolean useSquareLoss;
  
  public CvsmLoglikelihoodOracle(CvsmFamily family, boolean useSquareLoss) {
    this.family = Preconditions.checkNotNull(family);
    this.useSquareLoss = useSquareLoss;
  }

  @Override
  public SufficientStatistics initializeGradient() {
    return family.getNewSufficientStatistics();
  }

  @Override
  public Cvsm instantiateModel(SufficientStatistics parameters) {
    return family.getModelFromParameters(parameters);
  }

  @Override
  public double accumulateGradient(SufficientStatistics gradient, Cvsm instantiatedModel,
      CvsmExample example, LogFunction log) {
    CvsmTree tree = instantiatedModel.getInterpretationTree(example.getLogicalForm());
    CvsmTree gradientTree = null;
    if (useSquareLoss) {
      gradientTree = new CvsmSquareLossTree(example.getTargetDistribution(), tree);
    } else {
      gradientTree = new CvsmKlLossTree(example.getTargetDistribution(), tree);
    }

    log.startTimer("backpropagate_gradient");
    Tensor root = gradientTree.getValue().getTensor();
    gradientTree.backpropagateGradient(LowRankTensor.zero(root.getDimensionNumbers(), root.getDimensionSizes()),
        family, gradient);
    log.stopTimer("backpropagate_gradient");

    return gradientTree.getLoss();
  }
}
