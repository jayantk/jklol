package com.jayantkrish.jklol.models.dynamic;

import java.util.List;

import com.google.common.base.Preconditions;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.jayantkrish.jklol.models.Variable;
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.models.VariableNumMap.VariableRelabeling;
import com.jayantkrish.jklol.models.VariableProtos.VariablePatternProto;
import com.jayantkrish.jklol.util.IndexedList;

/**
 * A pattern of variables which serves the role of {@link VariableNumMap}s for
 * dynamically-constructed {@code FactorGraph}s. The number of variables and
 * factors in dynamic {@code FactorGraph} s (e.g., sequence models like linear
 * chain CRFs) depends on the instance under consideration. Hence, a fixed set
 * of variables (like a {@code VariableNumMap}) no longer serves to identify
 * these sets of tied variables/factors.
 * <p>
 * {@link DynamicVariableSet} is used to dynamically instantiate variables.
 * {@code VariablePattern} identifies portions of these dynamically-constructed
 * sets and maps them back to a template {@code VariableNumMap}. The variables
 * in the template are used for compiling sufficient statistics, etc. In both
 * cases, each generated/identified variable is parameterized by a set of
 * integer values, which behave like indices into replications of a set of
 * plates.
 * 
 * @author jayantk
 */
public interface VariablePattern {

  /**
   * Identifies all of the variables which match this pattern, and returns them
   * along with the role served by each variable in the match. Matches are
   * returned in order of the matched replication index.
   * 
   * @param allVariables
   * @return
   */
  List<VariableMatch> matchVariables(VariableNumMap inputVariables);

  /**
   * Gets all variables which match this pattern, with any set of input
   * arguments.
   * 
   * @param inputVariables
   * @return
   */
  VariableNumMap getAllMatchingVariables(VariableNumMap inputVariables);

  /**
   * Gets a serialized representation of this.
   * 
   * @param variableTypeIndex
   * @return
   */
  VariablePatternProto toProto(IndexedList<Variable> variableTypeIndex);
  
  /**
   * Represents a match of a variable pattern against a {@code VariableNumMap}.
   * This class maps each matched variable to a variable in a template
   * {@code VariableNumMap}.
   * 
   * @author jayant
   */
  public static class VariableMatch {

    // Variables which match replications of template variables.
    private VariableNumMap allVariables;

    // Mapping from names/indices of matched variables to the names/indices of
    // the corresponding template variables.
    private BiMap<Integer, Integer> variableIndexMap;
    private BiMap<String, String> variableNameMap;

    public VariableMatch(VariableNumMap fixedVariables) {
      this.allVariables = fixedVariables;

      variableIndexMap = HashBiMap.create();
      variableNameMap = HashBiMap.create();
      for (int varNum : fixedVariables.getVariableNums()) {
        variableIndexMap.put(varNum, varNum);
        variableNameMap.put(fixedVariables.getVariableNameFromIndex(varNum),
            fixedVariables.getVariableNameFromIndex(varNum));
      }
    }

    /**
     * Gets a single set of variables matched by a {@code VariableNamePattern}.
     * The returned variables are a subset of variables passed to the matching
     * procedure.
     * 
     * @return
     */
    public VariableNumMap getMatchedVariables() {
      return allVariables;
    }

    /**
     * Gets a mapping from {@link #getMatchedVariables()} to the names and
     * indices of the template variables.
     * 
     * @return
     */
    public VariableRelabeling getMappingToTemplate() {
      return new VariableRelabeling(variableIndexMap, variableNameMap);
    }

    /**
     * Adds a new matching pair of variables to this pattern match.
     * 
     * @param templateVariable
     * @param matchedVariable
     */
    public void addMatch(VariableNumMap templateVariable, VariableNumMap matchedVariable) {
      Preconditions.checkArgument(templateVariable.size() == 1);
      Preconditions.checkArgument(matchedVariable.size() == 1);
      variableIndexMap.put(matchedVariable.getVariableNums().get(0),
          templateVariable.getVariableNums().get(0));
      variableNameMap.put(matchedVariable.getVariableNames().get(0),
          templateVariable.getVariableNames().get(0));
      allVariables = allVariables.union(matchedVariable);
    }

    @Override
    public String toString() {
      return allVariables.toString() + " " + variableIndexMap.toString()
          + " " + variableNameMap.toString();
    }
  }
}