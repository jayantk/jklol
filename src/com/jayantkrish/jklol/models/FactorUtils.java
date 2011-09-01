package com.jayantkrish.jklol.models;

import java.util.Collection;
import java.util.List;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;

/**
 * Utility methods for manipulating collections of {@link Factor}s.
 * 
 * @author jayant
 */
public final class FactorUtils {

  /**
   * Applies {@link Factor.coerceToDiscrete} to every {@code Factor} in
   * {@code factors}.
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

  /**
   * Multiplies together {@code factors} and returns the result. This is a
   * convenience wrapper around {@link Factor#product(Factor)}. {@code factors}
   * cannot be empty.
   * 
   * @param factors
   * @return
   */
  public static Factor product(List<Factor> factors) {
    Preconditions.checkNotNull(factors);
    Preconditions.checkArgument(!factors.isEmpty());
    return factors.get(0).product(factors.subList(1, factors.size()));
  }
  
  /**
   * Adds together {@code factors} and returns the result. This is a
   * convenience wrapper around {@link Factor#add(Factor)}. {@code factors}
   * cannot be empty.
   * 
   * @param factors
   * @return
   */
  public static Factor add(List<Factor> factors) {
    Preconditions.checkNotNull(factors);
    Preconditions.checkArgument(!factors.isEmpty());
    return factors.get(0).add(factors.subList(1, factors.size()));
  }
}
