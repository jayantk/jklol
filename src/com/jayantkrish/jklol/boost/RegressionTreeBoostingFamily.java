package com.jayantkrish.jklol.boost;

import java.util.Arrays;
import java.util.List;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.jayantkrish.jklol.dtree.RegressionTree;
import com.jayantkrish.jklol.dtree.RegressionTreeTrainer;
import com.jayantkrish.jklol.models.DiscreteFactor;
import com.jayantkrish.jklol.models.DiscreteVariable;
import com.jayantkrish.jklol.models.Factor;
import com.jayantkrish.jklol.models.RegressionTreeFactor;
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.models.parametric.SufficientStatistics;
import com.jayantkrish.jklol.parallel.MapReduceConfiguration;
import com.jayantkrish.jklol.parallel.MapReduceExecutor;
import com.jayantkrish.jklol.parallel.Mapper;
import com.jayantkrish.jklol.parallel.Reducers;
import com.jayantkrish.jklol.tensor.AppendOnlySparseTensorBuilder;
import com.jayantkrish.jklol.tensor.DenseTensor;
import com.jayantkrish.jklol.tensor.DenseTensorBuilder;
import com.jayantkrish.jklol.tensor.SparseTensor;
import com.jayantkrish.jklol.tensor.Tensor;
import com.jayantkrish.jklol.tensor.TensorBuilder;
import com.jayantkrish.jklol.util.Assignment;

/**
 * Family that trains a set of regression trees to predict outcome
 * weights. This family is defined over a single vector-valued
 * variable, and any number of discrete variables. Regression trees
 * are trained to predict outcome weights for the discrete variables
 * using the value of the vector variable as features.
 * 
 * @author jayant
 */
public class RegressionTreeBoostingFamily extends AbstractBoostingFactorFamily {
  private static final long serialVersionUID = 1L;

  private final RegressionTreeTrainer trainer;

  private final DiscreteVariable featureDictionary;
  private final Tensor outputOutcomes;

  /**
   * 
   * @param conditionalVars
   * @param unconditionalVars
   * @param trainer
   * @param featureDictionary
   * @param outputOutcomes
   *          The set of possible output outcomes, each of which
   *          receives its own regression tree. If {@code null}, all
   *          possible outcomes are used.
   */
  public RegressionTreeBoostingFamily(VariableNumMap conditionalVars,
      VariableNumMap unconditionalVars, RegressionTreeTrainer trainer,
      DiscreteVariable featureDictionary, Tensor outputOutcomes) {
    super(conditionalVars, unconditionalVars);
    this.trainer = Preconditions.checkNotNull(trainer);

    this.featureDictionary = Preconditions.checkNotNull(featureDictionary);
    if (outputOutcomes != null) {
      this.outputOutcomes = outputOutcomes;
    } else {
      this.outputOutcomes = DenseTensor.constant(unconditionalVars.getVariableNumsArray(),
          unconditionalVars.getVariableSizes(), 1.0);
    }

    Preconditions.checkArgument(conditionalVars.size() == 1);
  }

  @Override
  public FunctionalGradient getNewFunctionalGradient() {
    return FactorFunctionalGradient.empty();
  }

  @Override
  public SufficientStatistics getNewSufficientStatistics() {
    RegressionTree[] trees = new RegressionTree[outputOutcomes.size()];
    for (int i = 0; i < trees.length; i++) {
      trees[i] = RegressionTree.createLeaf(0.0);
    }
    return new RegressionTreeSufficientStatistics(trees);
  }

  @Override
  public Factor getModelFromParameters(SufficientStatistics statistics) {
    RegressionTreeSufficientStatistics stats = (RegressionTreeSufficientStatistics) statistics;
    return new RegressionTreeFactor(getConditionalVariables(), getUnconditionalVariables(),
        featureDictionary, stats.getTrees(), outputOutcomes);
  }

  @Override
  public void incrementGradient(FunctionalGradient gradient, Factor marginal,
      Assignment assignment) {
    ((FactorFunctionalGradient) gradient).addExample(marginal, assignment);
  }

  @Override
  public SufficientStatistics projectGradient(FunctionalGradient gradient) {
    FactorFunctionalGradient grad = (FactorFunctionalGradient) gradient;
    int[] outputDims = outputOutcomes.getDimensionNumbers();
    int exampleDim = outputDims[outputDims.length - 1] + 1;
    int featureDim = exampleDim + 1;

    // Build the feature matrix from the feature vectors in the
    // assignments.
    List<Assignment> assignments = grad.getRegressionAssignments();
    TensorBuilder featureMatrixBuilder = new AppendOnlySparseTensorBuilder(new int[] { exampleDim, featureDim },
        new int[] { assignments.size(), featureDictionary.numValues() });
    for (int i = 0; i < assignments.size(); i++) {
      Assignment assignment = assignments.get(i);
      Tensor featureVector = (Tensor) assignment.getOnlyValue();
      int numValues = featureVector.size();
      int[] dimKey = new int[1];
      for (int j = 0; j < numValues; j++) {
        featureVector.keyNumToDimKey(featureVector.indexToKeyNum(j), dimKey);
        featureMatrixBuilder.put(new int[] { i, dimKey[0] }, featureVector.getByIndex(j));
      }
    }
    Tensor featureMatrix = featureMatrixBuilder.build();

    // Build a tensor of the regression targets for each outcome.
    int[] newDims = Arrays.copyOf(outputDims, outputDims.length + 1);
    newDims[newDims.length - 1] = exampleDim;
    int[] newDimSizes = Arrays.copyOf(outputOutcomes.getDimensionSizes(), outputDims.length + 1);
    newDimSizes[newDimSizes.length - 1] = assignments.size();
    TensorBuilder targetBuilder = new DenseTensorBuilder(newDims, newDimSizes);

    int[] exampleDims = new int[] { exampleDim };
    int[] exampleDimSizes = new int[] { assignments.size() };

    List<Factor> targets = grad.getRegressionTargets();
    for (int i = 0; i < assignments.size(); i++) {
      DiscreteFactor target = targets.get(i).coerceToDiscrete();
      targetBuilder.increment(target.getWeights().outerProduct(
          SparseTensor.singleElement(exampleDims, exampleDimSizes, new int[] { i }, 1.0)));
    }
    Tensor targetTensor = targetBuilder.build();

    // Train a regression tree for each outcome. Trees are trained in parallel.
    List<RegressionTreeData> dataSets = Lists.newArrayList();
    for (int i = 0; i < outputOutcomes.size(); i++) {
      int[] dimKey = outputOutcomes.keyNumToDimKey(outputOutcomes.indexToKeyNum(i));
      Tensor outcomeTargets = targetTensor.slice(outputDims, dimKey);
      dataSets.add(new RegressionTreeData(featureMatrix, outcomeTargets, i));
    }
    MapReduceExecutor executor = MapReduceConfiguration.getMapReduceExecutor();
    List<TrainedRegressionTree> trainedTrees = executor.mapReduce(dataSets, 
        new RegressionTreeMapper(trainer), Reducers.<TrainedRegressionTree>getAggregatingListReducer());

    // Parallel execution may reorder the trees. Identify the appropriate tree
    // for each outcome.
    RegressionTree[] trees = new RegressionTree[outputOutcomes.size()];
    for (TrainedRegressionTree tree : trainedTrees) {
      trees[tree.getTreeIndex()] = tree.getTree();
    }

    return new RegressionTreeSufficientStatistics(trees);
  }

  @Override
  public String getParameterDescription(SufficientStatistics parameters, int numFeatures) {
    RegressionTree[] trees = ((RegressionTreeSufficientStatistics) parameters).getTrees();
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < trees.length; i++) {
      sb.append(trees[i].toString());
      sb.append("\n");
    }
    return sb.toString();
  }

  private static class RegressionTreeData {
    private final Tensor featureMatrix;
    private final Tensor targets;

    private final int treeIndex;

    public RegressionTreeData(Tensor featureMatrix, Tensor targets, int treeIndex) {
      this.featureMatrix = featureMatrix;
      this.targets = targets;
      this.treeIndex = treeIndex;
    }

    public Tensor getFeatureMatrix() {
      return featureMatrix;
    }

    public Tensor getTargets() {
      return targets;
    }

    public int getTreeIndex() {
      return treeIndex;
    }
  }

  private static class TrainedRegressionTree {
    private final int treeIndex;
    private final RegressionTree tree;

    public TrainedRegressionTree(int treeIndex, RegressionTree tree) {
      this.treeIndex = treeIndex;
      this.tree = tree;
    }

    public int getTreeIndex() {
      return treeIndex;
    }

    public RegressionTree getTree() {
      return tree;
    }
  }

  private static class RegressionTreeMapper extends Mapper<RegressionTreeData, TrainedRegressionTree> {    
    private final RegressionTreeTrainer trainer;

    public RegressionTreeMapper(RegressionTreeTrainer trainer) {
      this.trainer = trainer;
    }

    @Override
    public TrainedRegressionTree map(RegressionTreeData item) {
      RegressionTree tree = trainer.train(item.getFeatureMatrix(), item.getTargets());
      return new TrainedRegressionTree(item.getTreeIndex(), tree);
    }
  }
}
