package com.jayantkrish.jklol.cfg;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.common.base.Preconditions;
import com.google.common.collect.Iterators;
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
import com.jayantkrish.jklol.models.VariableNumMap.VariableRelabeling;
import com.jayantkrish.jklol.util.Assignment;

/**
 * A CfgFactor embeds a context-free grammar in a Bayes Net. The factor defines
 * a distribution over terminal productions conditioned on the root production.
 * 
 * You can run exact inference in a Bayes Net containing a CfgFactor provided
 * that the terminal productions are conditioned on.
 */
public class CfgFactor extends AbstractFactor {

  // Information about the parent (root of the parse tree) variable.
  private final VariableNumMap parent;
  private final int parentVarNum;
  private final DiscreteVariable parentVar;
  // Information about the child (terminals of the parse tree) variable.
  private final VariableNumMap child;
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
  public CfgFactor(VariableNumMap parent, VariableNumMap child, CfgParser parser) {
    super(parent.union(child));
    Preconditions.checkArgument(parent.size() == 1);
    Preconditions.checkArgument(child.size() == 1);

    this.parent = parent;
    this.parentVarNum = parent.getVariableNums().get(0);
    this.parentVar = parent.getDiscreteVariables().get(0);
    this.child = child;
    this.childVarNum = child.getVariableNums().get(0);
    this.childVar = child.getDiscreteVariables().get(0);

    this.parser = parser;

    this.parentInboundMessage = null;
    this.childInboundMessage = null;
    this.cachedCharts = Maps.newHashMap();
  }

  /*
   * For implementing products of factors.
   */
  private CfgFactor(VariableNumMap parent, VariableNumMap child, CfgParser parser, 
      DiscreteFactor parentInboundMessage, DiscreteFactor childInboundMessage) {
    super(parent.union(child));
    Preconditions.checkArgument(parent.size() == 1);
    Preconditions.checkArgument(child.size() == 1);

    this.parent = parent;
    this.parentVarNum = parent.getVariableNums().get(0);
    this.parentVar = parent.getDiscreteVariables().get(0);
    this.child = child;
    this.childVarNum = child.getVariableNums().get(0);
    this.childVar = child.getDiscreteVariables().get(0);

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
    List<?> childVarValue = (List<?>) a.getValue(childVarNum);
    ParseChart c = parser.parseMarginal(childVarValue, a.getValue(parentVarNum), true);
    double probability = c.getPartitionFunction();
    if (parentInboundMessage != null) {
      probability *= parentInboundMessage.getUnnormalizedProbability(a);
    }
    if (childInboundMessage != null) {
      probability *= childInboundMessage.getUnnormalizedProbability(a);
    }
    return probability;
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

    return new CfgFactor(parent, child, parser, newParentMessage, newChildMessage);
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
    if (a.contains(parentVarNum)) {
      List<Integer> parentVarNumList = Arrays.asList(new Integer[] { parentVarNum });
      TableFactor newParentFactor = TableFactor.pointDistribution(
          getVars().intersection(parentVarNumList), a.intersection(parentVarNumList));
      factorsToMultiply.add(newParentFactor);
    }

    if (a.contains(childVarNum)) {
      List<Integer> childVarNumList = Arrays.asList(new Integer[] { childVarNum });
      TableFactor newChildFactor = TableFactor.pointDistribution(
          getVars().intersection(childVarNumList), a.intersection(childVarNumList));
      factorsToMultiply.add(newChildFactor);
    }

    VariableNumMap varsToEliminate = getVars().intersection(a.getVariableNums());
    return this.product(factorsToMultiply).marginalize(varsToEliminate.getVariableNums());
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
      // retain is less than 2.
      Factor root = chart.getInsideEntries(0, chart.chartSize() - 1).relabelVariables(
          VariableRelabeling.createFromVariables(parser.getParentVariable(), parent));

      if (parentInboundMessage != null) {
        return parentInboundMessage.product(root);
      } else {
        return root;
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
   * child variable as the inputVar to the parser. Returns the resulting chart.
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
    Assignment childAssignment = Iterators.getOnlyElement(childInboundMessage.outcomeIterator());
    List<?> childValue = (List<?>) childAssignment.getValues().get(0);

    cachedCharts.put(useSumProduct, parser.parseInsideMarginal(childValue, useSumProduct));
    return cachedCharts.get(useSumProduct);
  }

  /**
   * Runs both the inside and outside portions of CFG parsing, using the
   * distribution over the child/parent variable as the inputVar to the parser.
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

    parser.parseOutsideMarginal(cachedCharts.get(useSumProduct), parentInboundMessage.relabelVariables(
        VariableRelabeling.createFromVariables(parentInboundMessage.getVars(), parser.getParentVariable())));
    return cachedCharts.get(useSumProduct);
  }

  @Override
  public Factor relabelVariables(VariableRelabeling relabeling) {
    throw new UnsupportedOperationException("Not implemented.");
  }
}
