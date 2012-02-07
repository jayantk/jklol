package com.jayantkrish.jklol.preprocessing;

import java.util.Map;

/**
 * Represents a method for generating features of type {@code B} from objects of
 * type {@code A}. {@code FeatureGenerator} is intended to be used in
 * conjunction with a {@code FeatureVectorGenerator} to construct feature
 * vectors from raw data.
 * 
 * @author jayantk
 * @param <A>
 * @param <B>
 */
public interface FeatureGenerator<A, B> {

  /**
   * Generates features for {@code item} and returns their values in a map. The
   * returned may may contain different key sets for different inputs. Any keys
   * which are not generated on a given input are assumed to have a count of 0.
   * 
   * @param item
   * @return
   */
  public Map<B, Double> generateFeatures(A item);
}
