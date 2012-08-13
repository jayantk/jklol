package com.jayantkrish.jklol.training;

import java.util.List;

import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.jayantkrish.jklol.evaluation.Example;
import com.jayantkrish.jklol.models.dynamic.DynamicAssignment;
import com.jayantkrish.jklol.models.parametric.SufficientStatistics;
import com.jayantkrish.jklol.util.Assignment;

/**
 * Adapts a {@code Trainer} that takes examples of {@code DynamicAssignment}s to
 * accept other inputs, using a conversion from the other inputs to
 * {@code DynamicAssignment}s.
 * 
 * @author jayantk
 */
public class TrainerAdapter<T, E> implements Trainer<T, E> {

  private final Trainer<T, Example<DynamicAssignment, DynamicAssignment>> trainer;
  private final Function<E, Example<DynamicAssignment, DynamicAssignment>> converter;

  public TrainerAdapter(Trainer<T, Example<DynamicAssignment, DynamicAssignment>> trainer,
      Function<E, Example<DynamicAssignment, DynamicAssignment>> converter) {
    this.trainer = Preconditions.checkNotNull(trainer);
    this.converter = Preconditions.checkNotNull(converter);
  }

  /**
   * Gets a {@code TrainerAdapter} that transforms
   * {@code Example<Assignment, Assignment>} examples to
   * {@code Example<DynamicAssignment, DynamicAssignment>}.
   * 
   * @param trainer
   * @return
   */
  public static <T> TrainerAdapter<T, Example<Assignment, Assignment>> createAssignmentAdapter(
      Trainer<T, Example<DynamicAssignment, DynamicAssignment>> trainer) {
    return new TrainerAdapter<T, Example<Assignment, Assignment>>(trainer, new ExampleConverter());
  }

  public SufficientStatistics train(T modelFamily,
      SufficientStatistics initialParameters, Iterable<E> trainingData) {

    // This code performs the conversion once up front, which is presumably more
    // efficient. However, it is also possible to simply pass the transformed
    // iterable to the underlying trainer.
    List<Example<DynamicAssignment, DynamicAssignment>> dynamicData =
        Lists.newArrayList(Iterables.transform(trainingData, converter));

    return trainer.train(modelFamily, initialParameters, dynamicData);
  }

  /**
   * Class for converting {@code Example}s containing {@code DynamicAssignment}s
   * to {@code Example}s containing {@code Assignment}s. This is a very common
   * operation.
   * 
   * @author jayantk
   */
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
