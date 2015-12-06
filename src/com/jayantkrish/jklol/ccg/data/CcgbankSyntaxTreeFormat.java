package com.jayantkrish.jklol.ccg.data;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.common.base.Preconditions;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.jayantkrish.jklol.ccg.CcgSyntaxTree;
import com.jayantkrish.jklol.ccg.HeadedSyntacticCategory;
import com.jayantkrish.jklol.ccg.SyntacticCategory;
import com.jayantkrish.jklol.ccg.lambda.ExpressionFactories;
import com.jayantkrish.jklol.ccg.lambda.ExpressionParser;
import com.jayantkrish.jklol.ccg.lambda2.Expression2;
import com.jayantkrish.jklol.data.LineDataFormat;

public class CcgbankSyntaxTreeFormat extends LineDataFormat<CcgSyntaxTree> {

  private final Map<SyntacticCategory, HeadedSyntacticCategory> syntaxMap;
  private final Set<String> featurelessSyntacticCategories;
  
  public static Set<String> DEFAULT_CATEGORIES_TO_STRIP = Sets.newHashSet("N", "NP", "PP");
  
  public CcgbankSyntaxTreeFormat(Map<SyntacticCategory, HeadedSyntacticCategory> syntaxMap,
      Set<String> featurelessSyntacticCategories) {
    this.syntaxMap = Preconditions.checkNotNull(syntaxMap);
    this.featurelessSyntacticCategories = Sets.newHashSet(featurelessSyntacticCategories);
  }

  public static CcgbankSyntaxTreeFormat defaultFormat() {
    return new CcgbankSyntaxTreeFormat(Maps.<SyntacticCategory, HeadedSyntacticCategory>newHashMap(),
        DEFAULT_CATEGORIES_TO_STRIP);
  }

  @Override
  public CcgSyntaxTree parseFrom(String treeString) {
    ExpressionParser<Expression2> parser = new ExpressionParser<Expression2>('(', ')', '<', '>', '\\', true,
        ExpressionParser.DEFAULT_SEPARATOR, new String[0], new String[0], ExpressionFactories.getExpression2Factory());
    Expression2 treeExpression = parser.parse(treeString);
    return expressionToSyntaxTree(treeExpression, 0);
  }

  private CcgSyntaxTree expressionToSyntaxTree(Expression2 expression, int numWordsOnLeft) { 
    List<Expression2> subexpressions = expression.getSubexpressions();
    String constantName = subexpressions.get(0).getConstant();
    String[] parts = constantName.replaceFirst("^<(.*)>$", "$1").split("\\s");

    List<Expression2> arguments = subexpressions.subList(1, subexpressions.size());
    if (arguments.size() == 0) {
      // Terminal
      Preconditions.checkArgument(parts.length == 6);
      Preconditions.checkArgument(parts[0].equals("L"));

      String syntaxPart = parts[1];
      List<String> words = Arrays.asList(parts[4]);
      List<String> posTags = Arrays.asList(parts[2]);
      SyntacticCategory rootCat = SyntacticCategory.parseFrom(syntaxPart);

      // May be null, in which case the true headed syntactic category is unobserved.
      HeadedSyntacticCategory headedSyntax = syntaxMap.get(rootCat);

      rootCat = stripFeatures(rootCat);

      return CcgSyntaxTree.createTerminal(rootCat, rootCat, numWordsOnLeft, 
          numWordsOnLeft + words.size() - 1, words, posTags, headedSyntax);
    } else if (arguments.size() == 1) {
      // Unary rule        
      SyntacticCategory rootCat = stripFeatures(SyntacticCategory.parseFrom(parts[1]));
      CcgSyntaxTree baseTree = expressionToSyntaxTree(arguments.get(0), numWordsOnLeft);
      return baseTree.replaceRootSyntax(rootCat);
    } else if (arguments.size() == 2) {
      // Binary rule
      CcgSyntaxTree left = expressionToSyntaxTree(arguments.get(0), numWordsOnLeft);
      CcgSyntaxTree right = expressionToSyntaxTree(arguments.get(1), left.getSpanEnd() + 1);

      SyntacticCategory rootCat = stripFeatures(SyntacticCategory.parseFrom(parts[1]));
      return CcgSyntaxTree.createNonterminal(rootCat, rootCat, left, right);
    } else {
      Preconditions.checkState(false, "Illegal number of arguments to nonterminal: " + expression);
    }
    return null;
  }

  private final SyntacticCategory stripFeatures(SyntacticCategory syntax) {
    return syntax.stripFeatures(featurelessSyntacticCategories);
  }
}
