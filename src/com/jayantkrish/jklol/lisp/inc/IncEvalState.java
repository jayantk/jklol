package com.jayantkrish.jklol.lisp.inc;

import com.jayantkrish.jklol.lisp.Environment;
import com.jayantkrish.jklol.tensor.Tensor;

public class IncEvalState {
  private final Object continuation;
  private final Environment environment;

  private final Object denotation;
  private final Object diagram;
  private final double prob;
  
  private final Tensor features;

  public IncEvalState(Object continuation, Environment environment,
      Object denotation, Object diagram, double prob, Tensor features) {
    this.continuation = continuation;
    this.environment = environment;
    this.denotation = denotation;
    this.diagram = diagram;
    this.prob = prob;
    
    this.features = features;
  }

  public final Object getContinuation() {
    return continuation;
  }
  
  public final Environment getEnvironment() {
    return environment;
  }

  public final Object getDenotation() {
    return denotation;
  }

  public final Object getDiagram() {
    return diagram;
  }

  public final double getProb() {
    return prob;
  }

  public final Tensor getFeatures() {
    return features;
  }

  @Override
  public String toString() {
    return continuation + " " + denotation + " " + diagram + " " + prob;
  }
}