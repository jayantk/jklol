package com.jayantkrish.jklol.ccg.lexinduct;

import java.util.List;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.jayantkrish.jklol.cfg.CfgParseChart;
import com.jayantkrish.jklol.cfg.CfgParseTree;
import com.jayantkrish.jklol.cfg.CfgParser;
import com.jayantkrish.jklol.models.DiscreteFactor;
import com.jayantkrish.jklol.models.Factor;
import com.jayantkrish.jklol.models.TableFactor;
import com.jayantkrish.jklol.models.TableFactorBuilder;
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.tensor.DenseTensorBuilder;
import com.jayantkrish.jklol.tensor.SparseTensorBuilder;
import com.jayantkrish.jklol.tensor.Tensor;

public class LagrangianAlignmentDecoder {
  private final int numIterations;
  
  public LagrangianAlignmentDecoder(int numIterations) {
    this.numIterations = numIterations;
  }

  public LagrangianDecodingResult decode(CfgAlignmentModel model,
      List<AlignmentExample> trainingData, DiscreteFactor lexiconFactor) {
    VariableNumMap nonterminalVar = model.getParentVar();
    VariableNumMap wordVar = model.getTerminalVar();
    VariableNumMap vars = wordVar.union(nonterminalVar);

    // A words by nonterminals matrix holding the lagrange multipliers
    // that tie each training example's lexicon to the global lexicon.
    List<TableFactorBuilder> exampleBuilders = Lists.newArrayList();
    for (int i = 0; i < trainingData.size(); i++) {
      TableFactorBuilder lagrangeWordMultipliers = new TableFactorBuilder(vars, SparseTensorBuilder.getFactory());
      exampleBuilders.add(lagrangeWordMultipliers);
    }
    
    // Builder for holding the sum of the lagrange multipliers from each example.
    TableFactorBuilder lagrangeSum = new TableFactorBuilder(vars, DenseTensorBuilder.getFactory());
    // Builder holding the lexicon entries used for each example.
    TableFactorBuilder usedEntries = new TableFactorBuilder(vars, SparseTensorBuilder.getFactory());
    List<CfgParseTree> finalParses = Lists.newArrayList();
    for (int i = 0; i < numIterations; i++) {
      System.out.println(i);
      double stepSize = 1 / Math.sqrt(i + 1);

      lagrangeSum.multiply(0.0);
      for (int j = 0; j < trainingData.size(); j++) {
        lagrangeSum.incrementWeight(exampleBuilders.get(j).build());
      }
      Tensor lexiconIndicator = lexiconFactor.add(lagrangeSum.build()).getWeights().findKeysLargerThan(0.0);

      List<CfgParseTree> usedTerminals = Lists.newArrayList();
      finalParses.clear();
      for (int j = 0; j < trainingData.size(); j++) {
        // Find the best parse tree given the lagrange multipliers for this
        // training example.
        AlignmentExample example = trainingData.get(j);
        TableFactorBuilder exampleMultipliers = exampleBuilders.get(j);
        
        // TODO: this could be buildNoCopy.
        TableFactor logWeights = exampleMultipliers.build();
        TableFactor exampleWeights = new TableFactor(exampleMultipliers.getVars(),
            logWeights.getWeights().elementwiseProduct(-1.0).elementwiseExp());

        // Decode the best parse tree given the example weights.
        CfgParser parser = model.getCfgParser(example, exampleWeights);
        Factor rootFactor = model.getRootFactor(example.getTree(), parser.getParentVariable());
        CfgParseChart chart = parser.parseMarginal(example.getWords(), rootFactor, false);
        CfgParseTree tree = chart.getBestParseTree();

        usedTerminals.clear();
        getTerminals(tree, usedTerminals);
        usedEntries.multiply(0.0);
        for (int k = 0; k < usedTerminals.size(); k++) {
          CfgParseTree usedTerminal = usedTerminals.get(k);
          usedEntries.setWeight(1.0, usedTerminal.getTerminalProductions(), usedTerminal.getRoot());
        }
        
        // Lagrange multipliers gradient for this example.
        Tensor usedEntriesTensor = usedEntries.build().getWeights();
        Tensor exampleWordLagrangeGradient = lexiconIndicator.elementwiseAddition(usedEntriesTensor.elementwiseProduct(-1.0));

        // Perform the gradient update for this example's lagrange multipliers.
        exampleMultipliers.incrementWeight(new TableFactor(vars, exampleWordLagrangeGradient).product(-1.0 * stepSize));

        // This implements the <= constraint on the multipliers.
        exampleMultipliers.max(TableFactor.zero(vars));
        
        // Keep track of the parses for this iteration.
        finalParses.add(tree);
      }

      if (i % 100 == 0) {
        System.out.println(lagrangeSum.build().getParameterDescription());
        System.out.println(new TableFactor(lagrangeSum.getVars(), lexiconIndicator).getParameterDescription());
      }
    }
    
    return new LagrangianDecodingResult(finalParses);
  }
  
  /**
   * Gets the terminal subtrees of {@code tree}, in left-to-right order.
   * 
   * @param tree
   * @param accumulator
   */
  private void getTerminals(CfgParseTree tree, List<CfgParseTree> accumulator) {
    if (tree.isTerminal()) {
      accumulator.add(tree);
    } else {
      getTerminals(tree.getLeft(), accumulator);
      getTerminals(tree.getRight(), accumulator);
    }
  }
  
  public static class LagrangianDecodingResult {
    private final List<CfgParseTree> parseTrees;
    
    public LagrangianDecodingResult(List<CfgParseTree> parseTrees) {
      this.parseTrees = ImmutableList.copyOf(parseTrees);
    }
    
    public List<CfgParseTree> getParseTrees() {
      return parseTrees;
    }
  }
}
