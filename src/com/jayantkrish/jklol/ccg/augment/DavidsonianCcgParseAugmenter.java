package com.jayantkrish.jklol.ccg.augment;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.primitives.Ints;
import com.jayantkrish.jklol.ccg.CcgBinaryRule;
import com.jayantkrish.jklol.ccg.CcgCategory;
import com.jayantkrish.jklol.ccg.CcgParse;
import com.jayantkrish.jklol.ccg.CcgUnaryRule;
import com.jayantkrish.jklol.ccg.Combinator;
import com.jayantkrish.jklol.ccg.HeadedSyntacticCategory;
import com.jayantkrish.jklol.ccg.LexiconEntryInfo;
import com.jayantkrish.jklol.ccg.SyntacticCategory.Direction;
import com.jayantkrish.jklol.ccg.UnaryCombinator;
import com.jayantkrish.jklol.ccg.lambda.ExpressionParser;
import com.jayantkrish.jklol.ccg.lambda2.Expression2;
import com.jayantkrish.jklol.ccg.lambda2.StaticAnalysis;

/**
 * A rule-based system that assigns a semantics to sentences given a
 * CCG parse using neo-Davidsonian semantics.
 * 
 * @author jayantk
 */
public class DavidsonianCcgParseAugmenter implements CcgParseAugmenter {
  
  private final String wordPredicateFormatString;
  
  /**
   * 
   * @param wordPredicateFormatString A format string for mapping words in the
   * sentence to predicate names. Use "%s" as a reasonable default.
   */
  public DavidsonianCcgParseAugmenter(String wordPredicateFormatString) {
    this.wordPredicateFormatString = Preconditions.checkNotNull(wordPredicateFormatString);
  }

  public CcgParse addLogicalForms(CcgParse input) {
    CcgParse result = null;
    if (input.isTerminal()) {
      LexiconEntryInfo lexiconEntry = input.getLexiconEntry();
      CcgCategory currentEntry = lexiconEntry.getCategory();
      HeadedSyntacticCategory cat = currentEntry.getSyntax();

      String predicateString = String.format(wordPredicateFormatString, Joiner.on("_").join(input.getWords()));
      Expression2 logicalForm = logicalFormFromSyntacticCategory(cat, predicateString);

      CcgCategory newCategory = new CcgCategory(cat, logicalForm, currentEntry.getSubjects(), 
          currentEntry.getArgumentNumbers(), currentEntry.getObjects(), currentEntry.getAssignment());
      LexiconEntryInfo newLexiconEntry = lexiconEntry.replaceCategory(newCategory);

      result = CcgParse.forTerminal(input.getHeadedSyntacticCategory(), newLexiconEntry,
          input.getSpannedPosTags(), input.getSemanticHeads(), input.getNodeDependencies(),  input.getWords(),
          input.getNodeProbability(), input.getUnaryRule(),
          input.getSpanStart(), input.getSpanEnd());
    } else {
      CcgParse left = addLogicalForms(input.getLeft());
      CcgParse right = addLogicalForms(input.getRight());
      
      result = CcgParse.forNonterminal(input.getHeadedSyntacticCategory(), input.getSemanticHeads(),
          input.getNodeDependencies(), input.getNodeProbability(), left, right, input.getCombinator(),
          input.getUnaryRule(), input.getSpanStart(), input.getSpanEnd());
    }
    
    // Handle binary rules in combinators
    if (!result.isTerminal() && result.getCombinator().getBinaryRule() != null) {
      Combinator combinator = result.getCombinator();
      CcgBinaryRule rule = combinator.getBinaryRule();

      Expression2 newLogicalForm = logicalFormFromBinaryRule(rule.getLeftSyntacticType(),
          rule.getRightSyntacticType(), rule.getParentSyntacticType(), rule.getCombinatorType());

      // Rebuild the CcgParse wrapping the new logical form. 
      // The logical form is deep in nested objects, so this looks complicated.
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

      Expression2 newLogicalForm = logicalFormFromUnaryRule(rule.getInputSyntacticCategory(),
          rule.getResultSyntacticCategory());
      
      CcgUnaryRule newRule = new CcgUnaryRule(rule.getInputSyntacticCategory(),
          rule.getResultSyntacticCategory(), newLogicalForm);
      UnaryCombinator newCombinator = new UnaryCombinator(combinator.getInputType(),
          combinator.getSyntax(), combinator.getSyntaxUniqueVars(), combinator.getSyntaxHeadVar(),
          combinator.getVariableRelabeling(), combinator.getInverseRelabeling(), newRule);
      result = result.addUnaryRule(newCombinator, result.getHeadedSyntacticCategory());
    }

    return result;
  }

  public static Expression2 logicalFormFromBinaryRule(HeadedSyntacticCategory left,
      HeadedSyntacticCategory right, HeadedSyntacticCategory parent, Combinator.Type type) {
    if (type != Combinator.Type.CONJUNCTION) {
      // Create a "virtual" syntactic category which encodes the function
      // specification of left -> (right -> parent). This allows us to
      // automatically derive a logical form for the rule.
      int parentHeadVar = parent.getHeadVariable();
      HeadedSyntacticCategory virtualCategory = parent.addArgument(right, Direction.RIGHT, parentHeadVar)
          .addArgument(left, Direction.RIGHT, parentHeadVar);

      return logicalFormFromSyntacticCategory(virtualCategory, null);
    } else {
      // The implementation of the conjunction case here is a bit of a
      // hack that's dependent on the particular rules in CCGbank.
      // This can be replaced by an explicit list of substitutions if
      // necessary.
      
      // This code assumes that the left category instantiates the conj,
      // and the right category is the category being conjuncted.
      Expression2 quantifiedVar = Expression2.constant("qvar");

      Expression2 parentExpression = logicalFormFromSyntacticCategory(parent, null);
      String firstArg = StaticAnalysis.getLambdaArguments(parentExpression, 0).get(0);
      Expression2 body = StaticAnalysis.getLambdaBody(parentExpression, 0).substitute(firstArg, quantifiedVar);

      return ExpressionParser.expression2().parseSingleExpression(
          "(lambda $L $R (lambda $1 (forall (qvar (set $R $1)) " + body + ")))");
    }
  }

  public static Expression2 logicalFormFromUnaryRule(HeadedSyntacticCategory child,
      HeadedSyntacticCategory parent) {
    // Create a "virtual" syntactic category that encodes the function 
    // input -> parent.
    HeadedSyntacticCategory virtualCategory = parent.addArgument(child,
        Direction.RIGHT, parent.getHeadVariable());
    
    return logicalFormFromSyntacticCategory(virtualCategory, null);
  }

  public static Expression2 logicalFormFromSyntacticCategory(HeadedSyntacticCategory cat, String word) {
    // Category needs to be in canonical form for the
    // numerical ordering of variables in the category to
    // coincide with the category's argument ordering.
    cat = cat.getCanonicalForm();
    
    int[] uniqueVars = cat.getUniqueVariables();
    boolean[] isEntityVar = new boolean[uniqueVars.length];
    Arrays.fill(isEntityVar, false);
    markEntityVars(cat, uniqueVars, isEntityVar);
    
    Set<Integer> entityVarNums = Sets.newHashSet();
    for (int i = 0; i < uniqueVars.length; i++) {
      if (isEntityVar[i]) {
        entityVarNums.add(uniqueVars[i]);
      }
    }

    // Decide which entity variables to bind in this expression.
    Set<Integer> varsToBind = Sets.newHashSet();
    boolean generateAllArgs = true;
    if (word == null) {
      Map<Integer, HeadedSyntacticCategory> observedArgumentHeads = Maps.newHashMap();
      HeadedSyntacticCategory tempCat = cat;
      while (!tempCat.isAtomic() &&
          (!observedArgumentHeads.containsKey(tempCat.getHeadVariable()) || !observedArgumentHeads.get(tempCat.getHeadVariable()).equals(tempCat))
          && !tempCat.getArgumentType().equals(tempCat.getReturnType())) {
        HeadedSyntacticCategory argumentCat = tempCat.getArgumentType();
        int[] argVars = argumentCat.getUniqueVariables();
        for (int i = 0; i < argVars.length; i++) {
          if (entityVarNums.contains(argVars[i])) {
            varsToBind.add(argVars[i]);
          }
        }

        observedArgumentHeads.put(argumentCat.getHeadVariable(), argumentCat);
        tempCat = tempCat.getReturnType();
      }
      generateAllArgs = false;
    } else {
      varsToBind.addAll(entityVarNums);
      generateAllArgs = true;
    }

    List<String> boundEntityVars = Lists.newArrayList();
    for (int boundVarNum : varsToBind) {
      boundEntityVars.add("e" + boundVarNum);
    }

    List<String> bodyTerms = Lists.newArrayList();
    if (word != null) {
      bodyTerms.add("(" + word + " " + Joiner.on(" ").join(boundEntityVars) + ")");
    }
    buildBodySpec(cat, bodyTerms, varsToBind, generateAllArgs);

    String body = "(and " + Joiner.on(" ").join(bodyTerms) + ")";

    List<HeadedSyntacticCategory> args = cat.getArgumentTypes();
    HeadedSyntacticCategory result = cat.getFinalReturnType();
    
    boundEntityVars.remove("e" + result.getHeadVariable());
    String expr = null;
    if (boundEntityVars.size() > 0) {
      expr = "(lambda e" + result.getHeadVariable() + "(exists " + Joiner.on(" ").join(boundEntityVars) + " " + body + "))";
    } else {
      expr = "(lambda e" + result.getHeadVariable() + " " + body + ")";
    }

    for (int i = args.size() - 1; i >= 0; i--) {
      expr = "(lambda f" + args.get(i).getHeadVariable() + " " + expr + ")";
    }
    
    return ExpressionParser.expression2().parseSingleExpression(expr);
  }
  
  /**
   * Marks all unique variables in a syntactic category
   * associated with atomic syntactic categories.
   */
  private static void markEntityVars(HeadedSyntacticCategory cat, int[] uniqueVars,
      boolean[] isEntityVar) {
    if (cat.isAtomic()) {
      for (int var : cat.getUniqueVariables()) {
        int index = Ints.indexOf(uniqueVars, var);
        isEntityVar[index] = true;
      }
    } else {
      markEntityVars(cat.getArgumentType(), uniqueVars, isEntityVar);
      markEntityVars(cat.getReturnType(), uniqueVars, isEntityVar);
    }
  }
  
  private static void buildBodySpec(HeadedSyntacticCategory cat, List<String> bodyTerms,
      Set<Integer> boundEntityVars, boolean generateAllArgs) {
    Map<Integer, HeadedSyntacticCategory> observedArgHeads = Maps.newHashMap();
    if (!cat.isAtomic()) {
      HeadedSyntacticCategory arg = cat.getArgumentType();
      int argHeadVar = arg.getHeadVariable();
      observedArgHeads.put(argHeadVar, arg);

      StringBuilder bodyTermBuilder = new StringBuilder("(");
      bodyTermBuilder.append(argHeadVar);
      bodyTermBuilder.append(" ");

      List<HeadedSyntacticCategory> argArguments = arg.getArgumentTypes();
      List<Integer> argArgumentNums = Lists.newArrayList();
      for (HeadedSyntacticCategory argArgument : argArguments) {
        argArgumentNums.add(argArgument.getHeadVariable());
      }

      bodyTerms.add(buildApplicationTerm(arg, "f" + argHeadVar, boundEntityVars));

      if (generateAllArgs || (!observedArgHeads.containsKey(cat.getReturnType().getHeadVariable()) || 
          !observedArgHeads.get(cat.getReturnType().getHeadVariable()).equals(cat.getReturnType()))) {
        buildBodySpec(cat.getReturnType(), bodyTerms, boundEntityVars, generateAllArgs);
      }
    }
  }

  private static String buildApplicationTerm(HeadedSyntacticCategory arg, String baseFunc,
      Set<Integer> boundEntityVars) {
    if (arg.isAtomic()) {
      return "(" + baseFunc + " e" + arg.getHeadVariable() + ")"; 
    } else {
      String argFunc = null;
      if (arg.getArgumentType().isAtomic() && boundEntityVars.contains(arg.getArgumentType().getHeadVariable())) {
        argFunc = "(lambda x (= x e" + arg.getArgumentType().getHeadVariable() + "))";
      } else { 
        argFunc = "f" + arg.getArgumentType().getHeadVariable();
      }

      String newBaseFunc = "(" + baseFunc + " " + argFunc + ")"; 
      return buildApplicationTerm(arg.getReturnType(), newBaseFunc, boundEntityVars);
    }
  }
}
