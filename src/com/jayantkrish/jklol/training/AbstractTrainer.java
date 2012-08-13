package com.jayantkrish.jklol.training;

import java.util.List;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.jayantkrish.jklol.evaluation.Example;
import com.jayantkrish.jklol.models.dynamic.DynamicAssignment;

public abstract class AbstractTrainer<T, E> implements Trainer<T, E> {

  /**
   * Gets the output values of {@code trainingData}. If
   * {@code checkNoInputs == true}, this method also checks to make sure that
   * the input assignments are empty; such a check is useful when a trainer
   * always optimizes a joint distribution.
   * 
   * @param trainingData
   * @param checkNoInputs
   * @return
   */
  protected List<DynamicAssignment> getOutputAssignments(
      Iterable<Example<DynamicAssignment, DynamicAssignment>> trainingData,
      boolean checkNoInputs) {
    List<DynamicAssignment> trainingDataList = Lists.newArrayList();
    for (Example<DynamicAssignment, DynamicAssignment> example : trainingData) {
      Preconditions.checkArgument(!checkNoInputs || example.getInput().equals(DynamicAssignment.EMPTY));
      trainingDataList.add(example.getOutput());
    }
    return trainingDataList;
  }
}
