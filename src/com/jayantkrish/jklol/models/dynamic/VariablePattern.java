package com.jayantkrish.jklol.models.dynamic;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.common.base.Preconditions;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.primitives.Ints;
import com.jayantkrish.jklol.models.Variable;
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
 * {@code VariablePattern}s have two uses. The first use is to dynamically
 * instantiate sets of variables based on an input instance (see {@link Plate}).
 * The second purpose is to identify these dynamically constructed sets in
 * existing {@code FactorGraph}s, for compiling sufficient statistics, etc. In
 * both cases, the generated/identified variables are parameterized by a single
 * integer value, which behaves like the index of the particular replication in
 * a list of replications.
 * 
 * @author jayantk
 */
public class VariablePattern {

  private static final String SEPARATOR = "-";

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
    return new VariablePattern(Collections.<VariableNameMatcher>emptyList(), 
        VariableNumMap.emptyMap(), variables);
  }

  /**
   * Identifies all of the variables which match this pattern, and returns them
   * along with the role served by each variable in the match.
   * 
   * @param allVariables
   * @return
   */
  public List<VariableMatch> matchVariables(VariableNumMap inputVariables) {
    // Find all variables which begin with a prefix in variableNamePrefixes,
    // identify their replication index, and aggregate the matches by
    // replication index.
    Map<Integer, VariableMatch> variableMatches = Maps.newHashMap();
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
      if (match.getMatchedVariables().equals(allMatchVariables)) {
        validMatches.add(match);
      }
    }
    return validMatches;
  }

  public Map<String, Variable> instantiateWithArgument(int replicationIndex) {
    Map<String, Variable> instantiatedVariables = Maps.newHashMap();
    for (int i = 0; i < templateVariableMatchers.size(); i++) {
      instantiatedVariables.put(templateVariableMatchers.get(i).getPrefix() + replicationIndex,
          templateVariables.getVariables().get(i));
    }
    return instantiatedVariables;
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
  }

  public static class VariableNameMatcher {

    private final String variableNamePrefix;
    private final Pattern pattern;
    private final int indexOffset;

    public VariableNameMatcher(String variableNamePrefix, int indexOffset) {
      this.variableNamePrefix = variableNamePrefix;
      this.indexOffset = indexOffset;

      pattern = Pattern.compile(variableNamePrefix + SEPARATOR + "(\\d+)");
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
     * Gets the prefix of the variable names which are matched by {@code this}.
     * 
     * @return
     */
    public String getPrefix() {
      return variableNamePrefix;
    }
  }
}
