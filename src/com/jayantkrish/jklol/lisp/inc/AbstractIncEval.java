package com.jayantkrish.jklol.lisp.inc;

import java.util.Collections;
import java.util.List;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.jayantkrish.jklol.ccg.lambda2.Expression2;
import com.jayantkrish.jklol.lisp.Environment;
import com.jayantkrish.jklol.tensor.Tensor;
import com.jayantkrish.jklol.training.LogFunction;
import com.jayantkrish.jklol.training.NullLogFunction;
import com.jayantkrish.jklol.util.KbestQueue;

/**
 * KbestQueue implementations of {@code IncrementalEval} methods.
 * 
 * @author jayantk
 *
 */
public abstract class AbstractIncEval implements IncEval {
  
  /**
   * Gets the feature vector used to initialize search states.
   * 
   * @param initialDiagram
   * @return
   */
  protected Tensor getInitialFeatureVector(Object initialDiagram) {
    return null;
  }
  
  protected IncEvalChart initializeChart(Expression2 lf, Object initialDiagram,
      IncEvalCost cost, Environment startEnv, IncEvalSearchLog searchLog,
      int beamSize) {
    Preconditions.checkArgument(initialDiagram != null);

    // Construct and queue the start state. 
    // Note that continuation may be null, meaning that lf cannot
    // be evaluated by this class. If this is the case, this method
    // will return the initialState as the only result.
    Object continuation = lfToContinuation(lf, startEnv);
    Tensor featureVector = getInitialFeatureVector(initialDiagram);

    IncEvalChart chart = new IncEvalChart(beamSize, cost, searchLog);
    IncEvalState initialState = chart.alloc();
    initialState.set(continuation, startEnv, null, initialDiagram, 1.0, featureVector);
    chart.offer(null, initialState);

    return chart;
  }

  @Override
  public List<IncEvalState> evaluateBeam(Expression2 lf, Object initialDiagram,
      int beamSize) {
    return evaluateBeam(lf, initialDiagram, null, getEnvironment(),
        new NullLogFunction(), beamSize);
  }

  @Override
  public List<IncEvalState> evaluateBeam(Expression2 lf, Object initialDiagram,
      IncEvalCost cost, int beamSize) {
    return evaluateBeam(lf, initialDiagram, cost, getEnvironment(),
        new NullLogFunction(), beamSize);
  }
  
  @Override
  public List<IncEvalState> evaluateBeam(Expression2 lf, Object initialDiagram,
      IncEvalCost cost, LogFunction log, int beamSize) {
    return evaluateBeam(lf, initialDiagram, cost, getEnvironment(),
        log, beamSize);
  }

  @Override
  public List<IncEvalState> evaluateBeam(Expression2 lf, Object initialDiagram,
      Environment initialEnv, int beamSize) {
    return evaluateBeam(lf, initialDiagram, null, initialEnv,
        new NullLogFunction(), beamSize);
  }
  
  @Override
  public List<IncEvalState> evaluateBeam(Expression2 lf, Object initialDiagram,
      IncEvalCost cost, Environment startEnv, LogFunction log, int beamSize) {
    return evaluateBeam(lf, initialDiagram, cost, startEnv, log, null, beamSize);
  }

  @Override
  public List<IncEvalState> evaluateBeam(Expression2 lf, Object initialDiagram,
      IncEvalCost cost, Environment startEnv, LogFunction log, IncEvalSearchLog searchLog,
      int beamSize) {
    IncEvalChart chart = initializeChart(lf, initialDiagram, cost, startEnv, searchLog, beamSize);
    while (chart.size() > 0) {
      /*
      System.out.println("====");
      System.out.println("chart size: " + chart.size());
      System.out.println("finished size: " + chart.getFinishedHeap().size());
      System.out.println("beam size: " + chart.getCurrentBeamSize());
      System.out.println("num free: " + chart.getNumFree());
      */

      chart.moveHeapToBeam();
      int currentBeamSize = chart.getCurrentBeamSize();
      IncEvalState[] currentBeam = chart.getCurrentBeam();

      for (int i = 0; i < currentBeamSize; i++) {
        IncEvalState state = currentBeam[i];
        Preconditions.checkState(state.getContinuation() != null);
        log.startTimer("evaluate_continuation");
        evaluateContinuation(state, chart, log);
        log.stopTimer("evaluate_continuation");
      }
      chart.clearBeam();
    }

    List<IncEvalState> finalStates = Lists.newArrayList();
    KbestQueue<IncEvalState> finishedHeap = chart.getFinishedHeap();
    while (finishedHeap.size() > 0) {
      finalStates.add(finishedHeap.removeMin());
    }
    Collections.reverse(finalStates);
    return finalStates;
  }
}
