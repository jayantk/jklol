package com.jayantkrish.jklol.ccg.pattern;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import com.google.common.collect.Maps;
import com.jayantkrish.jklol.ccg.CcgParse;
import com.jayantkrish.jklol.ccg.Combinator.Type;
import com.jayantkrish.jklol.ccg.HeadedSyntacticCategory;

public class CcgPropagateFeaturesPattern implements CcgPattern {
  
  public CcgPropagateFeaturesPattern() {}
  
  @Override
  public List<CcgParse> match (CcgParse parse) {
    if (parse.isTerminal()) {
      // Feature propagation is only applicable to nonterminals. 
      return Arrays.asList(parse);
    }

    HeadedSyntacticCategory newRoot = null, funcCat = null, argCat = null;
    if (parse.getCombinator().getType() == Type.FORWARD_APPLICATION ||
        parse.getCombinator().getType() == Type.BACKWARD_APPLICATION) {
      if (parse.getCombinator().isArgumentOnLeft()) {
        funcCat = parse.getRight().getHeadedSyntacticCategory();
        argCat = parse.getLeft().getHeadedSyntacticCategory();
      } else {
        funcCat = parse.getLeft().getHeadedSyntacticCategory();
        argCat = parse.getRight().getHeadedSyntacticCategory();
      }

      Map<Integer, String> assignedFeatures = Maps.newHashMap();
      Map<Integer, String> otherAssignedFeatures = Maps.newHashMap();
      Map<Integer, Integer> relabeledFeatures = Maps.newHashMap();
      if (!funcCat.isAtomic() && funcCat.getArgumentType().isUnifiableWith(argCat, assignedFeatures,
          otherAssignedFeatures, relabeledFeatures)) {
        newRoot = funcCat.getReturnType().assignFeatures(assignedFeatures, relabeledFeatures);
      } 
    }

    if (newRoot == null) {
      // Not function application or the parse replacements messed up
      // the syntactic tree.
      newRoot = parse.getHeadedSyntacticCategory();
    }

    return Arrays.asList(CcgParse.forNonterminal(newRoot, parse.getSemanticHeads(), parse.getNodeDependencies(),
        parse.getNodeProbability(), parse.getLeft(), parse.getRight(), parse.getCombinator(),
        parse.getUnaryRule(), parse.getSpanStart(), parse.getSpanEnd()));
  }
}
