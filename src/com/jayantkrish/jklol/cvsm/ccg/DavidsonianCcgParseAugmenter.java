package com.jayantkrish.jklol.cvsm.ccg;

import java.util.Arrays;
import java.util.List;

import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.primitives.Ints;
import com.jayantkrish.jklol.ccg.CcgBinaryRule;
import com.jayantkrish.jklol.ccg.CcgCategory;
import com.jayantkrish.jklol.ccg.CcgParse;
import com.jayantkrish.jklol.ccg.CcgUnaryRule;
import com.jayantkrish.jklol.ccg.Combinator;
import com.jayantkrish.jklol.ccg.HeadedSyntacticCategory;
import com.jayantkrish.jklol.ccg.SyntacticCategory.Direction;
import com.jayantkrish.jklol.ccg.UnaryCombinator;
import com.jayantkrish.jklol.ccg.lambda.ConstantExpression;
import com.jayantkrish.jklol.ccg.lambda.Expression;
import com.jayantkrish.jklol.ccg.lambda.ExpressionParser;
import com.jayantkrish.jklol.ccg.lambda.ForAllExpression;
import com.jayantkrish.jklol.ccg.lambda.LambdaExpression;

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
      CcgCategory currentEntry = input.getLexiconEntry();
      HeadedSyntacticCategory cat = currentEntry.getSyntax();

      String predicateString = String.format(wordPredicateFormatString, Joiner.on("_").join(input.getWords()));
      Expression logicalForm = logicalFormFromSyntacticCategory(cat, predicateString);

      CcgCategory lexiconEntry = new CcgCategory(cat, logicalForm, currentEntry.getSubjects(), 
          currentEntry.getArgumentNumbers(), currentEntry.getObjects(), currentEntry.getAssignment());

      result = CcgParse.forTerminal(input.getHeadedSyntacticCategory(), lexiconEntry,
          input.getLexiconTriggerWords(), input.getSpannedPosTags(), input.getSemanticHeads(), input.getNodeDependencies(), 
          input.getWords(), input.getNodeProbability(), input.getUnaryRule(),
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

      LambdaExpression newLogicalForm = logicalFormFromBinaryRule(rule.getLeftSyntacticType(),
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

      LambdaExpression newLogicalForm = logicalFormFromUnaryRule(rule.getInputSyntacticCategory(),
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

  public static LambdaExpression logicalFormFromBinaryRule(HeadedSyntacticCategory left,
      HeadedSyntacticCategory right, HeadedSyntacticCategory parent, Combinator.Type type) {
    if (type != Combinator.Type.CONJUNCTION) {
      // Create a "virtual" syntactic category which encodes the function
      // specification of left -> (right -> parent). This allows us to
      // automatically derive a logical form for the rule.
      int parentHeadVar = parent.getHeadVariable();
      HeadedSyntacticCategory virtualCategory = parent.addArgument(right, Direction.RIGHT, parentHeadVar)
          .addArgument(left, Direction.RIGHT, parentHeadVar);

      LambdaExpression newLogicalForm = (LambdaExpression) logicalFormFromSyntacticCategory(virtualCategory, null);

      // Binary rules are two argument functions, but the returned
      // function is curried to accept each argument one at a time.
      LambdaExpression nestedLogicalForm = (LambdaExpression) newLogicalForm.getBody();
      List<ConstantExpression> arguments = Lists.newArrayList();
      arguments.addAll(newLogicalForm.getArguments());
      arguments.addAll(nestedLogicalForm.getArguments());
      return new LambdaExpression(arguments, nestedLogicalForm.getBody());
    } else {
      // The implementation of the conjunction case here is a bit of a
      // hack that's dependent on the particular rules in CCGbank.
      // This can be replaced by an explicit list of substitutions if
      // necessary.
      
      // This code assumes that the left category instantiates the conj,
      // and the right category is the category being conjuncted.
      
      ConstantExpression leftVar = new ConstantExpression("$L");
      ConstantExpression rightVar = new ConstantExpression("$R");
      ConstantExpression argVar = new ConstantExpression("$1");
      ConstantExpression quantifiedVar = new ConstantExpression("qvar");

      LambdaExpression parentExpression = (LambdaExpression) logicalFormFromSyntacticCategory(parent, null);
      ConstantExpression firstArg = parentExpression.getArguments().get(0);
      Expression body = parentExpression.getBody().renameVariable(firstArg, quantifiedVar);

      ForAllExpression forAllExpr = new ForAllExpression(
          Arrays.asList(quantifiedVar),
          Arrays.asList(ExpressionParser.lambdaCalculus().parseSingleExpression("(set $R $1)")),
          body);
      
      Expression simplified = forAllExpr.simplify();
      
      LambdaExpression firstLambda = new LambdaExpression(Arrays.asList(argVar), simplified);
      return new LambdaExpression(Arrays.asList(leftVar, rightVar), firstLambda);
    }
  }

  public static LambdaExpression logicalFormFromUnaryRule(HeadedSyntacticCategory child,
      HeadedSyntacticCategory parent) {
    // Create a "virtual" syntactic category that encodes the function 
    // input -> parent.
    HeadedSyntacticCategory virtualCategory = parent.addArgument(child,
        Direction.RIGHT, parent.getHeadVariable());
    
    return (LambdaExpression) logicalFormFromSyntacticCategory(virtualCategory, null);
  }

  public static Expression logicalFormFromSyntacticCategory(HeadedSyntacticCategory cat, String word) {
    // Category needs to be in canonical form for the
    // numerical ordering of variables in the category to
    // coincide with the category's argument ordering.
    cat = cat.getCanonicalForm();
    
    int[] uniqueVars = cat.getUniqueVariables();
    boolean[] isEntityVar = new boolean[uniqueVars.length];
    Arrays.fill(isEntityVar, false);
    markEntityVars(cat, uniqueVars, isEntityVar);
    
    List<String> entityVars = Lists.newArrayList();
    for (int i = 0; i < uniqueVars.length; i++) {
      if (isEntityVar[i]) {
        entityVars.add("e" + uniqueVars[i]); 
      }
    }

    List<String> bodyTerms = Lists.newArrayList();
    if (word != null) {
      bodyTerms.add("(" + word + " " + Joiner.on(" ").join(entityVars) + ")");
    }
    buildBodySpec(cat, bodyTerms);

    String body = "(and " + Joiner.on(" ").join(bodyTerms) + ")";

    List<HeadedSyntacticCategory> args = cat.getArgumentTypes();
    HeadedSyntacticCategory result = cat.getFinalReturnType();
    
    entityVars.remove("e" + result.getHeadVariable());
    String expr = null;
    if (entityVars.size() > 0) {
      expr = "(lambda e" + result.getHeadVariable() + "(exists " + Joiner.on(" ").join(entityVars) + " " + body + "))";
    } else {
      expr = "(lambda e" + result.getHeadVariable() + " " + body + ")";
    }

    for (int i = args.size() - 1; i >= 0; i--) {
      expr = "(lambda f" + args.get(i).getHeadVariable() + " " + expr + ")";
    }
    
    return ExpressionParser.lambdaCalculus().parseSingleExpression(expr);
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
  
  private static void buildBodySpec(HeadedSyntacticCategory cat, List<String> bodyTerms) {
    if (!cat.isAtomic()) {
      HeadedSyntacticCategory arg = cat.getArgumentType();
      int argHeadVar = arg.getHeadVariable(); 

      StringBuilder bodyTermBuilder = new StringBuilder("(");
      bodyTermBuilder.append(argHeadVar);
      bodyTermBuilder.append(" ");

      List<HeadedSyntacticCategory> argArguments = arg.getArgumentTypes();
      List<Integer> argArgumentNums = Lists.newArrayList();
      for (HeadedSyntacticCategory argArgument : argArguments) {
        argArgumentNums.add(argArgument.getHeadVariable());
      }

      bodyTerms.add(buildApplicationTerm(arg, "f" + argHeadVar));
      
      buildBodySpec(cat.getReturnType(), bodyTerms);
    }
  }

  private static String buildApplicationTerm(HeadedSyntacticCategory arg, String baseFunc) {
    if (arg.isAtomic()) {
      return "(" + baseFunc + " e" + arg.getHeadVariable() + ")"; 
    } else {
      String argFunc = "(lambda x (= x e" + arg.getArgumentType().getHeadVariable() + "))"; 

      String newBaseFunc = "(" + baseFunc + " " + argFunc + ")"; 
      return buildApplicationTerm(arg.getReturnType(), newBaseFunc);
    }
  }
}
