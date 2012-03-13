package com.jayantkrish.jklol.cfg;

import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import com.google.common.base.Preconditions;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.jayantkrish.jklol.models.DiscreteFactor;
import com.jayantkrish.jklol.models.DiscreteFactor.Outcome;
import com.jayantkrish.jklol.models.DiscreteVariable;
import com.jayantkrish.jklol.models.Factor;
import com.jayantkrish.jklol.models.TableFactor;
import com.jayantkrish.jklol.models.Variable;
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.models.VariableNumMap.VariableRelabeling;
import com.jayantkrish.jklol.tensor.Tensor;
import com.jayantkrish.jklol.util.Assignment;
import com.jayantkrish.jklol.util.HeapUtils;

/**
 * A CKY-style parser for probabilistic context free grammars in Chomsky normal
 * form (with a multi-terminal production extension).
 */
public class CfgParser {

  // The root nonterminal symbol in binary and terminal production rules
  private final VariableNumMap parentVar;
  // The left and right components of a production rule.
  private final VariableNumMap leftVar;
  private final VariableNumMap rightVar;
  // The terminals of a terminal production rule.
  private final VariableNumMap terminalVar;
  // Variable associated auxiliary information associated with each possible
  // terminal and production rule. (For example, this variable can represent
  // typed edges in a dependency parse).
  private final VariableNumMap ruleTypeVar;

  // The types of variables mapping nonterminals and rule types to indexes.
  private final DiscreteVariable nonterminalVariableType;
  private final DiscreteVariable ruleVariableType;

  // Each entry in the parse chart contains a factor defined over parentVar.
  // These relabelings are necessary to apply binaryDistribution and
  // terminalDistribution during parsing.
  private final VariableRelabeling leftToParent;
  private final VariableRelabeling rightToParent;
  private final VariableRelabeling parentToLeft;
  private final VariableRelabeling parentToRight;

  private final DiscreteFactor binaryDistribution;
  private final DiscreteFactor terminalDistribution;

  // The parser uses the tensor representations of the nonterminal distributions
  // in order to improve parsing speed.
  private final Tensor binaryDistributionWeights;

  // If greater than 0, this parser performs a beam search over trees,
  // maintaining up to beamSize trees at each chart node.
  private final int beamSize;
  // If true, the parser is allowed to skip portions of the terminal symbols
  // during parsing.
  private final boolean canSkipTerminals;

  /**
   * If {@code beamSize > 0}, this parser will initialize and cache information
   * for performing beam searches over parse trees. The given beamSize will then
   * be used for all invocations of {@link #beamSearch}.
   * 
   * @param parentVar
   * @param leftVar
   * @param rightVar
   * @param terminalVar
   * @param ruleTypeVar
   * @param binaryDistribution
   * @param terminalDistribution
   * @param beamSize
   * @param canSkipTerminals if {@code true}, the parser may skip terminal
   * symbols during the parse. Only implemented for beam searches.
   */
  public CfgParser(VariableNumMap parentVar, VariableNumMap leftVar, VariableNumMap rightVar,
      VariableNumMap terminalVar, VariableNumMap ruleTypeVar, DiscreteFactor binaryDistribution,
      DiscreteFactor terminalDistribution, int beamSize, boolean canSkipTerminals) {
    Preconditions.checkArgument(parentVar.size() == 1 && leftVar.size() == 1
        && rightVar.size() == 1 && terminalVar.size() == 1 && ruleTypeVar.size() == 1);
    Preconditions.checkArgument(binaryDistribution.getVars().equals(VariableNumMap.unionAll(
        parentVar, leftVar, rightVar, ruleTypeVar)));
    Preconditions.checkArgument(terminalDistribution.getVars().equals(VariableNumMap.unionAll(
        parentVar, terminalVar, ruleTypeVar)));
    Preconditions.checkArgument(terminalVar.getOnlyVariableNum() < parentVar.getOnlyVariableNum());
    Preconditions.checkArgument(leftVar.getOnlyVariableNum() < parentVar.getOnlyVariableNum());
    Preconditions.checkArgument(rightVar.getOnlyVariableNum() < parentVar.getOnlyVariableNum());
    this.parentVar = parentVar;
    this.leftVar = leftVar;
    this.rightVar = rightVar;
    this.terminalVar = terminalVar;
    this.ruleTypeVar = ruleTypeVar;
    this.binaryDistribution = binaryDistribution;
    this.terminalDistribution = terminalDistribution;

    this.ruleVariableType = ruleTypeVar.getDiscreteVariables().get(0);
    this.nonterminalVariableType = parentVar.getDiscreteVariables().get(0);
    this.binaryDistributionWeights = binaryDistribution.getWeights();

    // Construct some variable->variable renamings which are useful during
    // parsing.
    this.leftToParent = VariableRelabeling.createFromVariables(leftVar, parentVar);
    this.rightToParent = VariableRelabeling.createFromVariables(rightVar, parentVar);
    this.parentToLeft = VariableRelabeling.createFromVariables(parentVar, leftVar);
    this.parentToRight = VariableRelabeling.createFromVariables(parentVar, rightVar);

    this.beamSize = beamSize;
    this.canSkipTerminals = canSkipTerminals;
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
    BeamSearchParseChart chart = new BeamSearchParseChart((List<Object>) terminals, beamSize);

    // Construct an encoding for mapping partial parse trees to longs. Using an
    // encoding improves parsing efficiency.
    long[] treeKeyOffsets = new long[6];
    // Index of rule type.
    treeKeyOffsets[5] = 1;
    // Index of nonterminal root symbol.
    treeKeyOffsets[4] = treeKeyOffsets[5] * ruleVariableType.numValues();
    // Starting index for the right subtree, ending at spanEnd for the current
    // tree.
    treeKeyOffsets[3] = treeKeyOffsets[4] * nonterminalVariableType.numValues();
    // Ending index for the left subtree, starting at spanStart of the current
    // tree.
    treeKeyOffsets[2] = treeKeyOffsets[3] * (chart.chartSize() + 1);
    // Index into the beam of right subtrees.
    treeKeyOffsets[1] = treeKeyOffsets[2] * (chart.chartSize() + 1);
    // Index into the beam of left subtrees.
    treeKeyOffsets[0] = treeKeyOffsets[1] * beamSize;

    // Handle the terminal rules, mapping the observed terminals to possible
    // nonterminals.
    initializeBeamSearchChart((List<Object>) terminals, chart, treeKeyOffsets);

    // Construct a tree from the nonterminals.
    for (int spanSize = 1; spanSize < chart.chartSize(); spanSize++) {
      for (int spanStart = 0; spanStart + spanSize < chart.chartSize(); spanStart++) {
        int spanEnd = spanStart + spanSize;
        calculateInsideBeam(spanStart, spanEnd, chart, treeKeyOffsets);
      }
    }

    // This block of code only exists for logging purposes.
    /*
    int numTruncatedEntries = 0;
    for (int spanSize = 0; spanSize < chart.chartSize() - 1; spanSize++) {
      for (int spanStart = 0; spanStart + spanSize < chart.chartSize(); spanStart++) {
        if (chart.getNumParseTreeKeysForSpan(spanStart, spanStart + spanSize) == beamSize) {
          numTruncatedEntries++;
        }
      }
    }
    System.out.println("Truncated " + numTruncatedEntries + " chart entries");
    */

    // Map the integer encodings of trees back to tree objects.
    List<ParseTree> trees;
    if (canSkipTerminals) {
      trees = populateParseTreesFromChartSkippingTerminals(chart, treeKeyOffsets);
    } else {
      trees = Lists.newArrayList();
      int numRootTrees = chart.getNumParseTreeKeysForSpan(0, terminals.size() - 1);
      long[] rootKeys = chart.getParseTreeKeysForSpan(0, terminals.size() - 1);
      double[] rootProbs = chart.getParseTreeProbsForSpan(0, terminals.size() - 1);
      for (int i = 0; i < numRootTrees; i++) {
        trees.add(mapTreeKeyToParseTree(rootKeys[i], rootProbs[i], 0, terminals.size() - 1, chart, treeKeyOffsets));
      }
    }

    Collections.sort(trees);
    Collections.reverse(trees);
    return trees;
  }
  
  private List<ParseTree> populateParseTreesFromChartSkippingTerminals(BeamSearchParseChart chart, 
      long[] treeEncodingOffsets) {
    // If we can skip terminals, a parse for any subtree of the sentence
    // is a parse for the entire sentence. Identify a probability threshold
    // which all returned parse trees must be above.
    long[] bestKeys = new long[beamSize + 1];
    double[] bestValues = new double[beamSize + 1];
    int heapSize = 0;
    for (int spanSize = 0; spanSize < chart.chartSize() - 1; spanSize++) {
      for (int spanStart = 0; spanStart + spanSize < chart.chartSize(); spanStart++) {
        int numTreesInSpan = chart.getNumParseTreeKeysForSpan(spanStart, spanStart + spanSize);
        long[] spanKeys = chart.getParseTreeKeysForSpan(spanStart, spanStart + spanSize);
        double[] spanValues = chart.getParseTreeProbsForSpan(spanStart, spanStart + spanSize);          
        for (int i = 0; i < numTreesInSpan; i++) {
          HeapUtils.offer(bestKeys, bestValues, heapSize, spanKeys[i], spanValues[i]);
          heapSize++;

          if (heapSize > beamSize) {
            HeapUtils.removeMin(bestKeys, bestValues, heapSize);
            heapSize--;
          }
        }
      }
    }
    // Construct the parse trees above the probability threshold.
    List<ParseTree> trees = Lists.newArrayList();
    if (heapSize > 0) {
      double minimumProbability = bestValues[0]; 
      for (int spanSize = 0; spanSize < chart.chartSize() - 1; spanSize++) {
        for (int spanStart = 0; spanStart + spanSize < chart.chartSize(); spanStart++) {
          int numTreesInSpan = chart.getNumParseTreeKeysForSpan(spanStart, spanStart + spanSize);
          long[] spanKeys = chart.getParseTreeKeysForSpan(spanStart, spanStart + spanSize);
          double[] spanValues = chart.getParseTreeProbsForSpan(spanStart, spanStart + spanSize);          
          for (int i = 0; i < numTreesInSpan; i++) {
            if (spanValues[i] >= minimumProbability) {
              trees.add(mapTreeKeyToParseTree(spanKeys[i], spanValues[i], spanStart, spanStart + spanSize,
                  chart, treeEncodingOffsets));
            }
          }
        }
      }
    }
    return trees;
  }

  private ParseTree mapTreeKeyToParseTree(long treeKey, double treeProb, int spanStart, int spanEnd,
      BeamSearchParseChart chart, long[] treeEncodingOffsets) {
    // Unpack the components of treeKey.
    int ruleIndex = (int) ((treeKey % treeEncodingOffsets[4]) / treeEncodingOffsets[5]);
    int rootIndex = (int) ((treeKey % treeEncodingOffsets[3]) / treeEncodingOffsets[4]);
    int rightSplitIndex = (int) ((treeKey % treeEncodingOffsets[2]) / treeEncodingOffsets[3]);
    int leftSplitIndex = (int) ((treeKey % treeEncodingOffsets[1]) / treeEncodingOffsets[2]);
    int rightSubtreeBeamIndex = (int) ((treeKey % treeEncodingOffsets[0]) / treeEncodingOffsets[1]);
    int leftSubtreeBeamIndex = (int) (treeKey / treeEncodingOffsets[0]);

    Object rootObj = nonterminalVariableType.getValue(rootIndex);
    Object ruleObj = ruleVariableType.getValue(ruleIndex);
    // int[] out = {leftSubtreeBeamIndex, rightSubtreeBeamIndex, leftSplitIndex,
    // rightSplitIndex, rootIndex, ruleIndex};
    // System.out.println(treeKey + " " + spanStart + " " + spanEnd + " " +
    // Arrays.toString(out));
    // System.out.println("     " + Arrays.toString(treeEncodingOffsets));

    if (leftSplitIndex == chart.chartSize() &&
        rightSplitIndex == chart.chartSize()) {
      // The tree is a terminal.
      return new ParseTree(rootObj, ruleObj, chart.getTerminals().subList(spanStart, spanEnd + 1), treeProb);
    } else {
      // Tree is a nonterminal. Identify the left and right subtrees by decoding
      // the current key.
      long leftTreeKey = chart.getParseTreeKeysForSpan(spanStart, leftSplitIndex)[leftSubtreeBeamIndex];
      double leftProb = chart.getParseTreeProbsForSpan(spanStart, leftSplitIndex)[leftSubtreeBeamIndex];
      long rightTreeKey = chart.getParseTreeKeysForSpan(rightSplitIndex, spanEnd)[rightSubtreeBeamIndex];
      double rightProb = chart.getParseTreeProbsForSpan(rightSplitIndex, spanEnd)[rightSubtreeBeamIndex];

      return new ParseTree(rootObj, ruleObj,
          mapTreeKeyToParseTree(leftTreeKey, leftProb, spanStart, leftSplitIndex, chart, treeEncodingOffsets),
          mapTreeKeyToParseTree(rightTreeKey, rightProb, rightSplitIndex, spanEnd, chart, treeEncodingOffsets),
          treeProb);
    }
  }

  /**
   * Gets the probability of generating {@code tree} from {@code terminals}.
   * 
   * @param terminals
   * @param tree
   * @return
   */
  public double getProbability(List<?> terminals, ParseTree tree) {
    if (!tree.getTerminalProductions().equals(terminals) && !canSkipTerminals) {
      return 0.0;
    } else {
      return getProbabilityHelper(tree);
    }
  }

  private double getProbabilityHelper(ParseTree tree) {
    if (tree.isTerminal()) {
      Assignment terminalAssignment = terminalVar.outcomeArrayToAssignment(tree.getTerminalProductions())
          .union(parentVar.outcomeArrayToAssignment(tree.getRoot()))
          .union(ruleTypeVar.outcomeArrayToAssignment(tree.getRuleType()));
      return terminalDistribution.getUnnormalizedProbability(terminalAssignment);
    } else {
      Assignment binaryAssignment = leftVar.outcomeArrayToAssignment(tree.getLeft().getRoot())
          .union(rightVar.outcomeArrayToAssignment(tree.getRight().getRoot()))
          .union(parentVar.outcomeArrayToAssignment(tree.getRoot()))
          .union(ruleTypeVar.outcomeArrayToAssignment(tree.getRuleType()));
      return binaryDistribution.getUnnormalizedProbability(binaryAssignment) *
          getProbabilityHelper(tree.getLeft()) *
          getProbabilityHelper(tree.getRight());
    }
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
    return new ParseChart(terminals, parentVar, leftVar, rightVar, terminalVar, ruleTypeVar, useSumProduct);
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
      VariableNumMap allButLeft = VariableNumMap.unionAll(rightVar, parentVar, ruleTypeVar);
      VariableNumMap allButRight = VariableNumMap.unionAll(leftVar, parentVar, ruleTypeVar);
      Factor leftMarginal, rightMarginal;
      if (chart.getSumProduct()) {
        leftMarginal = binaryRuleMarginal.marginalize(allButLeft).relabelVariables(
            leftToParent);
        rightMarginal = binaryRuleMarginal.marginalize(allButRight).relabelVariables(
            rightToParent);
        leftOutside = leftOutside.marginalize(allButLeft).relabelVariables(
            leftToParent);
        rightOutside = rightOutside.marginalize(allButRight).relabelVariables(
            rightToParent);
      } else {
        leftMarginal = binaryRuleMarginal.maxMarginalize(allButLeft)
            .relabelVariables(leftToParent);
        rightMarginal = binaryRuleMarginal.maxMarginalize(allButRight)
            .relabelVariables(rightToParent);
        leftOutside = leftOutside.maxMarginalize(allButLeft).relabelVariables(
            leftToParent);
        rightOutside = rightOutside.maxMarginalize(allButRight).relabelVariables(
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

          Factor terminalRules = terminalDistribution.conditional(assignment);
          if (chart.getSumProduct()) {
            chart.updateInsideEntryTerminal(i, j, terminalRules.marginalize(ruleTypeVar));
          } else {
            chart.updateInsideEntryTerminal(i, j, terminalRules.maxMarginalize(ruleTypeVar));
          }
        }
      }
    }
  }

  private void updateTerminalRuleCounts(ParseChart chart) {
    Variable terminalListValue = Iterables.getOnlyElement(terminalVar.getVariables());
    List<?> terminals = chart.getTerminals();

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

  /**
   * Initializes a beam search chart using the terminal production distribution.
   * 
   * @param terminals
   * @param chart
   */
  private void initializeBeamSearchChart(List<Object> terminals, BeamSearchParseChart chart,
      long[] treeEncodingOffsets) {
    Variable terminalListValue = Iterables.getOnlyElement(terminalVar.getVariables());

    // Adding this to a tree key indicates that the tree is a terminal.
    long terminalSignal = ((long) chart.chartSize()) * (treeEncodingOffsets[3] + treeEncodingOffsets[2]);

    for (int i = 0; i < terminals.size(); i++) {
      for (int j = i; j < terminals.size(); j++) {
        if (terminalListValue.canTakeValue(terminals.subList(i, j + 1))) {
          Assignment assignment = terminalVar.outcomeArrayToAssignment(terminals.subList(i, j + 1));
          Iterator<Outcome> iterator = terminalDistribution.outcomePrefixIterator(assignment);

          while (iterator.hasNext()) {
            Outcome bestOutcome = iterator.next();
            int root = nonterminalVariableType.getValueIndex(bestOutcome.getAssignment().getValue(parentVar.getOnlyVariableNum()));
            int ruleType = ruleVariableType.getValueIndex(bestOutcome.getAssignment().getValue(ruleTypeVar.getOnlyVariableNum()));
            long partialKeyNum = (root * treeEncodingOffsets[4]) + (ruleType * treeEncodingOffsets[5]);
            chart.addParseTreeKeyForSpan(i, j, terminalSignal + partialKeyNum,
                bestOutcome.getProbability());
          }
          // System.out.println(i + "." + j + ": " + assignment + " : " +
          // chart.getParseTreesForSpan(i, j));
        }
      }
    }
  }

  /**
   * Calculates the parse trees that span {@code spanStart} to {@code spanEnd},
   * inclusive. Requires {@code spanEnd - spanStart > 1}.
   * 
   * For efficiency, this beam search computes parse trees whose nodes are
   * integers (which map to values of nonterminals).
   * 
   * @param spanStart
   * @param spanEnd
   * @param chart
   */
  private void calculateInsideBeam(int spanStart, int spanEnd, BeamSearchParseChart chart,
      long[] treeEncodingOffsets) {
    // For efficiency, precompute values which are used repeatedly in the loop
    // below.
    double[] values = binaryDistributionWeights.getValues();
    long[] dimensionOffsets = binaryDistributionWeights.getDimensionOffsets();

    for (int i = 0; i < spanEnd - spanStart; i++) {
      for (int j = i + 1; j < (canSkipTerminals ? 1 + spanEnd - spanStart : i + 2); j++) {

        // These variables store the (integer encoded) parse trees from the two subspans.  
        long[] leftParseTreeKeys = chart.getParseTreeKeysForSpan(spanStart, spanStart + i);
        double[] leftParseTreeProbs = chart.getParseTreeProbsForSpan(spanStart, spanStart + i);
        int numLeftParseTrees = chart.getNumParseTreeKeysForSpan(spanStart, spanStart + i);
        long[] rightParseTreeKeys = chart.getParseTreeKeysForSpan(spanStart + j, spanEnd);
        double[] rightParseTreeProbs = chart.getParseTreeProbsForSpan(spanStart + j, spanEnd);
        int numRightParseTrees = chart.getNumParseTreeKeysForSpan(spanStart + j, spanEnd);

        long spanKey = ((spanStart + i) * treeEncodingOffsets[2]) + ((spanStart + j) * treeEncodingOffsets[3]);
        for (int leftIndex = 0; leftIndex < numLeftParseTrees; leftIndex++) {
          // Parse out the parent node's index from the left tree.
          int leftRoot = (int) ((leftParseTreeKeys[leftIndex] % treeEncodingOffsets[3]) / treeEncodingOffsets[4]);
          long leftIndexPartialKey = (leftIndex * treeEncodingOffsets[0]) + spanKey;
          for (int rightIndex = 0; rightIndex < numRightParseTrees; rightIndex++) {
            int rightRoot = (int) ((rightParseTreeKeys[rightIndex] % treeEncodingOffsets[3]) / treeEncodingOffsets[4]);
            long rightIndexPartialKey = (rightIndex * treeEncodingOffsets[1]) + leftIndexPartialKey; 

            // Compute the tensor index containing any production rules
            // where leftRoot is the left nonterminal and rightRoot is the right
            // nonterminal.
            long partialKeyNum = leftRoot * dimensionOffsets[0] + rightRoot * dimensionOffsets[1];
            int startIndex = binaryDistributionWeights.getNearestIndex(partialKeyNum);
            long endKeyNum = partialKeyNum + dimensionOffsets[1];
            long startKeyNum;

            
            while (startIndex < values.length) {
              startKeyNum = binaryDistributionWeights.indexToKeyNum(startIndex);
              if (startKeyNum >= endKeyNum) { break; }
              double treeProb = leftParseTreeProbs[leftIndex] * rightParseTreeProbs[rightIndex] * values[startIndex];
              long treeKeyNum = rightIndexPartialKey + startKeyNum - partialKeyNum;
              chart.addParseTreeKeyForSpan(spanStart, spanEnd, treeKeyNum, treeProb);

              startIndex++;
            }
          }
        }
      }
    }
  }
}