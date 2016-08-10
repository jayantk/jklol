package com.jayantkrish.jklol.p3;

import java.util.List;

import com.google.common.collect.Lists;
import com.jayantkrish.jklol.lisp.inc.IncEvalChart;
import com.jayantkrish.jklol.lisp.inc.IncEvalCost;
import com.jayantkrish.jklol.lisp.inc.IncEvalSearchLog;
import com.jayantkrish.jklol.lisp.inc.IncEvalState;
import com.jayantkrish.jklol.util.ObjectPool;

public class KbIncEvalChart extends IncEvalChart {
  
  private ObjectPool<KbState> kbPool;
  private List<ObjectPool<FunctionAssignment>> assignmentPool;

  public KbIncEvalChart(int beamSize, IncEvalCost cost, IncEvalSearchLog searchLog,
      KbState startKb) {
    super(beamSize, cost, searchLog);
    this.kbPool = new ObjectPool<KbState>(KbState.getCopySupplier(startKb),
        maxPoolSize, new KbState[0]);
    
    this.assignmentPool = Lists.newArrayList();
    List<FunctionAssignment> assignments = startKb.getAssignments();
    for (FunctionAssignment a : assignments) {
      this.assignmentPool.add(new ObjectPool<FunctionAssignment>(
          FunctionAssignment.getCopySupplier(a), maxPoolSize, new FunctionAssignment[0]));
    }
  }

  public KbState allocCopyOf(KbState other) {
    KbState kb = kbPool.alloc();
    other.shallowCopyTo(kb);
    return kb;
  }

  public ObjectPool<FunctionAssignment> getAssignmentPool(int functionIndex) {
    return assignmentPool.get(functionIndex);
  }

  @Override
  protected void dealloc(IncEvalState state) {
    if (state.getDiagram() != null) {
      KbState kb = (KbState) state.getDiagram();
      /*
      List<FunctionAssignment> assignments = kb.getAssignments();
      for (int updated : kb.getUpdatedFunctionIndexes()) {
        assignmentPool.get(updated).dealloc(assignments.get(updated));
      }
      kb.clear();
      */
      this.kbPool.dealloc(kb);
    }
    state.setDiagram(null);
    statePool.dealloc(state);
  }
}
