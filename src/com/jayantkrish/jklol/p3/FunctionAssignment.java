package com.jayantkrish.jklol.p3;

import java.util.List;

import com.google.common.base.Supplier;
import com.jayantkrish.jklol.tensor.Tensor;

public interface FunctionAssignment {

  public Object getValue(List<?> args);
  
  public void putValue(List<?> args, Object value);

  public boolean isConsistentWith(FunctionAssignment other);

  public boolean isEqualTo(FunctionAssignment other);

  /**
   * Gets a feature vector representing the current assignment
   * of values to this function. These features are used in a
   * per-function classifier that scores this assignment. That is,
   * the score assigned using these features is independent of
   * all other assignments. 
   *
   * @return
   */
  public Tensor getFeatureVector();

  public Tensor getPredicateFeatureVector();

  public FunctionAssignment copy();
  
  public void copyTo(FunctionAssignment assignment);

  public static Supplier<FunctionAssignment> getCopySupplier(final FunctionAssignment a) {
    return new Supplier<FunctionAssignment>() {
      @Override
      public FunctionAssignment get() {
        return a.copy();
      }
    };
  }
}
