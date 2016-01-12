package com.jayantkrish.jklol.tensor;

import java.io.Serializable;

/**
 * Basic tensor interface that only supports retrieving values
 * given a key.
 * 
 * @author jayantk
 *
 */
public interface TensorHash extends Serializable {

  /**
   * Gets the value associated with {@code keyNum}, which is interpreted as a
   * key by successively mod'ing it by the size of each dimension of
   * {@code this}. Equivalent {@code getByDimKey(convertToDimKey(keyNum))}.
   * 
   * @param keyNum
   * @return
   */
  double get(long keyNum);
}
