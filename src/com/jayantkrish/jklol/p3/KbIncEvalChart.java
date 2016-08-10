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
  private int[][] assignmentRefCounts;
  private int[] nextAssignmentIds;

  public KbIncEvalChart(int beamSize, IncEvalCost cost, IncEvalSearchLog searchLog,
      KbState startKb) {
    super(beamSize, cost, searchLog);
    this.kbPool = new ObjectPool<KbState>(KbState.getNullSupplier(
        startKb.getTypeNames(), startKb.getTypeVars(), startKb.getFunctions()),
        maxPoolSize, new KbState[0]);

    this.assignmentPool = Lists.newArrayList();
    List<FunctionAssignment> assignments = startKb.getAssignments();
    for (FunctionAssignment a : assignments) {
      this.assignmentPool.add(new ObjectPool<FunctionAssignment>(
          FunctionAssignment.getCopySupplier(a), maxPoolSize, new FunctionAssignment[0]));
    }
    
    this.assignmentRefCounts = new int[assignments.size()][maxPoolSize];
    this.nextAssignmentIds = new int[assignments.size()];
  }

  public KbState allocCopyOf(KbState other) {
    KbState kb = kbPool.alloc();
    other.shallowCopyTo(kb);
    
    /*
    List<FunctionAssignment> assignments = kb.getAssignments();
    for (int i = 0; i < assignments.size(); i++) {
      assignmentRefCounts[i][assignments.get(i).getId()]++;
    }
    */
    
    return kb;
  }

  public KbState allocDeepCopyOf(KbState other) {
    KbState kb = kbPool.alloc();
    other.shallowCopyTo(kb);

    /*
    for (int i = 0; i < kb.getFunctions().size(); i++) {
      allocAssignment(kb, i);
      other.getAssignment(i).copyTo(kb.getAssignment(i));
    }
    */

    return kb;
  }

  public void allocAssignment(KbState kb, int functionIndex) {
    /*
    FunctionAssignment a = assignmentPool.get(functionIndex).alloc();
    
    int id = a.getId();
    if (id == -1) {
      id = nextAssignmentIds[functionIndex];
      nextAssignmentIds[functionIndex]++;
      a.setId(id);
    } else {
      Preconditions.checkState(assignmentRefCounts[functionIndex][id] == 0);
    }
    assignmentRefCounts[functionIndex][id] = 1;
    
    int oldId = kb.getAssignment(functionIndex).getId();
    if (oldId != -1) {
      assignmentRefCounts[functionIndex][oldId]--;
    }

    kb.setAssignment(functionIndex, a);
    */
    kb.setAssignment(functionIndex, kb.getAssignment(functionIndex).copy());
  }

  @Override
  protected void dealloc(IncEvalState state) {
    if (state.getDiagram() != null) {
      KbState kb = (KbState) state.getDiagram();
      /*
      List<FunctionAssignment> assignments = kb.getAssignments();
      for (int i = 0; i < assignments.size(); i++) {
        assignmentRefCounts[i][assignments.get(i).getId()]--;
        
        if (assignmentRefCounts[i][assignments.get(i).getId()] == 0) {
          assignmentPool.get(i).dealloc(assignments.get(i));
        }
      }
      */
      kb.clear();
      kbPool.dealloc(kb);
    }
    state.setDiagram(null);
    statePool.dealloc(state);
  }
}
