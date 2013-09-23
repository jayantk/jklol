package com.jayantkrish.jklol.ccg.data;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import com.google.common.base.Preconditions;
import com.jayantkrish.jklol.ccg.CcgSyntaxTree;
import com.jayantkrish.jklol.ccg.HeadedSyntacticCategory;
import com.jayantkrish.jklol.ccg.SyntacticCategory;
import com.jayantkrish.jklol.ccg.lambda.ApplicationExpression;
import com.jayantkrish.jklol.ccg.lambda.ConstantExpression;
import com.jayantkrish.jklol.ccg.lambda.Expression;
import com.jayantkrish.jklol.ccg.lambda.ExpressionFactories;
import com.jayantkrish.jklol.ccg.lambda.ExpressionParser;
import com.jayantkrish.jklol.data.LineDataFormat;

public class CcgbankSyntaxTreeReader extends LineDataFormat<CcgSyntaxTree> {

  private final Map<SyntacticCategory, HeadedSyntacticCategory> syntaxMap;
  
  public CcgbankSyntaxTreeReader(Map<SyntacticCategory, HeadedSyntacticCategory> syntaxMap) {
    this.syntaxMap = Preconditions.checkNotNull(syntaxMap);
  }
  
  @Override
  public CcgSyntaxTree parseFrom(String treeString) {
    ExpressionParser<Expression> parser = new ExpressionParser<Expression>('(', ')', '<', '>', ExpressionFactories.getDefaultFactory());
    Expression treeExpression = parser.parseSingleExpression(treeString);
    return expressionToSyntaxTree(treeExpression, 0);
  }

  private CcgSyntaxTree expressionToSyntaxTree(Expression expression, int numWordsOnLeft) { 
      ApplicationExpression app = (ApplicationExpression) expression;
      String constantName = ((ConstantExpression) app.getFunction()).getName();
      String[] parts = constantName.replaceFirst("^<(.*)>$", "$1").split("\\s");
      
      List<Expression> arguments = app.getArguments();
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

        return CcgSyntaxTree.createTerminal(rootCat, rootCat, numWordsOnLeft, 
            numWordsOnLeft + words.size() - 1, words, posTags, headedSyntax);
      } else if (arguments.size() == 1) {
        // Unary rule        
        SyntacticCategory rootCat = SyntacticCategory.parseFrom(parts[1]);
        CcgSyntaxTree baseTree = expressionToSyntaxTree(app.getArguments().get(0),
            numWordsOnLeft);
        return baseTree.replaceRootSyntax(rootCat);
      } else if (arguments.size() == 2) {
        // Binary rule
        CcgSyntaxTree left = expressionToSyntaxTree(app.getArguments().get(0),
            numWordsOnLeft);
        CcgSyntaxTree right = expressionToSyntaxTree(app.getArguments().get(1),
            left.getSpanEnd() + 1);

        SyntacticCategory rootCat = SyntacticCategory.parseFrom(parts[1]);
        return CcgSyntaxTree.createNonterminal(rootCat, rootCat, left, right);
      } else {
        Preconditions.checkState(false, "Illegal number of arguments to nonterminal: " + expression);
      }
    return null;
  }
}
