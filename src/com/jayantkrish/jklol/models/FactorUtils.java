package com.jayantkrish.jklol.models;

import java.util.Collection;
import java.util.List;

import com.google.common.collect.Lists;

/**
 * Utility methods for manipulating collections of {@link Factor}s.
 * 
 * @author jayant
 */
public final class FactorUtils {

  /**
   * Applies {@link Factor.coerceToDiscrete} to every {@code Factor} in {@code
   * factors}.
   * 
   * @param factors
   * @return
   */
  public static List<DiscreteFactor> coerceToDiscrete(Collection<Factor> factors) {
    List<DiscreteFactor> returnFactors = Lists.newArrayList();
    for (Factor factor : factors) {
      returnFactors.add(factor.coerceToDiscrete());
    }
    return returnFactors;
  }
}
