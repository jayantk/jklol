package com.jayantkrish.jklol.models;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.jayantkrish.jklol.util.Assignment;

/**
 * A {@code Factor} which only supports the {@code conditional} inference
 * operation. Such factors are useful in conditional models, where certain
 * inputs are always provided to the model. 
 * <p>
 * This abstract class implements the
 * operations which do not have to be implemented by conditional factors. All of
 * the implemented operations throw an exception when called.
 * 
 * @author jayant
 */
public abstract class AbstractConditionalFactor extends AbstractFactor {
  
  private static final long serialVersionUID = 389192415388880283L;

  public AbstractConditionalFactor(VariableNumMap vars) {
    super(vars);
  }

  @Override
  public Set<SeparatorSet> getComputableOutboundMessages(Map<SeparatorSet, Factor> inboundMessages) {
    throw new UnsupportedOperationException();
  }

  @Override
  public Factor marginalize(Collection<Integer> varNumsToEliminate) {
    throw new UnsupportedOperationException();
  }

  @Override
  public Factor maxMarginalize(Collection<Integer> varNumsToEliminate) {
    throw new UnsupportedOperationException();
  }

  @Override
  public Factor add(Factor other) {
    throw new UnsupportedOperationException();
  }

  @Override
  public Factor maximum(Factor other) {
    throw new UnsupportedOperationException();
  }

  @Override
  public Factor product(Factor other) {
    throw new UnsupportedOperationException();
  }

  @Override
  public Factor product(double constant) {
    throw new UnsupportedOperationException();
  }

  @Override
  public Factor inverse() {
    throw new UnsupportedOperationException();
  }

  @Override
  public double size() {
    throw new UnsupportedOperationException();
  }

  @Override
  public Assignment sample() {
    throw new UnsupportedOperationException();
  }

  @Override
  public List<Assignment> getMostLikelyAssignments(int numAssignments) {
    throw new UnsupportedOperationException();
  }
}
