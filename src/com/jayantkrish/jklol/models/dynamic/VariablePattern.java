package com.jayantkrish.jklol.models.dynamic;

import java.io.Serializable;
import java.util.Arrays;
import java.util.List;

import com.google.common.base.Preconditions;
import com.google.common.primitives.Ints;
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.models.VariableNumMap.VariableRelabeling;
import com.jayantkrish.jklol.util.IntBiMap;

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
public interface VariablePattern extends Serializable {

  /**
   * Identifies all of the variables which match this pattern, and returns them
   * along with the role served by each variable in the match. Matches are
   * returned in order of the matched replication index.
   * 
   * @param matchedVariables
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
   * Represents a match of a variable pattern against a {@code VariableNumMap}.
   * This class maps each matched variable to a variable in a template
   * {@code VariableNumMap}.
   * 
   * @author jayant
   */
  public static class VariableMatch {
    // Variables which match replications of template variables.
    private final VariableNumMap matchedVariables;
    private final VariableNumMap templateVariables;
    
    // Mapping from names/indices of matched variables to the names/indices of
    // the corresponding template variables.
    private final VariableRelabeling relabeling;

    public VariableMatch(VariableNumMap matchedVariables, VariableNumMap templateVariables, 
        VariableRelabeling relabeling) {
      this.matchedVariables = Preconditions.checkNotNull(matchedVariables);
      this.templateVariables = Preconditions.checkNotNull(templateVariables);
      this.relabeling = Preconditions.checkNotNull(relabeling);
    }
    
    public static final VariableMatch identity(VariableNumMap vars) {
      return new VariableMatch(vars, vars, VariableRelabeling.identity(vars));
    }

    /**
     * Gets a single set of variables matched by a {@code VariableNamePattern}.
     * The returned variables are a subset of variables passed to the matching
     * procedure.
     * 
     * @return
     */
    public VariableNumMap getMatchedVariables() {
      return matchedVariables;
    }
    
    public VariableNumMap getTemplateVariables() {
      return templateVariables;
    }

    /**
     * Gets a mapping from {@link #getMatchedVariables()} to the names and
     * indices of the template variables.
     * 
     * @return
     */
    public VariableRelabeling getMappingToTemplate() {
      return relabeling;
    }
    
    public VariableNumMap getMatchedVariablesFromTemplateVariables(VariableNumMap templateVariables) {
      VariableRelabeling inverseRelabeling = getMappingToTemplate().inverse(); 
      return inverseRelabeling.apply(templateVariables).intersection(matchedVariables);
    }
  }

  public static class VariableMatchBuilder {
    private final int[] templateVariableNums;
    private final int[] matchedVariableNums;
    
    public VariableMatchBuilder(int[] templateVariableNums) {
      this.templateVariableNums = templateVariableNums;
      this.matchedVariableNums = new int[templateVariableNums.length];
      Arrays.fill(matchedVariableNums, -1);
    }

    /**
     * Adds a new matching pair of variables to this pattern match.
     * 
     * @param templateVariable
     * @param matchedVariable
     */
    public void addMatch(int templateVariableNum, int matchedVariableNum) {
      int index = Arrays.binarySearch(templateVariableNums, templateVariableNum);
      matchedVariableNums[index] = matchedVariableNum;
    }
    
    /**
     * Returns {@code true} if all of the template variables have been
     * matched to an input variable.
     * 
     * @return
     */
    public boolean isComplete() {
      return Ints.indexOf(matchedVariableNums, -1) == -1;
    }

    public VariableMatch build(VariableNumMap fixedVariables, VariableNumMap templateVariables,
        VariableNumMap variablesToMatch) {
      VariableNumMap matchedVariables = variablesToMatch.intersection(matchedVariableNums);
      
      VariableRelabeling fixedVariableRelabeling = VariableRelabeling.identity(fixedVariables);

      int[] templateCopy = Arrays.copyOf(templateVariableNums, templateVariableNums.length);
      int[] matchedCopy = Arrays.copyOf(matchedVariableNums, matchedVariableNums.length);

      IntBiMap mapping = IntBiMap.fromUnsortedKeyValues(matchedCopy, templateCopy);
      VariableRelabeling matchedRelabeling = new VariableRelabeling(matchedVariables,
          templateVariables, mapping);
      return new VariableMatch(matchedVariables.union(fixedVariables),
          templateVariables.union(fixedVariables), fixedVariableRelabeling.union(matchedRelabeling));
    }
  }
}