package com.jayantkrish.jklol.cfg;

import java.io.Serializable;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.primitives.Ints;
import com.jayantkrish.jklol.models.DiscreteFactor;
import com.jayantkrish.jklol.models.DiscreteFactor.Outcome;
import com.jayantkrish.jklol.models.DiscreteVariable;
import com.jayantkrish.jklol.models.Factor;
import com.jayantkrish.jklol.models.TableFactor;
import com.jayantkrish.jklol.models.Variable;
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.tensor.Tensor;
import com.jayantkrish.jklol.util.Assignment;
import com.jayantkrish.jklol.util.HeapUtils;

/**
 * A CKY-style parser for probabilistic context free grammars in Chomsky normal
 * form (with a multi-terminal production extension).
 */
public class CfgParser implements Serializable {

  private static final long serialVersionUID = 8780756461990735127L;
  
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

  // The distributions over terminals, binary rules, and the root symbol.
  private final DiscreteFactor rootDistribution;
  private final DiscreteFactor binaryDistribution;
  private final DiscreteFactor terminalDistribution;

  // The parser uses the tensor representations of the nonterminal distributions
  // in order to improve parsing speed.
  private final Tensor binaryDistributionWeights;

  // If true, the parser is allowed to skip portions of the terminal symbols
  // during parsing.
  private final boolean canSkipTerminals;
  private final Assignment skipSymbol;

  /**
   * If {@code beamSize > 0}, this parser will initialize and cache information
   * for performing beam searches over parse trees. The given beamSize will then
   * be used for all invocations of {@link #beamSearch}.
   * 
   * @param parentVar
   * @param leftVar
   * @param rightVar
   * @param terminalVar variable over *lists* of terminal symbols. 
   * @param ruleTypeVar
   * @param rootDistribution
   * @param binaryDistribution
   * @param terminalDistribution
   * @param canSkipTerminals
   * @param skipSymbol
   */
  public CfgParser(VariableNumMap parentVar, VariableNumMap leftVar, VariableNumMap rightVar,
      VariableNumMap terminalVar, VariableNumMap ruleTypeVar, DiscreteFactor rootDistribution,
      DiscreteFactor binaryDistribution, DiscreteFactor terminalDistribution,
      boolean canSkipTerminals, Assignment skipSymbol) {
    Preconditions.checkArgument(parentVar.size() == 1 && leftVar.size() == 1
        && rightVar.size() == 1 && terminalVar.size() == 1 && ruleTypeVar.size() == 1);
    Preconditions.checkArgument(rootDistribution.getVars().equals(VariableNumMap.unionAll(
        parentVar)));
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
    this.rootDistribution = rootDistribution;
    this.binaryDistribution = binaryDistribution;
    this.terminalDistribution = terminalDistribution;

    this.ruleVariableType = ruleTypeVar.getDiscreteVariables().get(0);
    this.nonterminalVariableType = parentVar.getDiscreteVariables().get(0);
    this.binaryDistributionWeights = binaryDistribution.getWeights();

    this.canSkipTerminals = canSkipTerminals;
    this.skipSymbol = skipSymbol;
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
  
  public VariableNumMap getTerminalVariable() {
    return terminalVar;
  }

  // //////////////////////////////////////////////////////////////////////
  // the following methods are the important ones for running the parser in
  // isolation.
  // //////////////////////////////////////////////////////////////////////

  /**
   * Compute the marginal distribution over all grammar entries conditioned on
   * the given sequence of terminals.
   */
  public CfgParseChart parseMarginal(List<?> terminals, Object root, boolean useSumProduct) {
    CfgParseChart chart = createParseChart(terminals, useSumProduct);
    Factor rootFactor = TableFactor.pointDistribution(parentVar,
        parentVar.outcomeArrayToAssignment(root));
    return marginal(chart, terminals, rootFactor);
  }
  
  /**
   * Computes the marginal distribution over CFG parses given terminals.
   * 
   * @param terminals
   * @param useSumProduct
   * @return
   */
  public CfgParseChart parseMarginal(List<?> terminals, boolean useSumProduct) {
    Factor rootDist = TableFactor.unity(parentVar);
    return parseMarginal(terminals, rootDist, useSumProduct);
  }

  /**
   * Compute the distribution over CFG entries, the parse root, and the
   * children, conditioned on the provided terminals and assuming the provided
   * distributions over the root node.
   */
  public CfgParseChart parseMarginal(List<?> terminals, Factor rootDist, boolean useSumProduct) {
    return marginal(createParseChart(terminals, useSumProduct), terminals, rootDist);
  }

  /**
   * Performs a beam search over parse trees maintaining up to
   * {@code beamSize} trees at each node of the parse tree. This
   * method can be used to approximate the marginal distribution over parse
   * trees. The returned list of trees is sorted from most to least probable.
   * 
   * @param terminals
   * @param beamSize
   * @return
   */
  @SuppressWarnings("unchecked")
  public List<CfgParseTree> beamSearch(List<?> terminals, int beamSize) {
    if (terminals.size() == 0) {
      // Zero size inputs occur because unknown words may be automatically skipped.
      return Collections.emptyList();
    }
    
    BeamSearchCfgParseChart chart = new BeamSearchCfgParseChart((List<Object>) terminals, beamSize);

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
     * int numTruncatedEntries = 0; for (int spanSize = 0; spanSize <
     * chart.chartSize() - 1; spanSize++) { for (int spanStart = 0; spanStart +
     * spanSize < chart.chartSize(); spanStart++) { if
     * (chart.getNumParseTreeKeysForSpan(spanStart, spanStart + spanSize) ==
     * beamSize) { numTruncatedEntries++; } } } System.out.println("Truncated "
     * + numTruncatedEntries + " chart entries");
     */

    // Map the integer encodings of trees back to tree objects.
    List<CfgParseTree> trees;
    trees = Lists.newArrayList();
    int numRootTrees = chart.getNumParseTreeKeysForSpan(0, terminals.size() - 1);
    long[] rootKeys = chart.getParseTreeKeysForSpan(0, terminals.size() - 1);
    double[] rootProbs = chart.getParseTreeProbsForSpan(0, terminals.size() - 1);
    for (int i = 0; i < numRootTrees; i++) {
      CfgParseTree tree = mapTreeKeyToParseTree(rootKeys[i], rootProbs[i], 0, terminals.size() - 1, chart, treeKeyOffsets);
      tree = tree.multiplyProbability(rootDistribution.getUnnormalizedProbability(tree.getRoot()));
      trees.add(tree);
    }

    Collections.sort(trees);
    Collections.reverse(trees);
    return trees;
  }

  private List<CfgParseTree> populateParseTreesFromChartSkippingTerminals(BeamSearchCfgParseChart chart,
      long[] treeEncodingOffsets, int beamSize) {
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
    List<CfgParseTree> trees = Lists.newArrayList();
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
    trees.add(CfgParseTree.EMPTY);
    return trees;
  }

  private CfgParseTree mapTreeKeyToParseTree(long treeKey, double treeProb, int spanStart, int spanEnd,
      BeamSearchCfgParseChart chart, long[] treeEncodingOffsets) {
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
      return new CfgParseTree(rootObj, ruleObj, chart.getTerminals().subList(spanStart, spanEnd + 1), treeProb,
          spanStart, spanEnd);
    } else {
      // Tree is a nonterminal. Identify the left and right subtrees by decoding
      // the current key.
      long leftTreeKey = chart.getParseTreeKeysForSpan(spanStart, leftSplitIndex)[leftSubtreeBeamIndex];
      double leftProb = chart.getParseTreeProbsForSpan(spanStart, leftSplitIndex)[leftSubtreeBeamIndex];
      long rightTreeKey = chart.getParseTreeKeysForSpan(rightSplitIndex, spanEnd)[rightSubtreeBeamIndex];
      double rightProb = chart.getParseTreeProbsForSpan(rightSplitIndex, spanEnd)[rightSubtreeBeamIndex];

      return new CfgParseTree(rootObj, ruleObj,
          mapTreeKeyToParseTree(leftTreeKey, leftProb, spanStart, leftSplitIndex, chart, treeEncodingOffsets),
          mapTreeKeyToParseTree(rightTreeKey, rightProb, rightSplitIndex, spanEnd, chart, treeEncodingOffsets),
          treeProb);
    }
  }

  /**
   * Gets the probability of {@code tree}.
   * 
   * @param tree
   * @return
   */
  public double getProbability(CfgParseTree tree) {
    double ruleProbability = getProbabilityHelper(tree);
    double rootProbability = rootDistribution.getUnnormalizedProbability(tree.getRoot());
    return ruleProbability * rootProbability;
  }

  private double getProbabilityHelper(CfgParseTree tree) {
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
  // running the CFG parser as part of a graphical model.
  // ////////////////////////////////////////////////////////////////////////////////

  /**
   * Calculate the inside probabilities (i.e., run the upward pass of variable
   * elimination).
   */
  public CfgParseChart parseInsideMarginal(List<?> terminals, boolean useSumProduct) {
    CfgParseChart chart = createParseChart(terminals, useSumProduct);
    initializeChart(chart, terminals);
    upwardChartPass(chart);
    return chart;
  }

  public CfgParseChart parseOutsideMarginal(CfgParseChart chart, Factor rootDist) {
    Preconditions.checkState(chart.getInsideCalculated(), "Inside probabilities not calculated.");
    Preconditions.checkState(!chart.getOutsideCalculated(), "Outside probabilities already calculated.");

    Factor root = rootDistribution.product(rootDist);
    chart.updateOutsideEntry(0, chart.chartSize() - 1, root.coerceToDiscrete().getWeights().getValues(), 
        root, parentVar);
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
  private CfgParseChart createParseChart(List<?> terminals, boolean useSumProduct) {
    return new CfgParseChart(terminals, parentVar, leftVar, rightVar, terminalVar, ruleTypeVar,
        binaryDistribution, useSumProduct);
  }

  /*
   * Helper method for computing marginals / max-marginals with an arbitrary
   * distribution on terminals.
   */
  private CfgParseChart marginal(CfgParseChart chart, List<?> terminals,
      Factor rootDist) {
    initializeChart(chart, terminals);
    upwardChartPass(chart);
    // Set the initial outside probabilities
    Factor root = rootDistribution.product(rootDist);
    chart.updateOutsideEntry(0, chart.chartSize() - 1, 
        root.coerceToDiscrete().getWeights().getValues(), root, parentVar);
    downwardChartPass(chart);
    return chart;
  }

  /*
   * This method calculates all of the inside probabilities by iteratively
   * parsing larger and larger spans of the sentence.
   */
  private void upwardChartPass(CfgParseChart chart) {
    double[] newValues = new double[binaryDistributionWeights.getValues().length];

    // spanSize is the number of words *in addition* to the word under
    // spanStart.
    for (int spanSize = 1; spanSize < chart.chartSize(); spanSize++) {
      for (int spanStart = 0; spanStart + spanSize < chart.chartSize(); spanStart++) {
        int spanEnd = spanStart + spanSize;
        calculateInside(spanStart, spanEnd, chart, newValues);
      }
    }
    chart.setInsideCalculated();
  }

  /*
   * Calculate a single inside probability entry.
   */
  private void calculateInside(int spanStart, int spanEnd, CfgParseChart chart, double[] newValues) {
    double[] binaryRuleValues = binaryDistributionWeights.getValues();
    
    int leftIndex = Ints.indexOf(binaryDistributionWeights.getDimensionNumbers(), leftVar.getOnlyVariableNum());
    int rightIndex = Ints.indexOf(binaryDistributionWeights.getDimensionNumbers(), rightVar.getOnlyVariableNum());
    
    for (int i = 0; i < spanEnd - spanStart; i++) {
      double[] left = chart.getInsideEntriesArray(spanStart, spanStart + i);
      double[] right = chart.getInsideEntriesArray(spanStart + i + 1, spanEnd);

      for (int j = 0; j < newValues.length; j++) {
        newValues[j] = binaryRuleValues[j] * left[binaryDistributionWeights.indexToPartialDimKey(j, leftIndex)]
            * right[binaryDistributionWeights.indexToPartialDimKey(j, rightIndex)];
      }

      chart.updateInsideEntry(spanStart, spanEnd, i, newValues, binaryDistribution);
    }
  }

  /*
   * Compute the outside probabilities moving downward from the top of the tree.
   */
  private void downwardChartPass(CfgParseChart chart) {
    Preconditions.checkState(chart.getInsideCalculated());

    // Calculate root marginal, which is not included in the rest of the pass.
    // Also compute the partition function.
    Factor rootOutside = chart.getOutsideEntries(0, chart.chartSize() - 1);
    Factor rootInside = chart.getInsideEntries(0, chart.chartSize() - 1);
    Factor rootMarginal = rootOutside.product(rootInside);
    chart.setPartitionFunction(rootMarginal.marginalize(parentVar).getUnnormalizedProbability(
        Assignment.EMPTY));

    double[] newValues = new double[binaryDistributionWeights.getValues().length];
    for (int spanSize = chart.chartSize() - 1; spanSize >= 1; spanSize--) {
      for (int spanStart = 0; spanStart + spanSize < chart.chartSize(); spanStart++) {
        int spanEnd = spanStart + spanSize;
        calculateOutside(spanStart, spanEnd, chart, newValues);
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
  private void calculateOutside(int spanStart, int spanEnd, CfgParseChart chart, double[] newValues) {
    Factor parentOutside = chart.getOutsideEntries(spanStart, spanEnd);
    Tensor parentWeights = parentOutside.coerceToDiscrete().getWeights();

    Tensor binaryDistributionWeights = binaryDistribution.coerceToDiscrete().getWeights();
    int parentIndex = Ints.indexOf(binaryDistributionWeights.getDimensionNumbers(), parentVar.getOnlyVariableNum());
    int leftIndex = Ints.indexOf(binaryDistributionWeights.getDimensionNumbers(), leftVar.getOnlyVariableNum());
    int rightIndex = Ints.indexOf(binaryDistributionWeights.getDimensionNumbers(), rightVar.getOnlyVariableNum());

    int length = newValues.length;
    double[] binaryDistributionValues = binaryDistributionWeights.getValues();
    for (int i = 0; i < spanEnd - spanStart; i++) {
      double[] leftInside = chart.getInsideEntriesArray(spanStart, spanStart + i);
      double[] rightInside = chart.getInsideEntriesArray(spanStart + i + 1, spanEnd);

      for (int j = 0; j < length; j++) {
        newValues[j] = binaryDistributionValues[j] * parentWeights.get(binaryDistributionWeights.indexToPartialDimKey(j, parentIndex));
        newValues[j] *= rightInside[binaryDistributionWeights.indexToPartialDimKey(j, rightIndex)];
      }
      chart.updateOutsideEntry(spanStart, spanStart + i, newValues, binaryDistribution, leftVar);
      
      for (int j = 0; j < length; j++) {
        newValues[j] *= leftInside[binaryDistributionWeights.indexToPartialDimKey(j, leftIndex)];
      }
      chart.updateBinaryRuleExpectations(newValues);

      for (int j = 0; j < length; j++) {
        if (newValues[j] != 0.0) {
          newValues[j] = newValues[j] / rightInside[binaryDistributionWeights.indexToPartialDimKey(j, rightIndex)];
        }
      }
      chart.updateOutsideEntry(spanStart + i + 1, spanEnd, newValues, binaryDistribution, rightVar);
    }
  }

  /*
   * Fill in the initial chart entries implied by the given set of terminals.
   */
  private void initializeChart(CfgParseChart chart, List<?> terminals) {
    Variable terminalListValue = terminalVar.getOnlyVariable();

    // Calcuate the probability of skipping each individual word,
    // if that operation is permitted.
    double[] skipProbs = null;
    if (canSkipTerminals) {
      skipProbs = calculateSkipProbabilities(terminals);
    }

    for (int i = 0; i < terminals.size(); i++) {
      for (int j = i; j < terminals.size(); j++) {
        if (terminalListValue.canTakeValue(terminals.subList(i, j + 1))) {
          Assignment assignment = terminalVar.outcomeArrayToAssignment(terminals.subList(i, j + 1));

          DiscreteFactor terminalRules = terminalDistribution.conditional(assignment);
          chart.updateInsideEntryTerminal(i, j, i, j, terminalRules);
          
          if (canSkipTerminals) {
            if (i != 0) {
              DiscreteFactor skipTerminals = getWordSkipTerminalDistribution(terminalRules, 0, j,
                  i, j, skipProbs);
              chart.updateInsideEntryTerminal(0, j, i, j, skipTerminals);
            }

            for (int k = j + 1; k < terminals.size(); k++) {
              DiscreteFactor skipTerminals = getWordSkipTerminalDistribution(terminalRules, i, k,
                  i, j, skipProbs);
              chart.updateInsideEntryTerminal(i, k, i, j, skipTerminals);
              if (i != 0) {
                skipTerminals = getWordSkipTerminalDistribution(terminalRules, 0, k,
                  i, j, skipProbs);
                chart.updateInsideEntryTerminal(0, k, i, j, skipTerminals);
              }
            }
          }
        }
      }
    }
  }
  
  private DiscreteFactor getWordSkipTerminalDistribution(DiscreteFactor terminalConditional,
      int spanStart, int spanEnd, int terminalSpanStart, int terminalSpanEnd, double[] skipProbs) {
    terminalConditional = terminalConditional.add(terminalConditional.product(
        TableFactor.pointDistribution(parentVar.union(ruleTypeVar), skipSymbol).product(-1.0)));

    double prob = 1.0;
    for (int i = spanStart; i < terminalSpanStart; i++) {
      prob *= skipProbs[i];
    }
    
    for (int i = terminalSpanEnd + 1; i <= spanEnd; i++) {
      prob *= skipProbs[i];
    }
    
    return terminalConditional.product(prob);
  }
  
  private double[] calculateSkipProbabilities(List<?> terminals) {
    double[] probs = new double[terminals.size()];
    for (int i = 0; i < terminals.size(); i++) {
      Assignment assignment = terminalVar.outcomeArrayToAssignment(terminals.subList(i, i + 1));
      probs[i] = terminalDistribution.getUnnormalizedProbability(assignment.union(skipSymbol));
    }
    return probs;
  }

  private void updateTerminalRuleCounts(CfgParseChart chart) {
    Variable terminalListValue = terminalVar.getOnlyVariable();
    List<?> terminals = chart.getTerminals();
    
    // Calcuate the probability of skipping each individual word,
    // if that operation is permitted.
    double[] skipProbs = null;
    if (canSkipTerminals) {
      skipProbs = calculateSkipProbabilities(terminals);
    }

    for (int i = 0; i < terminals.size(); i++) {
      for (int j = i; j < terminals.size(); j++) {
        if (terminalListValue.canTakeValue(terminals.subList(i, j + 1))) {
          Assignment assignment = terminalVar.outcomeArrayToAssignment(terminals
              .subList(i, j + 1));

          DiscreteFactor terminalRuleConditional = terminalDistribution.product(
              TableFactor.pointDistribution(terminalVar, assignment));
          Factor terminalRuleMarginal = terminalRuleConditional.product(chart.getOutsideEntries(i, j));
          chart.updateTerminalRuleExpectations(terminalRuleMarginal);
          
          if (canSkipTerminals) {
            if (i != 0) {
              updateTerminalRuleCountsWordSkip(terminalRuleConditional, chart, terminals, 0, j, i, j, skipProbs);
            }

            for (int k = j + 1; k < terminals.size(); k++) {
              updateTerminalRuleCountsWordSkip(terminalRuleConditional, chart, terminals, i, k, i, j, skipProbs);
              if (i != 0) {
                updateTerminalRuleCountsWordSkip(terminalRuleConditional, chart, terminals, 0, k, i, j, skipProbs);
              }
            }
          }
        }
      }
    }
  }
  
  private void updateTerminalRuleCountsWordSkip(DiscreteFactor terminalConditional, CfgParseChart chart,
      List<?> terminals, int spanStart, int spanEnd, int terminalSpanStart, int terminalSpanEnd,
      double[] skipProbs) {
    DiscreteFactor skipConditional = getWordSkipTerminalDistribution(terminalConditional,
        spanStart, spanEnd, terminalSpanStart, terminalSpanEnd, skipProbs);
    DiscreteFactor terminalRuleMarginal = skipConditional.product(chart.getOutsideEntries(spanStart, spanEnd));
    chart.updateTerminalRuleExpectations(terminalRuleMarginal);
    
    double skipProb = terminalRuleMarginal.getTotalUnnormalizedProbability();

    VariableNumMap terminalVars = VariableNumMap.unionAll(terminalVar, parentVar, ruleTypeVar);
    for (int i = spanStart; i < terminalSpanStart; i++) {
      Assignment terminalSkipAssignment = terminalVar.outcomeArrayToAssignment(terminals.subList(i, i + 1))
          .union(skipSymbol);
      chart.updateTerminalRuleExpectations(TableFactor.pointDistribution(
          terminalVars, terminalSkipAssignment).product(skipProb));
    }

    for (int i = terminalSpanEnd + 1; i <= spanEnd; i++) {
      Assignment terminalSkipAssignment = terminalVar.outcomeArrayToAssignment(terminals.subList(i, i + 1))
          .union(skipSymbol);
      chart.updateTerminalRuleExpectations(TableFactor.pointDistribution(
          terminalVars, terminalSkipAssignment).product(skipProb));
    }
  }

  /**
   * Initializes a beam search chart using the terminal production distribution.
   * 
   * @param terminals
   * @param chart
   */
  private void initializeBeamSearchChart(List<Object> terminals, BeamSearchCfgParseChart chart,
      long[] treeEncodingOffsets) {
    Variable terminalListValue = terminalVar.getOnlyVariable();

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
  private void calculateInsideBeam(int spanStart, int spanEnd, BeamSearchCfgParseChart chart,
      long[] treeEncodingOffsets) {
    // For efficiency, precompute values which are used repeatedly in the loop
    // below.
    double[] values = binaryDistributionWeights.getValues();
    long[] dimensionOffsets = binaryDistributionWeights.getDimensionOffsets();

    for (int i = 0; i < spanEnd - spanStart; i++) {
      for (int j = i + 1; j < (canSkipTerminals ? 1 + spanEnd - spanStart : i + 2); j++) {

        // These variables store the (integer encoded) parse trees from the two
        // subspans.
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
              if (startKeyNum >= endKeyNum) {
                break;
              }
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