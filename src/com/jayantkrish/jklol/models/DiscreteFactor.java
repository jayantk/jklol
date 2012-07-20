package com.jayantkrish.jklol.models;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

import com.google.common.base.Preconditions;
import com.google.common.collect.Ordering;
import com.google.common.collect.Sets;
import com.google.common.collect.SortedSetMultimap;
import com.google.common.collect.TreeMultimap;
import com.google.common.primitives.Ints;
import com.jayantkrish.jklol.tensor.SparseTensor;
import com.jayantkrish.jklol.tensor.SparseTensorBuilder;
import com.jayantkrish.jklol.tensor.Tensor;
import com.jayantkrish.jklol.util.Assignment;
import com.jayantkrish.jklol.util.Pair;
import com.jayantkrish.jklol.util.PairComparator;

/**
 * DiscreteFactor provides a generic implementation of most methods of the
 * {@link Factor} interface for variables which take on discrete values.
 */
public abstract class DiscreteFactor extends AbstractFactor {

  private double partitionFunction;

  /**
   * DiscreteFactor must be defined only over {@link DiscreteVariable}s. Throws
   * an IllegalArgumentException if vars contains anything but
   * DiscreteVariables.
   * 
   * @param vars
   */
  public DiscreteFactor(VariableNumMap vars) {
    super(vars);
    Preconditions.checkArgument(vars.getDiscreteVariables().size() == vars.size());

    this.partitionFunction = -1.0;
  }

  // ////////////////////////////////////////////////////////////////////////
  // Additional methods provided by DiscreteFactors which are not provided by
  // Factor.
  // ////////////////////////////////////////////////////////////////////////

  /**
   * Gets an iterator over all outcomes with nonzero probability. Each
   * {@code Assignment} in the returned iterator is a single possible outcome.
   */
  public abstract Iterator<Outcome> outcomeIterator();

  /**
   * Gets an iterator over all {@code Assignment}s in this which are supersets
   * of {@code prefix}. {@code prefix} must contain variables in order from
   * lowest-numbered to highest-numbered. For example, {@code prefix} can assign
   * both the lowest and second-lowest numbered variables, but cannot assign
   * just the second-lowest variable.
   * 
   * @param prefix
   * @return
   */
  public abstract Iterator<Outcome> outcomePrefixIterator(Assignment prefix);

  /**
   * Gets the table of weights over the discrete variables in {@code this}
   * factor. This method is used to perform efficient mathematical operations on
   * {@code DiscreteFactors}, and should only be used by {@code DiscreteFactor}
   * and subclasses.
   * 
   * @return
   */
  public abstract Tensor getWeights();

  // /////////////////////////////////////////////////////////////////////////////////
  // Overrides of Factor methods.
  // /////////////////////////////////////////////////////////////////////////////////

  @Override
  public Set<SeparatorSet> getComputableOutboundMessages(Map<SeparatorSet, Factor> inboundMessages) {
    Preconditions.checkNotNull(inboundMessages);

    Set<SeparatorSet> possibleOutbound = Sets.newHashSet();
    for (Map.Entry<SeparatorSet, Factor> inboundMessage : inboundMessages.entrySet()) {
      if (inboundMessage.getValue() == null) {
        possibleOutbound.add(inboundMessage.getKey());
      }
    }

    if (possibleOutbound.size() == 1) {
      return possibleOutbound;
    } else if (possibleOutbound.size() == 0) {
      return inboundMessages.keySet();
    } else {
      return Collections.emptySet();
    }
  }

  @Override
  public DiscreteFactor conditional(Assignment a) {
    VariableNumMap varsToEliminate = getVars().intersection(a.getVariableNums());

    // Efficiency improvement: only create a new factor if necessary.
    if (varsToEliminate.size() == 0) {
      return this;
    }

    Assignment subAssignment = a.intersection(varsToEliminate);
    int[] key = varsToEliminate.assignmentToIntArray(subAssignment);
    int[] eliminatedDimensions = Ints.toArray(varsToEliminate.getVariableNums());
    return new TableFactor(getVars().removeAll(varsToEliminate),
        getWeights().slice(eliminatedDimensions, key));
  }

  @Override
  public DiscreteFactor marginalize(Collection<Integer> varNumsToEliminate) {
    return new TableFactor(getVars().removeAll(varNumsToEliminate),
        getWeights().sumOutDimensions(Sets.newHashSet(varNumsToEliminate)));
  }

  @Override
  public DiscreteFactor maxMarginalize(Collection<Integer> varNumsToEliminate) {
    return new TableFactor(getVars().removeAll(varNumsToEliminate),
        getWeights().maxOutDimensions(Sets.newHashSet(varNumsToEliminate)));
  }

  @Override
  public DiscreteFactor add(Factor other) {
    Preconditions.checkArgument(other.getVars().equals(getVars()));
    return new TableFactor(getVars(), getWeights()
        .elementwiseAddition(other.coerceToDiscrete().getWeights()));
  }

  @Override
  public DiscreteFactor maximum(Factor other) {
    Preconditions.checkArgument(other.getVars().equals(getVars()));
    return new TableFactor(getVars(), getWeights()
        .elementwiseMaximum(other.coerceToDiscrete().getWeights()));
  }

  @Override
  public DiscreteFactor product(Factor other) {
    Preconditions.checkArgument(getVars().containsAll(other.getVars()));
    return new TableFactor(getVars(), getWeights()
        .elementwiseProduct(other.coerceToDiscrete().getWeights()));
  }

  @Override
  public DiscreteFactor product(List<Factor> factors) {
    List<DiscreteFactor> discreteFactors = Factors.coerceToDiscrete(factors);

    // Multiply the factors in order from smallest to largest to keep
    // the intermediate results as sparse as possible.
    SortedSetMultimap<Double, DiscreteFactor> factorsBySize =
        TreeMultimap.create(Ordering.natural(), Ordering.arbitrary());
    for (DiscreteFactor factor : discreteFactors) {
      factorsBySize.put(factor.size(), factor);
    }

    Tensor result = getWeights();
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

  /**
   * Returns a {@code DiscreteFactor} that assigns each outcome the product of
   * its probability in {@code this} and {@code other}. {@code other}'s variables
   * must be disjoint from this factor's variables.
   * 
   * @param other
   * @return
   */
  public DiscreteFactor outerProduct(Factor other) {
    Preconditions.checkArgument(getVars().intersection(other.getVars()).size() == 0);
    // This implementation is slow, but Tensors currently don't support outer products.
    DiscreteFactor otherAsDiscrete = other.coerceToDiscrete();
    TableFactorBuilder builder = new TableFactorBuilder(getVars().union(other.getVars()), 
        SparseTensorBuilder.getFactory());
    Iterator<Outcome> myIter = outcomeIterator();
    while (myIter.hasNext()) {
      Outcome myOutcome = myIter.next();
      Iterator<Outcome> otherIter = otherAsDiscrete.outcomeIterator();
      while (otherIter.hasNext()) {
        Outcome otherOutcome = otherIter.next();
        builder.setWeight(myOutcome.getAssignment().union(otherOutcome.getAssignment()),
            myOutcome.getProbability() * otherOutcome.getProbability());
      }
    }
    return builder.build();
  }

  @Override
  public DiscreteFactor inverse() {
    return new TableFactor(getVars(), getWeights().elementwiseInverse());
  }

  @Override
  public Assignment sample() {
    double draw = Math.random();
    double partitionFunction = getPartitionFunction();
    double sumProb = 0.0;
    Iterator<Outcome> iter = outcomeIterator();
    Assignment a = null;
    Outcome o = null;
    while (iter.hasNext() && sumProb <= draw) {
      o = iter.next();
      a = o.getAssignment();
      sumProb += o.getProbability() / partitionFunction;
    }

    if (a == null) {
      // If we didn't draw a sample, fail early.
      throw new IllegalStateException("Could not sample from DiscreteFactor." + this + " : "
          + sumProb);
    }
    return a;
  }

  @Override
  public List<Assignment> getMostLikelyAssignments(int numAssignments) {
    Iterator<Outcome> iter = outcomeIterator();
    PriorityQueue<Pair<Double, Assignment>> pq = new PriorityQueue<Pair<Double, Assignment>>(
        numAssignments + 1,
        Ordering.from(new PairComparator<Double, Assignment>()).compound(Ordering.arbitrary()));

    while (iter.hasNext()) {
      Outcome outcome = iter.next();
      pq.offer(new Pair<Double, Assignment>(outcome.getProbability(), outcome.getAssignment()));
      if (pq.size() > numAssignments) {
        pq.poll();
      }
    }

    List<Assignment> mostLikely = new ArrayList<Assignment>();
    while (pq.size() > 0) {
      mostLikely.add(pq.poll().getRight());
    }
    Collections.reverse(mostLikely);
    return mostLikely;
  }

  @Override
  public DiscreteFactor coerceToDiscrete() {
    return this;
  }

  @Override
  public DiscreteObjectFactor coerceToDiscreteObject() {
    throw new UnsupportedOperationException("Not yet implemented");
  }
  
  @Override
  public String getParameterDescription() {
    StringBuilder sb = new StringBuilder();
    Iterator<Outcome> iter = outcomeIterator();
    while (iter.hasNext()) {
      Outcome outcome = iter.next();
      sb.append(outcome.toString());
      sb.append("\n");
    }
    return sb.toString();
  }
  
  @Override
  public String getParameterDescriptionXML() {
    StringBuilder sb = new StringBuilder();
    Iterator<Outcome> iter = outcomeIterator();
    while (iter.hasNext()) {
      Outcome outcome = iter.next();
      sb.append("<outcome>\n"+outcome.toXML()+"</outcome>\n");
    }
    return sb.toString();
  }


  @Override
  public int hashCode() {
    return getVars().hashCode();
  }

  @Override
  public boolean equals(Object other) {
    if (other == this) {
      return true;
    } else if (other instanceof DiscreteFactor) {
      DiscreteFactor factor = (DiscreteFactor) other;
      return factor.getVars().equals(getVars()) && factor.getWeights().equals(getWeights());
    }
    return false;
  }

  /**
   * Get the partition function = denominator = total sum probability of all
   * assignments.
   */
  private double getPartitionFunction() {
    if (partitionFunction != -1.0) {
      return partitionFunction;
    }

    partitionFunction = 0.0;
    Iterator<Outcome> outcomeIterator = outcomeIterator();
    while (outcomeIterator.hasNext()) {
      partitionFunction += outcomeIterator.next().getProbability();
    }
    return partitionFunction;
  }

  /**
   * An assignment and its corresponding unnormalized probability. For
   * efficiency, {@code Outcome}s are mutable. Typically, an iterator will
   * repeatedly return the same outcome instance with a different wrapped
   * assignment and value.
   * 
   * @author jayantk
   */
  public class Outcome {
    private Assignment assignment;
    private double probability;

    public Outcome(Assignment assignment, double probability) {
      this.assignment = assignment;
      this.probability = probability;
    }

    public Assignment getAssignment() {
      return assignment;
    }

    public void setAssignment(Assignment assignment) {
      this.assignment = assignment;
    }

    public double getProbability() {
      return probability;
    }

    public void setProbability(double probability) {
      this.probability = probability;
    }

    @Override
    public String toString() {
      return assignment + "=" + probability;
    }
    
    public String toXML() {
      return assignment.toXML() + "<probability>"+probability+"</probability>\n";
    }
  }
}