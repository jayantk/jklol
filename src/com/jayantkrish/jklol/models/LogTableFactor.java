package com.jayantkrish.jklol.models;

import java.util.Iterator;
import java.util.List;

import com.google.common.base.Preconditions;
import com.google.common.collect.Ordering;
import com.google.common.collect.SortedSetMultimap;
import com.google.common.collect.TreeMultimap;
import com.jayantkrish.jklol.util.AllAssignmentIterator;
import com.jayantkrish.jklol.util.Assignment;
import com.jayantkrish.jklol.util.SparseTensor;

/**
 * {@code LogTableFactor} represents a discrete distribution using
 * log-probabilities. This representation is efficient for log-linear or
 * max-margin models, where the representation of the factor in log-space is
 * sparse. This factor can interoperate with other, non-logarithmically
 * represented {@code DiscreteFactor}s; however such interoperation is expensive
 * because the {@code LogTableFactor} is essentially converted into a much
 * denser {@code TableFactor}.
 * 
 * The relevant logarithms and exponents for {@code LogTableFactor} are computed
 * using base e.
 * 
 * @author jayantk
 */
public class LogTableFactor extends DiscreteFactor {

  private final SparseTensor logWeights;

  /**
   * Constructs a {@code LogTableFactor} using {@code logWeights} as the log
   * unnormalized probability of each outcome.
   * 
   * @param vars
   * @param logWeights
   */
  public LogTableFactor(VariableNumMap vars, SparseTensor logWeights) {
    super(vars);
    Preconditions.checkArgument(vars.size() == vars.getDiscreteVariables().size());
    this.logWeights = logWeights;
  }

  @Override
  public double getUnnormalizedProbability(Assignment a) {
    Preconditions.checkArgument(a.containsVars(getVars().getVariableNums()));
    return Math.exp(logWeights.get(getVars().assignmentToIntArray(a)));
  }

  @Override
  public double size() {
    return logWeights.size();
  }

  @Override
  public Iterator<Assignment> outcomeIterator() {
    // Note that all assignments have nonzero probability, hence the returned
    // iterator must iterate over all assignments to this.
    return new AllAssignmentIterator(getVars());
  }

  @Override
  public SparseTensor getWeights() {
    // This is conceptually possible, but extremely expensive.
    throw new UnsupportedOperationException("Not implemented");
  }
  
  public SparseTensor getLogWeights() {
    return logWeights; 
  }
  
  ////////////////////////////////////////////////////////////////////
  // DiscreteFactor overrides, for efficiency.
  ////////////////////////////////////////////////////////////////////
  
  @Override
  public DiscreteFactor product(Factor other) {
    if (!(other instanceof LogTableFactor)) {
      return super.product(other);
    }
    Preconditions.checkArgument(getVars().containsAll(other.getVars()));
    LogTableFactor logFactor = (LogTableFactor) other;
    
    return new LogTableFactor(getVars(), getWeights()
        .elementwiseProduct(other.coerceToDiscrete().getWeights()));
  }

  @Override
  public DiscreteFactor product(List<Factor> factors) {
    List<DiscreteFactor> discreteFactors = FactorUtils.coerceToDiscrete(factors);

    // Multiply the factors in order from smallest to largest to keep
    // the intermediate results as sparse as possible.
    SortedSetMultimap<Double, DiscreteFactor> factorsBySize =
        TreeMultimap.create(Ordering.natural(), Ordering.arbitrary());
    for (DiscreteFactor factor : discreteFactors) {
      factorsBySize.put(factor.size(), factor);
    }

    SparseTensor result = getWeights();
    for (Double size : factorsBySize.keySet()) {
      for (DiscreteFactor factor : factorsBySize.get(size)) {
        result = result.elementwiseProduct(factor.getWeights());
      }
    }
    return new TableFactor(getVars(), result);
  }

  @Override
  public DiscreteFactor product(double constant) {
    return new TableFactor(getVars(), getWeights()
        .elementwiseProduct(SparseTensor.getScalarConstant(constant)));
  }

  @Override
  public DiscreteFactor inverse() {
    return new TableFactor(getVars(), getWeights().elementwiseInverse());
  }

}
