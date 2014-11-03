package com.jayantkrish.jklol.training;

import java.util.List;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.jayantkrish.jklol.models.parametric.SufficientStatistics;
import com.jayantkrish.jklol.parallel.MapReduceConfiguration;
import com.jayantkrish.jklol.parallel.MapReduceExecutor;
import com.jayantkrish.jklol.parallel.Mapper;
import com.jayantkrish.jklol.parallel.Reducers;

public class ExpectationMaximization {

  private final int numIterations;

  private final LogFunction log;

  public ExpectationMaximization(int numIterations, LogFunction log) {
    this.numIterations = numIterations;
    this.log = Preconditions.checkNotNull(log);
  }

  public <M, E, O> SufficientStatistics train(EmOracle<M, E, O> oracle, 
     SufficientStatistics initialParameters, Iterable<E> trainingData) {

    MapReduceExecutor executor = MapReduceConfiguration.getMapReduceExecutor();
    List<E> trainingDataList = Lists.newArrayList(trainingData);
    SufficientStatistics parameters = initialParameters;
    for (int i = 0; i < numIterations; i++) {
      log.notifyIterationStart(i);

      log.startTimer("e_step");
      M model = oracle.instantiateModel(parameters);
      List<O> expectations = executor.mapReduce(trainingDataList, 
          new ExpectationMapper<M, E, O>(model, parameters, oracle, log),
          Reducers.<O>getAggregatingListReducer());
      log.stopTimer("e_step");

      log.startTimer("m_step");
      parameters = oracle.maximizeParameters(expectations, parameters, log);
      log.stopTimer("m_step");

      log.notifyIterationEnd(i);
    }
    
    return parameters;
  }  
  
  /**
   * Computes expectations on training data.
   * 
   * @author jayantk
   * @param <M> model
   * @param <E> example (input) type
   * @param <O> expectation (output) type
   */
  private static class ExpectationMapper<M, E, O> extends Mapper<E, O> {

    private final M model;
    private final SufficientStatistics modelParameters;
    private final EmOracle<M, E, O> oracle;
    private final LogFunction log;
    
    public ExpectationMapper(M model, SufficientStatistics modelParameters,
        EmOracle<M, E, O> oracle, LogFunction log) {
      this.model = Preconditions.checkNotNull(model);
      this.modelParameters = Preconditions.checkNotNull(modelParameters);
      this.oracle = Preconditions.checkNotNull(oracle);
      this.log = Preconditions.checkNotNull(log);
    }
    
    @Override
    public O map(E input) {
      return oracle.computeExpectations(model, modelParameters, input, log);
    }
  }
}