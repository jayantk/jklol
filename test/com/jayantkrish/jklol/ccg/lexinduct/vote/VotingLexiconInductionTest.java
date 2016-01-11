package com.jayantkrish.jklol.ccg.lexinduct.vote;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import junit.framework.TestCase;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.jayantkrish.jklol.ccg.CcgCkyInference;
import com.jayantkrish.jklol.ccg.CcgBinaryRule;
import com.jayantkrish.jklol.ccg.CcgExample;
import com.jayantkrish.jklol.ccg.CcgParser;
import com.jayantkrish.jklol.ccg.CcgUnaryRule;
import com.jayantkrish.jklol.ccg.DefaultCcgFeatureFactory;
import com.jayantkrish.jklol.ccg.HeadedSyntacticCategory;
import com.jayantkrish.jklol.ccg.LexiconEntry;
import com.jayantkrish.jklol.ccg.ParametricCcgParser;
import com.jayantkrish.jklol.ccg.lambda.ExplicitTypeDeclaration;
import com.jayantkrish.jklol.ccg.lambda.ExpressionParser;
import com.jayantkrish.jklol.ccg.lambda.Type;
import com.jayantkrish.jklol.ccg.lambda.TypeDeclaration;
import com.jayantkrish.jklol.ccg.lambda2.CommutativeReplacementRule;
import com.jayantkrish.jklol.ccg.lambda2.Expression2;
import com.jayantkrish.jklol.ccg.lambda2.ExpressionComparator;
import com.jayantkrish.jklol.ccg.lambda2.ExpressionReplacementRule;
import com.jayantkrish.jklol.ccg.lambda2.ExpressionSimplifier;
import com.jayantkrish.jklol.ccg.lambda2.LambdaApplicationReplacementRule;
import com.jayantkrish.jklol.ccg.lambda2.SimplificationComparator;
import com.jayantkrish.jklol.ccg.lambda2.StaticAnalysis;
import com.jayantkrish.jklol.ccg.lambda2.VariableCanonicalizationReplacementRule;
import com.jayantkrish.jklol.ccg.lexinduct.vote.VotingLexiconInduction.ParserInfo;
import com.jayantkrish.jklol.models.parametric.SufficientStatistics;
import com.jayantkrish.jklol.nlpannotation.AnnotatedSentence;
import com.jayantkrish.jklol.training.DefaultLogFunction;

public class VotingLexiconInductionTest extends TestCase {

  String[][] dataSet1 = new String[][] {
      {"what plano border us", "(border:<e,<e,t>> plano:e us:e)"},
      {"texas in us", "(in:<e,<e,t>> texas:e us:e)"},
      {"us in plano", "(in:<e,<e,t>> us:e plano:e)"},
      {"does texas border plano ?", "(border:<e,<e,t>> texas:e plano:e)"},
      {"is plano in texas", "(in:<e,<e,t>> plano:e texas:e)"},
      {"city in texas", "(lambda x (and:<t*,t> (city:<e,t> x) (in:<e,<e,t>> x texas:e)))"},
      {"major city in texas", "(lambda x (and:<t*,t> (major:<e,t> x) (city:<e,t> x) (in:<e,<e,t>> x texas:e)))"},
      {"state in us", "(lambda x (and:<t*,t> (state:<e,t> x) (in:<e,<e,t>> x us:e)))"},
/*      {"biggest city", "(argmax:<<e,t>,<<e,i>,e>> (lambda x (city:<e,t> x)) (lambda x (size:<e,i> x)))"},
      {"biggest state", "(argmax:<<e,t>,<<e,i>,e>> (lambda x (state:<e,t> x)) (lambda x (size:<e,i> x)))"},
      {"what city is the biggest in texas", "(argmax:<<e,t>,<<e,i>,e>> (lambda x (and:<t*,t> (city:<e,t> x) (in:<e,<e,t>> x texas:e))) (lambda x (size:<e,i> x)))"}
*/  };
  
  String[][] templateStrings = new String[][] {{"NP{0}", "$0", "$0", "e"},
      {"N{0}", "(lambda x ($0 x))", "$0", "<e,t>"},
      {"((S{0}\\NP{1}){0}/NP{2}){0}", "(lambda x y ($0 x y))", "$0", "<e,<e,t>>"},
      {"((S{0}\\NP{1}){0}/NP{2}){0}", "(lambda y x ($0 x y))", "$0", "<e,<e,t>>"},
      {"((N{0}\\N{0}){1}/NP{2}){1}", "(lambda x f y (and:<t*,t> (f y) ($0 x y)))", "$0", "<e,<e,t>>"},
      {"((N{0}\\N{0}){1}/NP{2}){1}", "(lambda x f y (and:<t*,t> (f y) ($0 y x)))", "$0", "<e,<e,t>>"},
      {"(N{0}/N{0}){1}", "(lambda f x (and:<t*,t> (f x) ($0 x)))", "$0", "<e,t>"},
      {"SKIP{0}", "skip"},
  };
  
  String[] initialLexiconStrings = new String[] {
      "texas,NP{0},texas:e,0 texas:e",
      "plano,NP{0},plano:e,0 plano:e",
      "in,((N{0}\\N{0}){1}/N{2}){1},\"(lambda y x (in:<e,<e,t>> x y))\",\"1 in:<e,<e,t>>\",\"in:<e,<e,t>> 1 0\",\"in:<e,<e,t>> 2 2\"",
  };

  ExpressionSimplifier simplifier;
  ExpressionComparator comparator;
  LexemeExtractor extractor;
  
  LexiconInductionCcgParserFactory factory;
  Genlex genlex;
  
  Set<LexicalTemplate> templates;
  List<CcgExample> examples;
  List<LexiconEntry> initialLexicon;
  List<String> predicates;
  List<Type> predicateTypes;

  public void setUp() {
    List<ExpressionReplacementRule> rules = Lists.newArrayList();
    rules.add(new LambdaApplicationReplacementRule());
    rules.add(new VariableCanonicalizationReplacementRule());
    rules.add(new CommutativeReplacementRule("and:<t*,t>"));
    simplifier = new ExpressionSimplifier(rules);
    comparator = new SimplificationComparator(simplifier);
    extractor = new PredicateLexemeExtractor(Sets.newHashSet("and:<t*,t>"));
    
    templates = parseTemplates(templateStrings);
    examples = parseData(dataSet1);
    initialLexicon = LexiconEntry.parseLexiconEntries(Arrays.asList(initialLexiconStrings));
    
    Set<String> predicateSet = Sets.newHashSet();
    for (CcgExample example : examples) {
      predicateSet.addAll(StaticAnalysis.getFreeVariables(example.getLogicalForm()));
    }

    TypeDeclaration typeDeclaration = ExplicitTypeDeclaration.getDefault();
    List<String> predicates = Lists.newArrayList();
    List<Type> predicateTypes = Lists.newArrayList();
    for (String predicate : predicateSet) {
      predicates.add(predicate);
      predicateTypes.add(StaticAnalysis.inferType(Expression2.constant(predicate),
          TypeDeclaration.TOP, typeDeclaration));
    }

    String[] ruleLines = {"DUMMY{0} BLAH{0}",
        "SKIP{0} NP{1} NP{1},(lambda $L $R $R)",
        "SKIP{0} N{1} N{1},(lambda $L $R $R)",
        "SKIP{0} S{1} S{1},(lambda $L $R $R)",
        "NP{1} SKIP{0} NP{1},(lambda $L $R $L)",
        "N{1} SKIP{0} N{1},(lambda $L $R $L)",
        "S{1} SKIP{0} S{1},(lambda $L $R $L)",
    };
    List<CcgUnaryRule> unaryRules = Lists.newArrayList();
    List<CcgBinaryRule> binaryRules = Lists.newArrayList();
    CcgBinaryRule.parseBinaryAndUnaryRules(Arrays.asList(ruleLines), binaryRules, unaryRules);
    factory = new LexiconInductionCcgParserFactory(binaryRules, unaryRules,
        new DefaultCcgFeatureFactory(false, false), null, true, null, true);
    genlex = new TemplateGenlex(1, templates, predicates, predicateTypes);
  }
  
  public static List<CcgExample> parseData(String[][] data) {
    List<CcgExample> examples = Lists.newArrayList();
    for (int i = 0; i < data.length; i++) {
      Expression2 lf = ExpressionParser.expression2().parse(data[i][1]);
      List<String> words = Arrays.asList(data[i][0].split(" "));
      List<String> pos = Collections.nCopies(words.size(), ParametricCcgParser.DEFAULT_POS_TAG);
      AnnotatedSentence sentence = new AnnotatedSentence(words, pos);
      examples.add(new CcgExample(sentence, null, null, lf));
    }
    return examples;
  }

  public static Set<LexicalTemplate> parseTemplates(String[][] templateStrings) {
    Set<LexicalTemplate> templates = Sets.newHashSet();
    for (int i = 0; i < templateStrings.length; i++) {
      HeadedSyntacticCategory cat = HeadedSyntacticCategory.parseFrom(templateStrings[i][0]);
      Expression2 lf = ExpressionParser.expression2().parse(templateStrings[i][1]);
      
      List<String> vars = Lists.newArrayList();
      List<Type> varTypes = Lists.newArrayList();
      for (int j = 2; j < templateStrings[i].length; j += 2) {
        vars.add(templateStrings[i][j]);
        varTypes.add(ExpressionParser.typeParser().parse(templateStrings[i][j + 1]));
      }
      templates.add(new LexicalTemplate(cat, lf, vars, varTypes));
    }
    return templates;
  }
  
  public void test() {
    System.out.println(initialLexicon);

    VotingLexiconInduction ind = new VotingLexiconInduction(10, 0.1, 1,
        CcgCkyInference.getDefault(1000), comparator, new MaxVote(extractor));

    ParserInfo info = ind.train(factory, genlex, initialLexicon, examples, new DefaultLogFunction(1, false));
    CcgParser parser = info.getParser();
    ParametricCcgParser family = info.getFamily();
    SufficientStatistics parameters = info.getParameters();
    
    for (LexiconEntry entry : info.getLexiconEntries()) {
      System.out.println(entry);
    }
    
    System.out.println(family.getParameterDescription(parameters));
    
    for (CcgExample example : examples) {
      System.out.println(example.getSentence());
      Expression2 predicted = parser.parse(example.getSentence()).getLogicalForm();
      Expression2 correct = example.getLogicalForm();

      System.out.println(predicted);
      System.out.println(correct);
      
      assertTrue(comparator.equals(predicted, correct));
    }
    
  }
  
  public void testLexemeExtractor() {
    Lexeme lexeme = extractor.extractLexeme(initialLexicon.get(0));
    assertEquals(Arrays.asList("texas"), lexeme.getTokens());
    assertEquals(Sets.newHashSet("texas:e"), lexeme.getPredicates());
  }
}
