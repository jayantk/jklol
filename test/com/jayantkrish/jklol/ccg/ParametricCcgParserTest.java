package com.jayantkrish.jklol.ccg;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

import junit.framework.TestCase;

import com.google.common.base.Preconditions;
import com.google.common.collect.Sets;
import com.jayantkrish.jklol.ccg.supertag.ListSupertaggedSentence;
import com.jayantkrish.jklol.models.parametric.SufficientStatistics;

public class ParametricCcgParserTest extends TestCase {

  private static final String[] lexicon = {
    "block,N{0},(lambda x (pred:block x)),0 pred:block", 
    "object,N{0},(lambda x (pred:object x)),0 pred:object",
    "red,(N{1}/N{1}){0},(lambda $1 (lambda x (and ($1 x) (pred:red x)))),0 pred:red,pred:red 1 1",
    "green,(N{1}/N{1}){0},(lambda $1 (lambda x (and ($1 x) (pred:green x)))),0 pred:green,pred:green 1 1",
    "green,N{0},(lambda x (pred:green x)),0 pred:green", 
    "the,(N{1}/N{1}){0},(lambda $1 $1),0 the", 
    "a,(N{1}/N{1}){0},(lambda $1 $1),0 the",
    "near,((N{1}\\N{1}){0}/N{2}){0},(lambda $2 $1 (lambda x (exists y (and ($1 x) (pred:close x y) ($2 y))))),0 pred:close,pred:close 1 1,pred:close 2 2",
    "near,((N{1}\\N{1}){0}/N{2}){0},(lambda $2 $1 (lambda x (exists y (and ($1 x) (pred:near x y) ($2 y))))),0 pred:near,pred:near 1 1,pred:near 2 2",
    "near,((S{1}/(S{1}\\N{0}){1}){0}/N{2}){0},(lambda $2 $1 ($1 (lambda x (exists y (and (pred:near x y) ($2 y)))))),0 pred:near,pred:near 2 2",
    "near,(PP{0}/N{1}){0},(lambda $1 $1),0 pred:near,pred:near 2 1",
    "kinda,((N{1}/N{1}){2}/(N{1}/N{1}){2}){0},(lambda $1 (lambda x ((pred:almost $1) x))),0 pred:almost,pred:almost 1 2",
    "is,((S{0}\\N{1}){0}/N{2}){0},(lambda $2 $1 (exists x y (and ($1 x) (pred:equals x y) ($2 y)))),0 pred:equals,pred:equals 1 1,pred:equals 2 2",
    "\",\",((N{1}\\N{1}){0}/N{2}){0},(lambda $1 $2 (lambda x (and ($1 x) ($2 x)))),\"0 ,\",\", 1 1\",\", 2 2\"",
    "2,N{0},pred:num,0 NUM", 
    "2,(N{1}/N{1}){0},,0 NUM,NUM 1 1",
    "\"#\",(N{1}/N{1}){0},,0 #,# 1 1",
    "\"#\",((N{1}/N{1}){2}/(N{1}/N{1}){2}){0},,0 #,# 1 2",
    "foo,ABC{0},,0 foo", "foo,ABCD{0},,0 foo",
    "unk-jj,(N{1}/N{1}){0},,0 pred:unk-jj,pred:unk-jj 1 1",
    "unk-jj,N{0},,0 pred:unk-jj",
    "unk-jj,(PP{1}/N{1}){0},,0 pred:unk-jj,pred:unk-jj 1 1",
    "that,((N{1}\\N{1}){0}/(S{2}/N{1}){2}){0},,0 that,that 1 1,that 2 2"
  };

  private static final String[] ruleArray = {"N{0} (S{1}/(S{1}\\N{0}){1}){1}", "ABC{0} ABCD{0}"};

  private static final Set<String> posTags = Sets.newHashSet("NN", "JJ", "IN", "VB");
  
  private ParametricCcgParser family;
  private SufficientStatistics parameters;
  private CcgParser parser;
  
  private static final double TOLERANCE = 1e-10;
  
  public void setUp() {
    family = ParametricCcgParser.parseFromLexicon(Arrays.asList(lexicon), Arrays.asList(ruleArray),
        new DefaultCcgFeatureFactory(null, true), posTags, true, null, false, false);
    parameters = family.getNewSufficientStatistics();
    parser = family.getModelFromParameters(parameters);
  }

  public void testTerminals() {
    List<CcgParse> parses = beamSearch(parser, Arrays.asList("Green"), Arrays.asList("NN"), 10);

    CcgParse nounParse = null;
    CcgParse adjParse = null;
    SyntacticCategory nounCat = SyntacticCategory.parseFrom("N");
    SyntacticCategory adjCat = SyntacticCategory.parseFrom("N/N");
    SyntacticCategory typeRaisedCat = SyntacticCategory.parseFrom("S/(S\\N)");
    for (CcgParse parse : parses) {
      if (parse.getSyntacticCategory().equals(nounCat)) {
        nounParse = parse;
      } else if (parse.getSyntacticCategory().equals(adjCat)) {
        adjParse = parse;
      }
    }
    Preconditions.checkState(nounParse != null);
    Preconditions.checkState(adjParse != null);

    parses = beamSearch(parser, Arrays.asList("unknown_adjective"), Arrays.asList("JJ"), 10);
    CcgParse unknownAdjParse = null;
    for (CcgParse parse : parses) {
      if (parse.getSyntacticCategory().equals(adjCat)) {
        unknownAdjParse = parse;
      }
    }
    Preconditions.checkState(unknownAdjParse != null);

    family.incrementSufficientStatistics(parameters, nounParse, 1.0);
    family.incrementSufficientStatistics(parameters, adjParse, -0.5);
    family.incrementSufficientStatistics(parameters, unknownAdjParse, 0.75);
    CcgParser newParser = family.getModelFromParameters(parameters);

    System.out.println(family.getParameterDescription(parameters));
    
    // The NN -> N{0} parameter has a weight of 1, as does the root
    // NN -> N{0} parameter and root N{0} parameter.
    parses = beamSearch(newParser, Arrays.asList("BLOCK"), Arrays.asList("NN"), 10);
    assertEquals(2, parses.size());
    for (CcgParse parse : parses) {
      System.out.println(parse);
    }

    assertEquals(3.0, Math.log(parses.get(0).getSubtreeProbability()), TOLERANCE);
    assertEquals(nounCat, parses.get(0).getSyntacticCategory());
    // This parse only triggers the lexicon parameter.
    assertEquals(1.0, Math.log(parses.get(1).getSubtreeProbability()), TOLERANCE);

    // the green -> N{0} parameter has weight 1, as do the 
    // green -> N{0} root and N{0} root parameters. 
    parses = beamSearch(newParser, Arrays.asList("green"), Arrays.asList("JJ"), 10);
    assertEquals(3, parses.size());
    assertEquals(3.0, Math.log(parses.get(0).getSubtreeProbability()), TOLERANCE);
    assertEquals(nounCat, parses.get(0).getSyntacticCategory());
    assertEquals(1.0, Math.log(parses.get(1).getSubtreeProbability()), TOLERANCE);
    assertEquals(typeRaisedCat, parses.get(1).getSyntacticCategory());
    assertEquals(3 * (-0.5) + 3 * 0.75, Math.log(parses.get(2).getSubtreeProbability()), TOLERANCE);
    assertEquals(adjCat, parses.get(2).getSyntacticCategory());

    // Check that other unknown words get correct parameterizations.
    parses = beamSearch(newParser, Arrays.asList("another_unknown_adjective"),
        Arrays.asList("JJ"), 10);
    assertEquals(4, parses.size());
    assertEquals(5 * 0.75 - 0.5, Math.log(parses.get(0).getSubtreeProbability()));
    assertEquals(adjCat, parses.get(0).getSyntacticCategory());
    assertEquals(1.0, Math.log(parses.get(1).getSubtreeProbability()));
    assertEquals(nounCat, parses.get(1).getSyntacticCategory());
  }

  private List<CcgParse> beamSearch(CcgParser parser, List<String> words,
      List<String> posTags, int beamSize) {
    return parser.beamSearch(ListSupertaggedSentence.createWithUnobservedSupertags(words, 
        posTags), beamSize);
  }
}


