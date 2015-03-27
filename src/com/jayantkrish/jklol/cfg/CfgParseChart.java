package com.jayantkrish.jklol.cfg;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

import com.google.common.base.Preconditions;
import com.jayantkrish.jklol.models.Factor;
import com.jayantkrish.jklol.models.TableFactor;
import com.jayantkrish.jklol.models.VariableNumMap;

/**
 * The "chart" of a CKY-style parser which enables efficient computations of
 * feature expectations of the parser.
 * 
 * ParseChart also enables the computation of both marginals and max-marginals
 * with a single inside-outside algorithm.
 */
public class CfgParseChart {

  // Storage for the various probability distributions.
  private Factor[][] insideChart;
  private Factor[][] outsideChart;
  private Factor[][] marginalChart;

  private VariableNumMap parentVar;
  private VariableNumMap leftVar;
  private VariableNumMap rightVar;
  private VariableNumMap terminalVar;
  private VariableNumMap ruleTypeVar;

  private Factor binaryRuleExpectations;
  private Factor terminalRuleExpectations;

  private List<?> terminals;

  private int numTerminals;
  private boolean sumProduct;
  private boolean insideCalculated;
  private boolean outsideCalculated;
  private double partitionFunction;

  /**
   * Create a parse chart with the specified number of terminal symbols.
   * 
   * If "sumProduct" is true, then updates for the same symbol add (i.e., the
   * ParseChart computes marginals). Otherwise, updates use the maximum
   * probability, meaning ParseChart computes max-marginals.
   */
  public CfgParseChart(List<?> terminals, VariableNumMap parent, VariableNumMap left, 
      VariableNumMap right, VariableNumMap terminal, VariableNumMap ruleTypeVar, 
      boolean sumProduct) {
    this.terminals = terminals;
    this.parentVar = parent;
    this.leftVar = left;
    this.rightVar = right;
    this.ruleTypeVar = ruleTypeVar;

    this.terminalVar = terminal;
    this.sumProduct = sumProduct;
    
    this.numTerminals = terminals.size();

    insideChart = new Factor[numTerminals][numTerminals];
    outsideChart = new Factor[numTerminals][numTerminals];
    marginalChart = new Factor[numTerminals][numTerminals];
    binaryRuleExpectations = TableFactor.zero(VariableNumMap.unionAll(parentVar, leftVar, rightVar, ruleTypeVar));
    terminalRuleExpectations = TableFactor.zero(VariableNumMap.unionAll(parentVar, terminalVar, ruleTypeVar));

    insideCalculated = false;
    outsideCalculated = false;
    partitionFunction = 0.0;

    // TODO: backpointers
    /*
    if (!sumProduct) {
      backpointers = (Map<Object, PriorityQueue<Backpointer>>[][]) Array.newInstance(new HashMap<Object, PriorityQueue<Backpointer>>().getClass(), new int[] { numTerminals, numTerminals });
      beamWidth = 1;
    }
    */
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

    // The first entry initializes the chart at this span.
    if (insideChart[spanStart][spanEnd] == null) {
      if (sumProduct) { 
        insideChart[spanStart][spanEnd] = binaryRuleProbabilities.marginalize(
            VariableNumMap.unionAll(leftVar, rightVar, ruleTypeVar)); 
      } else {
        insideChart[spanStart][spanEnd] = binaryRuleProbabilities.maxMarginalize(
            VariableNumMap.unionAll(leftVar, rightVar, ruleTypeVar));
        // TODO: backpointers
        /*
        backpointers[spanStart][spanEnd] = new HashMap<Object, PriorityQueue<Backpointer>>();
        */
      }
      return;
    }

    if (sumProduct) {
      insideChart[spanStart][spanEnd] = insideChart[spanStart][spanEnd].add(
          binaryRuleProbabilities.marginalize(
              VariableNumMap.unionAll(leftVar, rightVar, ruleTypeVar)));
    } else {
      insideChart[spanStart][spanEnd] = insideChart[spanStart][spanEnd].maximum(
          binaryRuleProbabilities.maxMarginalize(
              VariableNumMap.unionAll(leftVar, rightVar, ruleTypeVar)));

      // TODO: backpointers
      /*
      if (!backpointers[spanStart][spanEnd].containsKey(p)) {
        backpointers[spanStart][spanEnd].put(p, new PriorityQueue<Backpointer>());
      }
      backpointers[spanStart][spanEnd].get(p).offer(new Backpointer(splitInd, rule, prob, ruleProb));

      if (backpointers[spanStart][spanEnd].get(p).size() > beamWidth) {
        backpointers[spanStart][spanEnd].get(p).poll();
      }
      */
    }
  }

  /**
   * Update an entry of the inside chart with a new production. Depending on the
   * type of the chart, this performs either a sum or max over productions of
   * the same type in the same entry.
   */
  public void updateInsideEntryTerminal(int spanStart, int spanEnd, Factor factor) {
    Preconditions.checkArgument(factor.getVars().size() == 1);
    // The first entry initializes the chart at this span.
    if (insideChart[spanStart][spanEnd] == null) {
      if (sumProduct) {
        insideChart[spanStart][spanEnd] = factor;
      } else {
        insideChart[spanStart][spanEnd] = factor;
        // TODO: backpointers
        /*
        backpointers[spanStart][spanEnd] = new HashMap<Object, PriorityQueue<Backpointer>>();
        */
      }
      return;
    }

    if (sumProduct) {
      insideChart[spanStart][spanEnd] = insideChart[spanStart][spanEnd].add(factor);
    } else {
      insideChart[spanStart][spanEnd] = insideChart[spanStart][spanEnd].maximum(factor);

      // TODO: backpointers
      /*
      if (!backpointers[spanStart][spanEnd].containsKey(p)) {
        backpointers[spanStart][spanEnd].put(p, new PriorityQueue<Backpointer>());
      }
      backpointers[spanStart][spanEnd].get(p).offer(new Backpointer(splitInd, rule, prob, ruleProb));

      if (backpointers[spanStart][spanEnd].get(p).size() > beamWidth) {
        backpointers[spanStart][spanEnd].get(p).poll();
      }
      */
    }
  }

  /**
   * Update an entry of the outside chart with a new production. Depending on
   * the type of the chart, this performs either a sum or max over productions
   * of the same type in the same entry.
   */
  public void updateOutsideEntry(int spanStart, int spanEnd, Factor factor) {
    Preconditions.checkArgument(factor.getVars().size() == 1);
    if (outsideChart[spanStart][spanEnd] == null) {
      outsideChart[spanStart][spanEnd] = factor;
      return;
    }

    if (sumProduct) {
      outsideChart[spanStart][spanEnd] = outsideChart[spanStart][spanEnd].add(factor);
    } else {
      outsideChart[spanStart][spanEnd] = outsideChart[spanStart][spanEnd].maximum(factor);
    }
  }

  /**
   * Update an entry of the marginal chart with a new production. Depending on
   * the type of the chart, this performs either a sum or max over productions
   * of the same type in the same entry.
   */
  public void updateMarginalEntry(int spanStart, int spanEnd, Factor marginal) {
    Preconditions.checkArgument(marginal.getVars().size() == 1);
    if (marginalChart[spanStart][spanEnd] == null) {
      marginalChart[spanStart][spanEnd] = marginal;
      return;
    }

    if (sumProduct) {
      marginalChart[spanStart][spanEnd] = marginalChart[spanStart][spanEnd].add(marginal);
    } else {
      marginalChart[spanStart][spanEnd] = marginalChart[spanStart][spanEnd].maximum(marginal);
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
    if (insideChart[spanStart][spanEnd] == null) {
      return TableFactor.zero(parentVar);
    }
    return insideChart[spanStart][spanEnd];
  }

  /**
   * Get the outside unnormalized probabilities over productions at a particular
   * span in the tree.
   */
  public Factor getOutsideEntries(int spanStart, int spanEnd) {
    if (outsideChart[spanStart][spanEnd] == null) {
      return TableFactor.zero(parentVar);
    }
    return outsideChart[spanStart][spanEnd];
  }

  /**
   * Get the marginal unnormalized probabilities over productions at a
   * particular node in the tree.
   */
  public Factor getMarginalEntries(int spanStart, int spanEnd) {
    if (marginalChart[spanStart][spanEnd] == null) {
      return TableFactor.zero(parentVar);
    }

    return marginalChart[spanStart][spanEnd];
  }

  /**
   * Get the inside distribution over the productions at the root of the tree.
   */
  public Factor getRootDistribution() {
    return getInsideEntries(0, chartSize() - 1);
  }

  /**
   * Gets the {@code numBest} most probable (i.e., max-marginal) parse trees
   * given {@code rootDistribution} is the max-marginal over the root node.
   * 
   * @param rootDistribution
   * @param numTrees
   * @return
   */
  public List<CfgParseTree> getBestParseTrees(Map<Object, Double> rootDistribution, int numTrees) {
    PriorityQueue<CfgParseTree> bestTrees = new PriorityQueue<CfgParseTree>();
    for (Object root : rootDistribution.keySet()) {
      for (CfgParseTree parseTree : getBestParseTrees(root, numTrees)) {
        bestTrees.offer(parseTree.multiplyProbability(rootDistribution.get(root)));

        if (bestTrees.size() > numTrees) {
          bestTrees.poll();
        }
      }
    }

    List<CfgParseTree> bestTreeList = new ArrayList<CfgParseTree>(bestTrees);
    Collections.sort(bestTreeList);
    Collections.reverse(bestTreeList);
    return bestTreeList;
  }

  /**
   * Get the best parse trees spanning the entire sentence.
   */
  public List<CfgParseTree> getBestParseTrees(Object root, int numTrees) {
    return getBestParseTreesWithSpan(root, 0, chartSize() - 1, numTrees);
  }

  /**
   * If this tree contains max-marginals, recover the best parse subtree for a
   * given symbol with the specified span.
   */
  public List<CfgParseTree> getBestParseTreesWithSpan(Object root, int spanStart,
      int spanEnd, int numTrees) {
    /*
    assert !sumProduct;
    assert numTrees <= beamWidth;

    if (backpointers[spanStart][spanEnd] == null ||
        !backpointers[spanStart][spanEnd].containsKey(root)) {
      return Collections.emptyList();
    }

    Backpointer[] bps = backpointers[spanStart][spanEnd].get(root).toArray(new Backpointer[] {});
    Arrays.sort(bps);

    PriorityQueue<ParseTree> bestTrees = new PriorityQueue<ParseTree>();
    for (int i = bps.length - 1; i >= Math.max(0, bps.length - numTrees); i--) {
      Backpointer backpointer = bps[i];
      if (!backpointer.isTerminal()) {
        int splitInd = backpointer.getSplitInd();
        BinaryProduction rule = backpointer.getBinaryProduction();

        List<ParseTree> leftTrees = getBestParseTreesWithSpan(rule.getLeft(), spanStart, spanStart + splitInd, numTrees);
        List<ParseTree> rightTrees = getBestParseTreesWithSpan(rule.getRight(), spanStart + splitInd + 1, spanEnd, numTrees);

        for (ParseTree left : leftTrees) {
          for (ParseTree right : rightTrees) {
            bestTrees.offer(new ParseTree(left, right, rule,
                left.getProbability() * right.getProbability() * backpointer.getRuleProbability()));
          }
        }
      } else {
        // Terminal
        bestTrees.offer(new ParseTree(backpointer.getTerminalProduction(), backpointer.getProbability()));
      }
    }
    while (bestTrees.size() > numTrees) {
      bestTrees.poll();
    }

    List<ParseTree> bestTreeList = new ArrayList<ParseTree>(bestTrees);
    Collections.sort(bestTreeList);
    Collections.reverse(bestTreeList);
    return bestTreeList;
    */
    return null;
  }

  /**
   * Update the expected number of times that a binary production rule is used
   * in a parse. (This method assumes sum-product)
   */
  public void updateBinaryRuleExpectations(Factor binaryRuleMarginal) {
    binaryRuleExpectations = binaryRuleExpectations.add(binaryRuleMarginal);
  }

  /**
   * Compute the expected *unnormalized* probability of every rule.
   */
  public Factor getBinaryRuleExpectations() {
    return binaryRuleExpectations;
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

    sb.append("\nmarginal:\n");
    for (int i = 0; i < chartSize(); i++) {
      for (int j = 0; j < chartSize(); j++) {
        if (marginalChart[i][j] != null) {
          sb.append("(");
          sb.append(i);
          sb.append(",");
          sb.append(j);
          sb.append(") ");
          sb.append(marginalChart[i][j]);
          sb.append("\n");
        }
      }
    }
    return sb.toString();
  }
}