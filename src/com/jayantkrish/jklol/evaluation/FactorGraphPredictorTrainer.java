package com.jayantkrish.jklol.evaluation;

import java.util.List;

import com.google.common.base.Preconditions;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.jayantkrish.jklol.inference.JunctionTree;
import com.jayantkrish.jklol.models.dynamic.DynamicAssignment;
import com.jayantkrish.jklol.models.dynamic.VariablePattern;
import com.jayantkrish.jklol.models.parametric.ParametricFactorGraph;
import com.jayantkrish.jklol.models.parametric.SufficientStatistics;
import com.jayantkrish.jklol.training.Trainer;
import com.jayantkrish.jklol.util.Converter;

/**
 * Trains a predictor based on a {@link ParametricFactorGraph}. This class
 * handles data conversion and other common aspects of training a factor graph.
 * 
 * @author jayantk
 * @param <I>
 * @param <O>
 */
public abstract class FactorGraphPredictorTrainer<I, O> implements PredictorTrainer<I, O> {
  
  private final Trainer<ParametricFactorGraph, Example<DynamicAssignment, DynamicAssignment>> trainer;
  private final double parameterPerturbation;
  
  public FactorGraphPredictorTrainer(Trainer<ParametricFactorGraph, Example<DynamicAssignment, DynamicAssignment>> trainer, 
      double parameterPerturbation) {
    this.trainer = Preconditions.checkNotNull(trainer);
    this.parameterPerturbation = parameterPerturbation;
  }

  @Override
  public Predictor<I, O> train(Iterable<Example<I, O>> trainingData) {
    ParametricFactorGraph model = constructGraphicalModel(trainingData);
    
    Converter<I, DynamicAssignment> inputConverter = getInputConverter(model);
    Converter<O, DynamicAssignment> outputConverter = getOutputConverter(model);
    Converter<Example<I, O>, Example<DynamicAssignment, DynamicAssignment>> exampleConverter =
        Example.converter(inputConverter, outputConverter);
    List<Example<DynamicAssignment, DynamicAssignment>> trainingDataAssignments =
        Lists.newArrayList(Iterables.transform(trainingData, exampleConverter));
    
    SufficientStatistics initialParameters = model.getNewSufficientStatistics();
    initialParameters.perturb(parameterPerturbation);
    System.out.println(initialParameters);
    SufficientStatistics finalParameters = trainer.train(model, initialParameters, 
        trainingDataAssignments);
    
    Predictor<DynamicAssignment, DynamicAssignment> assignmentPredictor = 
        new FactorGraphPredictor(model.getFactorGraphFromParameters(finalParameters),
            getOutputVariables(model), new JunctionTree());
    
    return ForwardingPredictor.create(assignmentPredictor, inputConverter, outputConverter); 
  } 

  protected abstract ParametricFactorGraph constructGraphicalModel(
      Iterable<Example<I, O>> trainingData);
  
  protected abstract VariablePattern getOutputVariables(ParametricFactorGraph model);
  
  protected abstract Converter<I, DynamicAssignment> getInputConverter(ParametricFactorGraph model);
  
  protected abstract Converter<O, DynamicAssignment> getOutputConverter(ParametricFactorGraph model);
}
