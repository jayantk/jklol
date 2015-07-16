package com.jayantkrish.jklol.ccg.augment;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.primitives.Ints;
import com.jayantkrish.jklol.ccg.CcgBinaryRule;
import com.jayantkrish.jklol.ccg.CcgCategory;
import com.jayantkrish.jklol.ccg.CcgParse;
import com.jayantkrish.jklol.ccg.CcgUnaryRule;
import com.jayantkrish.jklol.ccg.Combinator;
import com.jayantkrish.jklol.ccg.DependencyStructure;
import com.jayantkrish.jklol.ccg.HeadedSyntacticCategory;
import com.jayantkrish.jklol.ccg.UnaryCombinator;
import com.jayantkrish.jklol.ccg.lambda2.Expression2;

public class TemplateCcgParseAugmenter implements CcgParseAugmenter {
  
  private final List<CategoryPattern> patterns;
  private final List<BinaryRulePattern> binaryRulePatterns;
  private final List<UnaryRulePattern> unaryRulePatterns;
  
  private final boolean replaceExisting;
  
  public TemplateCcgParseAugmenter(List<CategoryPattern> patterns, 
      List<BinaryRulePattern> binaryRulePatterns, List<UnaryRulePattern> unaryRulePatterns,
      boolean replaceExisting) {
    this.patterns = ImmutableList.copyOf(patterns);
    this.binaryRulePatterns = ImmutableList.copyOf(binaryRulePatterns); 
    this.unaryRulePatterns = ImmutableList.copyOf(unaryRulePatterns);
    this.replaceExisting = replaceExisting;
  }

  public static CcgParseAugmenter parseFrom(List<String> lines, boolean replaceExisting) {
    int i = 0;
    while (!lines.get(i).trim().equals("LEXICON")) {
      i++;
    }
    i++;
    
    List<CategoryPattern> patterns = Lists.newArrayList();
    while (!lines.get(i).trim().equals("BINARY RULES")) {
      patterns.add(SemTypePattern.parseFrom(lines.get(i)));
      i++;
    }
    i++;

    List<BinaryRulePattern> binaryRulePatterns = Lists.newArrayList();
    while (!lines.get(i).trim().equals("UNARY RULES")) {
      binaryRulePatterns.add(BinaryRulePattern.parseFrom(lines.get(i)));
      i++;
    }
    i++;

    List<UnaryRulePattern> unaryRulePatterns = Lists.newArrayList();
    while (i < lines.size()) {
      unaryRulePatterns.add(UnaryRulePattern.parseFrom(lines.get(i)));
      i++;
    }

    return new TemplateCcgParseAugmenter(patterns, binaryRulePatterns, unaryRulePatterns, replaceExisting);
  }
  
  @Override
  public CcgParse addLogicalForms(CcgParse input) {
    return addLogicalFormsHelper(input, input);
  }

  private CcgParse addLogicalFormsHelper(CcgParse input, CcgParse wholeParse) {
    CcgParse result = null;
    if (input.isTerminal()) {
      CcgCategory currentEntry = input.getLexiconEntry();
      HeadedSyntacticCategory cat = currentEntry.getSyntax();
      
      Expression2 logicalForm = currentEntry.getLogicalForm();
      Collection<DependencyStructure> deps = wholeParse
          .getDependenciesWithHeadInSpan(input.getSpanStart(), input.getSpanEnd());

      if (logicalForm == null || replaceExisting) {
        for (CategoryPattern pattern : patterns) {
          if (pattern.matches(input.getWords(), cat.getSyntax(), deps)) {
            logicalForm = pattern.getLogicalForm(input.getWords(), cat.getSyntax(), deps);
            break;
          }
        }
      }

      CcgCategory lexiconEntry = new CcgCategory(cat, logicalForm, currentEntry.getSubjects(), 
          currentEntry.getArgumentNumbers(), currentEntry.getObjects(), currentEntry.getAssignment());

      result = CcgParse.forTerminal(input.getHeadedSyntacticCategory(), lexiconEntry,
          input.getLexiconTrigger(), input.getLexiconIndex(), input.getSpannedPosTags(),
          input.getSemanticHeads(), input.getNodeDependencies(),  input.getWords(),
          input.getNodeProbability(), input.getUnaryRule(), input.getSpanStart(), input.getSpanEnd()); 
    } else {
      CcgParse left = addLogicalFormsHelper(input.getLeft(), wholeParse);
      CcgParse right = addLogicalFormsHelper(input.getRight(), wholeParse);
      
      result = CcgParse.forNonterminal(input.getHeadedSyntacticCategory(), input.getSemanticHeads(),
          input.getNodeDependencies(), input.getNodeProbability(), left, right, input.getCombinator(),
          input.getUnaryRule(), input.getSpanStart(), input.getSpanEnd());
    }
    
    // Handle binary rules in combinators
    if (!result.isTerminal() && result.getCombinator().getBinaryRule() != null) {
      Combinator combinator = result.getCombinator();
      CcgBinaryRule rule = combinator.getBinaryRule();

      Expression2 newLogicalForm = rule.getLogicalForm();
      for (BinaryRulePattern pattern : binaryRulePatterns) {
        if (pattern.matches(rule)) {
          newLogicalForm = pattern.getLogicalForm(rule);
          break;
        }
      }

      CcgBinaryRule newRule = new CcgBinaryRule(rule.getLeftSyntacticType(), rule.getRightSyntacticType(),
          rule.getParentSyntacticType(), newLogicalForm, Arrays.asList(rule.getSubjects()),
          Arrays.asList(rule.getSubjectSyntacticCategories()), Ints.asList(rule.getArgumentNumbers()),
          Ints.asList(rule.getObjects()), rule.getCombinatorType());
      
      Combinator newCombinator = new Combinator(combinator.getSyntax(), combinator.getSyntaxUniqueVars(),
          combinator.getSyntaxHeadVar(), combinator.getLeftVariableRelabeling(), combinator.getLeftInverseRelabeling(), 
          combinator.getRightVariableRelabeling(), combinator.getRightInverseRelabeling(), 
          combinator.getResultOriginalVars(), combinator.getResultVariableRelabeling(), combinator.getResultInverseRelabeling(),
          combinator.getUnifiedVariables(), combinator.getSubjects(), combinator.getSubjectSyntacticCategories(),
          combinator.getArgumentNumbers(), combinator.getObjects(), combinator.isArgumentOnLeft(), 
          combinator.getArgumentReturnDepth(), newRule, combinator.getType());

      result = CcgParse.forNonterminal(result.getHeadedSyntacticCategory(), result.getSemanticHeads(),
          result.getNodeDependencies(), result.getNodeProbability(), result.getLeft(), result.getRight(), newCombinator,
          result.getUnaryRule(), result.getSpanStart(), result.getSpanEnd());
    }

    // Handle unary rules
    if (result.hasUnaryRule()) {
      UnaryCombinator combinator = result.getUnaryRule();
      CcgUnaryRule rule = combinator.getUnaryRule();
      Expression2 logicalForm = rule.getLogicalForm();
      for (UnaryRulePattern pattern : unaryRulePatterns) {
        if (pattern.matches(rule)) {
          logicalForm = pattern.getLogicalForm(rule);
          break;
        }
      }
      CcgUnaryRule newRule = new CcgUnaryRule(rule.getInputSyntacticCategory(),
          rule.getResultSyntacticCategory(), logicalForm);
      UnaryCombinator newCombinator = new UnaryCombinator(combinator.getInputType(),
          combinator.getSyntax(), combinator.getSyntaxUniqueVars(), combinator.getSyntaxHeadVar(),
          combinator.getVariableRelabeling(), combinator.getInverseRelabeling(), newRule);
      result = result.addUnaryRule(newCombinator, result.getHeadedSyntacticCategory());
    }

    return result;
  }
}
