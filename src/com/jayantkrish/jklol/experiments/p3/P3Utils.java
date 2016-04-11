package com.jayantkrish.jklol.experiments.p3;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.jayantkrish.jklol.ccg.ParametricCcgParser;
import com.jayantkrish.jklol.ccg.gi.ValueGroundedParseExample;
import com.jayantkrish.jklol.ccg.lambda2.CommutativeReplacementRule;
import com.jayantkrish.jklol.ccg.lambda2.ExpressionReplacementRule;
import com.jayantkrish.jklol.ccg.lambda2.ExpressionSimplifier;
import com.jayantkrish.jklol.ccg.lambda2.LambdaApplicationReplacementRule;
import com.jayantkrish.jklol.ccg.lambda2.VariableCanonicalizationReplacementRule;
import com.jayantkrish.jklol.lisp.AmbEval;
import com.jayantkrish.jklol.lisp.AmbEval.WrappedBuiltinFunction;
import com.jayantkrish.jklol.lisp.ConstantValue;
import com.jayantkrish.jklol.lisp.Environment;
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
      DiscreteVariable categoryFeatureNames, DiscreteVariable relationFeatureNames) {
    DiscreteFactor categoryFeatures = TableFactor.fromDelimitedFile(
        IoUtils.readLines(path + "/osm_kb.domain.entities"), ",");
    
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
        IoUtils.readLines(path + "/osm_kb.domain.entities"), ",", false,
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
        IoUtils.readLines(path + "/osm_kb.domain.relations"), ",", false,
        DenseTensorBuilder.getFactory());
    relationFeatures = new TableFactor(
        VariableNumMap.unionAll(entityVar1, entityVar2, lispTruthVar, entityPairFeatureVar),
        relationFeatures.getWeights());
    
    String[] parts = path.split("/");
    String id = parts[parts.length - 1];

    KbEnvironment env = new KbEnvironment(id, categoryFeatures, relationFeatures);
    KbState state = KbState.unassigned(env);

    List<ValueGroundedParseExample> examples = Lists.newArrayList();
    for (String exampleString : IoUtils.readLines(path + "/training.annotated.txt")) {
      if (exampleString.startsWith("*")) {
        continue;
      }
      
      String[] exampleParts = exampleString.split(";");
      String language = exampleParts[0];
      // Hacky tokenization.
      List<String> tokens = Arrays.asList(language.toLowerCase().replaceAll("([,?./\\(\\)-])", " $1 ")
        .replaceAll("(['])", " $1").split("[ ]+"));
      List<String> pos = Collections.nCopies(tokens.size(), ParametricCcgParser.DEFAULT_POS_TAG);
      AnnotatedSentence sentence = new AnnotatedSentence(tokens, pos);
      
      Set<String> denotation = Sets.newHashSet(exampleParts[1].split(","));
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
  
  public static ExpressionSimplifier getSimplifier() {
    return new ExpressionSimplifier(Arrays.<ExpressionReplacementRule>asList(
        new LambdaApplicationReplacementRule(),
        new VariableCanonicalizationReplacementRule(),
        new CommutativeReplacementRule("and:<t*,t>")));
  }

  private P3Utils() {
    // Prevent instantiation.
  }
}
