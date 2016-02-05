package com.jayantkrish.jklol.ccg.gi;

import java.util.Collections;
import java.util.List;

import com.google.common.collect.Lists;
import com.jayantkrish.jklol.ccg.lambda2.Expression2;
import com.jayantkrish.jklol.lisp.Environment;
import com.jayantkrish.jklol.util.KbestHeap;

public abstract class AbstractIncrementalEval implements IncrementalEval {

  @Override
  public List<IncrementalEvalState> evaluateBeam(Expression2 lf, Object initialDiagram, int beamSize) {
    // Working heap for queuing parses to process next.
    KbestHeap<IncrementalEvalState> heap = new KbestHeap<IncrementalEvalState>(beamSize,
        new IncrementalEvalState[0]);
    
    // Heap for finished parses.
    KbestHeap<IncrementalEvalState> finishedHeap = new KbestHeap<IncrementalEvalState>(beamSize,
        new IncrementalEvalState[0]);

    // Array of elements in the current beam.
    IncrementalEvalState[] currentBeam = new IncrementalEvalState[beamSize + 1];
    int currentBeamSize = 0;

    // Accumulator for storing future continuations.
    List<IncrementalEvalState> resultQueue = Lists.newArrayList();

    // Construct and queue the start state. 
    Environment env = getEnvironment();
    // Note that continuation may be null, meaning that lf cannot
    // be evaluated by this class. If this is the case, this method
    // will return the initialState as the only result.
    Object continuation = lfToContinuation(lf, env);
    IncrementalEvalState initialState = new IncrementalEvalState(continuation, env, null,
        initialDiagram, 1.0);
    heap.offer(initialState, 1.0);

    while (heap.size() > 0) {
      // Copy the heap to the current beam.
      IncrementalEvalState[] keys = heap.getKeys();
      for (int i = 0; i < heap.size(); i++) {
        currentBeam[i] = keys[i];
      }

      // Empty the heap.
      currentBeamSize = heap.size();
      heap.clear();

      for (int i = 0; i < currentBeamSize; i++) {
        IncrementalEvalState state = currentBeam[i];
        
        if (state.getContinuation() != null) {
          resultQueue.clear();
          evaluateContinuation(state, resultQueue);
          
          for (IncrementalEvalState next : resultQueue) {
            heap.offer(next, next.getProb());
          }
        } else {
          // Evaluation is finished.
          finishedHeap.offer(state, state.getProb());
        }
      }
    }
    
    List<IncrementalEvalState> finalStates = Lists.newArrayList();
    while (finishedHeap.size() > 0) {
      finalStates.add(finishedHeap.removeMin());
    }
    Collections.reverse(finalStates);
    return finalStates;
  }
}
