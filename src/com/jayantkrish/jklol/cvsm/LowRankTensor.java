package com.jayantkrish.jklol.cvsm;

import java.io.Serializable;

import com.google.common.collect.BiMap;
import com.jayantkrish.jklol.tensor.Tensor;

/**
 * Factored representation of a tensor as a sum of outer products
 * of vectors. This representation generalizes decomposed representations
 * such as USV^T (as produced by an SVD). 
 * 
 * @author jayant
 */
public interface LowRankTensor extends Serializable {
    
  public int[] getDimensionNumbers();
  
  public int[] getDimensionSizes();
  
  public Tensor getTensor();
  
  public double getByDimKey(int ... key);
  
  public LowRankTensor relabelDimensions(BiMap<Integer, Integer> relabeling);
  
  public LowRankTensor innerProduct(LowRankTensor other);
  
  public LowRankTensor elementwiseProduct(double value);
}
