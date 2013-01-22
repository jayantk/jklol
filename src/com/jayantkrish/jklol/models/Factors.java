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
public final class Factors {

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
    // The specification of .product requires that the variables in the inputVar factors are
    // a subset of the variables in the outer factor. Hence, we must identify the factor
    // containing the most variables.
    Factor biggestFactor = multiplicativeIdentity();
    int biggestFactorIndex = -1;
    for (int i = 0; i < factors.size(); i++) {
      Factor factor = factors.get(i);
      if (factor.getVars().size() >= biggestFactor.getVars().size()) {
        biggestFactor = factor;
        biggestFactorIndex = i;
      }
    }
    List<Factor> otherFactors = Lists.newArrayList(factors.subList(0, biggestFactorIndex));
    otherFactors.addAll(factors.subList(biggestFactorIndex + 1, factors.size()));

    return biggestFactor.product(otherFactors);
  }

  /**
   * Adds together {@code factors} and returns the result. This is a convenience
   * wrapper around {@link Factor#add(Factor)}. {@code factors} cannot be empty.
   * 
   * @param factors
   * @return
   */
  public static Factor add(List<Factor> factors) {
    Preconditions.checkNotNull(factors);
    Preconditions.checkArgument(!factors.isEmpty());
    return factors.get(0).add(factors.subList(1, factors.size()));
  }

  /**
   * Returns a factor {@code x} which behaves like the multiplicative identity.
   * That is, {@code x.product(factor) == factor}.
   * 
   * The returned factor is defined over an empty set of variables.
   * 
   * @return
   */
  public static Factor multiplicativeIdentity() {
    return TableFactor.unity(VariableNumMap.emptyMap());
  }
}
