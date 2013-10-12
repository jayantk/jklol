package com.jayantkrish.jklol.cvsm.ccg;

import java.util.List;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.jayantkrish.jklol.ccg.CcgCategory;
import com.jayantkrish.jklol.ccg.CcgParse;
import com.jayantkrish.jklol.ccg.CcgUnaryRule;
import com.jayantkrish.jklol.ccg.HeadedSyntacticCategory;
import com.jayantkrish.jklol.ccg.UnaryCombinator;
import com.jayantkrish.jklol.ccg.lambda.Expression;
import com.jayantkrish.jklol.ccg.lambda.LambdaExpression;

public class CcgParseAugmenter {
  
  private final List<CategoryPattern> patterns;
  private final List<UnaryRulePattern> unaryRulePatterns;
  
  public CcgParseAugmenter(List<CategoryPattern> patterns, List<UnaryRulePattern> unaryRulePatterns) {
    this.patterns = ImmutableList.copyOf(patterns);
    this.unaryRulePatterns = ImmutableList.copyOf(unaryRulePatterns);
  }

  public static CcgParseAugmenter parseFrom(List<String> lines) {
    int i = 0;
    while (!lines.get(i).trim().equals("LEXICON")) {
      i++;
    }
    i++;

    List<CategoryPattern> patterns = Lists.newArrayList();
    while (!lines.get(i).trim().equals("UNARY RULES")) {
      patterns.add(SemTypePattern.parseFrom(lines.get(i)));
      i++;
    }
    i++;

    List<UnaryRulePattern> unaryRulePatterns = Lists.newArrayList();
    while (i < lines.size()) {
      unaryRulePatterns.add(UnaryRulePattern.parseFrom(lines.get(i)));
      i++;
    }

    return new CcgParseAugmenter(patterns, unaryRulePatterns);
  }
  
  public CcgParse addLogicalForms(CcgParse input) {
    CcgParse result = null;
    if (input.isTerminal()) {
      HeadedSyntacticCategory cat = input.getHeadedSyntacticCategory();
      CcgCategory currentEntry = input.getLexiconEntry();
      Expression logicalForm = null;
      for (CategoryPattern pattern : patterns) {
        if (pattern.matches(input.getWords(), cat.getSyntax())) {
          logicalForm = pattern.getLogicalForm(input.getWords(), cat.getSyntax());
          break;
        }
      }

      CcgCategory lexiconEntry = new CcgCategory(cat, logicalForm, currentEntry.getSubjects(), 
                                                 currentEntry.getArgumentNumbers(), currentEntry.getObjects(), currentEntry.getAssignment());

      result = CcgParse.forTerminal(cat, lexiconEntry, input.getLexiconTriggerWords(), 
          input.getSpannedPosTags(), input.getSemanticHeads(), input.getNodeDependencies(), 
          input.getWords(), input.getNodeProbability(), input.getUnaryRule(),
          input.getSpanStart(), input.getSpanEnd()); 
    } else {
      CcgParse left = addLogicalForms(input.getLeft());
      CcgParse right = addLogicalForms(input.getRight());
      
      result = CcgParse.forNonterminal(input.getHeadedSyntacticCategory(), input.getSemanticHeads(),
          input.getNodeDependencies(), input.getNodeProbability(), left, right, input.getCombinator(),
          input.getUnaryRule(), input.getSpanStart(), input.getSpanEnd());
    }
    
    // Handle unary rules
    if (result.hasUnaryRule()) {
      LambdaExpression logicalForm = null;
      UnaryCombinator combinator = result.getUnaryRule();
      CcgUnaryRule rule = combinator.getUnaryRule();
      for (UnaryRulePattern pattern : unaryRulePatterns) {
        if (pattern.matches(rule)) {
          logicalForm = pattern.getLogicalForm(rule);
          break;
        }
      }
      CcgUnaryRule newRule = new CcgUnaryRule(rule.getInputSyntacticCategory(),
          rule.getResultSyntacticCategory(), logicalForm);
      UnaryCombinator newCombinator = new UnaryCombinator(combinator.getInputType(),
          combinator.getSyntax(), combinator.getSyntaxUniqueVars(),
          combinator.getVariableRelabeling(), newRule);
      result = result.addUnaryRule(newCombinator, result.getHeadedSyntacticCategory());
    }

    return result;
  }
}
