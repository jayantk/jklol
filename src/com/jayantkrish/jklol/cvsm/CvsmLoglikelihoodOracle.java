package com.jayantkrish.jklol.cvsm;

import com.google.common.base.Preconditions;
import com.jayantkrish.jklol.cvsm.lrt.TensorLowRankTensor;
import com.jayantkrish.jklol.cvsm.tree.CvsmKlLossTree;
import com.jayantkrish.jklol.cvsm.tree.CvsmSquareLossTree;
import com.jayantkrish.jklol.cvsm.tree.CvsmTree;
import com.jayantkrish.jklol.models.parametric.SufficientStatistics;
import com.jayantkrish.jklol.tensor.Tensor;
import com.jayantkrish.jklol.training.GradientOracle;
import com.jayantkrish.jklol.training.LogFunction;

/**
 * Oracle for training the parameters of a compositional vector space
 * model. This oracle is compatible with a number of different loss
 * functions.
 * 
 * @author jayantk
 */
public class CvsmLoglikelihoodOracle implements GradientOracle<Cvsm, CvsmExample> {

  private final CvsmFamily family;
  private final CvsmLoss lossFunction;

  public CvsmLoglikelihoodOracle(CvsmFamily family, CvsmLoss lossFunction) {
    this.family = Preconditions.checkNotNull(family);
    this.lossFunction = lossFunction;
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
    CvsmTree gradientTree = lossFunction.augmentTreeWithLoss(tree, example.getTargets());

    log.startTimer("backpropagate_gradient");
    Tensor root = gradientTree.getValue().getTensor();
    gradientTree.backpropagateGradient(TensorLowRankTensor.zero(
        root.getDimensionNumbers(), root.getDimensionSizes()), family, gradient);
    log.stopTimer("backpropagate_gradient");

    return gradientTree.getLoss();
  }
  
  public static interface CvsmLoss {

    /**
     * Adds loss nodes to {@code tree} to compute gradients, etc.
     * 
     * @param tree
     * @return
     */
    CvsmTree augmentTreeWithLoss(CvsmTree tree, Tensor targets);
  }
  
  public static class CvsmSquareLoss implements CvsmLoss {
    public CvsmTree augmentTreeWithLoss(CvsmTree tree, Tensor targets) {
      return new CvsmSquareLossTree(targets, tree);
    }
  }
  
  public static class CvsmKlLoss implements CvsmLoss {
    public CvsmTree augmentTreeWithLoss(CvsmTree tree, Tensor targets) {
      return new CvsmKlLossTree(targets, tree);
    }
  }

  /*
  public static class CvsmTreeLoss implements CvsmLoss {
    private CvsmLoss nodeLoss;
    
    public CvsmTree augmentTreeWithLoss(CvsmTree tree, Tensor targets) {
      for ()
    }
  }
  */
}
