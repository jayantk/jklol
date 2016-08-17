package com.jayantkrish.jklol.lisp.inc;

import com.google.common.base.Supplier;
import com.jayantkrish.jklol.lisp.Environment;
import com.jayantkrish.jklol.tensor.Tensor;

public class IncEvalState {
  private Object continuation;
  private Environment environment;

  private Object denotation;
  private Object diagram;
  private double prob;
  
  private Tensor features;
  private int id;

  public IncEvalState(Object continuation, Environment environment,
      Object denotation, Object diagram, double prob, Tensor features,
      int id) {
    this.continuation = continuation;
    this.environment = environment;
    this.denotation = denotation;
    this.diagram = diagram;
    this.prob = prob;
    this.features = features;
    this.id = id;
  }
  
  public static Supplier<IncEvalState> getSupplier() {
    return new Supplier<IncEvalState>() {
      @Override
      public IncEvalState get() {
        return new IncEvalState(null, null, null, null, 1.0, null, -1);
      }
    };
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
  
  public final int getId() {
    return id;
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

  public void setDiagram(Object diagram) {
    this.diagram = diagram;
  }

  public void setId(int id) {
    this.id = id;
  }

  @Override
  public String toString() {
    return "[IncEvalState " + continuation + " " + denotation + " " + diagram + " " + prob + "]";
  }
}