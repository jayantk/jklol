package com.jayantkrish.jklol.models;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import com.google.common.base.Function;
import com.google.common.base.Functions;
import com.google.common.base.Preconditions;
import com.google.common.collect.Iterables;
import com.google.common.collect.Iterators;
import com.google.common.collect.Lists;
import com.google.common.primitives.Ints;
import com.jayantkrish.jklol.models.VariableNumMap.VariableRelabeling;
import com.jayantkrish.jklol.tensor.DenseTensor;
import com.jayantkrish.jklol.tensor.DenseTensorBuilder;
import com.jayantkrish.jklol.tensor.LogSpaceTensorAdapter;
import com.jayantkrish.jklol.tensor.SparseTensorBuilder;
import com.jayantkrish.jklol.tensor.Tensor;
import com.jayantkrish.jklol.tensor.TensorBase.KeyValue;
import com.jayantkrish.jklol.util.Assignment;
import com.jayantkrish.jklol.util.IoUtils;

/**
 * A discrete probability distibution whose unnormalized probabilities
 * are given in a table, represeted as a {@code Tensor}. This factor
 * is the most common type of factor used to represent discrete factor
 * graphs.
 *
 * @author jayantk
 */
public class TableFactor extends DiscreteFactor {

  private static final long serialVersionUID = -3529693448358225350L;
  
  private final Tensor weights;

  /**
   * Constructs a {@code TableFactor} involving the specified variable numbers
   * (whose possible values are in variables). Note that vars can only contain
   * DiscreteVariables.
   */
  public TableFactor(VariableNumMap vars, Tensor weights) {
    super(vars);
    Preconditions.checkArgument(vars.size() == vars.getDiscreteVariables().size());
    Preconditions.checkArgument(vars.getVariableNums().equals(
        Ints.asList(weights.getDimensionNumbers())));
    this.weights = weights;
  }

  /**
   * Gets a {@code TableFactor} over {@code vars} which assigns unit weight to
   * all assignments in {@code assignments} and 0 to all other assignments.
   * Requires each assignment in {@code assignments} to contain all of
   * {@code vars}.
   * 
   * @param vars
   * @param assignment
   * @return
   */
  public static TableFactor pointDistribution(VariableNumMap vars, Assignment... assignments) {
    TableFactorBuilder builder = new TableFactorBuilder(vars, SparseTensorBuilder.getFactory());
    for (int i = 0; i < assignments.length; i++) {
      builder.setWeight(assignments[i], 1.0);
    }
    return builder.build();
  }

  /**
   * Gets a {@code TableFactor} over {@code vars} which assigns unit weight to
   * {@code assignment} and 0 to all other assignments. Requires
   * {@code assignment} to contain all of {@code vars}. The weights in the
   * returned factor are represented in logspace.
   * 
   * @param vars
   * @param assignment
   * @return
   */
  public static TableFactor logPointDistribution(VariableNumMap vars, Assignment assignment) {
    DenseTensorBuilder builder = new DenseTensorBuilder(Ints.toArray(vars.getVariableNums()),
        vars.getVariableSizes(), Double.NEGATIVE_INFINITY);
    builder.put(vars.assignmentToIntArray(assignment), 0.0);
    return new TableFactor(vars, new LogSpaceTensorAdapter(builder.build()));
  }

  /**
   * Gets a {@code TableFactor} over {@code vars} which assigns 0 weight to all
   * assignments.
   * 
   * @param vars
   * @return
   */
  public static TableFactor zero(VariableNumMap vars) {
    TableFactorBuilder builder = new TableFactorBuilder(vars, SparseTensorBuilder.getFactory());
    return builder.build();
  }

  /**
   * Gets a {@code TableFactor} over {@code vars} which assigns weight 1 to all
   * assignments. The weights are represented in log space.
   * 
   * @param vars
   * @return
   */
  public static TableFactor logUnity(VariableNumMap vars) {
    TableFactorBuilder builder = new TableFactorBuilder(vars, SparseTensorBuilder.getFactory());
    return builder.buildInLogSpace();
  }

  /**
   * Gets a {@code TableFactor} over {@code vars} which assigns weight 1 to all
   * assignments.
   * 
   * @param vars
   * @return
   */
  public static TableFactor unity(VariableNumMap vars) {
    return new TableFactor(vars, 
        DenseTensor.constant(vars.getVariableNumsArray(), vars.getVariableSizes(), 1.0));
  }

  public static FactorFactory getFactory() {
    return new FactorFactory() {
      @Override
      public Factor pointDistribution(VariableNumMap vars, Assignment assignment) {
        return TableFactor.pointDistribution(vars, assignment);
      }
    };
  }

  /**
   * Gets a {@code TableFactor} from a series of lines, each describing a single
   * assignment. Each line is {@code delimiter}-separated, and its ith entry is
   * the value of the ith variable in {@code variables}. The last value on each
   * line is the weight.
   * 
   * @param variables
   * @param inputConverters
   * @param lines
   * @param delimiter
   * @param ignoreInvalidAssignments if {@code true}, lines representing invalid
   * assignments to {@code variables} are skipped. If {@code false}, an error is
   * thrown.
   * @return
   */
  public static TableFactor fromDelimitedFile(List<VariableNumMap> variables, 
      List<? extends Function<String, ?>> inputConverters, Iterable<String> lines, String delimiter,
      boolean ignoreInvalidAssignments) {
    int numVars = variables.size();
    VariableNumMap allVars = VariableNumMap.unionAll(variables);
    TableFactorBuilder builder = new TableFactorBuilder(allVars, SparseTensorBuilder.getFactory());
    for (String line : lines) {
      // Ignore blank lines.
      if (line.trim().length() == 0) {
        continue;
      }
      
      String[] parts = line.split(delimiter);
      Preconditions.checkState(parts.length == (numVars + 1), "\"%s\" is incorrectly formatted", line);
      Assignment assignment = Assignment.EMPTY;
      for (int i = 0; i < numVars; i++) {
        Object value = inputConverters.get(i).apply(parts[i].intern());
        assignment = assignment.union(variables.get(i).outcomeArrayToAssignment(value));
      }

      // Check if the assignment is valid, if its not, then don't add it to the
      // feature set
      Preconditions.checkState(ignoreInvalidAssignments || allVars.isValidAssignment(assignment),
          "Invalid assignment: %s", assignment);
      if (!allVars.isValidAssignment(assignment)) {
        continue;
      }

      double weight = Double.parseDouble(parts[numVars]);
      builder.setWeight(assignment, weight);
    }

    return builder.build();
  }
  
  public static TableFactor fromDelimitedFile(List<VariableNumMap> variables, 
      Iterable<String> lines, String delimiter, boolean ignoreInvalidAssignments) {
    List<Function<String, String>> converters = Collections.nCopies(variables.size(), Functions.<String>identity());
    return fromDelimitedFile(variables, converters, lines, delimiter, ignoreInvalidAssignments);
  }
  
  public static TableFactor fromDelimitedFile(VariableNumMap vars, 
      List<? extends Function<String, ?>> inputConverters, Iterable<String> lines,
      String delimiter, boolean ignoreInvalidAssignments) {
    List<VariableNumMap> varList = Lists.newArrayList();
    for (Integer varNum : vars.getVariableNums()) {
      varList.add(vars.intersection(varNum));
    }
    System.out.println(varList);
    return fromDelimitedFile(varList, inputConverters, lines, delimiter, ignoreInvalidAssignments);    
  }
  
  public static TableFactor fromDelimitedFile(VariableNumMap vars, Iterable<String> lines,
      String delimiter, boolean ignoreInvalidAssignments) {
    List<VariableNumMap> varList = Lists.newArrayList();
    for (Integer varNum : vars.getVariableNums()) {
      varList.add(vars.intersection(varNum));
    }
    System.out.println(varList);
    return fromDelimitedFile(varList, lines, delimiter, ignoreInvalidAssignments);
  }
  
  public static TableFactor fromDelimitedFile(Iterable<String> lines, String delimiter) {
    String firstLine = Iterables.getFirst(lines, null);
    int numVars = firstLine.split(",").length - 1;
    List<VariableNumMap> vars = Lists.newArrayList();
    for (int i = 0; i < numVars; i++) {
      DiscreteVariable curVarType = new DiscreteVariable("varType-" + i, 
          IoUtils.readColumnFromDelimitedLines(lines, i, delimiter));
      VariableNumMap curVar = VariableNumMap.singleton(i, "varType-" + i, curVarType);
      vars.add(curVar);
    }

    return fromDelimitedFile(vars, lines, delimiter, false);
  }

  // //////////////////////////////////////////////////////////////////////////////
  // DiscreteFactor overrides.
  // //////////////////////////////////////////////////////////////////////////////

  @Override
  public Iterator<Outcome> outcomeIterator() {
    return mapKeyValuesToOutcomes(weights.keyValueIterator());
  }

  @Override
  public Iterator<Outcome> outcomePrefixIterator(Assignment prefix) {
    int[] keyPrefix = getVars().getFirstVariables(prefix.size()).assignmentToIntArray(prefix);
    return mapKeyValuesToOutcomes(weights.keyValuePrefixIterator(keyPrefix));
  }

  /**
   * Maps an iterator over a tensor's {@code KeyValue}s into {@code Outcome}s.
   */
  private Iterator<Outcome> mapKeyValuesToOutcomes(Iterator<KeyValue> iterator) {
    final Outcome outcome = new Outcome(null, 0.0);
    final VariableNumMap vars = getVars();

    return Iterators.transform(iterator, new Function<KeyValue, Outcome>() {
      @Override
      public Outcome apply(KeyValue keyValue) {
        outcome.setAssignment(vars.intArrayToAssignment(keyValue.getKey()));
        outcome.setProbability(keyValue.getValue());
        return outcome;
      }
    });
  }

  @Override
  public double getUnnormalizedProbability(Assignment a) {
    Preconditions.checkArgument(a.containsAll(getVars().getVariableNums()));
    return weights.getByDimKey(getVars().assignmentToIntArray(a));
  }

  @Override
  public double getUnnormalizedLogProbability(Assignment a) {
    Preconditions.checkArgument(a.containsAll(getVars().getVariableNums()));
    return weights.getLogByDimKey(getVars().assignmentToIntArray(a));
  }

  @Override
  public Tensor getWeights() {
    return weights;
  }

  @Override
  public double size() {
    return weights.size();
  }

  @Override
  public TableFactor relabelVariables(VariableRelabeling relabeling) {
    return new TableFactor(relabeling.apply(getVars()),
        weights.relabelDimensions(relabeling.getVariableIndexReplacementMap()));
  }

  @Override
  public String toString() {
    return "TableFactor(" + getVars() + ")(" + weights.size() + " weights)";
  }
}
