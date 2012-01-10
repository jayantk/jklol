package com.jayantkrish.jklol.cfg;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import com.google.common.base.Preconditions;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.jayantkrish.jklol.models.DiscreteVariable;
import com.jayantkrish.jklol.models.Factor;
import com.jayantkrish.jklol.models.TableFactor;
import com.jayantkrish.jklol.models.Variable;
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.models.VariableNumMap.VariableRelabeling;
import com.jayantkrish.jklol.util.Assignment;

/**
 * A CKY-style parser for probabilistic context free grammars in Chomsky normal
 * form (with a multi-terminal production extension).
 */
public class CfgParser {

  private final VariableNumMap parentVar;
  private final VariableNumMap leftVar;
  private final VariableNumMap rightVar;
  private final VariableNumMap terminalVar;

  // Each entry in the parse chart contains a factor defined over parentVar.
  // These
  // relabelings are necessary to apply binaryDistribution and
  // terminalDistribution
  // during parsing.
  private final VariableRelabeling leftToParent;
  private final VariableRelabeling rightToParent;
  private final VariableRelabeling parentToLeft;
  private final VariableRelabeling parentToRight;

  private final Factor binaryDistribution;
  private final Factor terminalDistribution;

  // Performing a beam search over trees requires the most probable parent
  // objects for each pair
  // of left and right subtree roots. Cache this information out of the factors
  // for improved parsing
  // speed.
  private final int beamSize;
  private Object[][] beamAssignmentCache;
  private double[][] beamProbabilityCache;

  /**
   * If {@code beamSize > 0}, this parser will initialize and cache information
   * for performing beam searches over parse trees. The given beamSize will then
   * be used for all invocations of {@link #beamSearch}.
   * 
   * @param parentVar
   * @param leftVar
   * @param rightVar
   * @param terminalVar
   * @param binaryDistribution
   * @param terminalDistribution
   * @param beamSize
   */
  public CfgParser(VariableNumMap parentVar, VariableNumMap leftVar, VariableNumMap rightVar,
      VariableNumMap terminalVar, Factor binaryDistribution, Factor terminalDistribution,
      int beamSize) {
    Preconditions.checkArgument(parentVar.size() == 1 && leftVar.size() == 1
        && rightVar.size() == 1 && terminalVar.size() == 1);
    this.parentVar = parentVar;
    this.leftVar = leftVar;
    this.rightVar = rightVar;
    this.terminalVar = terminalVar;
    this.binaryDistribution = binaryDistribution;
    this.terminalDistribution = terminalDistribution;

    // Construct some variable->variable renamings which are useful during
    // parsing.
    this.leftToParent = VariableRelabeling.createFromVariables(leftVar, parentVar);
    this.rightToParent = VariableRelabeling.createFromVariables(rightVar, parentVar);
    this.parentToLeft = VariableRelabeling.createFromVariables(parentVar, leftVar);
    this.parentToRight = VariableRelabeling.createFromVariables(parentVar, rightVar);

    this.beamSize = beamSize;
    if (beamSize > 0) {
      int numVariableCombinations = leftVar.getDiscreteVariables().get(0).numValues()
          * rightVar.getDiscreteVariables().get(0).numValues();
      this.beamAssignmentCache = new Object[numVariableCombinations][beamSize];
      this.beamProbabilityCache = new double[numVariableCombinations][beamSize];
      initializeBeamSearchCache();
    }
  }

  public Factor getBinaryDistribution() {
    return binaryDistribution;
  }

  public Factor getTerminalDistribution() {
    return terminalDistribution;
  }

  public VariableNumMap getParentVariable() {
    return parentVar;
  }

  public int getBeamSize() {
    return beamSize;
  }

  // //////////////////////////////////////////////////////////////////////
  // The following methods are the important ones for running the parser in
  // isolation.
  // //////////////////////////////////////////////////////////////////////

  /**
   * Compute the marginal distribution over all grammar entries conditioned on
   * the given sequence of terminals.
   */
  public ParseChart parseMarginal(List<?> terminals, Object root, boolean useSumProduct) {
    ParseChart chart = createParseChart(terminals, useSumProduct);
    Factor rootFactor = TableFactor.pointDistribution(parentVar,
        parentVar.outcomeArrayToAssignment(root));
    return marginal(chart, terminals, rootFactor);
  }

  /**
   * Compute the distribution over CFG entries, the parse root, and the
   * children, conditioned on the provided terminals and assuming the provided
   * distributions over the root node.
   */
  public ParseChart parseMarginal(List<?> terminals, Factor rootDist, boolean useSumProduct) {
    return marginal(createParseChart(terminals, useSumProduct), terminals, rootDist);
  }

  /**
   * Performs a beam search over parse trees maintaining up to
   * {@code this.getBeamSize()} trees at each node of the parse tree. This
   * method can be used to approximate the marginal distribution over parse
   * trees. The returned list of trees is sorted from most to least probable.
   * 
   * @param terminals
   * @param beamSize
   * @return
   */
  @SuppressWarnings("unchecked")
  public List<ParseTree> beamSearch(List<?> terminals) {
    BeamSearchParseChart chart = new BeamSearchParseChart(terminals, beamSize);

    initializeBeamSearchChart((List<Object>) terminals, chart);

    for (int spanSize = 1; spanSize < chart.chartSize(); spanSize++) {
      for (int spanStart = 0; spanStart + spanSize < chart.chartSize(); spanStart++) {
        int spanEnd = spanStart + spanSize;
        calculateInsideBeam(spanStart, spanEnd, chart);
      }
    }

    List<ParseTree> trees = Lists.newArrayList(chart.getParseTreesForSpan(0, terminals.size() - 1));
    Collections.sort(trees);
    Collections.reverse(trees);
    return trees;
  }
  
  /**
   * Gets the probability of generating {@code tree} from {@code terminals}.
   * 
   * @param terminals
   * @param tree
   * @return
   */
  public double getProbability(List<?> terminals, ParseTree tree) {
    if (!tree.getTerminalProductions().equals(terminals)) {
      return 0.0;
    } else {
      return getProbabilityHelper(tree);
    }
  }
  
  private double getProbabilityHelper(ParseTree tree) {
    if (tree.isTerminal()) {
      Assignment terminalAssignment = terminalVar.outcomeArrayToAssignment(tree.getTerminalProductions())
          .union(parentVar.outcomeArrayToAssignment(tree.getRoot()));
      return terminalDistribution.getUnnormalizedProbability(terminalAssignment);
    } else {
      Assignment binaryAssignment = leftVar.outcomeArrayToAssignment(tree.getLeft().getRoot())
          .union(rightVar.outcomeArrayToAssignment(tree.getRight().getRoot()))
          .union(parentVar.outcomeArrayToAssignment(tree.getRoot()));
      return binaryDistribution.getUnnormalizedProbability(binaryAssignment) *
          getProbabilityHelper(tree.getLeft()) *
          getProbabilityHelper(tree.getRight());
    }
  }

  /**
   * Compute the most likely sequence of productions (of a given length)
   * conditioned on the root symbol.
   */
  public ParseChart mostLikelyProductions(Object root, int length, int beamWidth) {
    // Create a fake list of terminals for the chart.
    List<?> terminals = Lists.newArrayList();
    for (int i = 0; i < length; i++) {
      terminals.add(null);
    }

    // TODO: beamWidth.
    ParseChart chart = createParseChart(terminals, false);
    initializeChartAllTerminals(chart);
    upwardChartPass(chart);
    chart.updateOutsideEntry(0, chart.chartSize() - 1,
        TableFactor.pointDistribution(parentVar, parentVar.outcomeArrayToAssignment(root)));
    downwardChartPass(chart);
    return chart;
  }

  // ////////////////////////////////////////////////////////////////////////////////
  // Methods for computing partial parse distributions, intended mostly for
  // running
  // the CFG parser as part of a graphical model.
  // ////////////////////////////////////////////////////////////////////////////////

  /**
   * Calculate the inside probabilities (i.e., run the upward pass of variable
   * elimination).
   */
  public ParseChart parseInsideMarginal(List<?> terminals, boolean useSumProduct) {
    ParseChart chart = createParseChart(terminals, useSumProduct);
    initializeChart(chart, terminals);
    upwardChartPass(chart);
    return chart;
  }

  public ParseChart parseOutsideMarginal(ParseChart chart, Factor rootDist) {
    assert chart.getInsideCalculated();
    assert !chart.getOutsideCalculated();

    chart.updateOutsideEntry(0, chart.chartSize() - 1, rootDist);
    downwardChartPass(chart);
    return chart;
  }

  // //////////////////////////////////////////////////////////////////////////////////
  // Misc
  // //////////////////////////////////////////////////////////////////////////////////

  public String toString() {
    return binaryDistribution.toString() + "\n" + terminalDistribution.toString();
  }

  /**
   * Initializes parse charts with the correct variable arguments.
   * 
   * @param terminals
   * @param useSumProduct
   * @return
   */
  private ParseChart createParseChart(List<?> terminals, boolean useSumProduct) {
    return new ParseChart(terminals, parentVar, leftVar, rightVar, terminalVar, useSumProduct);
  }

  /*
   * Helper method for computing marginals / max-marginals with an arbitrary
   * distribution on terminals.
   */
  private ParseChart marginal(ParseChart chart, List<?> terminals,
      Factor rootDist) {

    initializeChart(chart, terminals);
    upwardChartPass(chart);
    // Set the initial outside probabilities
    chart.updateOutsideEntry(0, chart.chartSize() - 1, rootDist);
    downwardChartPass(chart);
    return chart;
  }

  /*
   * This method calculates all of the inside probabilities by iteratively
   * parsing larger and larger spans of the sentence.
   */
  private void upwardChartPass(ParseChart chart) {
    // spanSize is the number of words *in addition* to the word under
    // spanStart.
    for (int spanSize = 1; spanSize < chart.chartSize(); spanSize++) {
      for (int spanStart = 0; spanStart + spanSize < chart.chartSize(); spanStart++) {
        int spanEnd = spanStart + spanSize;
        calculateInside(spanStart, spanEnd, chart);
      }
    }
    chart.setInsideCalculated();
  }

  /*
   * Calculate a single inside probability entry.
   */
  private void calculateInside(int spanStart, int spanEnd, ParseChart chart) {
    for (int k = 0; k < spanEnd - spanStart; k++) {
      Factor left = chart.getInsideEntries(spanStart, spanStart + k).relabelVariables(parentToLeft);
      Factor right = chart.getInsideEntries(spanStart + k + 1, spanEnd).relabelVariables(
          parentToRight);

      Factor binaryRuleDistribution = binaryDistribution.product(left).product(right);
      chart.updateInsideEntry(spanStart, spanEnd, k, binaryRuleDistribution);
    }
  }

  /*
   * Compute the outside probabilities moving downward from the top of the tree.
   */
  private void downwardChartPass(ParseChart chart) {
    assert chart.getInsideCalculated();

    // Calculate root marginal, which is not included in the rest of the pass.
    // Also compute the partition function.
    Factor rootOutside = chart.getOutsideEntries(0, chart.chartSize() - 1);
    Factor rootInside = chart.getInsideEntries(0, chart.chartSize() - 1);
    Factor rootMarginal = rootOutside.product(rootInside);
    chart.updateMarginalEntry(0, chart.chartSize() - 1, rootMarginal);
    chart.setPartitionFunction(rootMarginal.marginalize(parentVar).getUnnormalizedProbability(
        Assignment.EMPTY));

    for (int spanSize = chart.chartSize() - 1; spanSize >= 1; spanSize--) {
      for (int spanStart = 0; spanStart + spanSize < chart.chartSize(); spanStart++) {
        int spanEnd = spanStart + spanSize;
        calculateOutside(spanStart, spanEnd, chart);
      }
    }
    updateTerminalRuleCounts(chart);

    // Outside probabilities / partition function are now calculated.
    chart.setOutsideCalculated();
  }

  /*
   * Calculate a single outside probability entry (and its corresponding
   * marginal).
   */
  private void calculateOutside(int spanStart, int spanEnd, ParseChart chart) {
    Factor parentOutside = chart.getOutsideEntries(spanStart, spanEnd);
    for (int k = 0; k < spanEnd - spanStart; k++) {
      Factor leftInside = chart.getInsideEntries(spanStart, spanStart + k).relabelVariables(
          parentToLeft);
      Factor rightInside = chart.getInsideEntries(spanStart + k + 1, spanEnd).relabelVariables(
          parentToRight);

      Factor binaryRuleMarginal = binaryDistribution.product(Arrays.asList(rightInside,
          parentOutside, leftInside));
      chart.updateBinaryRuleExpectations(binaryRuleMarginal);

      Factor leftOutside = binaryDistribution.product(Arrays.asList(rightInside, parentOutside));
      Factor rightOutside = binaryDistribution.product(Arrays.asList(leftInside, parentOutside));
      Factor leftMarginal, rightMarginal;
      if (chart.getSumProduct()) {
        leftMarginal = binaryRuleMarginal.marginalize(rightVar.union(parentVar)).relabelVariables(
            leftToParent);
        rightMarginal = binaryRuleMarginal.marginalize(leftVar.union(parentVar)).relabelVariables(
            rightToParent);
        leftOutside = leftOutside.marginalize(rightVar.union(parentVar)).relabelVariables(
            leftToParent);
        rightOutside = rightOutside.marginalize(leftVar.union(parentVar)).relabelVariables(
            rightToParent);
      } else {
        leftMarginal = binaryRuleMarginal.maxMarginalize(rightVar.union(parentVar))
            .relabelVariables(leftToParent);
        rightMarginal = binaryRuleMarginal.maxMarginalize(leftVar.union(parentVar))
            .relabelVariables(rightToParent);
        leftOutside = leftOutside.maxMarginalize(rightVar.union(parentVar)).relabelVariables(
            leftToParent);
        rightOutside = rightOutside.maxMarginalize(leftVar.union(parentVar)).relabelVariables(
            rightToParent);
      }
      chart.updateOutsideEntry(spanStart, spanStart + k, leftOutside);
      chart.updateOutsideEntry(spanStart + k + 1, spanEnd, rightOutside);

      chart.updateMarginalEntry(spanStart, spanStart + k, leftMarginal);
      chart.updateMarginalEntry(spanStart + k + 1, spanEnd, rightMarginal);
    }
  }

  /*
   * Fill in the initial chart entries implied by the given set of terminals.
   */
  private void initializeChart(ParseChart chart, List<?> terminals) {
    Variable terminalListValue = Iterables.getOnlyElement(terminalVar.getVariables());

    for (int i = 0; i < terminals.size(); i++) {
      for (int j = i; j < terminals.size(); j++) {
        if (terminalListValue.canTakeValue(terminals.subList(i, j + 1))) {
          Assignment assignment = terminalVar.outcomeArrayToAssignment(terminals.subList(i, j + 1));
          chart.updateInsideEntryTerminal(i, j, terminalDistribution.conditional(assignment));
        }
      }
    }
  }

  /*
   * Fill in the chart using all terminals.
   */
  private void initializeChartAllTerminals(ParseChart chart) {
    DiscreteVariable terminalListValue = (DiscreteVariable) Iterables.getOnlyElement(terminalVar
        .getVariables());

    for (int i = 0; i < terminalListValue.numValues(); i++) {
      List<?> value = (List<?>) terminalListValue.getValue(i);
      int spanSize = value.size() - 1;
      Factor conditional = terminalDistribution.conditional(terminalVar
          .outcomeArrayToAssignment(value));
      for (int j = 0; j < chart.chartSize() - spanSize; j++) {
        chart.updateInsideEntryTerminal(j, j + spanSize, conditional);
      }
    }
  }

  private void updateTerminalRuleCounts(ParseChart chart) {
    Variable terminalListValue = Iterables.getOnlyElement(terminalVar.getVariables());
    List<?> terminals = chart.getTerminals();
    if (terminals != null) {
      for (int i = 0; i < terminals.size(); i++) {
        for (int j = i; j < terminals.size(); j++) {
          if (terminalListValue.canTakeValue(terminals.subList(i, j + 1))) {
            Assignment assignment = terminalVar.outcomeArrayToAssignment(terminals
                .subList(i, j + 1));

            Factor terminalRuleMarginal = terminalDistribution.product(
                TableFactor.pointDistribution(terminalVar, assignment));
            terminalRuleMarginal = terminalRuleMarginal.product(chart.getOutsideEntries(i, j));

            chart.updateTerminalRuleExpectations(terminalRuleMarginal);
          }
        }
      }
    }
  }

  /**
   * Initializes a beam search chart using the terminal production distribution.
   * 
   * @param terminals
   * @param chart
   */
  private void initializeBeamSearchChart(List<Object> terminals, BeamSearchParseChart chart) {
    Variable terminalListValue = Iterables.getOnlyElement(terminalVar.getVariables());

    for (int i = 0; i < terminals.size(); i++) {
      for (int j = i; j < terminals.size(); j++) {
        if (terminalListValue.canTakeValue(terminals.subList(i, j + 1))) {
          Assignment assignment = terminalVar.outcomeArrayToAssignment(terminals.subList(i, j + 1));
          Factor terminalFactor = terminalDistribution.conditional(assignment);

          for (Assignment bestAssignment : terminalFactor.getMostLikelyAssignments(chart
              .getBeamSize())) {
            chart.addParseTreeForSpan(i, j,
                new ParseTree(bestAssignment.getValues().get(0), terminals.subList(i, j + 1),
                    terminalFactor.getUnnormalizedProbability(bestAssignment)));
          }
        }
      }
    }
  }

  /**
   * Calculates the parse trees that span {@code spanStart} to {@code spanEnd},
   * inclusive. Requires {@code spanEnd - spanStart > 1}.
   * 
   * @param spanStart
   * @param spanEnd
   * @param chart
   */
  private void calculateInsideBeam(int spanStart, int spanEnd, BeamSearchParseChart chart) {
    for (int k = 0; k < spanEnd - spanStart; k++) {
      for (ParseTree leftTree : chart.getParseTreesForSpan(spanStart, spanStart + k)) {
        for (ParseTree rightTree : chart.getParseTreesForSpan(spanStart + k + 1, spanEnd)) {
          
          Object[] possibleParents = getBeamAssignments(leftTree.getRoot(), rightTree.getRoot());
          double[] parentProbabilities = getBeamProbabilities(leftTree.getRoot(), rightTree.getRoot());
          
          for (int i = 0; i < possibleParents.length && parentProbabilities[i] != 0.0; i++) {
            ParseTree combinedTree = new ParseTree(possibleParents[i], leftTree,
                rightTree, leftTree.getProbability() * rightTree.getProbability()
                    * parentProbabilities[i]);
            chart.addParseTreeForSpan(spanStart, spanEnd, combinedTree);
          }
        }
      }
    }
  }

  private Object[] getBeamAssignments(Object leftRoot, Object rightRoot) {
    return beamAssignmentCache[getBeamCacheIndex(leftRoot, rightRoot)];
  }

  private double[] getBeamProbabilities(Object leftRoot, Object rightRoot) {
    return beamProbabilityCache[getBeamCacheIndex(leftRoot, rightRoot)];
  }
  
  private int getBeamCacheIndex(Object leftRoot, Object rightRoot) {
    int leftRootInt = leftVar.getDiscreteVariables().get(0).getValueIndex(leftRoot);
    int rightRootInt = rightVar.getDiscreteVariables().get(0).getValueIndex(rightRoot);
    return (rightVar.getDiscreteVariables().get(0).numValues() * leftRootInt) + rightRootInt;
  }

  /**
   * Initializes {@code beamAssignmentCache} and {@code beamProbabilityCache} to
   * enable fast beam searches over parse trees.
   */
  private void initializeBeamSearchCache() {
    DiscreteVariable leftVariableType = leftVar.getDiscreteVariables().get(0);
    DiscreteVariable rightVariableType = rightVar.getDiscreteVariables().get(0);
    for (Object leftVarValue : leftVariableType.getValues()) {
      for (Object rightVarValue : rightVariableType.getValues()) {
        int index = getBeamCacheIndex(leftVarValue, rightVarValue);
        Arrays.fill(beamAssignmentCache[index], null);
        Arrays.fill(beamProbabilityCache[index], 0.0);
        
        Assignment combinedAssignment = leftVar.outcomeArrayToAssignment(leftVarValue)
            .union(rightVar.outcomeArrayToAssignment(rightVarValue));

        Factor parentFactor = binaryDistribution.conditional(combinedAssignment);
        List<Assignment> bestAssignments = parentFactor.getMostLikelyAssignments(beamSize);

        for (int i = 0; i < bestAssignments.size(); i++) {
          Assignment a = bestAssignments.get(i);
          beamAssignmentCache[index][i] = a.getValues().get(0);
          beamProbabilityCache[index][i] = parentFactor.getUnnormalizedProbability(a);
        }
      }
    }
  }
}