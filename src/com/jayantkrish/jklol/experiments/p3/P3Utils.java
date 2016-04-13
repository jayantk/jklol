package com.jayantkrish.jklol.experiments.p3;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.jayantkrish.jklol.ccg.ParametricCcgParser;
import com.jayantkrish.jklol.ccg.gi.ValueGroundedParseExample;
import com.jayantkrish.jklol.ccg.lambda.ExpressionParser;
import com.jayantkrish.jklol.ccg.lambda2.Expression2;
import com.jayantkrish.jklol.ccg.lambda2.ExpressionReplacementRule;
import com.jayantkrish.jklol.ccg.lambda2.ExpressionSimplifier;
import com.jayantkrish.jklol.ccg.lambda2.LambdaApplicationReplacementRule;
import com.jayantkrish.jklol.ccg.lambda2.VariableCanonicalizationReplacementRule;
import com.jayantkrish.jklol.lisp.AmbEval;
import com.jayantkrish.jklol.lisp.AmbEval.WrappedBuiltinFunction;
import com.jayantkrish.jklol.lisp.ConstantValue;
import com.jayantkrish.jklol.lisp.Environment;
import com.jayantkrish.jklol.lisp.LispUtil;
import com.jayantkrish.jklol.lisp.SExpression;
import com.jayantkrish.jklol.lisp.inc.ContinuationIncEval;
import com.jayantkrish.jklol.models.DiscreteFactor;
import com.jayantkrish.jklol.models.DiscreteVariable;
import com.jayantkrish.jklol.models.TableFactor;
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.nlpannotation.AnnotatedSentence;
import com.jayantkrish.jklol.tensor.DenseTensorBuilder;
import com.jayantkrish.jklol.util.IndexedList;
import com.jayantkrish.jklol.util.IoUtils;

public class P3Utils {

  public static List<ValueGroundedParseExample> readTrainingData(String path,
      DiscreteVariable categoryFeatureNames, DiscreteVariable relationFeatureNames,
      String categoryFilePath, String relationFilePath, String trainingFilePath) {
    DiscreteFactor categoryFeatures = TableFactor.fromDelimitedFile(
        IoUtils.readLines(path + "/" + categoryFilePath), ",");
    
    VariableNumMap entityVar = categoryFeatures.getVars().getFirstVariables(1)
        .relabelVariableNums(new int[] {0});
    VariableNumMap truthVar = VariableNumMap.singleton(2, "truthVar",
        new DiscreteVariable("truthVar", Arrays.asList("F", "T")));
    VariableNumMap lispTruthVar = VariableNumMap.singleton(2, "lispTruthVar",
        new DiscreteVariable("truthVar", Arrays.asList(ConstantValue.FALSE, ConstantValue.TRUE)));
    VariableNumMap entityFeatureVar = VariableNumMap.singleton(3, "entityFeatures",
        categoryFeatureNames);

    categoryFeatures = TableFactor.fromDelimitedFile(
        VariableNumMap.unionAll(entityVar, truthVar, entityFeatureVar),
        IoUtils.readLines(path + "/" + categoryFilePath), ",", false,
        DenseTensorBuilder.getFactory());
    categoryFeatures = new TableFactor(
        VariableNumMap.unionAll(entityVar, lispTruthVar, entityFeatureVar),
        categoryFeatures.getWeights());
    
    VariableNumMap entityVar1 = entityVar;
    VariableNumMap entityVar2 = entityVar.relabelVariableNums(new int[] {1});
    VariableNumMap entityPairFeatureVar = VariableNumMap.singleton(3,
        "entityPairFeatures", relationFeatureNames);

    DiscreteFactor relationFeatures = TableFactor.fromDelimitedFile(
        VariableNumMap.unionAll(entityVar1, entityVar2, truthVar, entityPairFeatureVar),
        IoUtils.readLines(path + "/" + relationFilePath), ",", false,
        DenseTensorBuilder.getFactory());
    relationFeatures = new TableFactor(
        VariableNumMap.unionAll(entityVar1, entityVar2, lispTruthVar, entityPairFeatureVar),
        relationFeatures.getWeights());
    
    String[] parts = path.split("/");
    String id = parts[parts.length - 1];

    KbEnvironment env = new KbEnvironment(id, categoryFeatures, relationFeatures);
    KbState state = KbState.unassigned(env);

    List<ValueGroundedParseExample> examples = Lists.newArrayList();
    for (String exampleString : IoUtils.readLines(path + "/" + trainingFilePath)) {
      if (exampleString.startsWith("*") || exampleString.startsWith("#") || exampleString.trim().length() == 0) {
        continue;
      }
      
      String[] exampleParts = exampleString.split(";");
      if (exampleParts.length < 2) {
        System.out.println("bad example: "+ exampleString);
        continue;
      }
      
      String language = exampleParts[0];
      // Hacky tokenization.
      List<String> tokens = Arrays.asList(language.toLowerCase().replaceAll("([,?./\\(\\)-])", " $1 ")
        .replaceAll("(['])", " $1").split("[ ]+"));
      List<String> pos = Collections.nCopies(tokens.size(), ParametricCcgParser.DEFAULT_POS_TAG);
      AnnotatedSentence sentence = new AnnotatedSentence(tokens, pos);
      
      String[] denotationParts = exampleParts[1].split(",");
      Set<String> denotation = Sets.newHashSet();
      if (denotationParts.length > 1 || denotationParts[0].length() != 0) {
        denotation.addAll(Arrays.asList(denotationParts));
      }
      examples.add(new ValueGroundedParseExample(sentence, state, denotation));
    }

    return examples;
  }
  
  public static Environment getEnvironment(IndexedList<String> symbolTable) {
    Environment env = AmbEval.getDefaultEnvironment(symbolTable);
    env.bindName("get-entities", new WrappedBuiltinFunction(new P3Functions.GetEntities()), symbolTable);
    env.bindName("kb-get", new WrappedBuiltinFunction(new P3Functions.KbGet()), symbolTable);
    env.bindName("kb-set", new WrappedBuiltinFunction(new P3Functions.KbSet()), symbolTable);
    env.bindName("list-to-set", new WrappedBuiltinFunction(new P3Functions.ListToSet()), symbolTable);
    return env;
  }

  public static ContinuationIncEval getIncEval(List<String> defFilenames) {
    IndexedList<String> symbolTable = AmbEval.getInitialSymbolTable(); 
    AmbEval ambEval = new AmbEval(symbolTable);
    Environment env = P3Utils.getEnvironment(symbolTable);
    ExpressionSimplifier simplifier = P3Utils.getSimplifier();

    SExpression defs = LispUtil.readProgram(defFilenames, symbolTable);

    Expression2 lfConversion = ExpressionParser.expression2().parse(
        "(lambda (x) (list-to-set-c (get-denotation x)))");
    return new ContinuationIncEval(ambEval, env, simplifier, defs, lfConversion);
  }
  
  public static ExpressionSimplifier getSimplifier() {
    return new ExpressionSimplifier(Arrays.<ExpressionReplacementRule>asList(
        new LambdaApplicationReplacementRule(),
        new VariableCanonicalizationReplacementRule()));
  }

  private P3Utils() {
    // Prevent instantiation.
  }
}
