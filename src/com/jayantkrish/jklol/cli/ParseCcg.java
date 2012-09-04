package com.jayantkrish.jklol.cli;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.List;

import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;

import com.jayantkrish.jklol.ccg.CcgParse;
import com.jayantkrish.jklol.ccg.CcgParser;

/**
 * Parses input sentences using a trained CCG parser. 
 * 
 * @author jayant
 */
public class ParseCcg {
  
  public static void main(String[] args) {
    OptionParser parser = new OptionParser();
    // Required arguments.
    OptionSpec<String> model = parser.accepts("model").withRequiredArg().ofType(String.class).required();
    OptionSet options = parser.parse(args);

    // Read the parser.
    CcgParser ccgParser = null;
    FileInputStream fis = null;
    ObjectInputStream in = null;
    try {
      fis = new FileInputStream(options.valueOf(model));
      in = new ObjectInputStream(fis);
      ccgParser = (CcgParser)in.readObject();
      in.close();
    } catch(IOException ex) {
      ex.printStackTrace();
      System.exit(1);
    } catch(ClassNotFoundException ex) {
      ex.printStackTrace();
      System.exit(1);
    }
    
    List<String> sentenceToParse = options.nonOptionArguments();
    List<CcgParse> parses = ccgParser.beamSearch(sentenceToParse, 10);
    if (parses.size() > 0) {
      System.out.println(parses.get(0).getAllDependencies());
    }
  }
}
