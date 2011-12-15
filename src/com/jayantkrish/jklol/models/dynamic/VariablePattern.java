package com.jayantkrish.jklol.models.dynamic;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.SortedMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.common.base.Preconditions;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.primitives.Ints;
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.models.VariableNumMap.VariableRelabeling;

/**
 * A pattern of variable names which serves the role of {@link VariableNumMap}s
 * for dynamically-constructed {@code FactorGraph}s. The number of variables and
 * factors in dynamic {@code FactorGraph} s (e.g., sequence models like linear
 * chain CRFs) depends on the instance under consideration. Hence, a fixed set
 * of variables (like a {@code VariableNumMap}) no longer serves to identify
 * these sets of tied variables/factors.
 * 
 * {@link DynamicVariableSet} is used to dynamically instantiate variables.
 * {@code VariablePattern} identifies these dynamically constructed sets in
 * existing {@code FactorGraph}s, for compiling sufficient statistics, etc. In
 * both cases, each generated/identified variable is parameterized by a set of
 * integer values, which behave like indices into replications of a set of
 * plates.
 * 
 * @author jayantk
 */
public class VariablePattern {

  // These variables may be instantiated multiple times with varying names.
  private final VariableNumMap templateVariables;
  private final ImmutableList<VariableNameMatcher> templateVariableMatchers;

  // These variables are instantiated exactly once.
  private final VariableNumMap fixedVariables;

  public VariablePattern(List<VariableNameMatcher> templateVariableMatchers,
      VariableNumMap templateVariables, VariableNumMap fixedVariables) {
    Preconditions.checkArgument(templateVariableMatchers.size() == templateVariables.size());
    this.templateVariableMatchers = ImmutableList.copyOf(templateVariableMatchers);
    this.fixedVariables = Preconditions.checkNotNull(fixedVariables);
    this.templateVariables = templateVariables;
  }

  /**
   * Gets the {@code VariablePattern} which generalizes the passed-in
   * {@code VariableNumMap}. The returned pattern matches exactly
   * {@code variables}.
   * 
   * @return
   */
  public static VariablePattern fromVariableNumMap(VariableNumMap variables) {
    return new VariablePattern(Collections.<VariableNameMatcher> emptyList(),
        VariableNumMap.emptyMap(), variables);
  }

  /**
   * Gets a {@code VariablePattern} which uses the names in {@code templateVars}
   * as the name templates for matching variables. The names are specified in a
   * rudimentary pattern language: if a name contains a "?(x)", this portion is
   * allowed to match any integer value. x is a number which is an integer
   * offset for the match.
   * 
   * @param variables
   * @return
   */
  public static VariablePattern fromTemplateVariables(VariableNumMap templateVariables,
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
    return new VariablePattern(matchers, templateVariables, fixedVariables);
  }

  /**
   * Gets a {@code VariablePattern} which matches {@code plateVariables} in each
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
      matchers.add(new VariableNameMatcher(plateName, variableName, 0));
    }
    return new VariablePattern(matchers, plateVariables, fixedVariables);
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

  /**
   * Identifies all of the variables which match this pattern, and returns them
   * along with the role served by each variable in the match. Matches are
   * returned in order of the matched replication index.
   * 
   * @param allVariables
   * @return
   */
  public List<VariableMatch> matchVariables(VariableNumMap inputVariables) {
    // All of the fixed variables must be matched in order to return anything.
    if (!inputVariables.containsAll(fixedVariables)) {
      return Collections.emptyList();
    }
    // Special case: if there aren't any templates, then only the fixed
    // variables should be returned.
    if (templateVariableMatchers.size() == 0) {
      return Arrays.asList(new VariableMatch(0, fixedVariables));
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
            variableMatches.put(replicationIndex,
                new VariableMatch(replicationIndex, fixedVariables));
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

  public VariableNumMap getAllMatchingVariables(VariableNumMap inputVariables) {
    // TODO: this is rather inefficient.
    VariableNumMap jointMatch = VariableNumMap.emptyMap();
    for (VariableMatch match : matchVariables(inputVariables)) {
      jointMatch = jointMatch.union(match.getMatchedVariables());
    }
    return jointMatch;
  }

  public static class VariableMatch {
    private final int replicationIndex;

    // Variables which match replications of template variables.
    private VariableNumMap allVariables;

    // Mapping from names/indices of matched variables to the names/indices of
    // the corresponding template variables.
    private BiMap<Integer, Integer> variableIndexMap;
    private BiMap<String, String> variableNameMap;

    public VariableMatch(int replicationIndex, VariableNumMap fixedVariables) {
      this.replicationIndex = replicationIndex;
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
     * Gets a single set of variables matched by a {@code VariablePattern}. The
     * returned variables are a subset of variables passed to the matching
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
     * Gets the replication index -- the integer parameter of
     * {@code VariablePattern}s -- that parameterizes the variable names of
     * {@code this.getMatchedVariables()}.
     * 
     * @return
     */
    public int getReplicationIndex() {
      return replicationIndex;
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

  public static class VariableNameMatcher {

    private final Pattern pattern;
    private final int indexOffset;

    public VariableNameMatcher(String variableNamePrefix, String variableNameSuffix, int indexOffset) {
      this.indexOffset = indexOffset;
      pattern = Pattern.compile(variableNamePrefix + "(\\d+)" + variableNameSuffix);
    }

    /**
     * Gets a set of replication indices which {@code variableName} matches, if
     * any.
     * 
     * @param variableName
     * @return
     */
    public Collection<Integer> getMatchedIndices(String variableName) {
      Matcher m = pattern.matcher(variableName);
      if (m.matches()) {
        int originalIndex = Integer.parseInt(m.group(1));
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
  }
}
