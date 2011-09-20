package com.jayantkrish.jklol.cfg;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

import com.google.common.collect.Maps;
import com.jayantkrish.jklol.util.DefaultHashMap;

/**
 * The "chart" of a CKY-style parser which enables efficient computations of
 * feature expectations of the parser.
 * 
 * ParseChart also enables the computation of both marginals and max-marginals
 * with a single inside-outside algorithm.
 */
public class ParseChart {

  // Storage for the various probability distributions.
  private DefaultHashMap<Production, Double>[][] insideChart;
  private DefaultHashMap<Production, Double>[][] outsideChart;
  private DefaultHashMap<Production, Double>[][] marginalChart;

  private Map<Production, PriorityQueue<Backpointer>>[][] backpointers;

  private DefaultHashMap<BinaryProduction, Double> binaryRuleExpectations;
  private DefaultHashMap<TerminalProduction, Double> terminalRuleExpectations;

  private Map<List<Production>, Double> terminalDist;;

  private int numTerminals;
  private int beamWidth;
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
  @SuppressWarnings({ "unchecked" })
  public ParseChart(int numTerminals, boolean sumProduct) {
    insideChart = (DefaultHashMap<Production, Double>[][]) Array.newInstance(new DefaultHashMap<Production, Double>(0.0).getClass(), new int[] { numTerminals, numTerminals });
    outsideChart = (DefaultHashMap<Production, Double>[][]) Array.newInstance(new DefaultHashMap<Production, Double>(0.0).getClass(), new int[] { numTerminals, numTerminals });
    marginalChart = (DefaultHashMap<Production, Double>[][]) Array.newInstance(new DefaultHashMap<Production, Double>(0.0).getClass(), new int[] { numTerminals, numTerminals });
    binaryRuleExpectations = new DefaultHashMap<BinaryProduction, Double>(0.0);
    terminalRuleExpectations = new DefaultHashMap<TerminalProduction, Double>(0.0);

    terminalDist = null;

    this.sumProduct = sumProduct;
    this.numTerminals = numTerminals;
    insideCalculated = false;
    outsideCalculated = false;
    partitionFunction = 0.0;

    if (!sumProduct) {
      backpointers = (Map<Production, PriorityQueue<Backpointer>>[][]) Array.newInstance(new HashMap<Production, PriorityQueue<Backpointer>>().getClass(), new int[] { numTerminals, numTerminals });
      beamWidth = 1;
    }
  }

  /**
   * Get the number of terminal symbols in the chart.
   */
  public int chartSize() {
    return numTerminals;
  }

  /**
   * If the chart represents the computation of max-marginals, set the width of
   * the beam used in the beam search for best parses.
   */
  public void setBeamWidth(int width) {
    assert !sumProduct;
    beamWidth = width;
  }

  /**
   * Update an entry of the inside chart with a new production. Depending on the
   * type of the chart, this performs either a sum or max over productions of
   * the same type in the same entry.
   */
  public void updateInsideEntry(int spanStart, int spanEnd, int splitInd,
      BinaryProduction rule, Production p, double prob, double ruleProb) {

    if (insideChart[spanStart][spanEnd] == null) {
      insideChart[spanStart][spanEnd] = new DefaultHashMap<Production, Double>(0.0);
      if (!sumProduct) {
        backpointers[spanStart][spanEnd] = new HashMap<Production, PriorityQueue<Backpointer>>();
      }
    }

    if (sumProduct) {
      insideChart[spanStart][spanEnd].put(p, insideChart[spanStart][spanEnd].get(p) + prob);
    } else {
      insideChart[spanStart][spanEnd].put(p, Math.max(prob, insideChart[spanStart][spanEnd].get(p)));

      if (!backpointers[spanStart][spanEnd].containsKey(p)) {
        backpointers[spanStart][spanEnd].put(p, new PriorityQueue<Backpointer>());
      }
      backpointers[spanStart][spanEnd].get(p).offer(new Backpointer(splitInd, rule, prob, ruleProb));

      if (backpointers[spanStart][spanEnd].get(p).size() > beamWidth) {
        backpointers[spanStart][spanEnd].get(p).poll();
      }
    }
  }

  /**
   * Update an entry of the inside chart with a new production. Depending on the
   * type of the chart, this performs either a sum or max over productions of
   * the same type in the same entry.
   */
  public void updateInsideEntryTerminal(int spanStart, int spanEnd,
      TerminalProduction rule, Production p, double prob, double ruleProb) {

    if (insideChart[spanStart][spanEnd] == null) {
      insideChart[spanStart][spanEnd] = new DefaultHashMap<Production, Double>(0.0);
      if (!sumProduct) {
        backpointers[spanStart][spanEnd] = new HashMap<Production, PriorityQueue<Backpointer>>();
      }
    }

    if (sumProduct) {
      insideChart[spanStart][spanEnd].put(p, insideChart[spanStart][spanEnd].get(p) + prob);
    } else {
      insideChart[spanStart][spanEnd].put(p, Math.max(prob, insideChart[spanStart][spanEnd].get(p)));

      if (!backpointers[spanStart][spanEnd].containsKey(p)) {
        backpointers[spanStart][spanEnd].put(p, new PriorityQueue<Backpointer>());
      }
      backpointers[spanStart][spanEnd].get(p).offer(new Backpointer(rule, prob, ruleProb));

      if (backpointers[spanStart][spanEnd].get(p).size() > beamWidth) {
        backpointers[spanStart][spanEnd].get(p).poll();
      }
    }
  }

  /**
   * Update an entry of the outside chart with a new production. Depending on
   * the type of the chart, this performs either a sum or max over productions
   * of the same type in the same entry.
   */
  public void updateOutsideEntry(int spanStart, int spanEnd, Production p, double prob) {
    if (outsideChart[spanStart][spanEnd] == null) {
      outsideChart[spanStart][spanEnd] = new DefaultHashMap<Production, Double>(0.0);
    }

    if (sumProduct) {
      outsideChart[spanStart][spanEnd].put(p, outsideChart[spanStart][spanEnd].get(p) + prob);
    } else {
      outsideChart[spanStart][spanEnd].put(p, Math.max(outsideChart[spanStart][spanEnd].get(p), prob));
    }
  }

  /**
   * Update an entry of the marginal chart with a new production. Depending on
   * the type of the chart, this performs either a sum or max over productions
   * of the same type in the same entry.
   */
  public void updateMarginalEntry(int spanStart, int spanEnd, Production p, double prob) {
    if (marginalChart[spanStart][spanEnd] == null) {
      marginalChart[spanStart][spanEnd] = new DefaultHashMap<Production, Double>(0.0);
    }

    if (sumProduct) {
      marginalChart[spanStart][spanEnd].put(p, marginalChart[spanStart][spanEnd].get(p) + prob);
    } else {
      marginalChart[spanStart][spanEnd].put(p, Math.max(marginalChart[spanStart][spanEnd].get(p), prob));
    }
  }

  /**
   * Set the distribution over terminals that was used to initialize this chart.
   */
  public void setTerminalDist(Map<List<Production>, Double> terminalDist) {
    this.terminalDist = terminalDist;
  }

  /**
   * Gets the distribution over terminals that was used to initialize this
   * chart.
   * 
   * @return
   */
  public Map<List<Production>, Double> getTerminalDist() {
    return terminalDist;
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
   * Get the inside unnormalized probabilities over productions at a particular
   * span in the tree.
   */
  public Map<Production, Double> getInsideEntries(int spanStart, int spanEnd) {
    if (insideChart[spanStart][spanEnd] == null) {
      return Collections.emptyMap();
    }
    return insideChart[spanStart][spanEnd].getBaseMap();
  }

  /**
   * Get the outside unnormalized probabilities over productions at a particular
   * span in the tree.
   */
  public Map<Production, Double> getOutsideEntries(int spanStart, int spanEnd) {
    if (outsideChart[spanStart][spanEnd] == null) {
      return Collections.emptyMap();
    }
    return outsideChart[spanStart][spanEnd].getBaseMap();
  }

  /**
   * Get the marginal unnormalized probabilities over productions at a
   * particular node in the tree.
   */
  public Map<Production, Double> getMarginalEntries(int spanStart, int spanEnd) {
    if (marginalChart[spanStart][spanEnd] == null) {
      return Collections.emptyMap();
    }

    return marginalChart[spanStart][spanEnd].getBaseMap();
  }

  /**
   * Get the inside distribution over the productions at the root of the tree.
   */
  public Map<Production, Double> getRootDistribution() {
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
  public List<ParseTree> getBestParseTrees(Map<Production, Double> rootDistribution, int numTrees) {
    PriorityQueue<ParseTree> bestTrees = new PriorityQueue<ParseTree>();
    for (Production root : rootDistribution.keySet()) {
      for (ParseTree parseTree : getBestParseTrees(root, numTrees)) {
        bestTrees.offer(parseTree.multiplyProbability(rootDistribution.get(root)));

        if (bestTrees.size() > numTrees) {
          bestTrees.poll();
        }
      }
    }

    List<ParseTree> bestTreeList = new ArrayList<ParseTree>(bestTrees);
    Collections.sort(bestTreeList);
    Collections.reverse(bestTreeList);
    return bestTreeList;
  }

  /**
   * Get the best parse trees spanning the entire sentence.
   */
  public List<ParseTree> getBestParseTrees(Production root, int numTrees) {
    return getBestParseTreesWithSpan(root, 0, chartSize() - 1, numTrees);
  }

  /**
   * If this tree contains max-marginals, recover the best parse subtree for a
   * given symbol with the specified span.
   */
  public List<ParseTree> getBestParseTreesWithSpan(Production root, int spanStart,
      int spanEnd, int numTrees) {
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
  }

  /**
   * Update the expected number of times that a binary production rule is used
   * in a parse. (This method assumes sum-product)
   */
  public void updateBinaryRuleExpectation(BinaryProduction bp, double prob) {
    binaryRuleExpectations.put(bp, binaryRuleExpectations.get(bp) + prob);
  }

  /**
   * Compute the expected *unnormalized* probability of every rule.
   */
  public Map<BinaryProduction, Double> getBinaryRuleExpectations() {
    return binaryRuleExpectations.getBaseMap();
  }

  /**
   * Update the expected number of times that a terminal production rule is used
   * in the parse.
   */
  public void updateTerminalRuleExpectation(TerminalProduction tp, double prob) {
    terminalRuleExpectations.put(tp, terminalRuleExpectations.get(tp) + prob);
  }

  /**
   * Compute the expected *unnormalized* probability of every terminal rule.
   */
  public Map<TerminalProduction, Double> getTerminalRuleExpectations() {
    return terminalRuleExpectations.getBaseMap();
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
          sb.append(insideChart[i][j].getBaseMap().toString());
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
          sb.append(outsideChart[i][j].getBaseMap().toString());
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
          sb.append(marginalChart[i][j].getBaseMap().toString());
          sb.append("\n");
        }
      }
    }
    return sb.toString();
  }
}