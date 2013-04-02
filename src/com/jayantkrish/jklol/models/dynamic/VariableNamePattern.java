package com.jayantkrish.jklol.models.dynamic;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.SortedMap;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.primitives.Ints;
import com.google.gwt.regexp.shared.RegExp;
import com.google.gwt.regexp.shared.MatchResult;
import com.jayantkrish.jklol.models.VariableNumMap;

/**
 * A pattern of variable names which serves the role of {@link VariableNumMap}s
 * for dynamically-constructed {@code FactorGraph}s. The number of variables and
 * factors in dynamic {@code FactorGraph} s (e.g., sequence models like linear
 * chain CRFs) depends on the instance under consideration. Hence, a fixed set
 * of variables (like a {@code VariableNumMap}) no longer serves to identify
 * these sets of tied variables/factors.
 * 
 * {@link DynamicVariableSet} is used to dynamically instantiate variables.
 * {@code VariableNamePattern} identifies these dynamically constructed sets in
 * existing {@code FactorGraph}s, for compiling sufficient statistics, etc. In
 * both cases, each generated/identified variable is parameterized by a set of
 * integer values, which behave like indices into replications of a set of
 * plates.
 * 
 * @author jayantk
 */
public class VariableNamePattern extends AbstractVariablePattern {
 
  private static final long serialVersionUID = 1L;

  // These variables may be instantiated multiple times with varying names.
  private final VariableNumMap templateVariables;
  private final ImmutableList<VariableNameMatcher> templateVariableMatchers;

  // These variables are instantiated exactly once.
  private final VariableNumMap fixedVariables;

  public VariableNamePattern(List<VariableNameMatcher> templateVariableMatchers,
      VariableNumMap templateVariables, VariableNumMap fixedVariables) {
    super();

    Preconditions.checkArgument(templateVariableMatchers.size() == templateVariables.size());
    this.templateVariableMatchers = ImmutableList.copyOf(templateVariableMatchers);
    this.fixedVariables = Preconditions.checkNotNull(fixedVariables);
    this.templateVariables = templateVariables;
  }

  /**
   * Gets a {@code VariableNamePattern} which uses the names in {@code templateVars}
   * as the name templates for matching variables. The names are specified in a
   * rudimentary pattern language: if a name contains a "?(x)", this portion is
   * allowed to match any integer value. x is a number which is an integer
   * offset for the match.
   * 
   * @param variables
   * @return
   */
  public static VariableNamePattern fromTemplateVariables(VariableNumMap templateVariables,
      VariableNumMap fixedVariables) {
    List<VariableNameMatcher> matchers = Lists.newArrayList();
    for (String variableName : templateVariables.getVariableNames()) {
      int varIndex = variableName.indexOf("?(");
      String variableNamePrefix = variableName.substring(0, varIndex);
      String variableNameSuffix = variableName.substring(varIndex + 1);

      int offsetIndex = variableNameSuffix.indexOf(")");
      int offset = Integer.parseInt(variableNameSuffix.substring(1, offsetIndex));
      variableNameSuffix = variableNameSuffix.substring(offsetIndex + 1);
      matchers.add(new VariableNameMatcher(variableNamePrefix, variableNameSuffix, offset));
    }
    return new VariableNamePattern(matchers, templateVariables, fixedVariables);
  }

  /**
   * Gets a {@code VariableNamePattern} which matches {@code plateVariables} in each
   * replication of {@code plateName}. {@code fixedVariables} are added to each
   * match as well.
   * 
   * @param plateName
   * @param plateVariables
   * @param fixedVariables
   * @return
   */
  public static VariablePattern fromPlate(String plateName, VariableNumMap plateVariables,
      VariableNumMap fixedVariables) {
    List<VariableNameMatcher> matchers = Lists.newArrayList();
    for (String variableName : plateVariables.getVariableNames()) {
      matchers.add(new VariableNameMatcher(plateName + "/", "/" + variableName, 0));
    }
    return new VariableNamePattern(matchers, plateVariables, fixedVariables);
  }

  /**
   * Gets the variables which may be matched multiple times in the factor graph.
   * 
   * @return
   */
  public VariableNumMap getTemplateVariables() {
    return templateVariables;
  }

  /**
   * Gets the variables which are matched exactly once in the factor graph.
   * 
   * @return
   */
  public VariableNumMap getFixedVariables() {
    return fixedVariables;
  }

  @Override
  public List<VariableMatch> matchVariables(VariableNumMap inputVariables) {
    // All of the fixed variables must be matched in order to return anything.
    if (!inputVariables.containsAll(fixedVariables)) {
      return Collections.emptyList();
    }
    // Special case: if there aren't any templates, then only the fixed
    // variables should be returned.
    if (templateVariableMatchers.size() == 0) {
      return Arrays.asList(new VariableMatch(fixedVariables));
    }

    // Find all variables which begin with a prefix in variableNamePrefixes,
    // identify their replication index, and aggregate the matches by
    // replication index.
    SortedMap<Integer, VariableMatch> variableMatches = Maps.newTreeMap();
    for (int i = 0; i < templateVariableMatchers.size(); i++) {
      int templateVariableIndex = templateVariables.getVariableNums().get(i);

      for (String variableName : inputVariables.getVariableNames()) {
        for (Integer replicationIndex : templateVariableMatchers.get(i).getMatchedIndices(variableName)) {
          if (!variableMatches.containsKey(replicationIndex)) {
            variableMatches.put(replicationIndex, new VariableMatch(fixedVariables));
          }
          variableMatches.get(replicationIndex).addMatch(
              templateVariables.intersection(Ints.asList(templateVariableIndex)),
              inputVariables.getVariablesByName(variableName));
        }
      }
    }

    // Eliminate any partial matches which do not contain a match for every
    // variable prefix.
    List<VariableMatch> validMatches = Lists.newArrayList();
    VariableNumMap allMatchVariables = templateVariables.union(fixedVariables);
    for (VariableMatch match : variableMatches.values()) {
      if (match.getMatchedVariables().size() == allMatchVariables.size()) {
        validMatches.add(match);
      }
    }
    return validMatches;
  }
  
  public static class VariableNameMatcher implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private final String pattern;
    private final int indexOffset;

    public VariableNameMatcher(String variableNamePrefix, String variableNameSuffix, int indexOffset) {
      this.indexOffset = indexOffset;
      this.pattern = variableNamePrefix + "(\\d+)" + variableNameSuffix;
    }
    
    public VariableNameMatcher(String pattern, int indexOffset) {
      this.indexOffset = indexOffset;
      this.pattern = pattern;
    }

    /**
     * Gets a set of replication indices which {@code variableName} matches, if
     * any.
     * 
     * @param variableName
     * @return
     */
    public Collection<Integer> getMatchedIndices(String variableName) {
      RegExp regexp = RegExp.compile(pattern);
      MatchResult m = regexp.exec(variableName);
      if (m != null) {
        int originalIndex = Integer.parseInt(m.getGroup(1));
        return Ints.asList(originalIndex - indexOffset);
      }
      return Collections.emptyList();
    }

    /**
     * Gets the replication index associated with this matcher.
     * 
     * @return
     */
    public Integer getOffset() {
      return indexOffset;
    }
    
    public String getPattern() {
      return pattern;
    }
  }
}
