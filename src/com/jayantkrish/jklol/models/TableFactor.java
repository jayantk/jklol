package com.jayantkrish.jklol.models;

import java.util.Iterator;

import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.collect.Iterators;
import com.google.common.primitives.Ints;
import com.jayantkrish.jklol.models.FactorGraphProtos.FactorProto;
import com.jayantkrish.jklol.models.FactorGraphProtos.TableFactorProto;
import com.jayantkrish.jklol.models.VariableNumMap.VariableRelabeling;
import com.jayantkrish.jklol.tensor.DenseTensorBuilder;
import com.jayantkrish.jklol.tensor.LogSpaceTensorAdapter;
import com.jayantkrish.jklol.tensor.Tensor;
import com.jayantkrish.jklol.tensor.Tensors;
import com.jayantkrish.jklol.tensor.TensorBase.KeyValue;
import com.jayantkrish.jklol.util.Assignment;

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
    TableFactorBuilder builder = new TableFactorBuilder(vars);
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
    TableFactorBuilder builder = new TableFactorBuilder(vars);
    return builder.build();
  }

  /**
   * Gets a {@code TableFactor} over {@code vars} which assigns weight 1 to all
   * assignments.
   * 
   * @param vars
   * @return
   */
  public static TableFactor unity(VariableNumMap vars) {
    TableFactorBuilder builder = new TableFactorBuilder(vars);
    return builder.buildInLogSpace();
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
   * Construct a {@code TableFactor} over {@code variables}, deserializing any
   * other values from {@code proto}. In most cases, this method should be used
   * indirectly via {@link FactorGraph#fromProto()}.
   * 
   * @param variables
   * @param proto
   * @return
   */
  public static TableFactor fromProto(VariableNumMap variables, TableFactorProto proto) {
    Preconditions.checkArgument(proto.hasWeights());
    return new TableFactor(variables, Tensors.fromProto(proto.getWeights()));
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
  public FactorProto toProto() {
    FactorProto.Builder builder = getProtoBuilder();
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
