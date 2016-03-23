package com.jayantkrish.jklol.cfg;

import com.google.common.base.Preconditions;
import com.jayantkrish.jklol.models.TableFactorBuilder;

public class CfgExpectation {
  private final TableFactorBuilder rootBuilder;
  private final TableFactorBuilder ruleBuilder;
  private final TableFactorBuilder nonterminalBuilder;
  private final TableFactorBuilder terminalBuilder;

  public CfgExpectation(TableFactorBuilder rootBuilder, TableFactorBuilder ruleBuilder,
      TableFactorBuilder nonterminalBuilder, TableFactorBuilder terminalBuilder) {
    this.rootBuilder = Preconditions.checkNotNull(rootBuilder);
    this.ruleBuilder = Preconditions.checkNotNull(ruleBuilder);
    this.nonterminalBuilder = Preconditions.checkNotNull(nonterminalBuilder);
    this.terminalBuilder = Preconditions.checkNotNull(terminalBuilder);
  }

  public TableFactorBuilder getRootBuilder() {
    return rootBuilder;
  }

  public TableFactorBuilder getRuleBuilder() {
    return ruleBuilder;
  }

  public TableFactorBuilder getNonterminalBuilder() {
    return nonterminalBuilder;
  }

  public TableFactorBuilder getTerminalBuilder() {
    return terminalBuilder;
  }

  public void increment(CfgExpectation other) {
    this.ruleBuilder.incrementWeight(other.ruleBuilder.build());
    this.nonterminalBuilder.incrementWeight(other.nonterminalBuilder.build());
    this.terminalBuilder.incrementWeight(other.terminalBuilder.build());
  }
  
  public void zeroOut() {
    this.ruleBuilder.multiply(0.0);
    this.nonterminalBuilder.multiply(0.0);
    this.terminalBuilder.multiply(0.0);
  }
}