package com.jayantkrish.jklol.ccg.pattern;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.jayantkrish.jklol.ccg.CcgCategory;
import com.jayantkrish.jklol.ccg.CcgParse;
import com.jayantkrish.jklol.ccg.Combinator;
import com.jayantkrish.jklol.ccg.DependencyStructure;
import com.jayantkrish.jklol.ccg.HeadedSyntacticCategory;
import com.jayantkrish.jklol.ccg.IndexedPredicate;
import com.jayantkrish.jklol.ccg.SyntacticCategory.Direction;

public class CcgPrepositionFixingPattern implements CcgPattern {

  @Override
  public List<CcgParse> match(CcgParse parse) {
    
    HeadedSyntacticCategory rootCat = parse.getHeadedSyntacticCategory();
    
    HeadedSyntacticCategory leftCat = parse.getLeft().getHeadedSyntacticCategory();
    HeadedSyntacticCategory verbReplacementCategory = leftCat.addArgument(
        HeadedSyntacticCategory.parseFrom("PP{1000}"), Direction.RIGHT, leftCat.getHeadVariable());

    int rightHead = parse.getRight().getHeadedSyntacticCategory().getHeadVariable();
    HeadedSyntacticCategory prepReplacementCategory = HeadedSyntacticCategory.parseFrom("PP{" + rightHead + "}");

    CcgParse newLeft = addVerbArgument(parse.getLeft(), verbReplacementCategory, 0);
    CcgParse newRight = addVerbArgument(parse.getRight(), prepReplacementCategory, 0);
    
    Combinator oldCombinator = parse.getCombinator();
    Combinator newCombinator = new Combinator(oldCombinator.getSyntax(), oldCombinator.getSyntaxUniqueVars(), oldCombinator.getSyntaxHeadVar(),
        oldCombinator.getLeftVariableRelabeling(), oldCombinator.getLeftInverseRelabeling(), oldCombinator.getRightVariableRelabeling(),
        oldCombinator.getRightInverseRelabeling(), oldCombinator.getResultOriginalVars(), oldCombinator.getResultVariableRelabeling(),
        oldCombinator.getResultInverseRelabeling(), oldCombinator.getUnifiedVariables(), oldCombinator.getSubjects(),
        oldCombinator.getSubjectSyntacticCategories(), oldCombinator.getArgumentNumbers(), oldCombinator.getObjects(),
        false, 0, null, Combinator.Type.FORWARD_APPLICATION);

    HeadedSyntacticCategory newLeftCat = parse.getLexiconEntryForWordIndex(
          Iterables.getFirst(parse.getHeadWordIndexes(), null)).getSyntax();

    int[] uniqueVars = verbReplacementCategory.getCanonicalForm().getUniqueVariables();
    int newArgNum = uniqueVars[uniqueVars.length - 1];

    // Create dependency structures
    List<DependencyStructure> deps = Lists.newArrayList();
    for (IndexedPredicate head : newLeft.getSemanticHeads()) {
      for (IndexedPredicate object : newRight.getSemanticHeads()) {
        deps.add(new DependencyStructure(head.getHead(), head.getHeadIndex(), newLeftCat,
            object.getHead(), object.getHeadIndex(), newArgNum));
      }
    }

    return Arrays.asList(CcgParse.forNonterminal(rootCat, parse.getSemanticHeads(), deps,
        parse.getNodeProbability(), newLeft, newRight, newCombinator, parse.getUnaryRule(),
        parse.getSpanStart(), parse.getSpanEnd()));
  }

  public static CcgParse addVerbArgument(CcgParse parse, HeadedSyntacticCategory catReplacement,
      int depth) {

    HeadedSyntacticCategory oldRoot = parse.getHeadedSyntacticCategory();
    HeadedSyntacticCategory newRoot = replaceCategoryAtDepth(oldRoot, catReplacement, 
        depth).getCanonicalForm();

    if (!parse.isTerminal()) {
      Set<IndexedPredicate> currentHeads = parse.getSemanticHeads();
      CcgParse left = parse.getLeft();
      CcgParse right = parse.getRight();
      Combinator combinator = parse.getCombinator();
      if (Sets.intersection(parse.getLeft().getSemanticHeads(), currentHeads).size() > 0) {
        if (parse.getLeft().getHeadedSyntacticCategory().getSyntax().getArgumentList().size()
            > oldRoot.getSyntax().getArgumentList().size()) {
          // Left category (head) was applied to the right category.
          left = addVerbArgument(parse.getLeft(), catReplacement, depth + 1);
        } else {
          // Right category is a modifier for the left category
          left = addVerbArgument(parse.getLeft(), catReplacement, depth);
          combinator = combinator.applicationToComposition(depth);
        }
      }

      if (Sets.intersection(parse.getRight().getSemanticHeads(), currentHeads).size() > 0) {
        if (parse.getRight().getHeadedSyntacticCategory().getSyntax().getArgumentList().size()
            > oldRoot.getSyntax().getArgumentList().size()) {
          // Right category (head) was applied to the left category.
          right = addVerbArgument(parse.getRight(), catReplacement, depth + 1);
        } else {
          // Left category is a modifier for the right category
          right = addVerbArgument(parse.getRight(), catReplacement, depth);
          combinator = combinator.applicationToComposition(depth);
        }
      }

      HeadedSyntacticCategory finalCat = left.getLexiconEntryForWordIndex(
          Iterables.getFirst(left.getHeadWordIndexes(), null)).getSyntax();

      List<DependencyStructure> deps = Lists.newArrayList();
      for (DependencyStructure dep : parse.getNodeDependencies()) {
        deps.add(new DependencyStructure(dep.getHead(), dep.getHeadWordIndex(), finalCat,
            dep.getObject(), dep.getObjectWordIndex(), dep.getArgIndex() + 1));
      }

      return CcgParse.forNonterminal(newRoot, parse.getSemanticHeads(), deps,
          parse.getNodeProbability(), left, right, combinator, parse.getUnaryRule(),
          parse.getSpanStart(), parse.getSpanEnd());
    } else {
      CcgCategory oldCategory = parse.getLexiconEntry();
      int numHeads = newRoot.getUniqueVariables().length;
      CcgCategory newCcgCategory = new CcgCategory(newRoot, oldCategory.getLogicalForm(),
          oldCategory.getSubjects(), oldCategory.getArgumentNumbers(), oldCategory.getObjects(),
          Collections.<Set<String>>nCopies(numHeads, Sets.<String>newHashSet()));

      return CcgParse.forTerminal(newRoot, newCcgCategory, parse.getLexiconTrigger(), parse.getLexiconIndex(),
          parse.getPosTags(), parse.getSemanticHeads(), parse.getNodeDependencies(), parse.getWords(),
          parse.getNodeProbability(), parse.getUnaryRule(), parse.getSpanStart(), parse.getSpanEnd());
    }
  }

  private static HeadedSyntacticCategory replaceCategoryAtDepth(HeadedSyntacticCategory category, 
      HeadedSyntacticCategory replacement, int depth) {
    if (depth == 0) {
      return replacement;
    } else {
      return replaceCategoryAtDepth(category.getReturnType(), replacement, depth - 1)
          .addArgument(category.getArgumentType(), category.getDirection(), category.getHeadVariable());
    }
  }
}
