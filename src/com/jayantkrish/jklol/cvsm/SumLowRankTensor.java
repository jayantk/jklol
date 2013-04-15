package com.jayantkrish.jklol.cvsm;

import java.util.List;

import com.google.common.base.Preconditions;
import com.google.common.collect.BiMap;
import com.google.common.collect.Lists;
import com.jayantkrish.jklol.tensor.Tensor;
import com.jayantkrish.jklol.tensor.Tensors;

/**
 * Represents a sum over a collection of low rank tensors. For
 * example, this class may be used to represent the sum over a set of
 * tensor outer products, the result of which is a low rank tensor.
 * 
 * @author jayantk
 */
public class SumLowRankTensor extends AbstractLowRankTensor {
  private static final long serialVersionUID = 1L;

  private final LowRankTensor[] tensors;

  private SumLowRankTensor(int[] dimensionNums, int[] dimensionSizes,
      LowRankTensor[] tensors) {
    super(dimensionNums, dimensionSizes);
    this.tensors = Preconditions.checkNotNull(tensors);
    Preconditions.checkArgument(tensors.length > 0);
  }

  public static SumLowRankTensor create(LowRankTensor[] tensors) {
    Preconditions.checkArgument(tensors.length > 0);
    int[] dimensionNums = tensors[0].getDimensionNumbers();
    int[] dimensionSizes = tensors[0].getDimensionSizes();

    return new SumLowRankTensor(dimensionNums, dimensionSizes, tensors);
  }
  
  public LowRankTensor[] getTerms() {
    return tensors;
  }

  @Override
  public Tensor getTensor() {
    List<Tensor> elements = Lists.newArrayList();
    for (int i = 0; i < tensors.length; i++) {
      elements.add(tensors[i].getTensor());
    }
    return Tensors.elementwiseAddition(elements);
  }

  @Override
  public LowRankTensor relabelDimensions(BiMap<Integer, Integer> relabeling) {
    LowRankTensor[] relabeled = new LowRankTensor[tensors.length];
    for (int i = 0; i < tensors.length; i++) {
      relabeled[i] = tensors[i].relabelDimensions(relabeling);
    }
    return SumLowRankTensor.create(relabeled);
  }

  @Override
  public LowRankTensor innerProduct(LowRankTensor other) {
    LowRankTensor[] results = new LowRankTensor[tensors.length];
    for (int i = 0; i < tensors.length; i++) {
      results[i] = tensors[i].innerProduct(other);
    }
    return SumLowRankTensor.create(results);
  }
  
  @Override
  public LowRankTensor elementwiseProduct(double value) {
    LowRankTensor[] multiplied = new LowRankTensor[tensors.length];
    for (int i = 0; i < tensors.length; i++) {
      multiplied[i] = tensors[i].elementwiseProduct(value);
    }
    return SumLowRankTensor.create(multiplied);
  }
}
