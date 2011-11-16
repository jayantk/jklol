package com.jayantkrish.jklol.cfg;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.jayantkrish.jklol.models.AbstractFactor;
import com.jayantkrish.jklol.models.DiscreteFactor;
import com.jayantkrish.jklol.models.DiscreteVariable;
import com.jayantkrish.jklol.models.Factor;
import com.jayantkrish.jklol.models.FactorUtils;
import com.jayantkrish.jklol.models.SeparatorSet;
import com.jayantkrish.jklol.models.TableFactor;
import com.jayantkrish.jklol.models.TableFactorBuilder;
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.models.loglinear.FeatureFunction;
import com.jayantkrish.jklol.util.Assignment;

/**
 * A CfgFactor embeds a context-free grammar in a Bayes Net. The factor defines
 * a distribution over terminal productions conditioned on the root production.
 * 
 * You can run exact inference in a Bayes Net containing a CfgFactor provided
 * that the terminal productions are conditioned on.
 */
public class CfgFactor extends AbstractFactor {

  private final int parentVarNum;
  private final DiscreteVariable parentVar;
  private final int childVarNum;
  private final DiscreteVariable childVar;

  private final CfgParser parser;

  private final DiscreteFactor parentInboundMessage;
  private final DiscreteFactor childInboundMessage;

  // A cache of the current parse charts (for normal and max-marginals),
  // for efficiency.
  private final Map<Boolean, ParseChart> cachedCharts;

  /**
   * This factor should always be instantiated over exactly two variables: the
   * parent variable is a distribution over Productions, the child variable is a
   * distribution over Lists of Productions. Sadly, the Java type system makes
   * enforcing this requirement difficult.
   */
  public CfgFactor(DiscreteVariable parentVar, DiscreteVariable childVar,
      int parentVarNum, int childVarNum, CfgParser parser) {
    super(new VariableNumMap(Arrays.asList(new Integer[] { parentVarNum, childVarNum }),
        Arrays.asList(new DiscreteVariable[] { parentVar, childVar })));

    this.parentVarNum = parentVarNum;
    this.parentVar = parentVar;
    this.childVarNum = childVarNum;
    this.childVar = childVar;

    this.parser = parser;

    this.parentInboundMessage = null;
    this.childInboundMessage = null;
    this.cachedCharts = Maps.newHashMap();
  }

  /*
   * For implementing products of factors.
   */
  private CfgFactor(DiscreteVariable parentVar, DiscreteVariable childVar,
      int parentVarNum, int childVarNum, CfgParser parser, 
      DiscreteFactor parentInboundMessage, DiscreteFactor childInboundMessage) {
    super(new VariableNumMap(Arrays.asList(new Integer[] { parentVarNum, childVarNum }),
        Arrays.asList(new DiscreteVariable[] { parentVar, childVar })));

    this.parentVarNum = parentVarNum;
    this.parentVar = parentVar;
    this.childVarNum = childVarNum;
    this.childVar = childVar;

    this.parser = parser;

    this.parentInboundMessage = parentInboundMessage;
    this.childInboundMessage = childInboundMessage;
    this.cachedCharts = Maps.newHashMap();
  }

  public CfgParser getParser() {
    return parser;
  }

  // ///////////////////////////////////////////////////////////
  // Required methods for Factor
  // ///////////////////////////////////////////////////////////

  @Override
  public double getUnnormalizedProbability(Assignment a) {
    List<Production> childVarValue = (List<Production>) a.getVarValue(childVarNum);
    ParseChart c = parser.parseMarginal(childVarValue, (Production) a.getVarValue(parentVarNum));
    double probability = c.getPartitionFunction();
    if (parentInboundMessage != null) {
      probability *= parentInboundMessage.getUnnormalizedProbability(a);
    }
    if (childInboundMessage != null) {
      probability *= childInboundMessage.getUnnormalizedProbability(a);
    }
    return probability;
  }

  public ProductionDistribution getProductionDistribution() {
    return parser.getDistribution();
  }

  // ///////////////////////////////////////////////////////////
  // Inference stuff
  // //////////////////////////////////////////////////////////

  @Override
  public Factor product(List<Factor> factors) {
    List<Factor> childFactors = Lists.newArrayList();
    List<Factor> parentFactors = Lists.newArrayList();
    // Partition the factors to multiply into factors over the parent and child
    // variables.
    for (Factor f : factors) {
      Preconditions.checkArgument(f.getVars().size() == 1 &&
          (f.getVars().contains(parentVarNum) || f.getVars().contains(childVarNum)));
      if (f.getVars().contains(parentVarNum)) {
        parentFactors.add(f);
      } else {
        childFactors.add(f);
      }
    }
    if (parentInboundMessage != null) {
      parentFactors.add(parentInboundMessage);
    }
    if (childInboundMessage != null) {
      childFactors.add(childInboundMessage);
    }

    // If the parent/child has any inbound messages, compute a new message for
    // it.
    DiscreteFactor newParentMessage = null;
    DiscreteFactor newChildMessage = null;
    if (parentFactors.size() > 0) {
      newParentMessage = FactorUtils.product(parentFactors).coerceToDiscrete();
    }
    if (childFactors.size() > 0) {
      newChildMessage = FactorUtils.product(childFactors).coerceToDiscrete();
    }

    return new CfgFactor(parentVar, childVar, parentVarNum, childVarNum,
        parser, newParentMessage, newChildMessage);
  }

  @Override
  public double size() {
    // This is an overestimate, but the exact number is not easy to calculate.
    return parentVar.numValues() * childVar.numValues();
  }

  @Override
  public Factor inverse() {
    throw new UnsupportedOperationException("Cannot invert CfgFactor");
  }

  @Override
  public Factor conditional(Assignment a) {
    // Get the conditional probability by multiplying the parent/child
    // variables by a point distribution.
    List<Factor> factorsToMultiply = Lists.newArrayList();
    if (a.containsVar(parentVarNum)) {
      List<Integer> parentVarNumList = Arrays.asList(new Integer[] { parentVarNum });
      TableFactor newParentFactor = TableFactor.pointDistribution(
          getVars().intersection(parentVarNumList), a.subAssignment(parentVarNumList));
      factorsToMultiply.add(newParentFactor);
    }

    if (a.containsVar(childVarNum)) {
      List<Integer> childVarNumList = Arrays.asList(new Integer[] { childVarNum });
      TableFactor newChildFactor = TableFactor.pointDistribution(
          getVars().intersection(childVarNumList), a.subAssignment(childVarNumList));
      factorsToMultiply.add(newChildFactor);
    }

    return this.product(factorsToMultiply);
  }

  @Override
  public Factor maxMarginalize(Collection<Integer> varNumsToEliminate) {
    return computeMarginal(varNumsToEliminate, false);
  }

  @Override
  public Factor marginalize(Collection<Integer> varNumsToEliminate) {
    return computeMarginal(varNumsToEliminate, true);
  }

  @Override
  public Set<SeparatorSet> getComputableOutboundMessages(Map<SeparatorSet, Factor> inboundMessages) {
    boolean receivedAllChildMessages = true;
    Set<SeparatorSet> parentSets = Sets.newHashSet();
    for (SeparatorSet sepset : inboundMessages.keySet()) {
      Preconditions.checkArgument(sepset.getSharedVars().size() == 1);
      if (sepset.getSharedVars().contains(parentVarNum)) {
        parentSets.add(sepset);
      } else if (sepset.getSharedVars().contains(childVarNum)) {
        receivedAllChildMessages = receivedAllChildMessages && (inboundMessages.get(sepset) != null);
      }
    }

    Set<SeparatorSet> computableMessages = Sets.newHashSet();
    if (receivedAllChildMessages) {
      computableMessages.addAll(parentSets);
      // We can't actually compute the child messages at the moment.
    }
    return computableMessages;
  }

  @Override
  public Factor add(Factor other) {
    throw new UnsupportedOperationException("Cannot add CfgFactors");
  }

  @Override
  public Factor maximum(Factor other) {
    throw new UnsupportedOperationException("Cannot maximize between CfgFactors");
  }

  @Override
  public Factor product(Factor other) {
    List<Factor> others = Lists.newArrayList();
    others.add(other);
    return product(others);
  }

  @Override
  public Factor product(double constant) {
    throw new UnsupportedOperationException("Cannot multiply CfgFactors by constants");
  }

  @Override
  public Assignment sample() {
    throw new UnsupportedOperationException("Cannot sample from CfgFactors");
  }

  @Override
  public List<Assignment> getMostLikelyAssignments(int numAssignments) {
    throw new UnsupportedOperationException("Cannot get likely assignments of CfgFactors");
  }

  @Override
  public double computeExpectation(FeatureFunction feature) {
    throw new UnsupportedOperationException("Cannot compute expectations of CfgFactors");
  }

  // ////////////////////////////////////////
  // Misc
  // ////////////////////////////////////////

  @Override
  public String toString() {
    return parser.toString();
  }

  /**
   * Computes a marginal. Abstracts over sum/max product.
   * 
   * @param varNumsToEliminate
   * @param useSumProduct
   * @return
   */
  private Factor computeMarginal(Collection<Integer> varNumsToEliminate, boolean useSumProduct) {
    Preconditions.checkNotNull(varNumsToEliminate);

    Set<Integer> varsToRetain = new HashSet<Integer>();
    varsToRetain.addAll(getVars().getVariableNums());
    varsToRetain.removeAll(varNumsToEliminate);

    if (varsToRetain.size() == 2) {
      return this;
    }
    Preconditions.checkArgument(!varsToRetain.contains(childVarNum));

    if (varsToRetain.contains(parentVarNum)) {
      ParseChart chart = getInsideChart(useSumProduct);
      // Only retaining the parent variable, since the number of variables to
      // retain
      // is less than 2.
      TableFactorBuilder builder = new TableFactorBuilder(new VariableNumMap(
          Arrays.asList(new Integer[] { parentVarNum }),
          Arrays.asList(new DiscreteVariable[] { parentVar })));
      Map<Production, Double> rootEntries = chart.getInsideEntries(0, chart.chartSize() - 1);
      List<Production> value = new ArrayList<Production>();
      value.add(null);
      for (Production p : rootEntries.keySet()) {
        value.set(0, p);
        builder.setWeightList(value, rootEntries.get(p));
      }

      if (parentInboundMessage != null) {
        return parentInboundMessage.product(builder.build());
      } else {
        return builder.build();
      }
    }

    // Both variables eliminated, so simply return the partition function.
    ParseChart chart = getMarginalChart(useSumProduct);
    TableFactorBuilder builder = new TableFactorBuilder(VariableNumMap.emptyMap());
    builder.setWeight(Assignment.EMPTY, chart.getPartitionFunction());
    return builder.build();
  }

  /**
   * Runs the inside portion of CFG parsing, using the distribution over the
   * child variable as the input to the parser. Returns the resulting chart.
   * 
   * @param useSumProduct
   * @return
   */
  private ParseChart getInsideChart(boolean useSumProduct) {
    if (cachedCharts.containsKey(useSumProduct) && cachedCharts.get(useSumProduct).getInsideCalculated()) {
      return cachedCharts.get(useSumProduct);
    }
    // First, try to marginalize out the child variable.
    Preconditions.checkState(childInboundMessage != null);
    Map<List<Production>, Double> childDist = Maps.newHashMap();
    Iterator<Assignment> childIter = childInboundMessage.outcomeIterator();
    while (childIter.hasNext()) {
      Assignment a = childIter.next();
      List<Production> val = (List<Production>) a.getVarValuesInKeyOrder().get(0);
      childDist.put(val, childInboundMessage.getUnnormalizedProbability(a));
    }

    cachedCharts.put(useSumProduct, parser.parseInsideMarginal(childDist, useSumProduct));
    return cachedCharts.get(useSumProduct);
  }

  /**
   * Runs both the inside and outside portions of CFG parsing, using the
   * distribution over the child/parent variable as the input to the parser.
   * Returns the resulting chart.
   * 
   * @param useSumProduct
   * @return
   */
  public ParseChart getMarginalChart(boolean useSumProduct) {
    Preconditions.checkState(parentInboundMessage != null);

    getInsideChart(useSumProduct);
    if (cachedCharts.get(useSumProduct).getOutsideCalculated()) {
      return cachedCharts.get(useSumProduct);
    }

    // Convert from factors to a distribution that the CFG parser understands.
    Map<Production, Double> parentDist = Maps.newHashMap();
    Iterator<Assignment> parentIter = parentInboundMessage.outcomeIterator();
    while (parentIter.hasNext()) {
      Assignment a = parentIter.next();
      Production val = (Production) a.getVarValuesInKeyOrder().get(0);
      parentDist.put(val, parentInboundMessage.getUnnormalizedProbability(a));
    }
    parser.parseOutsideMarginal(cachedCharts.get(useSumProduct), parentDist);
    return cachedCharts.get(useSumProduct);
  }
}
