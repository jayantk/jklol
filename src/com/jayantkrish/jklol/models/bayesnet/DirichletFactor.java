package com.jayantkrish.jklol.models.bayesnet;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.jayantkrish.jklol.models.AbstractFactor;
import com.jayantkrish.jklol.models.DiscreteFactor;
import com.jayantkrish.jklol.models.Factor;
import com.jayantkrish.jklol.models.FactorCoercionError;
import com.jayantkrish.jklol.models.RealVariable;
import com.jayantkrish.jklol.models.SeparatorSet;
import com.jayantkrish.jklol.models.TableFactor;
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.models.loglinear.FeatureFunction;
import com.jayantkrish.jklol.util.Assignment;
import com.jayantkrish.jklol.util.GammaMath;
import com.jayantkrish.jklol.util.Vector;

/**
 * DirichletFactor is a Dirichlet distribution over a single real-valued vector
 * variable.
 * 
 * @author jayant
 */
public class DirichletFactor extends AbstractFactor {

  private int realVarNum;
  private Vector parameters;

  /**
   * Instantiate a Dirichlet factor with the provided parameters for the
   * Dirichlet distribution. The ith value of parameters is the exponent for the
   * ith element of the real valued variable in vars.
   * 
   * @param vars A {@code VariableNumMap} containing a single real-valued
   * variable.
   * @param parameters The Dirichlet distribution parameters. The length of
   * parameters must be equal to the number of dimensions of the real-valued
   * variable. Every entry of this vector must be greater than 0.
   */
  public DirichletFactor(VariableNumMap vars, Vector parameters) {
    super(vars);
    Preconditions.checkArgument(vars.size() == 1);
    Preconditions.checkArgument(vars.getRealVariables().size() == 1);
    Preconditions.checkNotNull(parameters);
    Preconditions.checkArgument(vars.getRealVariables().get(0)
        .numDimensions() == parameters.numDimensions());

    realVarNum = vars.getVariableNums().get(0);
    this.parameters = new Vector(parameters);
  }

  @Override
  public Set<SeparatorSet> getComputableOutboundMessages(Map<SeparatorSet, Factor> inboundMessages) {
    throw new UnsupportedOperationException("Not yet implemented");
  }

  @Override
  public double computeExpectation(FeatureFunction feature) {
    throw new UnsupportedOperationException("Not implemented.");
  }

  @Override
  public Factor conditional(Assignment a) {
    if (a.containsVar(realVarNum)) {
      TableFactor factor = new TableFactor(getVars());
      factor.setWeight(a, getUnnormalizedProbability(a));
    }
    // Assignment doesn't contain the variable this factor is defined over.
    return this;
  }

  @Override
  public double getUnnormalizedProbability(Assignment assignment) {
    Preconditions.checkArgument(assignment.containsVar(realVarNum));
    Object objValue = assignment.getVarValue(realVarNum);
    Preconditions.checkArgument(objValue instanceof Vector);

    return getUnnormalizedProbability((Vector) objValue);
  }

  public double getUnnormalizedProbability(Vector value) {
    double prob = 1.0;
    for (int i = 0; i < value.numDimensions(); i++) {
      prob *= Math.pow(value.get(i), parameters.get(i));
    }
    return prob;
  }

  @Override
  public Factor marginalize(Collection<Integer> varNumsToEliminate) {
    if (varNumsToEliminate.contains(realVarNum)) {
      TableFactor factor = new TableFactor(VariableNumMap.emptyMap());
      factor.setWeight(Assignment.EMPTY, getPartitionFunction());
      return factor;
    }
    return this;
  }

  @Override
  public Factor maxMarginalize(Collection<Integer> varNumsToEliminate) {
    if (varNumsToEliminate.contains(realVarNum)) {
      TableFactor factor = new TableFactor(VariableNumMap.emptyMap());
      factor.setWeight(Assignment.EMPTY,
          getUnnormalizedProbability(modeVector()));
      return factor;
    }
    return this;
  }

  @Override
  public Factor maximum(Factor other) {
    throw new UnsupportedOperationException("Not yet implemented");
  }

  @Override
  public Factor add(Factor other) {
    throw new UnsupportedOperationException("Cannot add DirichletFactors.");
  }

  @Override
  public DirichletFactor coerceToDirichlet() {
    return this;
  }

  @Override
  public Factor product(Factor other) {
    DirichletFactor otherAsDirichlet = other.coerceToDirichlet();
    return DirichletFactor.productFactor(this, otherAsDirichlet);
  }

  /*
   * (non-Javadoc) This is more efficient than the default implementation in
   * {@code AbstractFactor}.
   * 
   * @see com.jayantkrish.jklol.models.AbstractFactor#product(java.util.List)
   */
  @Override
  public Factor product(List<Factor> others) {
    List<DirichletFactor> dirichletFactors = Lists.newArrayList();
    dirichletFactors.add(this);
    for (Factor other : others) {
      dirichletFactors.add(other.coerceToDirichlet());
    }
    return DirichletFactor.productFactor(dirichletFactors);
  }

  @Override
  public Factor product(double constant) {
    if (constant == 1.0) {
      return this;
    }
    throw new UnsupportedOperationException(
        "Cannot multiply DirichletFactors by constants other than 1.");
  }

  @Override
  public Assignment sample() {
    throw new UnsupportedOperationException("Not yet implemented.");
  }

  /**
   * Gets the parameter vector of this Dirichlet distribution.
   * 
   * @return
   */
  public Vector getDirichletParameters() {
    return parameters;
  }

  /**
   * Gets a vector which is a mode of this distribution. The mode is unique
   * unless this distribution is the uniform distribution. In the uniform case,
   * this method returns (1/D, ..., 1/D) where D is the dimensionality of the
   * vector.
   */
  private Vector modeVector() {
    double[] value = new double[parameters.numDimensions()];
    double denominator = 0.0;
    for (int i = 0; i < parameters.numDimensions(); i++) {
      denominator += parameters.get(i) - 1;
    }
    for (int i = 0; i < value.length; i++) {
      if (parameters.get(i) < 1) {
        value[i] = 0.0;
      } else {
        value[i] = (parameters.get(i) - 1) / denominator;
      }
    }
    return new Vector(value);
  }

  /**
   * Multiplies together a list of Dirichlet factors. The factors must be
   * defined over the same random variable.
   * 
   * @param factors
   * @return
   */
  public static DirichletFactor productFactor(DirichletFactor... factors) {
    return productFactor(Arrays.asList(factors));
  }

  /**
   * Multiplies together a list of Dirichlet factors. The factors must be
   * defined over the same random variable.
   * 
   * @param factors
   * @return
   */
  public static DirichletFactor productFactor(List<DirichletFactor> factors) {
    Preconditions.checkNotNull(factors.size());
    VariableNumMap vars = VariableNumMap.emptyMap();
    for (Factor factor : factors) {
      vars = vars.union(factor.getVars());
    }
    Preconditions.checkArgument(vars.size() == 1);
    Preconditions.checkArgument(vars.getRealVariables().size() == 1);

    RealVariable var = vars.getRealVariables().get(0);
    Vector newParameters = Vector.constantVector(var.numDimensions(), 0.0);
    for (DirichletFactor factor : factors) {
      newParameters.addTo(factor.getDirichletParameters());
    }
    return new DirichletFactor(vars, newParameters);
  }

  /**
   * Gets the partition function for this Dirichlet distribution, which is the
   * integral of the distribution over all of the variables in the
   * Dirichlet-distributed vector.
   * 
   * @return
   */
  private double getPartitionFunction() {
    double denom = 1.0;
    double total = 0.0;
    for (int i = 0; i < parameters.numDimensions(); i++) {
      denom *= GammaMath.gamma(parameters.get(i));
      total += parameters.get(i);
    }
    return denom / GammaMath.gamma(total);
  }

}
