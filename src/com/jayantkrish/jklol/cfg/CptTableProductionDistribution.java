package com.jayantkrish.jklol.cfg;

import java.util.Map;
import java.util.Set;

import com.google.common.base.Preconditions;
import com.jayantkrish.jklol.models.bayesnet.SufficientStatistics;
import com.jayantkrish.jklol.util.DefaultHashMap;

/**
 * A CptTableProductionDistribution maintains a separate CPT for each production
 * rule (i.e., no parameters are tied across production rules).
 */
public class CptTableProductionDistribution implements CptProductionDistribution {

  private Grammar grammar;
  private DefaultHashMap<Production, Double> denominators;
  private DefaultHashMap<BinaryProduction, Double> binaryRuleCounts;
  private DefaultHashMap<TerminalProduction, Double> terminalRuleCounts;

  /**
   * Initialize a probability distribution over the rules in {@code grammar}. 
   */
  public CptTableProductionDistribution(Grammar grammar) {
    this.grammar = grammar;
    binaryRuleCounts = new DefaultHashMap<BinaryProduction, Double>(0.0);
    terminalRuleCounts = new DefaultHashMap<TerminalProduction, Double>(0.0);
    denominators = new DefaultHashMap<Production, Double>(0.0);
  }

  @Override
  public double getRuleProbability(BinaryProduction rule) {
    return binaryRuleCounts.get(rule) / denominators.get(rule.getParent());
  }

  @Override
  public double getTerminalProbability(TerminalProduction rule) {
    return terminalRuleCounts.get(rule) / denominators.get(rule.getParent());
  }

  @Override
  public void increment(SufficientStatistics other, double multiplier) {
    Preconditions.checkArgument(other instanceof CptTableProductionDistribution);
    CptTableProductionDistribution otherDist = (CptTableProductionDistribution) other;
    incrementMap(denominators, otherDist.denominators, multiplier);
    incrementMap(binaryRuleCounts, otherDist.binaryRuleCounts, multiplier);
    incrementMap(terminalRuleCounts, otherDist.terminalRuleCounts, multiplier);
  }

  @Override
  public void increment(double amount) {
    for (Production nonterm : grammar.getAllNonTerminals()) {
      Set<BinaryProduction> bps = grammar.getBinaryProductions(nonterm);
      Set<TerminalProduction> tps = grammar.getTerminalProductions(nonterm);
      denominators.put(nonterm, (bps.size() + tps.size()) * amount);

      for (BinaryProduction bp : bps) {
        binaryRuleCounts.put(bp, amount);
      }
      for (TerminalProduction tp : tps) {
        terminalRuleCounts.put(tp, amount);
      }
    }
  }

  @Override
  public void incrementBinaryCpts(Map<BinaryProduction, Double> binaryRuleExpectations, double count) {
    for (BinaryProduction bp : binaryRuleExpectations.keySet()) {
      Production parent = bp.getParent();
      denominators.put(parent, denominators.get(parent) + (count * binaryRuleExpectations.get(bp)));
      binaryRuleCounts.put(bp, binaryRuleCounts.get(bp) + (count * binaryRuleExpectations.get(bp)));
    }
  }

  @Override
  public void incrementTerminalCpts(Map<TerminalProduction, Double> terminalRuleExpectations, double count) {
    for (TerminalProduction tp : terminalRuleExpectations.keySet()) {
      Production parent = tp.getParent();
      denominators.put(parent, denominators.get(parent) + (count * terminalRuleExpectations.get(tp)));
      terminalRuleCounts.put(tp, terminalRuleCounts.get(tp) + (count * terminalRuleExpectations.get(tp)));
    }
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    for (Production p : denominators.keySet()) {
      for (BinaryProduction bp : grammar.getBinaryProductions(p)) {
        sb.append(binaryRuleCounts.get(bp) / denominators.get(p));
        sb.append(" : ");
        sb.append(bp.toString());
        sb.append("\n");
      }

      for (TerminalProduction tp : grammar.getTerminalProductions(p)) {
        sb.append(terminalRuleCounts.get(tp) / denominators.get(p));
        sb.append(" : ");
        sb.append(tp.toString());
        sb.append("\n");
      }
    }
    return sb.toString();
  }
  
  /**
   * Increments each entry of {@code toIncrement} by its corresponding entry in
   * {@code amount} multiplied by {@code multiplier}.
   * 
   * @param toIncrement
   * @param amount
   * @param multiplier
   */
  private <T> void incrementMap(DefaultHashMap<T, Double> toIncrement,
      DefaultHashMap<T, Double> amount, double multiplier) {
    for (Map.Entry<T, Double> entry : amount.entrySet()) {
      if (toIncrement.containsKey(entry.getKey())) {
        toIncrement.put(entry.getKey(),
            toIncrement.get(entry.getKey()) + (multiplier * entry.getValue()));
      } else {
        toIncrement.put(entry.getKey(), entry.getValue());
      }
    }
  }
}
