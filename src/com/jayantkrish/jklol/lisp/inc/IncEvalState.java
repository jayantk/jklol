package com.jayantkrish.jklol.lisp.inc;

import com.jayantkrish.jklol.lisp.Environment;
import com.jayantkrish.jklol.tensor.Tensor;
import com.jayantkrish.jklol.util.Copyable;

public class IncEvalState implements Copyable<IncEvalState> {
  private Object continuation;
  private Environment environment;

  private Object denotation;
  private Object diagram;
  private double prob;
  
  private Tensor features;

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
  
  public final void set(Object continuation, Environment environment,
      Object denotation, Object diagram, double prob, Tensor features) {
    this.continuation = continuation;
    this.environment = environment;
    this.denotation = denotation;
    this.diagram = diagram;
    this.prob = prob;
    this.features = features;
  }

  @Override
  public void copyTo(IncEvalState item) {
    item.continuation = continuation;
    item.environment = environment;
    item.denotation = denotation;
    item.diagram = diagram;
    item.prob = prob;
    item.features = features;
  }

  @Override
  public IncEvalState copy() {
    IncEvalState s = new IncEvalState(null, null, null, null, 0.0, null);
    copyTo(s);
    return s;
  }

  @Override
  public String toString() {
    return "[IncEvalState " + continuation + " " + denotation + " " + diagram + " " + prob + "]";
  }
}