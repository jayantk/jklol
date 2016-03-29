package com.jayantkrish.jklol.lisp.inc;

import java.util.Collections;
import java.util.List;

import com.google.common.collect.Lists;
import com.jayantkrish.jklol.ccg.lambda2.Expression2;
import com.jayantkrish.jklol.lisp.Environment;
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
  
  @Override
  public void evaluateContinuation(IncEvalState state, List<IncEvalState> resultQueue) {
    evaluateContinuation(state, resultQueue, new NullLogFunction());
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
      IncEvalCost cost, Environment startEnv,
      LogFunction log, int beamSize) {
    // Working heap for queuing parses to process next.
    KbestQueue<IncEvalState> heap = new KbestQueue<IncEvalState>(beamSize,
        new IncEvalState[0]);
    
    // Heap for finished parses.
    KbestQueue<IncEvalState> finishedHeap = new KbestQueue<IncEvalState>(beamSize,
        new IncEvalState[0]);

    // Array of elements in the current beam.
    IncEvalState[] currentBeam = new IncEvalState[beamSize + 1];
    int currentBeamSize = 0;

    // Accumulator for storing future continuations.
    List<IncEvalState> resultQueue = Lists.newArrayList();

    // Construct and queue the start state. 
    // Note that continuation may be null, meaning that lf cannot
    // be evaluated by this class. If this is the case, this method
    // will return the initialState as the only result.
    Object continuation = lfToContinuation(lf, startEnv);
    IncEvalState initialState = new IncEvalState(continuation, startEnv, null,
        initialDiagram, 1.0, null);
    offer(heap, initialState, cost);

    while (heap.size() > 0) {
      // Copy the heap to the current beam.
      IncEvalState[] keys = heap.getItems();
      for (int i = 0; i < heap.size(); i++) {
        currentBeam[i] = keys[i];
      }

      // Empty the heap.
      currentBeamSize = heap.size();
      heap.clear();

      for (int i = 0; i < currentBeamSize; i++) {
        IncEvalState state = currentBeam[i];
        
        if (state.getContinuation() != null) {
          resultQueue.clear();
          log.startTimer("evaluate_continuation");
          evaluateContinuation(state, resultQueue, log);
          log.stopTimer("evaluate_continuation");
          
          for (IncEvalState next : resultQueue) {
            offer(heap, next, cost);
          }
        } else {
          // Evaluation is finished.
          offer(finishedHeap, state, cost);
        }
      }
    }
    
    List<IncEvalState> finalStates = Lists.newArrayList();
    while (finishedHeap.size() > 0) {
      finalStates.add(finishedHeap.removeMin());
    }
    Collections.reverse(finalStates);
    return finalStates;
  }

  private static void offer(KbestQueue<IncEvalState> heap, IncEvalState state,
      IncEvalCost cost) {
    if (cost == null) {
      heap.offer(state, state.getProb());
    } else {
      double costValue = cost.apply(state);
      if (costValue != Double.NEGATIVE_INFINITY) {
        heap.offer(state, state.getProb() * Math.exp(costValue));
      }
    }
  }
}
