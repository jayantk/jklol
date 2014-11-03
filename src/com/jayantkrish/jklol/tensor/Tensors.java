package com.jayantkrish.jklol.tensor;

import java.util.List;

import com.google.common.base.Preconditions;

/**
 * Static utility methods for manipulating {@code Tensor}s.
 * 
 * @author jayantk
 */
public class Tensors {

  public static Tensor elementwiseAddition(List<Tensor> tensors) {
    Preconditions.checkArgument(tensors.size() > 0);

    Tensor result = tensors.get(0);
    for (int i = 1; i < tensors.size(); i++) {
      result = result.elementwiseAddition(tensors.get(i));
    }
    return result;
  }
}
