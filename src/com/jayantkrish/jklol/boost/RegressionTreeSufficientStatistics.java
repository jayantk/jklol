package com.jayantkrish.jklol.boost;

import java.util.Arrays;

import com.google.common.base.Preconditions;
import com.jayantkrish.jklol.dtree.RegressionTree;
import com.jayantkrish.jklol.models.parametric.ListSufficientStatistics;
import com.jayantkrish.jklol.models.parametric.SufficientStatistics;

/**
 * Stores a collection of regression trees. This class does not
 * implement the methods of {@code SufficientStatistics} which 
 * only make sense for vector-valued parameters.
 * 
 * @author jayant
 */
public class RegressionTreeSufficientStatistics implements SufficientStatistics {
  private static final long serialVersionUID = 1L;
  
  private final RegressionTree[] trees;
  
  public RegressionTreeSufficientStatistics(RegressionTree[] trees) {
    this.trees = Preconditions.checkNotNull(trees);
  }
  
  public RegressionTree[] getTrees() {
    return trees;
  }

  @Override
  public void increment(SufficientStatistics other, double multiplier) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void transferParameters(SufficientStatistics other) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void increment(double amount) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void multiply(double amount) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void perturb(double stddev) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void softThreshold(double threshold) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void findEntriesLargerThan(double threshold) {
    throw new UnsupportedOperationException();
  }

  @Override
  public SufficientStatistics duplicate() {
    return new RegressionTreeSufficientStatistics(Arrays.copyOf(trees, trees.length));
  }

  @Override
  public double innerProduct(SufficientStatistics other) {
    throw new UnsupportedOperationException();
  }

  @Override
  public double getL2Norm() {
    throw new UnsupportedOperationException();
  }

  @Override
  public ListSufficientStatistics coerceToList() {
    throw new UnsupportedOperationException();
  }

  @Override
  public void makeDense() {}
  
  @Override
  public void zeroOut() {}

  @Override
  public String getDescription() {
    StringBuilder sb = new StringBuilder();
    for (RegressionTree tree : trees) {
      sb.append(tree.toString());
    }
    return sb.toString();
  }
}
