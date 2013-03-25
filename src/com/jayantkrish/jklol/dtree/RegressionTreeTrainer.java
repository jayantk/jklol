package com.jayantkrish.jklol.dtree;

import com.google.common.base.Preconditions;
import com.jayantkrish.jklol.tensor.DenseTensor;
import com.jayantkrish.jklol.tensor.Tensor;

public class RegressionTreeTrainer {
  
  private final int maxDepth;
  
  public RegressionTreeTrainer(int maxDepth) {
    this.maxDepth = maxDepth;
    Preconditions.checkArgument(maxDepth >= 0);
  }

  public RegressionTree train(Tensor data, Tensor targets) {
    return trainHelper(data, targets, DenseTensor.constant(
        targets.getDimensionNumbers(), targets.getDimensionSizes(), 1.0), 0);
  }
  
  private RegressionTree trainHelper(Tensor data, Tensor targets, Tensor indicators, int curDepth) {
    if (curDepth >= maxDepth) {
      return fitLeaf(targets, indicators);
    } else {
      Split split = findSplitForIndicatorFeatures(data, targets, indicators);

      int featureDim = data.getDimensionNumbers()[1];
      Tensor featureValues = data.slice(new int[] {featureDim}, new int[] {split.featureNum});
      Tensor higherIndicators = featureValues.findKeysLargerThan(split.splitValue);
      Tensor lowerIndicators = indicators.elementwiseAddition(higherIndicators.elementwiseProduct(-1.0));
      
      int numHigher = (int) higherIndicators.sumOutDimensions(higherIndicators.getDimensionNumbers()).getByDimKey();
      int numLower = (int) lowerIndicators.sumOutDimensions(lowerIndicators.getDimensionNumbers()).getByDimKey();
      
      if (numHigher == 0 || numLower == 0) {
        // No split exists that partitions the data into two sets.
        return fitLeaf(targets, indicators);
      }

      Tensor lowerData = data.elementwiseProduct(lowerIndicators);
      Tensor lowerTargets = targets.elementwiseProduct(lowerIndicators);
      Tensor higherData = data.elementwiseProduct(higherIndicators);
      Tensor higherTargets = targets.elementwiseProduct(higherIndicators);

      return RegressionTree.createSplit(split.featureNum, split.splitValue, 
          trainHelper(lowerData, lowerTargets, lowerIndicators, curDepth + 1),
          trainHelper(higherData, higherTargets, higherIndicators, curDepth + 1));
    }
  }
  
  private RegressionTree fitLeaf(Tensor targets, Tensor indicators) {
    double numExamples = indicators.sumOutDimensions(indicators.getDimensionNumbers()).getByDimKey();
    double sumOfExamples = targets.sumOutDimensions(targets.getDimensionNumbers()).getByDimKey();
    return RegressionTree.createLeaf(sumOfExamples / numExamples);
  }

  private static Split findSplitForIndicatorFeatures(Tensor data, Tensor targets,
      Tensor indicators) {
    Preconditions.checkArgument(data.getDimensionNumbers().length == 2);
    Preconditions.checkArgument(targets.getDimensionNumbers().length == 1);

    int dataDim = data.getDimensionNumbers()[0];
    int numDataPoints = (int) indicators.sumOutDimensions(indicators.getDimensionNumbers()).getByDimKey();
    int featureDim = data.getDimensionNumbers()[1];
    int numFeatures = data.getDimensionSizes()[1];
    // Assumption: data is indicator features.
    Tensor featureIndicators = DenseTensor.constant(new int[] {featureDim}, new int[] {numFeatures}, 1.0);
    Tensor featureSums = targets.sumOutDimensions(dataDim).outerProduct(featureIndicators);
    Tensor featureSumSquares = targets.elementwiseProduct(targets).sumOutDimensions(dataDim)
        .outerProduct(featureIndicators);

    Tensor featureOneCounts = data.sumOutDimensions(dataDim);
    Tensor featureOneSums = data.elementwiseProduct(targets).sumOutDimensions(dataDim);
    Tensor featureOneSumSquares = data.elementwiseProduct(targets.elementwiseProduct(targets))
        .sumOutDimensions(new int[] {dataDim});
    
    Tensor featureZeroCounts = featureOneCounts.elementwiseProduct(-1.0).elementwiseAddition(numDataPoints);
    Tensor featureZeroSums = featureSums.elementwiseAddition(featureOneSums.elementwiseProduct(-1.0));
    Tensor featureZeroSumSquares = featureSumSquares.elementwiseAddition(featureOneSumSquares.elementwiseProduct(-1.0));
    
    Tensor featureOneMeans = featureOneSums.elementwiseProduct(featureOneCounts.elementwiseInverse());
    Tensor featureZeroMeans = featureZeroSums.elementwiseProduct(featureZeroCounts.elementwiseInverse());
    
    Tensor featureOneSquareLoss = featureOneSumSquares.elementwiseAddition(
        featureOneMeans.elementwiseProduct(featureOneMeans).elementwiseProduct(
            featureOneCounts.elementwiseProduct(-1.0)));
    Tensor featureZeroSquareLoss = featureZeroSumSquares.elementwiseAddition(
        featureZeroMeans.elementwiseProduct(featureZeroMeans).elementwiseProduct(
            featureZeroCounts.elementwiseProduct(-1.0)));

    Tensor featureSquareLoss = featureOneSquareLoss.elementwiseAddition(featureZeroSquareLoss);
    
    long[] bestFeature = featureSquareLoss.elementwiseProduct(-1.0).getLargestValues(1);
    int bestFeatureNum = featureSquareLoss.keyNumToDimKey(bestFeature[0])[0];
    return new Split(bestFeatureNum, 0.5, featureZeroMeans.getByDimKey(bestFeatureNum),
        featureOneMeans.getByDimKey(bestFeatureNum));
  }

  private static class Split {
    public final int featureNum;
    public final double splitValue;
    
    public final double lowerMean;
    public final double higherMean;

    public Split(int featureNum, double splitValue, double lowerMean,
        double higherMean) {
      this.featureNum = featureNum;
      this.splitValue = splitValue;
      this.lowerMean = lowerMean;
      this.higherMean = higherMean;
    }
  }
}
