package com.jayantkrish.jklol.experiments.p3;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import com.google.common.base.Function;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import com.jayantkrish.jklol.ccg.ParametricCcgParser;
import com.jayantkrish.jklol.ccg.lambda.ExplicitTypeDeclaration;
import com.jayantkrish.jklol.ccg.lambda.ExpressionParser;
import com.jayantkrish.jklol.ccg.lambda.Type;
import com.jayantkrish.jklol.ccg.lambda.TypeDeclaration;
import com.jayantkrish.jklol.ccg.lambda2.CommutativeReplacementRule;
import com.jayantkrish.jklol.ccg.lambda2.CpsTransform;
import com.jayantkrish.jklol.ccg.lambda2.Expression2;
import com.jayantkrish.jklol.ccg.lambda2.ExpressionReplacementRule;
import com.jayantkrish.jklol.ccg.lambda2.ExpressionSimplifier;
import com.jayantkrish.jklol.ccg.lambda2.LambdaApplicationReplacementRule;
import com.jayantkrish.jklol.ccg.lambda2.StaticAnalysis;
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
import com.jayantkrish.jklol.tensor.DenseTensor;
import com.jayantkrish.jklol.tensor.DenseTensorBuilder;
import com.jayantkrish.jklol.util.IndexedList;
import com.jayantkrish.jklol.util.IoUtils;

public class P3Utils {

  public static List<P3KbExample> readTrainingData(String path,
      DiscreteVariable categoryFeatureNames, DiscreteVariable relationFeatureNames,
      String categoryFilePath, String relationFilePath, String trainingFilePath,
      String worldFilePath, IndexedList<String> categories, IndexedList<String> relations) {
    DiscreteFactor categoryFeatures = TableFactor.fromDelimitedFile(
        IoUtils.readLines(path + "/" + categoryFilePath), ",");
    
    VariableNumMap entityVar = categoryFeatures.getVars().getFirstVariables(1)
        .relabelVariableNums(new int[] {0});
    List<Object> entities = entityVar.getDiscreteVariables().get(0).getValues();
    VariableNumMap truthVar = VariableNumMap.singleton(2, "truthVar",
        new DiscreteVariable("truthVar", Arrays.asList("F", "T")));
    VariableNumMap entityFeatureVar = VariableNumMap.singleton(3, "entityFeatures",
        categoryFeatureNames);

    categoryFeatures = TableFactor.fromDelimitedFile(
        VariableNumMap.unionAll(entityVar, truthVar, entityFeatureVar),
        IoUtils.readLines(path + "/" + categoryFilePath), ",", false,
        DenseTensorBuilder.getFactory());
    
    VariableNumMap entityVar1 = entityVar;
    VariableNumMap entityVar2 = entityVar.relabelVariableNums(new int[] {1});
    VariableNumMap entityPairFeatureVar = VariableNumMap.singleton(3,
        "entityPairFeatures", relationFeatureNames);

    DiscreteFactor relationFeatures = TableFactor.fromDelimitedFile(
        VariableNumMap.unionAll(entityVar1, entityVar2, truthVar, entityPairFeatureVar),
        IoUtils.readLines(path + "/" + relationFilePath), ",", false,
        DenseTensorBuilder.getFactory());
    
    String[] parts = path.split("/");
    String id = parts[parts.length - 1];
    
    DenseTensor categoryFeatureTensor = DenseTensor.copyOf(categoryFeatures.conditional(
        truthVar.outcomeArrayToAssignment("T")).getWeights());
    DenseTensor relationFeatureTensor = DenseTensor.copyOf(relationFeatures.conditional(
        truthVar.outcomeArrayToAssignment("T")).getWeights());

    KbEnvironment env = new KbEnvironment(id, IndexedList.create(entities),
        categoryFeatureTensor.toMatrix(new int[] {0}, new int[] {3}),
        relationFeatureTensor.toMatrix(new int[] {0, 1}, new int[] {3}));
    KbState state = KbState.unassigned(env, categories, relations);
    
    KbState stateLabel = null;
    if (worldFilePath != null) {
      stateLabel = state;
      for (String predicateString : IoUtils.readLines(path + "/" + worldFilePath)) {
        if (!predicateString.startsWith("*")) {
          continue;
        }
        
        String[] predicateParts = predicateString.substring(1).split(";");
        String predicateName = predicateParts[0];
        String[] assignmentParts = predicateParts[1].split(",");
        if (predicateName.endsWith("-rel")) {
          String typedPredicateName = predicateName + ":<e,<e,t>>";
          Multimap<String, String> relationMap = HashMultimap.create();
          for (int i = 0; i < assignmentParts.length; i++) {
            String[] entityParts = assignmentParts[i].split("#");
            relationMap.put(entityParts[0], entityParts[1]);
          }
          
          for (Object arg1 : entities) {
            for (Object arg2 : entities) {
              if (relationMap.containsEntry(arg1, arg2)) {
                stateLabel = stateLabel.setRelationValue(typedPredicateName, arg1, arg2, ConstantValue.TRUE);
              } else {
                stateLabel = stateLabel.setRelationValue(typedPredicateName, arg1, arg2, ConstantValue.FALSE);
              }
            }
          }
        } else {
          String typedPredicateName = predicateName + ":<e,t>";
          Set<String> trueEntities = Sets.newHashSet(assignmentParts);
          Set<Object> falseEntities = Sets.newHashSet(env.getEntities());
          falseEntities.removeAll(trueEntities);
          
          for (Object trueEntity : trueEntities) {
            stateLabel = stateLabel.setCategoryValue(typedPredicateName, trueEntity, ConstantValue.TRUE);
          }
          
          for (Object falseEntity : falseEntities) {
            stateLabel = stateLabel.setCategoryValue(typedPredicateName, falseEntity, ConstantValue.FALSE);
          }
        }
      }
      
      for (String category : categories) {
        if (!stateLabel.getCategories().contains(category)) {
          for (Object entity : env.getEntities()) {
            stateLabel = stateLabel.setCategoryValue(category, entity, ConstantValue.FALSE);
          }
        }
      }
      
      for (String relation : relations) {
        if (!stateLabel.getRelations().contains(relation)) {
          for (Object arg1 : env.getEntities()) {
            for (Object arg2 : env.getEntities()) {
              stateLabel = stateLabel.setRelationValue(relation, arg1, arg2, ConstantValue.FALSE);
            }
          }
        }
      }
    }

    List<P3KbExample> examples = Lists.newArrayList();
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
      examples.add(new P3KbExample(sentence, state, denotation, stateLabel));
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

    ExpressionSimplifier continuationSimplifier = new ExpressionSimplifier(
        Arrays.<ExpressionReplacementRule>asList(
            new LambdaApplicationReplacementRule(),
            new VariableCanonicalizationReplacementRule()));
    
    P3CpsTransform transform = new P3CpsTransform(simplifier, continuationSimplifier,
        ExplicitTypeDeclaration.getDefault());
    return new ContinuationIncEval(ambEval, env, transform, defs);
  }

  public static ExpressionSimplifier getSimplifier() {
    return new ExpressionSimplifier(Arrays.<ExpressionReplacementRule>asList(
        new LambdaApplicationReplacementRule(),
        new VariableCanonicalizationReplacementRule(),
        new CommutativeReplacementRule("and:<t*,t>")
        // new ExistsReplacementRule("exists:<<e,t>,t>", "and:<t*,t>", "equal?:<⊤,<⊤,t>>")
        ));
  }

  private P3Utils() {
    // Prevent instantiation.
  }
  
  public static class P3CpsTransform implements Function<Expression2, Expression2> {
    
    private final ExpressionSimplifier simplifier;
    private final ExpressionSimplifier continuationSimplifier;
    private final TypeDeclaration typeDeclaration;
    private final Expression2 lfConversion;
    
    private static Type ENTITY_SET_TYPE = Type.parseFrom("<e,t>");

    public P3CpsTransform(ExpressionSimplifier simplifier,
        ExpressionSimplifier continuationSimplifier, TypeDeclaration typeDeclaration) {
      this.simplifier = simplifier;
      this.continuationSimplifier = continuationSimplifier;
      this.typeDeclaration = typeDeclaration;
      this.lfConversion = ExpressionParser.expression2().parse(
        "(lambda (x y) (list-to-set-c (get-denotation x y)))");
    }

    @Override
    public Expression2 apply(Expression2 lf) {
      lf = simplifier.apply(lf);
      Type t = StaticAnalysis.inferType(lf, typeDeclaration);

      if (typeDeclaration.unify(t, ENTITY_SET_TYPE).equals(ENTITY_SET_TYPE)) {
        lf = Expression2.nested(lfConversion, lf, Expression2.stringValue(lf.toString()));
      }

      return continuationSimplifier.apply(CpsTransform.apply(lf, Expression2.constant(
          ContinuationIncEval.FINAL_CONTINUATION)));
    }
  }
}
