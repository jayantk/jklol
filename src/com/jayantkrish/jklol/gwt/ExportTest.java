package com.jayantkrish.jklol.gwt;

import java.util.Arrays;
import java.util.List;

import org.timepedia.exporter.client.Export;
import org.timepedia.exporter.client.ExportPackage;
import org.timepedia.exporter.client.Exportable;

import com.jayantkrish.jklol.ccg.CcgParse;
import com.jayantkrish.jklol.ccg.CcgParser;
import com.jayantkrish.jklol.ccg.ParametricCcgParser;
import com.jayantkrish.jklol.cli.ModelUtils;
import com.jayantkrish.jklol.models.dynamic.DynamicFactorGraph;
import com.jayantkrish.jklol.models.parametric.ParametricFactorGraph;
import com.jayantkrish.jklol.models.parametric.SufficientStatistics;

@Export()
@ExportPackage("jklol")
public class ExportTest implements Exportable {

  private final String var;

  public ExportTest(String var) {
    this.var = var;
  }

  public String foo() {
    return "foo";
  }
  public static String bar(){
    return "bar";
  }

  public String getVar() {
    return var;
  }

  public static String testSequenceModel(String input) {
    List<String> emissionFeatures = Arrays.asList("the,DT,the=DT,1",
        "the,N,the=N,0.5", "thing,DT,thing=DT,0.5", "thing,N,thing=N,1");

    ParametricFactorGraph pfg = ModelUtils.buildSequenceModel(emissionFeatures, ","); 
    SufficientStatistics stats = pfg.getNewSufficientStatistics();
    stats.increment(1);
    DynamicFactorGraph model = pfg.getModelFromParameters(stats);

    List<String> words = Arrays.asList(input.split(" "));
    List<String> labels = ModelUtils.testSequenceModel(words, model);

    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < words.size(); i++) {
      sb.append(words.get(i) + "/" + labels.get(i) + " ");
    }

    return sb.toString();
  }
  
  public static String testParseCcg(String input) {
    String[] lexicon = {"I,N{0},0 I", "people,N{0},0 people", "berries,N{0},0 berries", "houses,N{0},0 houses",
    "eat,((S{0}\\N{1}){0}/N{2}){0},0 eat,eat 1 1,eat 2 2", "that,((N{1}\\N{1}){0}/(S{2}\\N{1}){2}){0},0 that,that 1 1,that 2 2", 
    "quickly,(((S{1}\\N{2}){1}/N{3}){1}/((S{1}\\N{2}){1}/N{3}){1}){0},0 quickly,quickly 1 1", 
    "in,((N{1}\\N{1}){0}/N{2}){0},0 in,in 1 1,in 2 2",
    "amazingly,((N{1}/N{1}){2}/(N{1}/N{1}){2}){0},0 amazingly,amazingly 1 2",
    "tasty,(N{1}/N{1}){0},0 tasty,tasty 1 1",
    "in,(((S{1}\\N{2}){1}\\(S{1}\\N{2}){1}){0}/N{3}){0},0 in,in 1 1,in 2 3",
    "and,((N{1}\\N{1}){0}/N{1}){0},0 and", 
    "almost,(((N{1}\\N{1}){2}/N{3}){2}/((N{1}\\N{1}){2}/N{3}){2}){0},0 almost,almost 1 2",
    "is,((S{0}\\N{1}){0}/N{2}){0},0 is,is 1 1, is 2 2", 
    "directed,((S{0}\\N{1}){0}/N{2}){0},0 directed,directed 1 2,directed 2 1",
    ";,;{0},0 ;", "or,conj{0},0 or",
    "about,(N{0}/(S{1}\\N{2}){1}){0},0 about,about 1 1", 
    "eating,((S{0}\\N{1}){0}/N{2}){0},0 eat,eat 1 1,eat 2 2",
    "rapidly,((S{1}\\N{2}){1}/(S{1}\\N{2}){1}){0},0 rapidly,rapidly 1 1",
    "colorful,(N{1}/N{1}){0},0 colorful,colorful 1 1"};
    
    String[] rules = {"FOO{0} FOO{1} FOO{1}", "FOO{0} FOO{0}"};

    ParametricCcgParser ccgFamily = ParametricCcgParser.parseFromLexicon(
        Arrays.asList(lexicon), Arrays.asList(rules), null, true);
    CcgParser parser = ccgFamily.getModelFromParameters(ccgFamily.getNewSufficientStatistics());
    
    List<CcgParse> parses = parser.beamSearch(10, input.split(" "));
    return parses.get(0).getAllDependencies().toString();
  }
}