package com.jayantkrish.jklol.lisp.inc;

import java.util.Collections;
import java.util.List;

import com.google.common.base.Predicate;
import com.google.common.collect.Lists;
import com.jayantkrish.jklol.ccg.lambda2.Expression2;
import com.jayantkrish.jklol.lisp.Environment;
import com.jayantkrish.jklol.util.KbestHeap;

/**
 * Common implementations of {@code IncrementalEval} methods.
 * 
 * @author jayantk
 *
 */
public abstract class AbstractIncEval implements IncEval {

  @Override
  public List<IncEvalState> evaluateBeam(Expression2 lf, Object initialDiagram,
      int beamSize) {
    return evaluateBeam(lf, initialDiagram, null, beamSize);
  }

  @Override
  public List<IncEvalState> evaluateBeam(Expression2 lf, Object initialDiagram,
      Predicate<IncEvalState> filter, int beamSize) {
    // Working heap for queuing parses to process next.
    KbestHeap<IncEvalState> heap = new KbestHeap<IncEvalState>(beamSize,
        new IncEvalState[0]);
    
    // Heap for finished parses.
    KbestHeap<IncEvalState> finishedHeap = new KbestHeap<IncEvalState>(beamSize,
        new IncEvalState[0]);

    // Array of elements in the current beam.
    IncEvalState[] currentBeam = new IncEvalState[beamSize + 1];
    int currentBeamSize = 0;

    // Accumulator for storing future continuations.
    List<IncEvalState> resultQueue = Lists.newArrayList();

    // Construct and queue the start state. 
    Environment env = getEnvironment();
    // Note that continuation may be null, meaning that lf cannot
    // be evaluated by this class. If this is the case, this method
    // will return the initialState as the only result.
    Object continuation = lfToContinuation(lf, env);
    IncEvalState initialState = new IncEvalState(continuation, env, null,
        initialDiagram, 1.0, null);
    offer(heap, initialState, filter);

    while (heap.size() > 0) {
      // Copy the heap to the current beam.
      IncEvalState[] keys = heap.getKeys();
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
          evaluateContinuation(state, resultQueue);
          
          for (IncEvalState next : resultQueue) {
            offer(heap, next, filter);
          }
        } else {
          // Evaluation is finished.
          offer(finishedHeap, state, filter);
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

  private static void offer(KbestHeap<IncEvalState> heap, IncEvalState state,
      Predicate<IncEvalState> filter) {
    if (filter == null || filter.apply(state)) {
      heap.offer(state, state.getProb());
    }
  }
}
