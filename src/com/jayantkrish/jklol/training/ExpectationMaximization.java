package com.jayantkrish.jklol.training;

import java.util.List;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.jayantkrish.jklol.models.parametric.SufficientStatistics;
import com.jayantkrish.jklol.parallel.MapReduceConfiguration;
import com.jayantkrish.jklol.parallel.MapReduceExecutor;
import com.jayantkrish.jklol.parallel.Mappers;
import com.jayantkrish.jklol.parallel.Reducer;

public class ExpectationMaximization {

  private final int numIterations;

  private final LogFunction log;

  public ExpectationMaximization(int numIterations, LogFunction log) {
    this.numIterations = numIterations;
    this.log = Preconditions.checkNotNull(log);
  }

  public <M, E, O, A> SufficientStatistics train(EmOracle<M, E, O, A> oracle, 
     SufficientStatistics initialParameters, Iterable<E> trainingData) {

    MapReduceExecutor executor = MapReduceConfiguration.getMapReduceExecutor();
    List<E> trainingDataList = Lists.newArrayList(trainingData);
    SufficientStatistics parameters = initialParameters;
    for (int i = 0; i < numIterations; i++) {
      log.notifyIterationStart(i);

      log.startTimer("instantiate_model");
      M model = oracle.instantiateModel(parameters);
      log.stopTimer("instantiate_model");
      log.startTimer("e_step");
      A expectations = executor.mapReduce(trainingDataList, 
          Mappers.identity(), new ExpectationReducer<M, E, O, A>(model, parameters, oracle, log));
      log.stopTimer("e_step");

      log.startTimer("m_step");
      parameters = oracle.maximizeParameters(expectations, parameters, log);
      log.stopTimer("m_step");

      log.notifyIterationEnd(i);
    }
    
    return parameters;
  }  

  private static class ExpectationReducer<M, E, O, A> implements Reducer<E, A> {
    private final M model;
    private final SufficientStatistics modelParameters;
    private final EmOracle<M, E, O, A> oracle;
    private final LogFunction log;

    public ExpectationReducer(M model, SufficientStatistics modelParameters,
        EmOracle<M, E, O, A> oracle, LogFunction log) {
      this.model = Preconditions.checkNotNull(model);
      this.modelParameters = Preconditions.checkNotNull(modelParameters);
      this.oracle = Preconditions.checkNotNull(oracle);
      this.log = Preconditions.checkNotNull(log);
    }

    @Override
    public A getInitialValue() {
      return oracle.getInitialExpectationAccumulator();
    }

    @Override
    public A reduce(E item, A accumulated) {
      return oracle.computeExpectations(model, modelParameters, item, accumulated, log);
    }

    @Override
    public A combine(A other, A accumulated) {
      return oracle.combineAccumulators(other, accumulated);
    }
  }
}