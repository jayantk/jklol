package com.jayantkrish.jklol.dtree;

import com.google.common.base.Preconditions;
import com.jayantkrish.jklol.tensor.DenseTensor;
import com.jayantkrish.jklol.tensor.Tensor;

public class RegressionTreeTrainer {

  
  public RegressionTree train(Tensor data, Tensor targets) {
    Split split = findSplitForIndicatorFeatures(data, targets);
    /*
    int splitFeature = 
    int featureDim = data.getDimensionNumbers()[1];
    Tensor featureValues = data.slice(new int[] {featureDim}, new int[] {splitFeature});
    
    Tensor higherIndicators = featureValues.isGreaterThan()
    */

    return RegressionTree.createSplit(split.featureNum, split.splitValue, 
        RegressionTree.createLeaf(split.lowerMean), RegressionTree.createLeaf(split.higherMean));
  }

  private static Split findSplitForIndicatorFeatures(Tensor data, Tensor targets) {
    Preconditions.checkArgument(data.getDimensionNumbers().length == 2);
    Preconditions.checkArgument(targets.getDimensionNumbers().length == 1);

    int dataDim = data.getDimensionNumbers()[0];
    int numDataPoints = data.getDimensionSizes()[0];
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
    
        /*
    (x_i - m)^2 = x_i^2 - (2 x_i m) + m^2 = sum (x_i^2) - 2 (n * m^2) + n * m^2
        = sum (x_i^2) - n * m^2
        */

    
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
