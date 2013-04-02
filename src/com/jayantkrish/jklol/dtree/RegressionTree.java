package com.jayantkrish.jklol.dtree;

import java.io.Serializable;

import com.google.common.base.Preconditions;
import com.jayantkrish.jklol.tensor.Tensor;

public class RegressionTree implements Serializable {
  private static final long serialVersionUID = 1L;

  // Populated values if this is a split point in the tree.
  // If this is a leaf, featureNum = -1
  private final int featureNum;
  private final double splitPoint;
  private final RegressionTree lowerTree;
  private final RegressionTree higherTree;
  
  // Only valid if both lowerTree and higher tree are null.
  private final double valueToPredict;
  
  public RegressionTree(int featureNum, double splitPoint, RegressionTree lowerTree,
      RegressionTree higherTree, double valueToPredict) {
    this.featureNum = featureNum;
    this.splitPoint = splitPoint;
    this.lowerTree = lowerTree;
    this.higherTree = higherTree;
    Preconditions.checkState(!(featureNum == -1 ^ lowerTree == null));
    Preconditions.checkState(!(featureNum == -1 ^ higherTree == null));
    
    this.valueToPredict = valueToPredict;
  }
  
  public static RegressionTree createSplit(int featureNum, double splitPoint,
      RegressionTree lowerTree, RegressionTree higherTree) {
    return new RegressionTree(featureNum, splitPoint, lowerTree, higherTree, 0);
  }
  
  public static RegressionTree createLeaf(double valueToPredict) {
    return new RegressionTree(-1, 0, null, null, valueToPredict);
  }
  
  public boolean isLeaf() {
    return featureNum == -1;
  }
  
  public int getFeature() {
    return featureNum;
  }
  
  public double getSplitPoint() {
    return splitPoint;
  }
  
  public RegressionTree getLowerTree() {
    return lowerTree;
  }
  
  public RegressionTree getHigherTree() {
    return higherTree;
  }
  
  public double getLeafValue() {
    return valueToPredict;
  }
  
  public double regress(Tensor datum) {
    Preconditions.checkArgument(datum.getDimensionNumbers().length == 1);
    
    if (isLeaf()) {
      return valueToPredict;
    } else {
      if (datum.getByDimKey(featureNum) > splitPoint) {
        return higherTree.regress(datum);
      } else {
        return lowerTree.regress(datum);
      }
    }
  }
  
  public String toString() {
    StringBuilder sb = new StringBuilder();
    toStringHelper(sb, "");
    return sb.toString();
  }
  
  private void toStringHelper(StringBuilder sb, String indent) {
    if (isLeaf()) {
      sb.append(indent);
      sb.append(valueToPredict);
    } else {
      sb.append(indent);
      sb.append("((feat");
      sb.append(featureNum + " <= " + splitPoint + ")\n");
      lowerTree.toStringHelper(sb, indent + "  ");
      sb.append("\n");
      higherTree.toStringHelper(sb, indent + "  ");
      sb.append(")");
    }
  }
}
