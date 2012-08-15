package com.jayantkrish.jklol.training;

import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.jayantkrish.jklol.evaluation.Example;
import com.jayantkrish.jklol.models.dynamic.DynamicAssignment;
import com.jayantkrish.jklol.models.dynamic.DynamicFactorGraph;
import com.jayantkrish.jklol.models.parametric.SufficientStatistics;
import com.jayantkrish.jklol.util.Assignment;

/**
 * Adapts an {@code Oracle} that takes examples of {@code DynamicAssignment}s to
 * accept other inputs, using a conversion from the other inputs to
 * {@code DynamicAssignment}s.
 * 
 * @author jayantk
 */
public class OracleAdapter<E> implements GradientOracle<DynamicFactorGraph, E> {

  private final GradientOracle<DynamicFactorGraph, Example<DynamicAssignment, DynamicAssignment>> oracle;
  private final Function<E, Example<DynamicAssignment, DynamicAssignment>> converter;
  
  public OracleAdapter(GradientOracle<DynamicFactorGraph, Example<DynamicAssignment, DynamicAssignment>> oracle, 
      Function<E, Example<DynamicAssignment, DynamicAssignment>> converter) {
    this.oracle = Preconditions.checkNotNull(oracle);
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
  public static OracleAdapter<Example<Assignment, Assignment>> createAssignmentAdapter(
      GradientOracle<DynamicFactorGraph, Example<DynamicAssignment, DynamicAssignment>> oracle) {
    return new OracleAdapter<Example<Assignment, Assignment>>(oracle, new ExampleConverter());
  }

  @Override
  public SufficientStatistics initializeGradient() {
    return oracle.initializeGradient();
  }

  @Override
  public DynamicFactorGraph instantiateModel(SufficientStatistics parameters) {
    return oracle.instantiateModel(parameters);
  }

  @Override
  public void accumulateGradient(SufficientStatistics gradient, DynamicFactorGraph instantiatedModel, 
      E example, LogFunction log) {
    oracle.accumulateGradient(gradient, instantiatedModel, converter.apply(example), log);
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
