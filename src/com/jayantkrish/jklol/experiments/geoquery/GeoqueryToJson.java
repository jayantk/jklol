package com.jayantkrish.jklol.experiments.geoquery;

import java.util.List;
import java.util.Map;

import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.jayantkrish.jklol.ccg.CcgExample;
import com.jayantkrish.jklol.ccg.cli.TrainSemanticParser;
import com.jayantkrish.jklol.ccg.lambda.ExpressionParser;
import com.jayantkrish.jklol.ccg.lambda.Type;
import com.jayantkrish.jklol.ccg.lambda.TypeDeclaration;
import com.jayantkrish.jklol.ccg.lambda2.Expression2;
import com.jayantkrish.jklol.ccg.lambda2.StaticAnalysis;
import com.jayantkrish.jklol.ccg.lambda2.StaticAnalysis.ScopeSet;
import com.jayantkrish.jklol.cli.AbstractCli;
import com.jayantkrish.jklol.util.IoUtils;

public class GeoqueryToJson extends AbstractCli {
  
  private OptionSpec<String> data;
  private OptionSpec<String> output;

  @Override
  public void initializeOptions(OptionParser parser) {
    data = parser.accepts("data").withRequiredArg().ofType(String.class).required();
    output = parser.accepts("output").withRequiredArg().ofType(String.class).required();
  }

  @Override
  public void run(OptionSet options) {
    List<CcgExample> examples = TrainSemanticParser.readCcgExamples(options.valueOf(data));
    
    ObjectMapper mapper = new ObjectMapper();
    ExpressionParser<Expression2> parser = ExpressionParser.expression2();
    List<String> lines = Lists.newArrayList();
    for (CcgExample example : examples) {
      Expression2 lf = example.getLogicalForm();
      System.out.println("== " + lf);
      List<Action> actions = generateTree(lf);
      
      List<String> lfTokens = parser.tokenize(lf.toString());
      
      GeoqueryExampleJson j = new GeoqueryExampleJson(example.getSentence().getWordsLowercase(),
          lf.toString(), lfTokens, actions);

      try {
        lines.add(mapper.writeValueAsString(j));
      } catch (JsonProcessingException e) {
        throw new RuntimeException(e);
      }
    }

    IoUtils.writeLines(options.valueOf(output), lines);
  }
  
  public static List<Action> generateTree(Expression2 lf) {
    Map<Integer, Type> typeMap = StaticAnalysis.inferTypeMap(lf,
        TypeDeclaration.TOP, GeoqueryUtil.getTypeDeclaration());
    ScopeSet scopes = StaticAnalysis.getScopes(lf);
    List<Action> actions = Lists.newArrayList();
    generateTree(lf, 0, typeMap, scopes, actions);
    return actions;
  }

  private static void generateTree(Expression2 lf, int index,
      Map<Integer, Type> typeMap, ScopeSet scopes, List<Action> actionAccumulator) {
    
    Type currentType = typeMap.get(index);
    if (StaticAnalysis.isLambda(lf, index)) {
      List<String> boundVars = Lists.newArrayList();
      List<Type> types = Lists.newArrayList();
      for (int ind : StaticAnalysis.getLambdaArgumentIndexes(lf, index)) {
        boundVars.add(lf.getSubexpression(ind).getConstant());
        types.add(typeMap.get(ind));
      }
      Preconditions.checkState(boundVars.size() == 1);
      
      String bodyType = typeMap.get(StaticAnalysis.getLambdaBodyIndex(lf, index)).toString();
      System.out.println("Action: LAMBDA " + currentType + " " + boundVars.get(0) + ":" + types.get(0));
      actionAccumulator.add(new Action("lambda", currentType.toString(), null, null, null, boundVars.get(0), bodyType));
      
      generateTree(lf, StaticAnalysis.getLambdaBodyIndex(lf, index), typeMap, scopes, actionAccumulator);
    } else if (lf.getSubexpression(index).isConstant()) {
      String constant = lf.getSubexpression(index).getConstant();

      if (scopes.getScope(index).isBound(constant)) {
        System.out.println("Action: VARIABLE " + currentType + " " + constant);
        actionAccumulator.add(new Action("variable", currentType.toString(), null, null, null, constant, null));
      } else {
        System.out.println("Action: CONSTANT " + currentType + " " + constant);
        actionAccumulator.add(new Action("constant", currentType.toString(), constant, null, null, null, null));
      }
    } else {
      int[] children = lf.getChildIndexes(index);

      // child 0 is the function name, remaining children are arguments
      String func = lf.getSubexpression(children[0]).getConstant();
      List<String> types = Lists.newArrayList();
      for (int i = 1; i < children.length; i++) {
        types.add(typeMap.get(children[i]).toString());
      }
      System.out.println("Action: APPLICATION " + currentType + " (" + func + " " + types + ")");
      actionAccumulator.add(new Action("application", currentType.toString(), null, func, types, null, null));

      for (int i = 1; i < children.length; i++) {
        generateTree(lf, children[i], typeMap, scopes, actionAccumulator);
      }
    }
  }

  public static void main(String[] args) {
    new GeoqueryToJson().run(args);
  }

  public static class GeoqueryExampleJson {
    public List<String> words;
    public String lf;
    public List<String> lfTokens;
    public List<Action> actions;

    public GeoqueryExampleJson(List<String> words, String lf, List<String> lfTokens,
        List<Action> actions) {
      this.words = words;
      this.lf = lf;
      this.lfTokens = lfTokens;
      this.actions = actions;
    }
  }
  
  public static class Action {
    // Four possible actions:
    // application, lambda, variable, constant
    public String action;
    public String type;
    
    // Non-null iff name == constant
    public String constant;
    
    // Non-null iff name == application
    public String appliedFunction;
    public List<String> appliedTypes;
    
    // Non-null iff name == lambda || variable
    public String variable;
    
    // Non-null iff name == lambda
    public String bodyType;

    public Action(String action, String type, String constant, String appliedFunction,
        List<String> appliedTypes, String variable, String bodyType) {
      this.action = action;
      this.type = type;
      this.constant = constant;
      this.appliedFunction = appliedFunction;
      this.appliedTypes = appliedTypes;
      this.variable = variable;
      this.bodyType = bodyType;
    }
  }
}
