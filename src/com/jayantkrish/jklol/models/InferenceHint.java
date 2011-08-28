package com.jayantkrish.jklol.models;

public class InferenceHint {

  private int[] factorEliminationOrder;

  public InferenceHint(int[] factorEliminationOrder) {
    this.factorEliminationOrder = factorEliminationOrder;
  }

  public int[] getFactorEliminationOrder() {
    return factorEliminationOrder;
  }
}
