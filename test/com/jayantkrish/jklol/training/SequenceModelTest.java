package com.jayantkrish.jklol.training;

import java.util.Arrays;
import java.util.List;

import junit.framework.TestCase;

import com.google.common.collect.Lists;
import com.google.common.primitives.Ints;
import com.jayantkrish.jklol.evaluation.Example;
import com.jayantkrish.jklol.models.DiscreteVariable;
import com.jayantkrish.jklol.models.ObjectVariable;
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.models.dynamic.Plate;
import com.jayantkrish.jklol.models.dynamic.VariablePattern;
import com.jayantkrish.jklol.models.loglinear.ConditionalLogLinearFactor;
import com.jayantkrish.jklol.models.loglinear.DiscreteLogLinearFactor;
import com.jayantkrish.jklol.models.loglinear.LogLinearModelBuilder;
import com.jayantkrish.jklol.models.parametric.ParametricFactorGraph;
import com.jayantkrish.jklol.tensor.SparseTensor;
import com.jayantkrish.jklol.tensor.Tensor;
import com.jayantkrish.jklol.util.Assignment;

/**
 * Regression test for training and predicting with sequence models.
 * 
 * @author jayantk
 */
public class SequenceModelTest extends TestCase {
  ParametricFactorGraph sequenceModel;
  VariableNumMap x, y, all;
  List<Example<Assignment, Assignment>> trainingData;

  public void setUp() {
    LogLinearModelBuilder builder = new LogLinearModelBuilder();

    DiscreteVariable outputVar = new DiscreteVariable("tf",
        Arrays.asList("T", "F"));
    ObjectVariable tensorVar = new ObjectVariable(Tensor.class);
    ObjectVariable listVar = new ObjectVariable(List.class);
    
    builder.addVariable("plateVar", listVar);
    VariableNumMap plateVar = builder.getVariables().getVariablesByName("plateVar");
    VariableNumMap all = new VariableNumMap(Ints.asList(0, 1), 
        Arrays.asList("x", "y"), Arrays.asList(tensorVar, outputVar));
    Plate plate = new Plate(plateVar, 
        VariablePattern.fromTemplateVariables(all, VariableNumMap.emptyMap()));
    builder.addPlate(plate);
 
    x = all.getVariablesByName("x");
    y = all.getVariablesByName("y");
    
    ConditionalLogLinearFactor f = new ConditionalLogLinearFactor(x, y, 4);
    builder.addFactor(f, VariablePattern.fromTemplateVariables(all, VariableNumMap.emptyMap()));
    
    VariableNumMap adjacentVars = new VariableNumMap(Ints.asList(0, 1), 
        Arrays.asList("y+0", "y+1"), Arrays.asList(outputVar, outputVar));
    builder.addFactor(DiscreteLogLinearFactor.createIndicatorFactor(adjacentVars),
        VariablePattern.fromTemplateVariables(adjacentVars, VariableNumMap.emptyMap()));

    sequenceModel = builder.build();
    
    // Construct some training data.
    List<Assignment> inputAssignments = Lists.newArrayList();
    for (int i = 0; i < 8; i++) {
      double[] values = new double[4];
      values[0] = (i % 2) * 2 - 1;
      values[1] = ((i / 2) % 2) * 2 - 1;
      values[2] = ((i / 4) % 2) * 2 - 1;
      values[3] = 1;
      inputAssignments.add(x.outcomeArrayToAssignment(SparseTensor.vector(0, 4, values)));
    }
    
    trainingData = Lists.newArrayList();
    trainingData.add(Example.create(inputAssignments.get(0), y.outcomeArrayToAssignment("F")));
    trainingData.add(Example.create(inputAssignments.get(3), y.outcomeArrayToAssignment("T")));
  }  
}
