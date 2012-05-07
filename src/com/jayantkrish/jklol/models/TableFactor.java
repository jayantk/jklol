package com.jayantkrish.jklol.models;

import java.util.Iterator;
import java.util.List;

import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.collect.Iterators;
import com.google.common.primitives.Ints;
import com.jayantkrish.jklol.models.FactorGraphProtos.FactorProto;
import com.jayantkrish.jklol.models.FactorGraphProtos.TableFactorProto;
import com.jayantkrish.jklol.models.VariableNumMap.VariableRelabeling;
import com.jayantkrish.jklol.tensor.DenseTensorBuilder;
import com.jayantkrish.jklol.tensor.LogSpaceTensorAdapter;
import com.jayantkrish.jklol.tensor.SparseTensorBuilder;
import com.jayantkrish.jklol.tensor.Tensor;
import com.jayantkrish.jklol.tensor.TensorBase.KeyValue;
import com.jayantkrish.jklol.tensor.Tensors;
import com.jayantkrish.jklol.util.AllAssignmentIterator;
import com.jayantkrish.jklol.util.Assignment;
import com.jayantkrish.jklol.util.IndexedList;

/**
 * A TableFactor is a representation of a factor where each weight is set
 * beforehand. The internal representation is sparse, making it appropriate for
 * factors where many weight settings are 0.
 * 
 * TableFactors are immutable.
 */
public class TableFactor extends DiscreteFactor {

  private final Tensor weights;

  /**
   * Constructs a {@code TableFactor} involving the specified variable numbers
   * (whose possible values are in variables). Note that vars can only contain
   * DiscreteVariables.
   */
  public TableFactor(VariableNumMap vars, Tensor weights) {
    super(vars);
    Preconditions.checkArgument(vars.size() == vars.getDiscreteVariables().size());
    Preconditions.checkArgument(vars.size() == weights.getDimensionNumbers().length);
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
    TableFactorBuilder builder = new TableFactorBuilder(vars, SparseTensorBuilder.getFactory());
    Iterator<Assignment> iterator = new AllAssignmentIterator(vars);
    while (iterator.hasNext()) {
      builder.setWeight(iterator.next(), 1.0);
    }
    return builder.build();
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
   * Construct a {@code TableFactor} over {@code variables} by deserializing the
   * factor's weights from {@code proto}. In most cases, this method should be
   * used indirectly via {@link FactorGraph#fromProto()}.
   * 
   * @param variables
   * @param proto
   * @return
   */
  public static TableFactor fromProto(FactorProto proto, IndexedList<Variable> variableTypeIndex) {
    Preconditions.checkArgument(proto.hasVariables());
    Preconditions.checkArgument(proto.getType().equals(FactorProto.FactorType.TABLE));
    Preconditions.checkArgument(proto.hasTableFactor());
    Preconditions.checkArgument(proto.getTableFactor().hasWeights());
    
    VariableNumMap variables = VariableNumMap.fromProto(proto.getVariables(), variableTypeIndex);
    return new TableFactor(variables, Tensors.fromProto(proto.getTableFactor().getWeights()));
  }

  /**
   * Gets a {@code TableFactor} from a series of lines, each describing a single
   * assignment. Each line is {@code delimiter}-separated, and its ith entry is the value of
   * the ith variable in {@code variables}. The last value on each line is the
   * weight.
   * 
   * @param variables
   * @param lines
   * @return
   */
  public static TableFactor fromDelimitedFile(List<VariableNumMap> variables, Iterable<String> lines, 
      String delimiter) {
    int numVars = variables.size();
    VariableNumMap allVars = VariableNumMap.unionAll(variables);
    TableFactorBuilder builder = new TableFactorBuilder(allVars, SparseTensorBuilder.getFactory());
    for (String line : lines) {
      String[] parts = line.split(delimiter); 
      Preconditions.checkState(parts.length == (numVars + 1), "\"%s\" is incorrectly formatted", line); 
      Assignment assignment = Assignment.EMPTY;
      for (int i = 0; i < numVars; i++) {
        assignment = assignment.union(variables.get(i).outcomeArrayToAssignment(parts[i]));
      }
      Preconditions.checkState(allVars.isValidAssignment(assignment));
      double weight = Double.parseDouble(parts[numVars]);
      builder.setWeight(assignment, weight);
    }
    return builder.build();
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
  public FactorProto toProto(IndexedList<Variable> variableTypeIndex) {
    FactorProto.Builder builder = getProtoBuilder(variableTypeIndex);
    builder.setType(FactorProto.FactorType.TABLE);

    TableFactorProto.Builder tableBuilder = builder.getTableFactorBuilder();
    tableBuilder.setWeights(weights.toProto());

    return builder.build();
  }

  @Override
  public String toString() {
    return "TableFactor(" + getVars() + ")(" + weights.size() + " weights)";
  }
}
