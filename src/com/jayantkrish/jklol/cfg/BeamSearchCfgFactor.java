package com.jayantkrish.jklol.cfg;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.jayantkrish.jklol.models.AbstractConditionalFactor;
import com.jayantkrish.jklol.models.DiscreteObjectFactor;
import com.jayantkrish.jklol.models.Factor;
import com.jayantkrish.jklol.models.FactorGraphProtos.FactorProto;
import com.jayantkrish.jklol.models.TableFactor;
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.models.VariableNumMap.VariableRelabeling;
import com.jayantkrish.jklol.util.Assignment;

public class BeamSearchCfgFactor extends AbstractConditionalFactor {

  private final VariableNumMap treeVariable;
  private final VariableNumMap terminalVariable;
  private final CfgParser parser;

  // A function mapping values of the terminal variable into lists of terminals.
  // This function is useful for handling out-of-vocabulary words, etc.
  private final Function<Object, List<Object>> terminalValueToTerminals;
  
  private final Predicate<? super ParseTree> validTreeFilter;

  /**
   * Create a {@code BeamSearchCfgFactor} which parses terminals in
   * {@code terminalVariable} with {@code parser}, producing a distribution over
   * parse trees in {@code treeVariable}. {@code terminalValueToTerminals} is a
   * preprocessing function which converts values of {@code terminalVariable}
   * into a sequence of terminal symbols for the parser. Common choices for
   * {@code terminalValueToTerminals} are {@link StringSplitter}.
   * 
   * @param treeVariable
   * @param terminalVariable
   * @param parser
   * @param terminalValueToTerminals
   */
  public BeamSearchCfgFactor(VariableNumMap treeVariable, VariableNumMap terminalVariable,
      CfgParser parser, Function<Object, List<Object>> terminalValueToTerminals,
      Predicate<? super ParseTree> validTreeFilter) {
    super(treeVariable.union(terminalVariable));
    this.treeVariable = treeVariable;
    this.terminalVariable = terminalVariable;
    this.parser = parser;

    this.terminalValueToTerminals = terminalValueToTerminals;
    this.validTreeFilter = validTreeFilter;
  }

  /**
   * Gets the context-free grammar parser used to parse sentences.
   * 
   * @return
   */
  public CfgParser getParser() {
    return parser;
  }

  @Override
  public double getUnnormalizedProbability(Assignment assignment) {
    Preconditions.checkArgument(assignment.containsAll(getVars().getVariableNums()));

    List<Object> terminals = terminalValueToTerminals.apply(assignment.getValue(terminalVariable.getOnlyVariableNum()));
    ParseTree tree = (ParseTree) assignment.getValue(treeVariable.getVariableNums().get(0));
    return parser.getProbability(terminals, tree);
  }

  @Override
  public double getUnnormalizedLogProbability(Assignment assignment) {
    return Math.log(getUnnormalizedProbability(assignment));
  }

  @Override
  public Factor relabelVariables(VariableRelabeling relabeling) {
    return new BeamSearchCfgFactor(relabeling.apply(treeVariable), relabeling.apply(terminalVariable),
        parser, terminalValueToTerminals, validTreeFilter);
  }

  @Override
  public Factor conditional(Assignment assignment) {
    VariableNumMap conditionedVars = getVars().intersection(assignment.getVariableNums());
    if (conditionedVars.size() == 0) {
      return this;
    }
    Preconditions.checkArgument(conditionedVars.containsAll(terminalVariable));
    List<Object> terminals = terminalValueToTerminals.apply(assignment.getValue(terminalVariable.getVariableNums().get(0)));

    if (conditionedVars.containsAll(treeVariable)) {
      // If we also observe the tree, generate a factor over no variables with
      // the appropriate probability.
      ParseTree tree = (ParseTree) assignment.getValue(treeVariable.getVariableNums().get(0));
      return TableFactor.pointDistribution(VariableNumMap.emptyMap(), Assignment.EMPTY).product(
          parser.getProbability(terminals, tree));
    } else {
      // Find the "best" parse trees for the given terminals.
      List<ParseTree> trees = parser.beamSearch(terminals);
      Map<Assignment, Double> treeProbabilities = Maps.newHashMap();
      for (ParseTree tree : trees) {
        if (validTreeFilter.apply(tree)) {
          treeProbabilities.put(treeVariable.outcomeArrayToAssignment(tree), tree.getProbability());
        }
      }
      return new DiscreteObjectFactor(treeVariable, treeProbabilities);
    }
  }

  @Override
  public FactorProto toProto() {
    throw new UnsupportedOperationException("Not yet implemented");
    /*
     * FactorProto.Builder builder = getProtoBuilder();
     * builder.setType(FactorProto.FactorType.BEAM_SEARCH_CFG);
     * 
     * BeamSearchCfgProto.Builder cfgBuilder =
     * builder.getBeamSearchCfgFactorBuilder();
     */
  }

  /**
   * Splits the input {@code String} into words on spaces. Assumes the input is
   * a {@code String}.
   */
  public static class StringSplitter implements Function<Object, List<Object>> {
    @Override
    public List<Object> apply(Object x) {
      Preconditions.checkArgument(x instanceof String);
      Object[] words = ((String) x).split(" ");
      return Arrays.asList(words);
    }
  }

  /**
   * Maps unrecognized terminals in the input to a special OOV
   * (out-of-vocabulary) symbol. Treats the input as a list of terminals
   * 
   * @author jayantk
   */
  public static class OovMapper implements Function<Object, List<Object>> {
    private final Object oovWord;
    private final Set<Object> recognizedWords;

    public OovMapper(Object oovWord, Set<? extends Object> recognizedWords) {
      this.oovWord = Preconditions.checkNotNull(oovWord);
      this.recognizedWords = Sets.newHashSet(Preconditions.checkNotNull(recognizedWords));
    }

    @Override
    public List<Object> apply(Object x) {
      Preconditions.checkArgument(x instanceof List);
      List<?> input = (List<?>) x;
      List<Object> output = Lists.newArrayListWithCapacity(input.size());
      for (Object inputWord : input) {
        if (recognizedWords.contains(inputWord)) {
          output.add(inputWord);
        } else {
          output.add(oovWord);
        }
      }
      return output;
    }
  }
}
