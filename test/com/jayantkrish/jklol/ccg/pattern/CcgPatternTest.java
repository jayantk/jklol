package com.jayantkrish.jklol.ccg.pattern;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import junit.framework.TestCase;

import com.google.common.base.Preconditions;
import com.google.common.collect.Sets;
import com.jayantkrish.jklol.ccg.CcgParse;
import com.jayantkrish.jklol.ccg.CcgParser;
import com.jayantkrish.jklol.ccg.DefaultCcgFeatureFactory;
import com.jayantkrish.jklol.ccg.ParametricCcgParser;
import com.jayantkrish.jklol.ccg.supertag.ListSupertaggedSentence;
import com.jayantkrish.jklol.models.parametric.SufficientStatistics;

public class CcgPatternTest extends TestCase {
  
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
    "that,((N{1}\\N{1}){0}/(S{2}/N{1}){2}){0},,0 that,that 1 1,that 2 2"
  };
  
  private static final String[] unknownLexicon = {};

  private static final String[] ruleArray = {"N{0} (S{1}/(S{1}\\N{0}){1}){1}", "ABC{0} ABCD{0}"};
  
  private ParametricCcgParser family;
  private SufficientStatistics parameters;
  private CcgParser parser;

  public void setUp() {
    family = ParametricCcgParser.parseFromLexicon(Arrays.asList(lexicon), Arrays.asList(unknownLexicon),
        Arrays.asList(ruleArray), new DefaultCcgFeatureFactory(null, true),
        Sets.newHashSet(ParametricCcgParser.DEFAULT_POS_TAG), true, null, false, false);
    parameters = family.getNewSufficientStatistics();
    parser = family.getModelFromParameters(parameters);
  }

  public void testWordPattern() {
    CcgPattern pattern = parsePattern("(word \"green\" \"block\")");
    
    CcgParse parse = parse(parser, Arrays.asList("green", "block"));
    List<CcgParse> matches = pattern.match(parse);
    assertEquals(1, matches.size());
    
    parse = parse(parser, Arrays.asList("block"));
    matches = pattern.match(parse);
    assertEquals(0, matches.size());
  }

  public void testSyntaxPattern() {
    CcgPattern pattern = parsePattern("(subtree (syntax \"N{0}\"))");

    CcgParse parse = parse(parser, Arrays.asList("green", "block", "near", "object"));
    List<CcgParse> matches = pattern.match(parse);
    assertEquals(4, matches.size());

    pattern = parsePattern("(subtree (syntax \"(N{0}\\N{0}){1}\"))");

    matches = pattern.match(parse);
    assertEquals(1, matches.size());
  }

  public void testCombinatorPattern() {
    CcgPattern pattern = parsePattern("(subtree (combinator (syntax \"(N{0}/N{0}){1}\") (syntax \"N{0}\")))");
    CcgParse parse = parse(parser, Arrays.asList("the", "green", "block"));
    List<CcgParse> matches = pattern.match(parse);
    assertEquals(2, matches.size());
    
    pattern = parsePattern("(subtree (combinator (syntax \"(N{0}/N{0}){1}\") (subtree (chain (syntax \"N{0}\") isTerminal))))");
    parse = parse(parser, Arrays.asList("the", "green", "block", "near", "object"));
    matches = pattern.match(parse);
    assertEquals(4, matches.size());
  }

  public void testSubtreePattern() {    
    CcgPattern pattern = parsePattern("(subtree (combinator (syntax \"(N{0}/N{0}){1}\") (subtree (chain (syntax \"N{0}\")))))");
    CcgParse parse = parse(parser, Arrays.asList("green", "block", "near", "object"));
    List<CcgParse> matches = pattern.match(parse);
    assertEquals(3, matches.size());
    
    pattern = parsePattern("(subtree (combinator (syntax \"(N{0}/N{0}){1}\") (head-subtree (chain (syntax \"N{0}\")))))");
    parse = parse(parser, Arrays.asList("green", "block", "near", "object"));
    matches = pattern.match(parse);
    assertEquals(2, matches.size());
  }

  public void testLogicalFormPattern() {
    CcgPattern pattern = parsePattern("(subtree (lf-regex \".*pred:object.*\"))");
    CcgParse parse = parse(parser, Arrays.asList("green", "block", "near", "object"));
    List<CcgParse> matches = pattern.match(parse);
    assertEquals(4, matches.size());
    
    pattern = parsePattern("(subtree (lf-regex \".*pred:block.*\"))");
    parse = parse(parser, Arrays.asList("green", "block", "near", "object"));
    matches = pattern.match(parse);
    assertEquals(3, matches.size());
  }

  private static CcgPattern parsePattern(String patternString) {
    List<CcgPattern> patterns = CcgPatternUtils.parseFrom(patternString);
    Preconditions.checkArgument(patterns.size() == 1);
    return patterns.get(0);
  }

  private CcgParse parse(CcgParser parser, List<String> words) {
    return parser.parse(ListSupertaggedSentence.createWithUnobservedSupertags(words,
        Collections.nCopies(words.size(), ParametricCcgParser.DEFAULT_POS_TAG)), null, null,
        -1L, Integer.MAX_VALUE, 1);
  }
}
