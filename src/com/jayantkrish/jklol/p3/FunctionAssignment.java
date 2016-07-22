package com.jayantkrish.jklol.p3;

import java.util.List;

import com.jayantkrish.jklol.tensor.Tensor;

public interface FunctionAssignment {

  public Object getValue(List<Object> args);
  
  public IndexableFunctionAssignment putValue(List<Object> args, Object value);
  
  public boolean isConsistentWith(FunctionAssignment other);

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
}
