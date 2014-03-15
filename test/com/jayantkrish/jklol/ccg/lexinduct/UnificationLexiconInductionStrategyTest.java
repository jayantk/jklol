package com.jayantkrish.jklol.ccg.lexinduct;

import java.util.Arrays;
import java.util.Set;

import junit.framework.TestCase;

import com.jayantkrish.jklol.ccg.CcgExactInference;
import com.jayantkrish.jklol.ccg.CcgInference;
import com.jayantkrish.jklol.ccg.LexiconEntry;
import com.jayantkrish.jklol.ccg.lambda.ExpressionParser;
import com.jayantkrish.jklol.ccg.lambda.MapTypeContext;
import com.jayantkrish.jklol.ccg.lambda.TypeContext;
import com.jayantkrish.jklol.ccg.lambda.TypedExpression;

public class UnificationLexiconInductionStrategyTest extends TestCase {

  UnificationLexiconInductionStrategy strategy;
  TypeContext context;
  ExpressionParser<TypedExpression> parser;

  private static final String[] typeDeclarations = {
    "translate:<ga,<ga,url>>",
    "map-search:<ga,url>",
    "word=:<s,ga>",
    "language=:<s,ga>",
    "query=:<s,ga>",
    "hello:s",
    "spanish:s",
    "french:s",
    "hindi:s",
    "restaurants:s",
  };

  public void setUp() {
    context = MapTypeContext.readTypeDeclarations(Arrays.asList(typeDeclarations));
    CcgInference inference = new CcgExactInference(null, -1L, Integer.MAX_VALUE, 1);
    
    strategy = new UnificationLexiconInductionStrategy(inference, context);
    parser = ExpressionParser.typedLambdaCalculus();
  }

  public void testWord() {
    Set<LexiconEntry> entries = strategy.proposeSplit(Arrays.asList("hello"),
        parser.parseSingleExpression("(word= hello)").getExpression());
    assertEquals(0, entries.size());
  }
  
  public void testMultiword1() {
    Set<LexiconEntry> entries = strategy.proposeSplit(Arrays.asList("to", "french"),
        parser.parseSingleExpression("(language= french)").getExpression());

    for (LexiconEntry entry : entries) {
      System.out.println(entry);
    }

    assertEquals(12, entries.size());
  }
  
  public void testMultiword2() {
    Set<LexiconEntry> entries = strategy.proposeSplit(Arrays.asList("translate", "hello"),
        parser.parseSingleExpression("(lambda $1:ga (translate (word= hello) $1))").getExpression());

    for (LexiconEntry entry : entries) {
      System.out.println(entry);
    }

    assertEquals(12, entries.size());
  }
  
}
