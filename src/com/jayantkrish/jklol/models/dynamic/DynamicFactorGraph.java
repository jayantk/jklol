package com.jayantkrish.jklol.models.dynamic;

import java.util.List;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.jayantkrish.jklol.models.Factor;
import com.jayantkrish.jklol.models.FactorGraph;
import com.jayantkrish.jklol.models.Variable;
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.models.VariableProtos.VariableProto;
import com.jayantkrish.jklol.models.Variables;
import com.jayantkrish.jklol.models.dynamic.DynamicFactorGraphProtos.DynamicFactorGraphProto;
import com.jayantkrish.jklol.models.dynamic.DynamicFactorGraphProtos.PlateFactorProto;
import com.jayantkrish.jklol.util.Assignment;
import com.jayantkrish.jklol.util.IndexedList;

/**
 * Represents a family of conditional {@link FactorGraph}s whose structure
 * depends on the input values. Examples of {@code DynamicFactorGraph}s include
 * sequence models, where the number of nodes in the graphical model depends on
 * the length of the input sequence.
 * 
 * The input to a {@code DynamicFactorGraph}s is a {@code DynamicAssignment},
 * which is a generalization of {@code Assignment}.
 * 
 * @author jayantk
 */
public class DynamicFactorGraph {

  private final DynamicVariableSet variables;

  // Plates represent graphical model structure which is replicated in a
  // data-dependent fashion.
  private final ImmutableList<PlateFactor> plateFactors;
  private final ImmutableList<String> factorNames;

  public DynamicFactorGraph(DynamicVariableSet variables, List<PlateFactor> plateFactors,
      List<String> factorNames) {
    this.variables = variables;
    this.plateFactors = ImmutableList.copyOf(plateFactors);
    this.factorNames = ImmutableList.copyOf(factorNames);
  }

  public DynamicVariableSet getVariables() {
    return variables;
  }

  public static DynamicFactorGraph fromFactorGraph(FactorGraph factorGraph) {
    DynamicVariableSet variables = DynamicVariableSet.fromVariables(factorGraph.getVariables());

    List<PlateFactor> plateFactors = Lists.newArrayList();
    for (Factor factor : factorGraph.getFactors()) {
      plateFactors.add(ReplicatedFactor.fromFactor(factor));
    }
    return new DynamicFactorGraph(variables, plateFactors, factorGraph.getFactorNames());
  }

  public FactorGraph conditional(DynamicAssignment assignment) {
    FactorGraph factorGraph = getFactorGraph(assignment);
    Assignment factorGraphAssignment = variables.toAssignment(assignment);
    return factorGraph.conditional(factorGraphAssignment);
  }

  public FactorGraph getFactorGraph(DynamicAssignment assignment) {
    VariableNumMap factorGraphVariables = variables.instantiateVariables(assignment);

    // Instantiate factors.
    List<Factor> factors = Lists.newArrayList();
    List<String> instantiatedNames = Lists.newArrayList();
    for (int i = 0; i < plateFactors.size(); i++) {
      PlateFactor plateFactor = plateFactors.get(i);
      List<Factor> replications = plateFactor.instantiateFactors(factorGraphVariables);
      factors.addAll(replications);
      
      for (int j = 0; j < replications.size(); j++) {
        instantiatedNames.add(factorNames.get(i) + "-" + j);
      }
    }

    return new FactorGraph(factorGraphVariables, factors, instantiatedNames,
        VariableNumMap.emptyMap(), Assignment.EMPTY);
  }

  public DynamicFactorGraph addPlateFactors(List<PlateFactor> factors, List<String> newFactorNames) {
    List<PlateFactor> allFactors = Lists.newArrayList(plateFactors);
    allFactors.addAll(factors);
    List<String> allNames = Lists.newArrayList(factorNames);
    allNames.addAll(newFactorNames);
    return new DynamicFactorGraph(getVariables(), allFactors, allNames);
  }
  
  // Serialization methods.
  
  public DynamicFactorGraphProto toProto() {
    IndexedList<Variable> variableTypeIndex = IndexedList.create(); 

    DynamicFactorGraphProto.Builder builder = toProtoBuilder(variableTypeIndex);
    
    for (Variable variable : variableTypeIndex.items()) {
      builder.addVariableType(variable.toProto());
    }
    
    return builder.build();
  }
  
  public DynamicFactorGraphProto.Builder toProtoBuilder(
      IndexedList<Variable> variableTypeIndex) {
    DynamicFactorGraphProto.Builder builder = DynamicFactorGraphProto.newBuilder();
    builder.setVariables(variables.toProto(variableTypeIndex));

    for (PlateFactor plateFactor : plateFactors) {
      builder.addFactor(plateFactor.toProto(variableTypeIndex));
    }
    
    builder.addAllFactorName(factorNames);

    return builder;
  }
  
  public static DynamicFactorGraph fromProto(DynamicFactorGraphProto proto) {
    IndexedList<Variable> variableTypeIndex = IndexedList.create();
    for (VariableProto variableProto : proto.getVariableTypeList()) {
      variableTypeIndex.add(Variables.fromProto(variableProto));
    }
    
    return fromProtoWithVariables(proto, variableTypeIndex);
  }
  
  public static DynamicFactorGraph fromProtoWithVariables(DynamicFactorGraphProto proto,
      IndexedList<Variable> variableTypeIndex) {
    DynamicVariableSet variableSet = DynamicVariableSet.fromProto(proto.getVariables(), variableTypeIndex);

    List<PlateFactor> plateFactors = Lists.newArrayList();
    for (PlateFactorProto factorProto : proto.getFactorList()) {
      plateFactors.add(PlateFactors.fromProto(factorProto, variableTypeIndex));
    }

    return new DynamicFactorGraph(variableSet, plateFactors, proto.getFactorNameList());
  }
}
