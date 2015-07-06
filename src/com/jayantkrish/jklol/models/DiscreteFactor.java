package com.jayantkrish.jklol.models;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Ordering;
import com.google.common.collect.Sets;
import com.google.common.collect.SortedSetMultimap;
import com.google.common.collect.TreeMultimap;
import com.jayantkrish.jklol.tensor.CachedSparseTensor;
import com.jayantkrish.jklol.tensor.SparseTensor;
import com.jayantkrish.jklol.tensor.SparseTensorBuilder;
import com.jayantkrish.jklol.tensor.Tensor;
import com.jayantkrish.jklol.training.LogFunction;
import com.jayantkrish.jklol.training.LogFunctions;
import com.jayantkrish.jklol.util.Assignment;
import com.jayantkrish.jklol.util.Pair;
import com.jayantkrish.jklol.util.PairComparator;
import com.jayantkrish.jklol.util.Pseudorandom;

/**
 * DiscreteFactor provides a generic implementation of most methods of the
 * {@link Factor} interface for variables which take on discrete values.
 */
public abstract class DiscreteFactor extends AbstractFactor {

  private static final long serialVersionUID = 6493003710183669273L;

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

  public TableFactor cacheWeightPermutations() {
    return new TableFactor(getVars(), CachedSparseTensor.cacheAllPermutations(
         (SparseTensor) getWeights()));
  }

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
    LogFunction log = LogFunctions.getLogFunction();
    log.startTimer("discrete_factor_conditional");
    VariableNumMap varsToEliminate = getVars().intersection(a.getVariableNumsArray());

    // Efficiency improvement: only create a new factor if necessary.
    if (varsToEliminate.size() == 0) {
      log.stopTimer("discrete_factor_conditional");
      return this;
    }

    int[] key = varsToEliminate.assignmentToIntArray(a);
    int[] eliminatedDimensions = varsToEliminate.getVariableNumsArray();
    TableFactor result = new TableFactor(getVars().removeAll(varsToEliminate),
        getWeights().slice(eliminatedDimensions, key));
    log.stopTimer("discrete_factor_conditional");
    return result; 
  }

  @Override
  public DiscreteFactor marginalize(Collection<Integer> varNumsToEliminate) {
    if (varNumsToEliminate.size() == 0) {
      return this;
    }
    return new TableFactor(getVars().removeAll(varNumsToEliminate),
        getWeights().sumOutDimensions(Sets.newHashSet(varNumsToEliminate)));
  }
  
  @Override
  public DiscreteFactor marginalize(VariableNumMap vars) {
    return marginalize(vars.getVariableNums());
  }

  @Override
  public DiscreteFactor maxMarginalize(Collection<Integer> varNumsToEliminate) {
    if (varNumsToEliminate.size() == 0) {
      return this;
    }
    return new TableFactor(getVars().removeAll(varNumsToEliminate),
        getWeights().maxOutDimensions(Sets.newHashSet(varNumsToEliminate)));
  }

  @Override
  public DiscreteFactor add(Factor other) {
    VariableNumMap varsNotInOther = getVars().removeAll(other.getVars());

    DiscreteFactor toAdd = other.coerceToDiscrete();
    if (varsNotInOther.size() > 0) {
      // Note that this process may fail for sparse tensors,
      // which only support some kinds of outer products.
      toAdd = TableFactor.unity(varsNotInOther).outerProduct(other);
    }

    return new TableFactor(getVars(), getWeights()
        .elementwiseAddition(toAdd.coerceToDiscrete().getWeights()));
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

    // TODO: multiply together factors defined over a subset of
    // the variables of this factor, to reduce computation time.

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
   * its probability in {@code this} and {@code other}. {@code other}'s
   * variables must be disjoint from this factor's variables.
   * 
   * @param other
   * @return
   */
  public DiscreteFactor outerProduct(Factor other) {
    Preconditions.checkArgument(getVars().intersection(other.getVars()).size() == 0);
    DiscreteFactor otherAsDiscrete = other.coerceToDiscrete();

    // See if the fast, tensor outer product implementation is usable.
    int[] myDims = getVars().getVariableNumsArray();
    int[] otherDims = other.getVars().getVariableNumsArray();
    if (myDims.length == 0 || otherDims.length == 0 || myDims[myDims.length - 1] < otherDims[0]) {
      return new TableFactor(getVars().union(other.getVars()), 
          getWeights().outerProduct(otherAsDiscrete.getWeights()));
    }
    
    // This implementation is slow, but Tensors currently don't support all
    // outer products.
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

  public DiscreteFactor innerProduct(Factor other) {
    VariableNumMap remainingVars = getVars().removeAll(other.getVars());
    return new TableFactor(remainingVars, getWeights()
        .innerProduct(other.coerceToDiscrete().getWeights()));
  }

  @Override
  public DiscreteFactor inverse() {
    return new TableFactor(getVars(), getWeights().elementwiseInverse());
  }

  @Override
  public Assignment sample() {
    double draw = Pseudorandom.get().nextDouble();
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
        (numAssignments > 0) ? numAssignments + 1 : 10,
        Ordering.from(new PairComparator<Double, Assignment>()).compound(Ordering.arbitrary()));

    while (iter.hasNext()) {
      Outcome outcome = iter.next();
      pq.offer(new Pair<Double, Assignment>(outcome.getProbability(), outcome.getAssignment()));
      // Negative numAssignments requires us to sort all of the assignments.
      if (numAssignments >= 0 && pq.size() > numAssignments) {
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

  /**
   * Gets all assignments to this factor which have non-zero weight.
   * 
   * @return
   */
  public List<Assignment> getNonzeroAssignments() {
    Iterator<Outcome> outcomeIter = outcomeIterator();
    List<Assignment> assignments = Lists.newArrayList();
    while (outcomeIter.hasNext()) {
      Outcome outcome = outcomeIter.next();
      if (outcome.getProbability() != 0.0) {
        assignments.add(outcome.getAssignment());
      }
    }
    return assignments;
  }

  @Override
  public DiscreteFactor coerceToDiscrete() {
    return this;
  }

  /**
   * Prints out this factor as a comma-separated values file, suitable for
   * reading using {@link TableFactor#fromDelimitedFile}.
   * 
   * @return
   */
  public String toCsv() {
    StringBuilder sb = new StringBuilder();
    Iterator<Outcome> iter = outcomeIterator();
    while (iter.hasNext()) {
      Outcome outcome = iter.next();
      sb.append(outcome.toCsv());
      if (iter.hasNext()) {
        sb.append("\n");
      }
    }
    return sb.toString();
  }
  
  public String toCsv(String linePrefix) {
    StringBuilder sb = new StringBuilder();
    Iterator<Outcome> iter = outcomeIterator();
    while (iter.hasNext()) {
      Outcome outcome = iter.next();
      sb.append(linePrefix + outcome.toCsv());
      if (iter.hasNext()) {
        sb.append("\n");
      }
    }
    return sb.toString();
  }

  @Override
  public String getParameterDescription() {
    return describeAssignments(getNonzeroAssignments());
  }

  /**
   * Gets a string description of {@code assignments} and their weights in
   * {@code this}. Zero-weight assignments are included.
   * 
   * @param assignments
   * @return
   */
  public String describeAssignments(List<Assignment> assignments) {
    return describeAssignments(assignments, true);
  }

  /**
   * Gets a string description of {@code assignments} and their weights in
   * {@code this}.
   * 
   * @param assignments
   * @param includeZeros if {@code false}, zero weight assignments are omitted.
   * @return
   */
  public String describeAssignments(List<Assignment> assignments, boolean includeZeros) {
    StringBuilder sb = new StringBuilder();
    for (Assignment assignment : assignments) {
      double unnormalizedProb = getUnnormalizedProbability(assignment);
      if (unnormalizedProb > 0 || includeZeros) {
        Outcome outcome = new Outcome(assignment, unnormalizedProb);
        sb.append(outcome.toString());
        sb.append("\n");
      }
    }
    return sb.toString();
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

    public String toCsv() {
      return Joiner.on(",").join(assignment.getValues()) + "," + probability;
    }
  }
}