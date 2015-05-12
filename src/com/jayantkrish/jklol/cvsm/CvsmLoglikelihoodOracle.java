package com.jayantkrish.jklol.cvsm;

import java.util.List;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.jayantkrish.jklol.ccg.lambda.Expression;
import com.jayantkrish.jklol.cvsm.lrt.TensorLowRankTensor;
import com.jayantkrish.jklol.cvsm.tree.CvsmHingeElementwiseLossTree;
import com.jayantkrish.jklol.cvsm.tree.CvsmKlElementwiseLossTree;
import com.jayantkrish.jklol.cvsm.tree.CvsmKlLossTree;
import com.jayantkrish.jklol.cvsm.tree.CvsmLogisticTree;
import com.jayantkrish.jklol.cvsm.tree.CvsmSquareLossTree;
import com.jayantkrish.jklol.cvsm.tree.CvsmTree;
import com.jayantkrish.jklol.cvsm.tree.CvsmValueLossTree;
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
  public double accumulateGradient(SufficientStatistics gradient,
      SufficientStatistics currentParameters, Cvsm instantiatedModel,
      CvsmExample example, LogFunction log) {
    CvsmTree tree = instantiatedModel.getInterpretationTree(example.getLogicalForm());
    CvsmTree gradientTree = lossFunction.augmentTreeWithLoss(tree, instantiatedModel, example.getTargets());

    log.startTimer("backpropagate_gradient");
    Tensor root = gradientTree.getValue().getTensor();
    CvsmGradient cvsmGradient = new CvsmGradient(); 
    gradientTree.backpropagateGradient(TensorLowRankTensor.zero(
        root.getDimensionNumbers(), root.getDimensionSizes()), cvsmGradient);

    family.incrementSufficientStatistics(cvsmGradient, instantiatedModel, gradient);
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
    CvsmTree augmentTreeWithLoss(CvsmTree tree, Cvsm cvsm, Tensor targets);
  }
  
  public static class CvsmSquareLoss implements CvsmLoss {
    @Override
    public CvsmTree augmentTreeWithLoss(CvsmTree tree, Cvsm cvsm, Tensor targets) {
      return new CvsmSquareLossTree(targets, tree);
    }
  }
  
  /**
   * KL divergence loss function where the output targets
   * represent a multinomial distribution over output.
   * 
   * @author jayantk
   */
  public static class CvsmKlLoss implements CvsmLoss {
    @Override
    public CvsmTree augmentTreeWithLoss(CvsmTree tree, Cvsm cvsm, Tensor targets) {
      return new CvsmKlLossTree(targets, tree);
    }
  }
  
  public static class CvsmValueLoss implements CvsmLoss {
    @Override
    public CvsmTree augmentTreeWithLoss(CvsmTree tree, Cvsm cvsm, Tensor targets) {
      return new CvsmValueLossTree(tree);
    }
  }

  public static class CvsmKlElementwiseLoss implements CvsmLoss {
    @Override
    public CvsmTree augmentTreeWithLoss(CvsmTree tree, Cvsm cvsm, Tensor targets) {
      return new CvsmKlElementwiseLossTree(targets, new CvsmLogisticTree(tree));
    }
  }

  public static class CvsmHingeElementwiseLoss implements CvsmLoss {
    @Override
    public CvsmTree augmentTreeWithLoss(CvsmTree tree, Cvsm cvsm, Tensor targets) {
      return new CvsmHingeElementwiseLossTree(targets, tree);
    }
  }

  public static class CvsmTreeLoss implements CvsmLoss {
    private CvsmLoss nodeLoss;
    
    private Expression augmentingExpression;
    private String bindingName;

    public CvsmTree augmentTreeWithLoss(CvsmTree tree, Cvsm cvsm, Tensor targets) {
      CvsmTree augmented = augmentTreeHelper(tree, targets, cvsm);
      return nodeLoss.augmentTreeWithLoss(augmented, cvsm, targets);
    }

    private CvsmTree augmentTreeHelper(CvsmTree tree, Tensor targets, Cvsm cvsm) {
      List<CvsmTree> subtrees = tree.getSubtrees();
      List<CvsmTree> augmentedSubtrees = Lists.newArrayList();
      for (CvsmTree subtree : subtrees) {
        augmentedSubtrees.add(augmentTreeHelper(subtree, targets, cvsm));
      }
      
      CvsmTree result = tree.replaceSubtrees(augmentedSubtrees);

      /*
      if (tree instanceof CvsmNullTree) {
        LowRankTensor value = tree.getValue();
        Cvsm newEnvironment = cvsm.addBinding(bindingName, value);
        CvsmTree evaluationTree = newEnvironment.getInterpretationTree(augmentingExpression);
        evaluationTree = nodeLoss.augmentTreeWithLoss(evaluationTree, newEnvironment, targets);
        result = new CvsmSplitTree(result, evaluationTree, bindingName);
      }
      */
      return result;
    }
  }
}
