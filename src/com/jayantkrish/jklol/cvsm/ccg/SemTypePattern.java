package com.jayantkrish.jklol.cvsm.ccg;

import java.util.List;

import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.jayantkrish.jklol.ccg.SyntacticCategory;
import com.jayantkrish.jklol.ccg.lambda.ConstantExpression;
import com.jayantkrish.jklol.ccg.lambda.Expression;
import com.jayantkrish.jklol.ccg.lambda.ExpressionParser;
import com.jayantkrish.jklol.util.CsvParser;

public class SemTypePattern implements CategoryPattern {

  private static final long serialVersionUID = 1L;
  
  private final SyntacticCategory patternCategory;
  private final Expression template;
  private final boolean matchOnSemanticType;
  
  private static final String WORD_SEP = "_";
  private static final String WORD_PATTERN = "<word>";

  public SemTypePattern(SyntacticCategory patternCategory, Expression template,
      boolean matchOnSemanticType) {
    this.patternCategory = Preconditions.checkNotNull(patternCategory);
    this.template = Preconditions.checkNotNull(template);
    this.matchOnSemanticType = matchOnSemanticType;
  }

  public static SemTypePattern parseFrom(String string) {
    String[] parts = new CsvParser(',', '"', CsvParser.NULL_ESCAPE).parseLine(string);
    Preconditions.checkArgument(parts.length == 3);
    
    return new SemTypePattern(SyntacticCategory.parseFrom(parts[0]),
        ExpressionParser.lambdaCalculus().parseSingleExpression(parts[1]),
        parts[3].equals("T"));
  }

  @Override
  public boolean matches(List<String> words, SyntacticCategory category) {
    if (matchOnSemanticType) {
      return hasSameSemanticType(patternCategory, category, true);
    } else {
      return patternCategory.isUnifiableWith(category);
    }
  }

  public static boolean hasSameSemanticType(SyntacticCategory pattern,
      SyntacticCategory target, boolean useDirectionality) {
    if (pattern.isAtomic()) {
      if (target.isAtomic()) {
        return true;
      } else {
        return false;
      }
    } else {
      if (target.isAtomic()) {
        return false; 
      } else {
        return (!useDirectionality || pattern.getDirection().equals(target.getDirection())) && 
            hasSameSemanticType(pattern.getArgument(), target.getArgument(), useDirectionality) &&
            hasSameSemanticType(pattern.getReturn(), target.getReturn(), useDirectionality);
      }
    }
  }

  @Override
  public Expression getLogicalForm(List<String> words, SyntacticCategory category) {
    String parameterName = Joiner.on(WORD_SEP).join(words);
    Expression result = template;
    for (ConstantExpression expression : template.getFreeVariables()) {
      String name = expression.getName();
      String newName = name;
      if (name.contains(WORD_PATTERN)) {
        newName = name.replace(WORD_PATTERN, parameterName);
      }
      // TODO: possibly include part of speech tags, etc.
      if (!newName.equals(name)) {
        result = result.renameVariable(expression, new ConstantExpression(newName));
      }
    }

    return result;
  }
}
