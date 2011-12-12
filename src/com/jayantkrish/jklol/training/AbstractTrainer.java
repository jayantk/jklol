package com.jayantkrish.jklol.training;

import java.util.List;

import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.jayantkrish.jklol.evaluation.Example;
import com.jayantkrish.jklol.models.dynamic.DynamicAssignment;
import com.jayantkrish.jklol.models.parametric.ParametricFactorGraph;
import com.jayantkrish.jklol.models.parametric.SufficientStatistics;
import com.jayantkrish.jklol.util.Assignment;

public abstract class AbstractTrainer implements Trainer {

  @Override
  public SufficientStatistics trainFixed(ParametricFactorGraph modelFamily,
      SufficientStatistics initialParameters,
      Iterable<Example<Assignment, Assignment>> trainingData) {

    // This code performs the conversion once up front, which is presumably more
    // efficient. However, it is also possible to simply pass the transformed
    // iterable to the underlying trainer.
    List<Example<DynamicAssignment, DynamicAssignment>> dynamicData =
        Lists.newArrayList(Iterables.transform(trainingData, new ExampleConverter()));

    return train(modelFamily, initialParameters, dynamicData);
  }

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

  private static class ExampleConverter implements Function<Example<Assignment, Assignment>,
      Example<DynamicAssignment, DynamicAssignment>> {

    @Override
    public Example<DynamicAssignment, DynamicAssignment> apply(
        Example<Assignment, Assignment> example) {
      return Example.create(DynamicAssignment.fromAssignment(example.getInput()),
          DynamicAssignment.fromAssignment(example.getOutput()));
    }
  }
}
