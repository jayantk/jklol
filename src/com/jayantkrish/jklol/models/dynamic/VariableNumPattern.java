package com.jayantkrish.jklol.models.dynamic;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.primitives.Ints;
import com.jayantkrish.jklol.models.VariableNumMap;

public class VariableNumPattern extends AbstractVariablePattern {
  private static final long serialVersionUID = 1L;

  // Matchers against the indexes of variables, instantiated from a
  // particular dynamic variable set.
  private final int[] plateStarts;
  private final int[] plateEnds;
  private final int[] plateReplicationSizes;
  private final int[] plateVarOffsets;
  private final int[] plateMatchIndexOffsets;
  private final VariableNumMap templateVariables;
  
  // These variables are instantiated exactly once.
  private final VariableNumMap fixedVariables;

  public VariableNumPattern(int[] plateStarts, int[] plateEnds, int[] plateReplicationSizes,
      int[] plateVarOffsets, int[] plateMatchIndexOffsets, VariableNumMap templateVariables,
      VariableNumMap fixedVariables) {
    super();

    Preconditions.checkArgument(plateStarts.length == plateEnds.length);
    Preconditions.checkArgument(plateStarts.length == plateReplicationSizes.length);
    Preconditions.checkArgument(plateStarts.length == plateVarOffsets.length);
    Preconditions.checkArgument(plateStarts.length == plateMatchIndexOffsets.length);
    Preconditions.checkArgument(plateStarts.length == templateVariables.size());

    this.plateStarts = plateStarts;
    this.plateEnds = plateEnds;
    this.plateReplicationSizes = plateReplicationSizes;
    this.plateVarOffsets = plateVarOffsets;
    this.plateMatchIndexOffsets = plateMatchIndexOffsets;
    this.templateVariables = templateVariables;

    this.fixedVariables = Preconditions.checkNotNull(fixedVariables);
    Preconditions.checkArgument(fixedVariables.intersection(
        templateVariables.getVariableNumsArray()).size() == 0);
  }

  /**
   * Gets a {@code VariableNumPattern} which uses the names in {@code templateVars}
   * as the name templates for matching variables. The names are specified in a
   * rudimentary pattern language: if a name contains a "?(x)", this portion is
   * allowed to match any integer value. x is a number which is an integer
   * offset for the match.
   * <p>
   * The returned pattern only works when applied to {@code variableSet}.
   * 
   * @param templateVariables
   * @param fixedVariables
   * @param variableSet
   * @return
   */
  public static VariableNumPattern fromTemplateVariables(VariableNumMap templateVariables,
      VariableNumMap fixedVariables, DynamicVariableSet variableSet) {
    
    int[] plateStarts = new int[templateVariables.size()];
    int[] plateEnds = new int[templateVariables.size()];
    int[] plateReplicationSizes = new int[templateVariables.size()];
    int[] plateVarOffsets = new int[templateVariables.size()];
    int[] plateMatchIndexOffsets = new int[templateVariables.size()];

    int[] templateVariableNums = templateVariables.getVariableNumsArray();
    String[] templateVariableNames = templateVariables.getVariableNamesArray();
    for (int i = 0; i < templateVariableNums.length; i++) {
      String variableName = templateVariableNames[i];
      String[] parts = variableSet.partitionVariableName(variableName);
      
      Preconditions.checkArgument(parts.length == 3);
      String plateName = parts[0];
      int varOffset = Integer.parseInt(parts[1].replaceAll("[?()]", ""));
      String withinPlateVariableName = parts[2];
      
      plateStarts[i] = variableSet.getPlateStartIndex(plateName);
      plateEnds[i] = variableSet.getPlateEndIndex(plateName);
      plateReplicationSizes[i] = variableSet.getPlate(plateName).getMaximumPlateSize();
      plateVarOffsets[i] = variableSet.getPlate(plateName).getFixedVariables()
          .getVariableByName(withinPlateVariableName);
      plateMatchIndexOffsets[i] = varOffset;
    }
    
    // Normalize the index offsets so the maximum offset is 0.
    if (plateMatchIndexOffsets.length > 0) {
      int maxOffset = Ints.max(plateMatchIndexOffsets);
      for (int i = 0; i < plateMatchIndexOffsets.length; i++) {
        plateMatchIndexOffsets[i] -= maxOffset;
      }
    }
    
    return new VariableNumPattern(plateStarts, plateEnds, plateReplicationSizes, plateVarOffsets,
        plateMatchIndexOffsets, templateVariables, fixedVariables);
  }

  @Override
  public List<VariableMatch> matchVariables(VariableNumMap inputVariables) {
    // All of the fixed variables must be matched in order to return anything.
    if (!inputVariables.containsAll(fixedVariables)) {
      return Collections.emptyList();
    }
    // Special case: if there aren't any templates, then only the fixed
    // variables should be returned.
    if (plateStarts.length == 0) {
      return Arrays.asList(VariableMatch.identity(fixedVariables));
    }

    int maxPlateReplication = -1;
    int[] inputVarNums = inputVariables.getVariableNumsArray();
    int numVars = inputVarNums.length;
    int numMatchers = plateStarts.length;
    for (int i = 0; i < numMatchers; i++) {
      for (int j = 0; j < numVars; j++) {
        if (inputVarNums[j] >= plateStarts[i] && inputVarNums[j] < plateEnds[i]) {
          int withinPlateIndex = inputVarNums[j] - plateStarts[i];
          int plateReplicationNum = withinPlateIndex / plateReplicationSizes[i];
          int replicationIndex = plateReplicationNum - plateMatchIndexOffsets[i];
          maxPlateReplication = Math.max(replicationIndex, maxPlateReplication);
        }
      }
    }

    // Aggregate pattern matches for each variable.
    int[] templateVarNums = templateVariables.getVariableNumsArray();
    VariableMatchBuilder[] variableMatches = new VariableMatchBuilder[maxPlateReplication + 1];
    for (int i = 0; i < numMatchers; i++) {      
      for (int j = 0; j < numVars; j++) {
        if (inputVarNums[j] >= plateStarts[i] && inputVarNums[j] < plateEnds[i]) {
          // The variable is within the plate we're matching.
          int withinPlateIndex = inputVarNums[j] - plateStarts[i];
          int plateReplicationNum = withinPlateIndex / plateReplicationSizes[i];
          int varOffset = withinPlateIndex % plateReplicationSizes[i];
          if (varOffset == plateVarOffsets[i]) {
            // The variable matches the pattern.
            int replicationIndex = plateReplicationNum - plateMatchIndexOffsets[i];
            if (variableMatches[replicationIndex] == null) {
              variableMatches[replicationIndex] = new VariableMatchBuilder(templateVarNums);
            }
            variableMatches[replicationIndex].addMatch(templateVarNums[i], inputVarNums[j]);
          }
        }
      }
    }

    // Eliminate any partial matches which do not contain a match for every
    // variable prefix.
    List<VariableMatch> validMatches = Lists.newArrayList();
    int numMatches = variableMatches.length;
    for (int i = 0; i < numMatches; i++) {
      VariableMatchBuilder matchBuilder = variableMatches[i];
      if (matchBuilder != null && matchBuilder.isComplete()) {
        validMatches.add(matchBuilder.build(fixedVariables, templateVariables, inputVariables));
      }
    }
    return validMatches;
  }
}
