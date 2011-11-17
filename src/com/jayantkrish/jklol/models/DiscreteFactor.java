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
import com.jayantkrish.jklol.util.AllAssignmentIterator;
import com.jayantkrish.jklol.util.Assignment;
import com.jayantkrish.jklol.util.Pair;
import com.jayantkrish.jklol.util.PairComparator;
import com.jayantkrish.jklol.util.SparseTensor;

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
  public abstract Iterator<Assignment> outcomeIterator();

  /**
   * Gets the table of weights over the discrete variables in {@code this}
   * factor. This method is used to perform efficient mathematical operations on
   * {@code DiscreteFactors}, and should only be used by {@code DiscreteFactor}
   * and subclasses.
   * 
   * @return
   */
  public abstract SparseTensor getWeights();

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
    TableFactorBuilder tableFactorBuilder = new TableFactorBuilder(varsToEliminate);
    tableFactorBuilder.setWeight(subAssignment, 1.0);
    return this.product(tableFactorBuilder.build())
        .marginalize(varsToEliminate.getVariableNums()); 
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

  @Override
  public Assignment sample() {
    double draw = Math.random();
    double partitionFunction = getPartitionFunction();
    double sumProb = 0.0;
    Iterator<Assignment> iter = outcomeIterator();
    Assignment a = null;
    while (iter.hasNext() && sumProb <= draw) {
      a = iter.next();
      sumProb += getUnnormalizedProbability(a) / partitionFunction;
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
    Iterator<Assignment> iter = outcomeIterator();
    PriorityQueue<Pair<Double, Assignment>> pq = new PriorityQueue<Pair<Double, Assignment>>(
        numAssignments, new PairComparator<Double, Assignment>());

    while (iter.hasNext()) {
      Assignment a = iter.next();
      pq.offer(new Pair<Double, Assignment>(getUnnormalizedProbability(a), new Assignment(a)));
      if (pq.size() > numAssignments) {
        pq.poll();
      }
    }
    
    // There may not be enough assignments with positive probability. Fill up
    // pq with zero probability assignments.
    if (pq.size() < numAssignments) {
      Iterator<Assignment> allAssignmentIter = new AllAssignmentIterator(getVars());
      while(allAssignmentIter.hasNext() && pq.size() < numAssignments) {
        Assignment a = allAssignmentIter.next();
        if (getUnnormalizedProbability(a) == 0.0) {
          pq.offer(new Pair<Double, Assignment>(0.0, a));
        }
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

  /**
   * Get the partition function = denominator = total sum probability of all
   * assignments.
   */
  private double getPartitionFunction() {
    if (partitionFunction != -1.0) {
      return partitionFunction;
    }

    partitionFunction = 0.0;
    Iterator<Assignment> outcomeIterator = outcomeIterator();
    while (outcomeIterator.hasNext()) {
      partitionFunction += getUnnormalizedProbability(outcomeIterator.next());
    }
    return partitionFunction;
  }
}