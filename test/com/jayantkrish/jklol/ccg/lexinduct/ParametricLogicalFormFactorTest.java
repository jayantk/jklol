package com.jayantkrish.jklol.ccg.lexinduct;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import junit.framework.TestCase;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.jayantkrish.jklol.ccg.lambda.ExpressionParser;
import com.jayantkrish.jklol.ccg.lambda2.Expression2;
import com.jayantkrish.jklol.ccg.lambda2.StaticAnalysis;
import com.jayantkrish.jklol.models.DiscreteVariable;
import com.jayantkrish.jklol.models.Factor;
import com.jayantkrish.jklol.models.TableFactorBuilder;
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.models.parametric.SufficientStatistics;
import com.jayantkrish.jklol.tensor.SparseTensorBuilder;
import com.jayantkrish.jklol.util.Assignment;

public class ParametricLogicalFormFactorTest extends TestCase {
  
  ParametricLogicalFormFactor f;
  VariableNumMap wordVar, lfVar, otherVars, featureVar, lfTemplateVar;
  
  String[] logicalForms = new String[] {
      "(foo bar)", "(lambda x (foo x))", "(lambda x (bar x))", "SKIP",
  };
  
  Expression2[] expressions;
  
  Assignment skipAssignment;

  public void setUp() {
    expressions = new Expression2[logicalForms.length];
    List<Expression2> templates = Lists.newArrayList();
    Map<Expression2, Expression2> lfTemplateMap = Maps.newHashMap();
    for (int i = 0; i < logicalForms.length; i++) {
      expressions[i] = ExpressionParser.expression2().parseSingleExpression(logicalForms[i]);
      
      if (logicalForms[i].equals("SKIP")) {
        continue;
      }

      Expression2 template = expressions[i];
      int j = 0;
      for (String freeVar : StaticAnalysis.getFreeVariables(template)) {
        template = template.substitute(freeVar, "$" + j);
      }
      templates.add(template);
      lfTemplateMap.put(expressions[i], template);
    }

    DiscreteVariable wordVarType = new DiscreteVariable("words", Arrays.asList("the", "man"));
    DiscreteVariable lfVarType = new DiscreteVariable("lf", Arrays.asList(expressions));
    DiscreteVariable otherVarType = new DiscreteVariable("other", Arrays.asList("TERMINAL", "APPLICATION"));
    DiscreteVariable featureVarType = new DiscreteVariable("feature", Arrays.asList("foo", "bar"));
    DiscreteVariable lfTemplateVarType = new DiscreteVariable("lfTemplate", templates);
    
    wordVar = VariableNumMap.singleton(0, "words", wordVarType);
    lfVar = VariableNumMap.singleton(1, "lf", lfVarType);
    otherVars = VariableNumMap.singleton(2, "other", otherVarType);
    featureVar = VariableNumMap.singleton(3, "features", featureVarType);
    lfTemplateVar = VariableNumMap.singleton(4, "lfTemplate", lfTemplateVarType);

    TableFactorBuilder lfFeatureBuilder = new TableFactorBuilder(lfVar.union(featureVar),
        SparseTensorBuilder.getFactory());
    for (int i = 0; i < expressions.length; i++) {
      for (String freeVar : StaticAnalysis.getFreeVariables(expressions[i])) {
        if (!freeVar.equals("SKIP")) {
          lfFeatureBuilder.setWeight(1.0, expressions[i], freeVar);
        }
      }
    }
    
    System.out.println(lfFeatureBuilder.build().getParameterDescription());

    TableFactorBuilder lfTemplateBuilder = new TableFactorBuilder(VariableNumMap.unionAll(
        lfVar, lfTemplateVar, otherVars), SparseTensorBuilder.getFactory());
    for (Expression2 lf : lfTemplateMap.keySet()) {
      lfTemplateBuilder.setWeight(1.0, lf, "TERMINAL", lfTemplateMap.get(lf));
    }
    
    System.out.println(lfTemplateBuilder.build().getParameterDescription());

    skipAssignment = lfVar.outcomeArrayToAssignment(expressions[3])
        .union(otherVars.outcomeArrayToAssignment("TERMINAL"));

    f = new ParametricLogicalFormFactor(wordVar, lfVar, otherVars, featureVar,
        lfFeatureBuilder.build(), lfTemplateVar, lfTemplateBuilder.build(), skipAssignment);
  }
  
  public void testFactor() {
    SufficientStatistics stats = f.getNewSufficientStatistics();
    stats.increment(1.0);
    
    Factor model = f.getModelFromParameters(stats);
    
    System.out.println(model.getParameterDescription());
    
    assertEquals(0.5, model.getUnnormalizedProbability("the", expressions[3], "TERMINAL"));
    assertEquals(0.0, model.getUnnormalizedProbability("the", expressions[3], "APPLICATION"));
    assertEquals(0.5 / 4, model.getUnnormalizedProbability("the", expressions[2], "TERMINAL"));
  }
}
