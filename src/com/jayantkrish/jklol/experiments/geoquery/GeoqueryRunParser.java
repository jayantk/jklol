package com.jayantkrish.jklol.experiments.geoquery;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;

import com.jayantkrish.jklol.ccg.CcgCkyInference;
import com.jayantkrish.jklol.ccg.CcgInference;
import com.jayantkrish.jklol.ccg.CcgParse;
import com.jayantkrish.jklol.ccg.CcgParser;
import com.jayantkrish.jklol.ccg.ParametricCcgParser;
import com.jayantkrish.jklol.ccg.lambda.ExpressionParser;
import com.jayantkrish.jklol.ccg.lambda2.Expression2;
import com.jayantkrish.jklol.ccg.lambda2.ExpressionReplacementRule;
import com.jayantkrish.jklol.ccg.lambda2.ExpressionSimplifier;
import com.jayantkrish.jklol.ccg.lambda2.LambdaApplicationReplacementRule;
import com.jayantkrish.jklol.ccg.lambda2.StaticAnalysis;
import com.jayantkrish.jklol.ccg.lambda2.VariableCanonicalizationReplacementRule;
import com.jayantkrish.jklol.ccg.lexicon.SpanFeatureAnnotation;
import com.jayantkrish.jklol.ccg.lexicon.StringContext;
import com.jayantkrish.jklol.cli.AbstractCli;
import com.jayantkrish.jklol.lisp.AmbEval;
import com.jayantkrish.jklol.lisp.Environment;
import com.jayantkrish.jklol.lisp.LispEval.EvalResult;
import com.jayantkrish.jklol.lisp.LispUtil;
import com.jayantkrish.jklol.lisp.ParametricBfgBuilder;
import com.jayantkrish.jklol.lisp.SExpression;
import com.jayantkrish.jklol.nlpannotation.AnnotatedSentence;
import com.jayantkrish.jklol.preprocessing.FeatureVectorGenerator;
import com.jayantkrish.jklol.training.NullLogFunction;
import com.jayantkrish.jklol.util.IndexedList;
import com.jayantkrish.jklol.util.IoUtils;

public class GeoqueryRunParser extends AbstractCli {
  private OptionSpec<String> model;

  private OptionSpec<String> environment;
  private OptionSpec<String> wordOpt;

  @Override
  public void initializeOptions(OptionParser parser) {
    // Required arguments.
    model = parser.accepts("model").withRequiredArg().ofType(String.class).required();
    environment = parser.accepts("environment").withRequiredArg().ofType(String.class).withValuesSeparatedBy(',');
    wordOpt = parser.nonOptions().ofType(String.class);
  }

  @Override
  public void run(OptionSet options) {
    GeoqueryModel geoqueryModel = IoUtils.readSerializedObject(options.valueOf(model), GeoqueryModel.class);
    CcgParser parser = geoqueryModel.getParser();
    FeatureVectorGenerator<StringContext> featureGen = geoqueryModel.getFeatureGen();
    CcgInference inferenceAlg = CcgCkyInference.getDefault(100);
    ExpressionSimplifier simplifier = new ExpressionSimplifier(Arrays.
        <ExpressionReplacementRule>asList(new LambdaApplicationReplacementRule(),
            new VariableCanonicalizationReplacementRule()));

    List<String> words = options.valuesOf(wordOpt);
    List<String> pos = Collections.nCopies(words.size(), ParametricCcgParser.DEFAULT_POS_TAG);
    AnnotatedSentence sentence = new AnnotatedSentence(words, pos);
    
    SpanFeatureAnnotation annotation = SpanFeatureAnnotation.annotate(sentence, featureGen);
    sentence = sentence.addAnnotation(GeoqueryUtil.FEATURE_ANNOTATION_NAME, annotation);

    CcgParse parse = inferenceAlg.getBestParse(parser, sentence, null, new NullLogFunction());

    Expression2 logicalForm = simplifier.apply(parse.getLogicalForm());
    System.out.println("logical form: " + logicalForm);

    if (options.has(environment)) {
      // Evaluate the logical form in the environment.
      IndexedList<String> symbolTable = AmbEval.getInitialSymbolTable();
      Environment env = AmbEval.getDefaultEnvironment(symbolTable);
      AmbEval eval = new AmbEval(symbolTable);
      ParametricBfgBuilder fgBuilder = new ParametricBfgBuilder(true);
      for (String environmentFile : options.valuesOf(environment)) {
        SExpression program = LispUtil.readProgram(Arrays.asList(environmentFile), symbolTable);
        eval.eval(program, env, fgBuilder);
      }

      String lfString = logicalForm.toString();
      if (StaticAnalysis.inferType(logicalForm, GeoqueryUtil.getTypeDeclaration()).isFunctional()) {
        lfString = "(filter " + lfString + " entities)";
      }
      SExpression expression = ExpressionParser.sExpression(symbolTable).parse(lfString);

      EvalResult result = eval.eval(expression, env, fgBuilder);
      System.out.println("answer: " + result.getValue());
    }
  }

  public static void main(String[] args) {
    new GeoqueryRunParser().run(args);
  }
}
