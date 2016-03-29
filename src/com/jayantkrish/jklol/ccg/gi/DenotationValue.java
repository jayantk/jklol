package com.jayantkrish.jklol.ccg.gi;

public class DenotationValue {
  private Object denotation;
  private double probability;

  public DenotationValue(Object denotation, double probability) {
    this.denotation = denotation;
    this.probability = probability;
  }

  public Object getDenotation() {
    return denotation;
  }

  public double getProbability() {
    return probability;
  }
}
