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
 * Common implementations of {@code IncrementalEval} methods.
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
  
  /*
  public List<IncEvalState> evaluateBeam(Expression2 lf, Object initialDiagram,
      IncEvalCost cost, Environment startEnv, LogFunction log, IncEvalSearchLog searchLog,
      int beamSize) {
    Preconditions.checkArgument(initialDiagram != null);
    // Construct and queue the start state. 
    // Note that continuation may be null, meaning that lf cannot
    // be evaluated by this class. If this is the case, this method
    // will return the initialState as the only result.
    Object continuation = lfToContinuation(lf, startEnv);
    Tensor featureVector = getInitialFeatureVector(initialDiagram);
    IncEvalState sizingState = new IncEvalState(continuation, startEnv, null,
        initialDiagram, 1.0, featureVector);

    IncEvalChart chart = new IncEvalChart(beamSize, sizingState, cost, searchLog);
    IncEvalState initialState = chart.alloc();
    sizingState.copyTo(initialState);
    chart.offer(null, initialState);
    
  }
  */

  @Override
  public List<IncEvalState> evaluateBeam(Expression2 lf, Object initialDiagram,
      IncEvalCost cost, Environment startEnv, LogFunction log, IncEvalSearchLog searchLog,
      int beamSize) {
    Preconditions.checkArgument(initialDiagram != null);

    // Construct and queue the start state. 
    // Note that continuation may be null, meaning that lf cannot
    // be evaluated by this class. If this is the case, this method
    // will return the initialState as the only result.
    Object continuation = lfToContinuation(lf, startEnv);
    Tensor featureVector = getInitialFeatureVector(initialDiagram);
    IncEvalState sizingState = new IncEvalState(continuation, startEnv, null,
        initialDiagram, 1.0, featureVector);

    IncEvalChart chart = new IncEvalChart(beamSize, sizingState, cost, searchLog);
    IncEvalState initialState = chart.alloc();
    sizingState.copyTo(initialState);
    chart.offer(null, initialState);
    
    while (chart.size() > 0) {
      System.out.println("====");
      System.out.println("chart size: " + chart.size());
      System.out.println("finished size: " + chart.getFinishedHeap().size());
      System.out.println("beam size: " + chart.getCurrentBeamSize());
      System.out.println("num free: " + chart.getNumFree());

      chart.moveHeapToBeam();
      int currentBeamSize = chart.getCurrentBeamSize();
      IncEvalState[] currentBeam = chart.getCurrentBeam();
      
      System.out.println("beam size: " + chart.getCurrentBeamSize());
      System.out.println("chart size: " + chart.size());

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

  public static class IncEvalChart {
    private final IncEvalState[] free;
    private int numFree;

    // Working heap for queuing parses to process next.
    private final KbestQueue<IncEvalState> heap;

    // Heap for finished parses.
    private final KbestQueue<IncEvalState> finishedHeap;
    
    // Current beam storing states to evaluate, whose
    // next states are added to heap.
    private final IncEvalState[] currentBeam;
    private int currentBeamSize;

    private final IncEvalCost cost;
    private final IncEvalSearchLog searchLog;

    public IncEvalChart(int beamSize, IncEvalState itemSize, IncEvalCost cost,
        IncEvalSearchLog searchLog) {
      // TODO: verify the calculation of number of necessary states.
      free = new IncEvalState[3 * (beamSize + 2)];

      heap = new KbestQueue<IncEvalState>(beamSize, new IncEvalState[0]);
      finishedHeap = new KbestQueue<IncEvalState>(beamSize, new IncEvalState[0]);
      currentBeam = new IncEvalState[beamSize];
      currentBeamSize = 0;

      this.cost = cost;
      this.searchLog = searchLog;

      // Allocate states
      for (int i = 0; i < free.length; i++) {
        free[i] = itemSize.copy();
      }
      numFree = free.length;
    }

    public int getNumFree() {
      return numFree;
    }

    public IncEvalState alloc() {
      Preconditions.checkState(numFree > 0);
      IncEvalState state = free[numFree - 1];
      numFree--;
      return state;
    }

    private void dealloc(IncEvalState state) {
      free[numFree] = state;
      numFree++;
    }

    public void offer(IncEvalState current, IncEvalState next) {
      offer(heap, current, next, searchLog);
    }

    public int size() {
      return heap.size();
    }
    
    public IncEvalState[] getItems() {
      return heap.getItems();
    }

    public void moveHeapToBeam() {
      // Validate that the beam is empty
      Preconditions.checkState(currentBeamSize == 0);
      
      // Copy the heap to the current beam.
      currentBeamSize = heap.size();
      IncEvalState[] keys = heap.getItems();
      for (int i = 0; i < currentBeamSize; i++) {
        currentBeam[i] = keys[i];
      }
      heap.clear();
    }
    
    public int getCurrentBeamSize() {
      return currentBeamSize;
    }
    
    public IncEvalState[] getCurrentBeam() {
      return currentBeam;
    }
    
    public void clearBeam() {
      for (int i = 0; i < currentBeamSize; i++) {
        dealloc(currentBeam[i]);
      }
      currentBeamSize = 0;
    }

    public KbestQueue<IncEvalState> getFinishedHeap() {
      return finishedHeap;
    }

    public void offerFinished(IncEvalState current, IncEvalState next) {
      offer(finishedHeap, current, next, null);
    }

    private void offer(KbestQueue<IncEvalState> heap, IncEvalState current,
        IncEvalState next, IncEvalSearchLog searchLog) {
      if (cost == null) {
        IncEvalState removed = heap.offer(next, next.getProb());
        if (removed != null) {
          dealloc(removed);
        }

        if (searchLog != null) {
          searchLog.log(current, next, 0.0);
        }
      } else {
        double costValue = cost.apply(next);
        if (costValue != Double.NEGATIVE_INFINITY) {
          IncEvalState removed = heap.offer(next, next.getProb() * Math.exp(costValue));
          if (removed != null) {
            dealloc(removed);
          }
        } else {
          dealloc(next);
        }

        if (searchLog != null) {
          searchLog.log(current, next, costValue);
        }
      }
    }
  }
}
