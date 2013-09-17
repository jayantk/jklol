package com.jayantkrish.jklol.probdb;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.tensor.SparseTensor;
import com.jayantkrish.jklol.tensor.SparseTensorBuilder;
import com.jayantkrish.jklol.tensor.Tensor;
import com.jayantkrish.jklol.tensor.TensorBase.KeyValue;
import com.jayantkrish.jklol.util.Assignment;
import com.jayantkrish.jklol.util.CsvParser;

public class TableAssignment { 
  
  private final VariableNumMap vars;
  private final Tensor indicators;
  
  public static final TableAssignment SATISFIABLE = new TableAssignment(
      VariableNumMap.emptyMap(), SparseTensor.getScalarConstant(1.0));
  public static final TableAssignment UNSATISFIABLE = new TableAssignment(
      VariableNumMap.emptyMap(), SparseTensor.getScalarConstant(0.0));
  
  public TableAssignment(VariableNumMap vars, Tensor indicators) {
    this.vars = Preconditions.checkNotNull(vars);
    this.indicators = Preconditions.checkNotNull(indicators);
    
    Preconditions.checkArgument(vars.getDiscreteVariables().size() == vars.size());
    Preconditions.checkArgument(Arrays.equals(vars.getVariableNumsArray(), indicators.getDimensionNumbers()));
  }

  public static TableAssignment fromDelimitedLines(VariableNumMap vars, Iterable<String> lines) {
    Preconditions.checkArgument(vars.getDiscreteVariables().size() == vars.size());
    CsvParser parser = CsvParser.defaultParser();
    SparseTensorBuilder builder = new SparseTensorBuilder(vars.getVariableNumsArray(),
        vars.getVariableSizes());
    for (String line : lines) {
      String[] parts = parser.parseLine(line);
      Assignment assignment = vars.outcomeToAssignment(parts);
      builder.put(vars.assignmentToIntArray(assignment), 1.0);
    }
    return new TableAssignment(vars, builder.build());
  }
  
  public VariableNumMap getVariables() {
    return vars;
  }
  
  public Tensor getIndicators() {
    return indicators;
  }
  
  /**
   * Gets the tuples which are in this assignment.
   * @return
   */
  public List<List<Object>> getTuples() {
    Iterator<KeyValue> keyValueIter = indicators.keyValueIterator();
    List<List<Object>> tuples = Lists.newArrayList();
    while (keyValueIter.hasNext()) {
      KeyValue keyValue = keyValueIter.next();
      if (keyValue.getValue() != 0.0) {
        tuples.add(vars.intArrayToAssignment(keyValue.getKey()).getValues());
      }
    }
    return tuples;
  }
  
  public TableAssignment relabelVariables(int[] relabeling) {
    VariableNumMap relabeledVars = vars.relabelVariableNums(relabeling);
    Tensor relabeledTensor = indicators.relabelDimensions(relabeling);
    
    return new TableAssignment(relabeledVars, relabeledTensor);
  }
  
  @Override
  public String toString() {
    Iterator<KeyValue> keyValueIter = indicators.keyValueIterator();
    StringBuilder sb = new StringBuilder();
    while (keyValueIter.hasNext()) {
      KeyValue keyValue = keyValueIter.next();
      if (keyValue.getValue() != 0.0) {
        sb.append(vars.intArrayToAssignment(keyValue.getKey()).getValues());
      }
    }
    return sb.toString();
  }
}
