package com.jayantkrish.jklol.models.dynamic;

import java.io.Serializable;
import java.util.List;

import com.jayantkrish.jklol.models.Factor;
import com.jayantkrish.jklol.models.VariableNumMap;

/**
 * {@code PlateFactor} is a generalization of a {@code Factor} that represents a
 * factor replicated across multiple instantiations of a {@link Plate}. This
 * interface behaves like a factory for {@code Factor}s, using the set of
 * variables in a {@code FactorGraph} as inputVar.
 * 
 * Note that this class does not support inference, as it represents a set of
 * factors. Inference can only be performed over factors instantiated by
 * {@code this}.
 * 
 * @author jayantk
 */
public interface PlateFactor extends Serializable {

  /**
   * Returns a list of {@code Factor}s created by replicating this
   * {@code ReplicatedFactor} for each set of matching variables in
   * {@code factorGraphVariables}.
   * 
   * @param factorGraphVariables
   * @return
   */
  public List<Factor> instantiateFactors(VariableNumMap factorGraphVariables);
  
  /**
   * Gets the factor replicated by {@code this}.
   * 
   * @return
   */
  public Factor getFactor();
}