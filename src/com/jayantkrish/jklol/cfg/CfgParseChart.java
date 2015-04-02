package com.jayantkrish.jklol.cfg;

import java.util.List;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.primitives.Ints;
import com.jayantkrish.jklol.models.Factor;
import com.jayantkrish.jklol.models.TableFactor;
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.tensor.Backpointers;
import com.jayantkrish.jklol.tensor.DenseTensor;
import com.jayantkrish.jklol.tensor.SparseTensor;
import com.jayantkrish.jklol.tensor.Tensor;
import com.jayantkrish.jklol.util.Assignment;

/**
 * The "chart" of a CKY-style parser which enables efficient computations of
 * feature expectations of the parser.
 * 
 * ParseChart also enables the computation of both marginals and max-marginals
 * with a single inside-outside algorithm.
 */
public class CfgParseChart {

  // Storage for the various probability distributions.
  private double[][][] insideChart;
  private double[][][] outsideChart;

  private VariableNumMap parentVar;
  private VariableNumMap leftVar;
  private VariableNumMap rightVar;
  private VariableNumMap terminalVar;
  private VariableNumMap ruleTypeVar;

  private Factor binaryRuleDistribution;
  private double[] binaryRuleExpectations;
  private Factor terminalRuleExpectations;

  private List<?> terminals;

  private int numTerminals;
  private final int numNonterminals;
  private boolean sumProduct;
  private boolean insideCalculated;
  private boolean outsideCalculated;
  private double partitionFunction;
  
  private final long[][][] backpointers;
  private final int[][][] splitBackpointers;

  /**
   * Create a parse chart with the specified number of terminal symbols.
   * 
   * If "sumProduct" is true, then updates for the same symbol add (i.e., the
   * ParseChart computes marginals). Otherwise, updates use the maximum
   * probability, meaning ParseChart computes max-marginals.
   */
  public CfgParseChart(List<?> terminals, VariableNumMap parent, VariableNumMap left, 
      VariableNumMap right, VariableNumMap terminal, VariableNumMap ruleTypeVar,
      Factor binaryRuleDistribution, boolean sumProduct) {
    this.terminals = terminals;
    this.parentVar = parent;
    this.leftVar = left;
    this.rightVar = right;
    this.ruleTypeVar = ruleTypeVar;

    this.terminalVar = terminal;
    this.sumProduct = sumProduct;
    
    this.numTerminals = terminals.size();
    this.numNonterminals = parentVar.getDiscreteVariables().get(0).numValues();

    insideChart = new double[numTerminals][numTerminals][numNonterminals];
    outsideChart = new double[numTerminals][numTerminals][numNonterminals];
    this.binaryRuleDistribution = binaryRuleDistribution;
    binaryRuleExpectations = new double[binaryRuleDistribution.coerceToDiscrete().getWeights().getValues().length];
    terminalRuleExpectations = TableFactor.zero(VariableNumMap.unionAll(parentVar, terminalVar, ruleTypeVar));

    insideCalculated = false;
    outsideCalculated = false;
    partitionFunction = 0.0;

    if (!sumProduct) {
      backpointers = new long[numTerminals][numTerminals][numNonterminals];
      splitBackpointers = new int[numTerminals][numTerminals][numNonterminals];
    } else {
      backpointers = null;
      splitBackpointers = null;
    }
  }

  /**
   * Get the number of terminal symbols in the chart.
   */
  public int chartSize() {
    return numTerminals;
  }

  /**
   * Update an entry of the inside chart with a new production. Depending on the
   * type of the chart, this performs either a sum or max over productions of
   * the same type in the same entry.
   */
  public void updateInsideEntry(int spanStart, int spanEnd, int splitInd,
      Factor binaryRuleProbabilities) {
    Preconditions.checkArgument(binaryRuleProbabilities.getVars().size() == 4);
    
    if (sumProduct) {
      updateEntrySumProduct(insideChart[spanStart][spanEnd], binaryRuleProbabilities.coerceToDiscrete().getWeights().getValues(),
          binaryRuleProbabilities.coerceToDiscrete().getWeights(), parentVar.getOnlyVariableNum());
    } else {
      int[] dimsToRemove = VariableNumMap.unionAll(leftVar, rightVar, ruleTypeVar).getVariableNumsArray();
      Tensor weights = binaryRuleProbabilities.coerceToDiscrete().getWeights();
      
      Backpointers tensorBackpointers = new Backpointers();
      Tensor message = weights.maxOutDimensions(dimsToRemove, tensorBackpointers);

      updateInsideEntryMaxProduct(spanStart, spanEnd, message, tensorBackpointers, splitInd);
    }
  }
  
  private final void updateEntrySumProduct(double[] entries, double[] messageValues,
      Tensor message, int varNum) {
    int[] dimNums = message.getDimensionNumbers();
    int parentIndex = Ints.indexOf(dimNums, varNum);
    for (int i = 0; i < messageValues.length; i++) {
      entries[message.indexToPartialDimKey(i, parentIndex)] += messageValues[i];
    }
  }
  
  private final void updateEntryMaxProduct(double[] entries, double[] messageValues,
      Tensor message, int varNum) {
    int[] dimNums = message.getDimensionNumbers();
    int parentIndex = Ints.indexOf(dimNums, varNum);
    for (int i = 0; i < messageValues.length; i++) {
      int entryInd = (int) message.indexToPartialDimKey(i, parentIndex);
      entries[entryInd] = Math.max(messageValues[i], entries[entryInd]);
    }
  }

  private final void updateInsideEntryMaxProduct(int spanStart, int spanEnd, Tensor message,
      Backpointers tensorBackpointers, int splitInd) {
    double[] chartEntries = insideChart[spanStart][spanEnd];
    updateEntryMaxProduct(chartEntries, message.getValues(), message, parentVar.getOnlyVariableNum());

    double[] messageValues = message.getValues();
    long[] entryBackpointers = backpointers[spanStart][spanEnd];
    int[] currentSplit = splitBackpointers[spanStart][spanEnd];
    for (int i = 0; i < messageValues.length; i++) {
      int nonterminalNum = (int) message.indexToKeyNum(i);
      double curVal = chartEntries[nonterminalNum];
      double msgVal = messageValues[i];

      if (msgVal >= curVal) {
        entryBackpointers[nonterminalNum] = tensorBackpointers.getBackpointer(nonterminalNum);
        currentSplit[nonterminalNum] = splitInd;
      }
    }
  }

  /**
   * Update an entry of the inside chart with a new production. Depending on the
   * type of the chart, this performs either a sum or max over productions of
   * the same type in the same entry.
   */
  public void updateInsideEntryTerminal(int spanStart, int spanEnd, Factor factor) {
    Preconditions.checkArgument(factor.getVars().size() == 2);
    // The first entry initializes the chart at this span.
    if (sumProduct) {
      updateEntrySumProduct(insideChart[spanStart][spanEnd], factor.coerceToDiscrete().getWeights().getValues(),
          factor.coerceToDiscrete().getWeights(), parentVar.getOnlyVariableNum());
    } else {
      int[] dimsToRemove = VariableNumMap.unionAll(ruleTypeVar).getVariableNumsArray();
      Tensor weights = factor.coerceToDiscrete().getWeights();
      
      Backpointers tensorBackpointers = new Backpointers();
      Tensor message = weights.maxOutDimensions(dimsToRemove, tensorBackpointers);

      // Negative split indexes are used to represent terminal rules.
      int splitInd = -1 * (spanStart * numTerminals + spanEnd + 1); 
      updateInsideEntryMaxProduct(spanStart, spanEnd, message, tensorBackpointers, splitInd);
    }
  }

  /**
   * Update an entry of the outside chart with a new production. Depending on
   * the type of the chart, this performs either a sum or max over productions
   * of the same type in the same entry.
   */
  public void updateOutsideEntry(int spanStart, int spanEnd, double[] values, Factor factor, VariableNumMap var) {
    if (sumProduct) {
      updateEntrySumProduct(outsideChart[spanStart][spanEnd],
          values, factor.coerceToDiscrete().getWeights(), var.getOnlyVariableNum());
    } else {
      updateEntryMaxProduct(outsideChart[spanStart][spanEnd],
          values, factor.coerceToDiscrete().getWeights(), var.getOnlyVariableNum());
    }
  }

  /**
   * Gets the terminals being parsed in this chart.
   * 
   * @return
   */
  public List<?> getTerminals() {
    return terminals;
  }

  /**
   * Set the bit on the chart stating that its inside probabilities are
   * calculated.
   */
  public void setInsideCalculated() {
    insideCalculated = true;
  }

  /**
   * Get whether the chart contains valid inside probabilities
   */
  public boolean getInsideCalculated() {
    return insideCalculated;
  }

  /**
   * Set the bit on the chart stating that its outside probabilities are
   * calculated.
   */
  public void setOutsideCalculated() {
    assert insideCalculated;
    outsideCalculated = true;
  }

  /**
   * Get whether the chart contains valid outside probabilities
   */
  public boolean getOutsideCalculated() {
    return outsideCalculated;
  }

  /**
   * Returns {@code true} if this chart is computing marginals (as opposed to
   * max-marginals).
   * 
   * @return
   */
  public boolean getSumProduct() {
    return sumProduct;
  }

  /**
   * Get the inside unnormalized probabilities over productions at a particular
   * span in the tree.
   */
  public Factor getInsideEntries(int spanStart, int spanEnd) {
    Tensor entries = new DenseTensor(parentVar.getVariableNumsArray(),
        parentVar.getVariableSizes(), insideChart[spanStart][spanEnd]);
    return new TableFactor(parentVar, entries);
  }

  /**
   * Get the outside unnormalized probabilities over productions at a particular
   * span in the tree.
   */
  public Factor getOutsideEntries(int spanStart, int spanEnd) {
    Tensor entries = new DenseTensor(parentVar.getVariableNumsArray(),
        parentVar.getVariableSizes(), outsideChart[spanStart][spanEnd]);
    return new TableFactor(parentVar, entries);
  }

  /**
   * Get the marginal unnormalized probabilities over productions at a
   * particular node in the tree.
   */
  public Factor getMarginalEntries(int spanStart, int spanEnd) {
    return getOutsideEntries(spanStart, spanEnd).product(getInsideEntries(spanStart, spanEnd));
  }

  /**
   * Get the inside distribution over the productions at the root of the tree.
   */
  public Factor getRootDistribution() {
    return getInsideEntries(0, chartSize() - 1);
  }

  /**
   * Get the best parse tree spanning the entire sentence.
   */
  public CfgParseTree getBestParseTree(Object root) {
    return getBestParseTreeWithSpan(root, 0, chartSize() - 1);
  }
  
  /**
   * If this tree contains max-marginals, recover the best parse subtree for a
   * given symbol with the specified span.
   */
  public CfgParseTree getBestParseTreeWithSpan(Object root, int spanStart,
      int spanEnd) {
    Preconditions.checkState(!sumProduct);
    // System.out.println(root);

    Assignment rootAssignment = parentVar.outcomeArrayToAssignment(root); 
    int rootNonterminalNum = parentVar.assignmentToIntArray(rootAssignment)[0];
    double prob = insideChart[spanStart][spanEnd][rootNonterminalNum] 
        * outsideChart[spanStart][spanEnd][rootNonterminalNum];
    
    if (prob == 0.0) {
      return null;
    }
    
    int splitInd = splitBackpointers[spanStart][spanEnd][rootNonterminalNum];
    if (splitInd < 0) {
      long terminalKey = backpointers[spanStart][spanEnd][rootNonterminalNum];
      
      // This is a really sucky way to transform the keys back to objects.
      VariableNumMap vars = parentVar.union(ruleTypeVar);
      int[] dimKey = TableFactor.zero(vars).getWeights().keyNumToDimKey(terminalKey);
      Assignment a = vars.intArrayToAssignment(dimKey);
      Object ruleType = a.getValue(ruleTypeVar.getOnlyVariableNum());
      
      List<Object> terminalList = Lists.newArrayList();
      terminalList.addAll(terminals.subList(spanStart, spanStart + 1));
      return new CfgParseTree(root, ruleType, terminalList, prob, spanStart, spanEnd);
    } else {
      long binaryRuleKey = backpointers[spanStart][spanEnd][rootNonterminalNum];
      int[] binaryRuleComponents = binaryRuleDistribution.coerceToDiscrete()
          .getWeights().keyNumToDimKey(binaryRuleKey);

      Assignment best = binaryRuleDistribution.getVars().intArrayToAssignment(binaryRuleComponents);
      Object leftRoot = best.getValue(leftVar.getOnlyVariableNum());
      Object rightRoot = best.getValue(rightVar.getOnlyVariableNum());
      Object ruleType = best.getValue(ruleTypeVar.getOnlyVariableNum());

      CfgParseTree leftTree = getBestParseTreeWithSpan(leftRoot, spanStart, spanStart + splitInd);
      CfgParseTree rightTree = getBestParseTreeWithSpan(rightRoot, spanStart + splitInd + 1, spanEnd);
      
      Preconditions.checkState(leftTree != null);
      Preconditions.checkState(rightTree != null);
      
      return new CfgParseTree(root, ruleType, leftTree, rightTree, prob);
    }
  }

  /**
   * Update the expected number of times that a binary production rule is used
   * in a parse. (This method assumes sum-product)
   */
  public void updateBinaryRuleExpectations(double[] binaryRuleMarginal) {
    for (int i = 0; i < binaryRuleExpectations.length; i++) {
      binaryRuleExpectations[i] += binaryRuleMarginal[i];
    }
  }

  /**
   * Compute the expected *unnormalized* probability of every rule.
   */
  public Factor getBinaryRuleExpectations() {
    Tensor binaryRuleWeights = binaryRuleDistribution.coerceToDiscrete().getWeights();
    SparseTensor tensor = SparseTensor.copyRemovingZeros(binaryRuleWeights,
        binaryRuleExpectations);

    return new TableFactor(binaryRuleDistribution.getVars(), tensor);
  }

  /**
   * Update the expected number of times that a terminal production rule is used
   * in the parse.
   */
  public void updateTerminalRuleExpectations(Factor terminalRuleMarginal) {
    terminalRuleExpectations = terminalRuleExpectations.add(terminalRuleMarginal);
  }

  /**
   * Compute the expected *unnormalized* probability of every terminal rule.
   */
  public Factor getTerminalRuleExpectations() {
    return terminalRuleExpectations;
  }

  /**
   * Set the normalizing constant for computing node marginals / rule
   * expectations.
   */
  public void setPartitionFunction(double normalizingConstant) {
    this.partitionFunction = normalizingConstant;
  }

  /**
   * Get the partition function for creating normalized probabilities.
   */
  public double getPartitionFunction() {
    return partitionFunction;
  }

  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("inside:\n");
    for (int i = 0; i < chartSize(); i++) {
      for (int j = 0; j < chartSize(); j++) {
        if (insideChart[i][j] != null) {
          sb.append("(");
          sb.append(i);
          sb.append(",");
          sb.append(j);
          sb.append(") ");
          sb.append(insideChart[i][j]);
          sb.append("\n");
        }
      }
    }

    sb.append("\noutside:\n");
    for (int i = 0; i < chartSize(); i++) {
      for (int j = 0; j < chartSize(); j++) {
        if (outsideChart[i][j] != null) {
          sb.append("(");
          sb.append(i);
          sb.append(",");
          sb.append(j);
          sb.append(") ");
          sb.append(outsideChart[i][j]);
          sb.append("\n");
        }
      }
    }

    return sb.toString();
  }
}