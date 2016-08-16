package com.jayantkrish.jklol.lisp.inc;

import com.google.common.base.Preconditions;
import com.jayantkrish.jklol.util.KbestQueue;
import com.jayantkrish.jklol.util.ObjectPool;

public class IncEvalChart {
  protected final int maxPoolSize; 
  protected final ObjectPool<IncEvalState> statePool;

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

  public IncEvalChart(int beamSize, IncEvalCost cost, IncEvalSearchLog searchLog) {
    maxPoolSize = 3 * (beamSize + 2);
    statePool = new ObjectPool<IncEvalState>(IncEvalState.getSupplier(),
        maxPoolSize, new IncEvalState[0]);

    heap = new KbestQueue<IncEvalState>(beamSize, new IncEvalState[0]);
    finishedHeap = new KbestQueue<IncEvalState>(beamSize, new IncEvalState[0]);
    currentBeam = new IncEvalState[beamSize];
    currentBeamSize = 0;

    this.cost = cost;
    this.searchLog = searchLog;
  }

  public IncEvalState alloc() {
    return statePool.alloc();
  }

  protected void dealloc(IncEvalState state) {
    statePool.dealloc(state);
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
      if (searchLog != null) {
        searchLog.log(current, next, 0.0);
      }

      IncEvalState removed = heap.offer(next, next.getProb());
      if (removed != null) {
        dealloc(removed);
      }
    } else {
      double costValue = cost.apply(next);
      if (searchLog != null) {
        searchLog.log(current, next, costValue);
      }

      if (costValue != Double.NEGATIVE_INFINITY) {
        IncEvalState removed = heap.offer(next, next.getProb() * Math.exp(costValue));
        if (removed != null) {
          dealloc(removed);
        }
      } else {
        dealloc(next);
      }
    }
  }
}