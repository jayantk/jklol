package com.jayantkrish.jklol.models.bayesnet;

import java.util.Iterator;
import java.util.List;

import com.google.common.base.Preconditions;
import com.google.common.primitives.Ints;
import com.jayantkrish.jklol.models.DiscreteFactor;
import com.jayantkrish.jklol.models.DiscreteFactor.Outcome;
import com.jayantkrish.jklol.models.DiscreteVariable;
import com.jayantkrish.jklol.models.Factor;
import com.jayantkrish.jklol.models.TableFactor;
import com.jayantkrish.jklol.models.Variable;
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.models.parametric.AbstractParametricFactor;
import com.jayantkrish.jklol.models.parametric.ParametricFactorGraphProtos.ParametricFactorProto;
import com.jayantkrish.jklol.models.parametric.SufficientStatistics;
import com.jayantkrish.jklol.models.parametric.TensorSufficientStatistics;
import com.jayantkrish.jklol.tensor.SparseTensorBuilder;
import com.jayantkrish.jklol.tensor.Tensor;
import com.jayantkrish.jklol.tensor.TensorBuilder;
import com.jayantkrish.jklol.util.AllAssignmentIterator;
import com.jayantkrish.jklol.util.Assignment;
import com.jayantkrish.jklol.util.IndexedList;

/**
 * A CptTableFactor is the typical factor you expect in a Bayesian Network. Its
 * unnormalized probabilities are simply child variable probabilities
 * conditioned on a parent.
 */
public class CptTableFactor extends AbstractParametricFactor {

  // These are the parent and child variables in the CPT.
  private final VariableNumMap parentVars;
  private final VariableNumMap childVars;

  /**
   * childrenNums are the variable numbers of the "child" nodes. The CptFactor
   * defines a probability distribution P(children | parents) over *sets* of
   * child variables. (In the Bayes Net DAG, there is an edge from every parent
   * to every child, and internally the children are a directed clique.)
   */
  public CptTableFactor(VariableNumMap parentVars, VariableNumMap childVars) {
    super(parentVars.union(childVars));
    Preconditions.checkArgument(parentVars.getDiscreteVariables().size() == parentVars.size());
    Preconditions.checkArgument(childVars.getDiscreteVariables().size() == childVars.size());
    this.parentVars = parentVars;
    this.childVars = childVars;
  }

  // ////////////////////////////////////////////////////////////////
  // ParametricFactor / CptFactor methods
  // ///////////////////////////////////////////////////////////////

  @Override
  public DiscreteFactor getFactorFromParameters(SufficientStatistics parameters) {
    TensorSufficientStatistics tensorStats = (TensorSufficientStatistics) parameters;
    Tensor allTensor = tensorStats.get().build();
    Tensor parentTensor = allTensor.sumOutDimensions(childVars.getVariableNums());
    
    return new TableFactor(getVars(), allTensor.elementwiseProduct(parentTensor.elementwiseInverse()));
  }
  
  @Override
  public String getParameterDescription(SufficientStatistics parameters, int numFeatures) { 
    throw new UnsupportedOperationException();
  }
  
  @Override
  public String getParameterDescriptionXML(SufficientStatistics parameters) {
    throw new UnsupportedOperationException("Not implemented");
  }

  @Override
  public TensorSufficientStatistics getNewSufficientStatistics() {
    TensorBuilder combinedTensor = getTensorFromVariables(getVars());
    return new TensorSufficientStatistics(getVars(), combinedTensor);
  }

  /**
   * Constructs a tensor with one dimension per variable in {@code variables}.
   * 
   * @param variables
   * @return
   */
  private static TensorBuilder getTensorFromVariables(VariableNumMap variables) {
    // Get the dimensions and dimension sizes for the tensor.
    int[] dimensions = Ints.toArray(variables.getVariableNums());
    int[] sizes = new int[dimensions.length];
    List<DiscreteVariable> varTypes = variables.getDiscreteVariables();
    for (int i = 0; i < varTypes.size(); i++) {
      sizes[i] = varTypes.get(i).numValues();
    }
    return new SparseTensorBuilder(dimensions, sizes);
  }

  @Override
  public void incrementSufficientStatisticsFromAssignment(SufficientStatistics statistics,
      Assignment a, double count) {
    Preconditions.checkArgument(a.containsAll(getVars().getVariableNums()));
    TensorSufficientStatistics tensorStats = (TensorSufficientStatistics) statistics;

    int[] combinedIndex = getVars().assignmentToIntArray(a);
    tensorStats.get().incrementEntry(count, combinedIndex);
  }

  @Override
  public void incrementSufficientStatisticsFromMarginal(SufficientStatistics statistics,
      Factor marginal, Assignment conditionalAssignment, double count, double partitionFunction) {
    Assignment conditionalSubAssignment = conditionalAssignment.intersection(getVars());

    TensorSufficientStatistics tensorStats = (TensorSufficientStatistics) statistics;
    Iterator<Outcome> outcomeIter = marginal.coerceToDiscrete().outcomeIterator();
    while (outcomeIter.hasNext()) {
      Outcome outcome = outcomeIter.next();
      Assignment a = outcome.getAssignment().union(conditionalSubAssignment);
      double incrementAmount = count * outcome.getProbability() / partitionFunction;
      
      int[] combinedIndex = getVars().assignmentToIntArray(a);
    
      tensorStats.get().incrementEntry(incrementAmount, combinedIndex);
    }
  }
  
  @Override
  public ParametricFactorProto toProto(IndexedList<Variable> variableTypeIndex) {
    throw new UnsupportedOperationException("Not yet implemented.");
  }

  // ///////////////////////////////////////////////////////////////////
  // CPTTableFactor methods
  // ///////////////////////////////////////////////////////////////////

  /**
   * Gets the parent variables of this factor.
   * 
   * @return
   */
  public VariableNumMap getParents() {
    return parentVars;
  }

  /**
   * Gets the child variables of this factor.
   * 
   * @return
   */
  public VariableNumMap getChildren() {
    return childVars;
  }

  /**
   * Get an iterator over all possible assignments to the parent variables
   */
  public Iterator<Assignment> parentAssignmentIterator() {
    return new AllAssignmentIterator(parentVars);
  }

  /**
   * Get an iterator over all possible assignments to the child variables
   */
  public Iterator<Assignment> childAssignmentIterator() {
    return new AllAssignmentIterator(childVars);
  }

  @Override
  public String toString() {
    return "[CptTableFactor Parents: " + parentVars.toString()
        + " Children: " + childVars.toString() + "]";
  }
}