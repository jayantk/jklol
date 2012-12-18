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
    String[] lexicon = {"block,N{0},0 pred:block", "object,N{0},0 pred:block",
      "red,(N{1}/N{1}){0},0 pred:red,pred:red 1 1", "green,(N{1}/N{1}){0},0 pred:green,pred:green 1 1",
      "green,N{0},0 pred:green", "the,(N{1}/N{1}){0},0 the", "a,(N{1}/N{1}){0},0 a",
      "near,((N{1}\\N{1}){0}/N{2}){0},0 pred:near,pred:near 1 1,pred:near 2 2",
      "near,((S{1}/(S{1}\\N{0}){1}){0}/N{2}){0},0 pred:near,pred:near 2 2",
      "near,(PP{0}/N{1}){0},0 pred:near,pred:near 2 1",
      "is,((S{0}\\N{1}){0}/N{2}){0},0 pred:equals,pred:equals 1 1,pred:equals 2 2"};
    
    String[] rules = {"FOO{0} FOO{1} FOO{1}", "FOO{0} FOO{0}"};

    ParametricCcgParser ccgFamily = ParametricCcgParser.parseFromLexicon(
        Arrays.asList(lexicon), Arrays.asList(rules), null, true);
    CcgParser parser = ccgFamily.getModelFromParameters(ccgFamily.getNewSufficientStatistics());
    
    List<CcgParse> parses = parser.beamSearch(10, input.split(" "));
    return parses.get(0).getAllDependencies().toString();
  }
}