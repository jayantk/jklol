package com.jayantkrish.jklol.models;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

import com.google.common.base.Preconditions;
import com.google.common.collect.BiMap;
import com.google.common.collect.Maps;
import com.google.common.collect.Ordering;
import com.google.common.collect.Sets;
import com.jayantkrish.jklol.models.FactorGraphProtos.FactorProto;
import com.jayantkrish.jklol.models.VariableNumMap.VariableRelabeling;
import com.jayantkrish.jklol.util.Assignment;
import com.jayantkrish.jklol.util.CountAccumulator;
import com.jayantkrish.jklol.util.DefaultHashMap;
import com.jayantkrish.jklol.util.Pair;
import com.jayantkrish.jklol.util.PairComparator;

/**
 * Represents a probability distribution that is a sum of point distributions
 * over arbitrary objects. This class is a generalization of a
 * {@link DiscreteFactor} where the underlying objects with point distributions
 * do not need to be numbered. Hence, this class is suitable for objects like
 * parse trees, where the number of items is too large to enumerate. If the
 * underlying objects are enumerable, a {@code TableFactor} will be more
 * efficient.
 * 
 * @author jayant
 */
public class DiscreteObjectFactor extends AbstractFactor {

  private final Map<Assignment, Double> probabilities;

  public DiscreteObjectFactor(VariableNumMap vars, Map<Assignment, Double> probabilities) {
    super(vars);
    this.probabilities = Preconditions.checkNotNull(probabilities);
  }

  /**
   * Creates a {@code DiscreteObjectFactor} that assigns weight 1.0 to each
   * assignment in {@code assignments}, and 0 to all other assignments.
   * 
   * @param vars
   * @param assignments
   * @return
   */
  public static DiscreteObjectFactor pointDistribution(VariableNumMap vars, Assignment... assignments) {
    Map<Assignment, Double> probabilities = Maps.newHashMap();
    for (int i = 0; i < assignments.length; i++) {
      Preconditions.checkArgument(assignments[i].containsAll(vars.getVariableNums()));
      probabilities.put(assignments[i], 1.0);
    }
    return new DiscreteObjectFactor(vars, probabilities);
  }

  public static FactorFactory getFactory() {
    return new FactorFactory() {
      @Override
      public Factor pointDistribution(VariableNumMap vars, Assignment assignment) {
        return DiscreteObjectFactor.pointDistribution(vars, assignment);
      }
    };
  }

  /**
   * Gets all {@code Assignments} in {@code this} with nonzero probability.
   * 
   * @return
   */
  public Iterable<Assignment> assignments() {
    return probabilities.keySet();
  }

  @Override
  public double getUnnormalizedProbability(Assignment assignment) {
    Preconditions.checkArgument(assignment.containsAll(getVars().getVariableNums()), 
        "Cannot get probability of %s . Factor variables %s", assignment, getVars());
    Assignment subAssignment = assignment.intersection(getVars().getVariableNums());
    if (probabilities.containsKey(subAssignment)) {
      return probabilities.get(subAssignment);
    }
    return 0.0;
  }

  public double getUnnormalizedLogProbability(Assignment assignment) {
    return Math.log(getUnnormalizedProbability(assignment));
  }

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
  public DiscreteObjectFactor relabelVariables(VariableRelabeling relabeling) {
    Map<Assignment, Double> newProbabilities = Maps.newHashMap();
    BiMap<Integer, Integer> variableNumberReplacementMap = relabeling.getVariableIndexReplacementMap();
    for (Map.Entry<Assignment, Double> entry : probabilities.entrySet()) {
      newProbabilities.put(entry.getKey().mapVariables(variableNumberReplacementMap),
          entry.getValue());
    }
    return new DiscreteObjectFactor(relabeling.apply(getVars()), newProbabilities);
  }

  @Override
  public Factor conditional(Assignment assignment) {
    Assignment subAssignment = assignment.intersection(getVars());
    VariableNumMap conditionedVars = getVars().intersection(assignment.getVariableNums());

    if (subAssignment.size() == 0) {
      return this;
    }
    return this.product(pointDistribution(conditionedVars, subAssignment)).marginalize(conditionedVars);
  }

  @Override
  public Factor marginalize(Collection<Integer> varNumsToEliminate) {
    if (varNumsToEliminate.size() == 0) {
      return this;
    }

    CountAccumulator<Assignment> newProbabilities = CountAccumulator.create();
    for (Assignment a : probabilities.keySet()) {
      Assignment subAssignment = a.removeAll(varNumsToEliminate);
      newProbabilities.increment(subAssignment, probabilities.get(a));
    }
    return new DiscreteObjectFactor(getVars().removeAll(varNumsToEliminate),
        newProbabilities.getCountMap());
  }

  @Override
  public Factor maxMarginalize(Collection<Integer> varNumsToEliminate) {
    if (varNumsToEliminate.size() == 0) {
      return this;
    }

    DefaultHashMap<Assignment, Double> newProbabilities = new DefaultHashMap<Assignment, Double>(0.0);
    for (Assignment a : probabilities.keySet()) {
      Assignment subAssignment = a.removeAll(varNumsToEliminate);
      double maxProb = Math.max(newProbabilities.get(subAssignment),
          probabilities.get(a));
      newProbabilities.put(subAssignment, maxProb);
    }
    return new DiscreteObjectFactor(getVars().removeAll(varNumsToEliminate),
        newProbabilities.getBaseMap());
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
  public DiscreteObjectFactor product(Factor other) {
    Map<Assignment, Double> newProbabilities = Maps.newHashMap(probabilities);
    for (Assignment a : newProbabilities.keySet()) {
      Assignment subAssignment = a.intersection(other.getVars());
      double subAssignmentProbability = other.getUnnormalizedProbability(subAssignment);
      newProbabilities.put(a, newProbabilities.get(a) * subAssignmentProbability);
    }
    return new DiscreteObjectFactor(getVars(), newProbabilities);
  }

  @Override
  public Factor product(double constant) {
    Map<Assignment, Double> newProbabilities = Maps.newHashMap(probabilities);
    for (Assignment a : newProbabilities.keySet()) {
      newProbabilities.put(a, newProbabilities.get(a) * constant);
    }
    return new DiscreteObjectFactor(getVars(), newProbabilities);
  }

  @Override
  public Factor inverse() {
    Map<Assignment, Double> newProbabilities = Maps.newHashMap(probabilities);
    for (Assignment a : newProbabilities.keySet()) {
      newProbabilities.put(a, 1.0 / newProbabilities.get(a));
    }
    return new DiscreteObjectFactor(getVars(), newProbabilities);
  }

  @Override
  public double size() {
    return probabilities.size();
  }

  @Override
  public Assignment sample() {
    throw new UnsupportedOperationException();
  }

  @Override
  public List<Assignment> getMostLikelyAssignments(int numAssignments) {
    PriorityQueue<Pair<Double, Assignment>> pq = new PriorityQueue<Pair<Double, Assignment>>(
        numAssignments + 1, 
        Ordering.from(new PairComparator<Double, Assignment>()).compound(Ordering.arbitrary())); 

    for (Assignment a : assignments()) {
      pq.offer(new Pair<Double, Assignment>(getUnnormalizedProbability(a), new Assignment(a)));
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
  public FactorProto toProto() {
    throw new UnsupportedOperationException();
  }
  
  @Override 
  public DiscreteFactor coerceToDiscrete() {
    TableFactorBuilder builder = TableFactorBuilder.fromMap(getVars(), probabilities);
    return builder.build();
  }
  
  @Override
  public DiscreteObjectFactor coerceToDiscreteObject() {
    return this;
  }

  @Override
  public String toString() {
    return "[DiscreteObjectFactor: " + getVars() + " " + probabilities.toString() + "]";
  }
}
