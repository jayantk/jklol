package com.jayantkrish.jklol.experiments.geoquery;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.jayantkrish.jklol.ccg.LexiconEntry;
import com.jayantkrish.jklol.ccg.lambda.ExplicitTypeDeclaration;
import com.jayantkrish.jklol.ccg.lambda.Type;
import com.jayantkrish.jklol.ccg.lambda.TypeDeclaration;
import com.jayantkrish.jklol.ccg.lambda2.CommutativeReplacementRule;
import com.jayantkrish.jklol.ccg.lambda2.Expression2;
import com.jayantkrish.jklol.ccg.lambda2.ExpressionReplacementRule;
import com.jayantkrish.jklol.ccg.lambda2.ExpressionSimplifier;
import com.jayantkrish.jklol.ccg.lambda2.LambdaApplicationReplacementRule;
import com.jayantkrish.jklol.ccg.lambda2.StaticAnalysis;
import com.jayantkrish.jklol.ccg.lambda2.VariableCanonicalizationReplacementRule;
import com.jayantkrish.jklol.util.Pair;

public class GeoqueryUtil {
  public static final String FEATURE_ANNOTATION_NAME = "features";
  
  public static ExpressionSimplifier getExpressionSimplifier() {
    return new ExpressionSimplifier(Arrays.
        <ExpressionReplacementRule>asList(new LambdaApplicationReplacementRule(),
            new VariableCanonicalizationReplacementRule(),
            new CommutativeReplacementRule("and:<t*,t>"),
            new CommutativeReplacementRule("or:<t*,t>"),
            new CommutativeReplacementRule("next_to:<lo,<lo,t>>")));
  }
  
  public static TypeDeclaration getSimpleTypeDeclaration() {
    Map<String, Type> typeReplacementMap = Maps.newHashMap();
    typeReplacementMap.put("lo", Type.createAtomic("e"));
    typeReplacementMap.put("c", Type.createAtomic("e"));
    typeReplacementMap.put("co", Type.createAtomic("e"));
    typeReplacementMap.put("s", Type.createAtomic("e"));
    typeReplacementMap.put("r", Type.createAtomic("e"));
    typeReplacementMap.put("l", Type.createAtomic("e"));
    typeReplacementMap.put("m", Type.createAtomic("e"));
    typeReplacementMap.put("p", Type.createAtomic("e"));
    Map<String, String> subtypeMap = Maps.newHashMap();
    return new ExplicitTypeDeclaration(subtypeMap, typeReplacementMap);
  }

  public static TypeDeclaration getTypeDeclaration() { 
    Map<String, Type> typeReplacementMap = Maps.newHashMap();
    typeReplacementMap.put("<<e,t>,<<e,i>,e>>", Type.parseFrom("<<#1,t>,<<#1,i>,#1>>"));
    typeReplacementMap.put("<<e,t>,<<e,i>,i>>", Type.parseFrom("<<#1,t>,<<#1,i>,i>>"));
    typeReplacementMap.put("<<e,t>,t>", Type.parseFrom("<<#1,t>,t>"));
    typeReplacementMap.put("<<e,t>,i>", Type.parseFrom("<<#1,t>,i>"));
    typeReplacementMap.put("<<e,t>,e>", Type.parseFrom("<<#1,t>,#1>"));

    Map<String, String> subtypeMap = Maps.newHashMap();
    subtypeMap.put("lo", "e");
    subtypeMap.put("c", "lo");
    subtypeMap.put("co", "lo");
    subtypeMap.put("s", "lo");
    subtypeMap.put("r", "lo");
    subtypeMap.put("l", "lo");
    subtypeMap.put("m", "lo");
    subtypeMap.put("p", "lo");
    return new ExplicitTypeDeclaration(subtypeMap, typeReplacementMap);
  }

  public static Pair<Lexeme, LexiconEntryTemplate> factorLexiconEntry(LexiconEntry entry,
      ExpressionSimplifier simplifier) {
    Expression2 lf = entry.getCategory().getLogicalForm();
    Set<String> freeVars = StaticAnalysis.getFreeVariables(lf);
    freeVars.remove("and:<t*,t>");
    freeVars.remove("exists:<<e,t>,t>");

    SortedMap<Integer, String> locVarMap = Maps.newTreeMap();
    for (String freeVar : freeVars) {
      int[] indexes = StaticAnalysis.getIndexesOfFreeVariable(lf, freeVar);
      for (int i = 0; i < indexes.length; i++) {
        locVarMap.put(indexes[i], freeVar);
      }
    }

    List<Integer> locs = Lists.newArrayList(locVarMap.keySet());
    List<String> items = Lists.newArrayList(locVarMap.values());
    List<String> newVarNames = StaticAnalysis.getNewVariableNames(locVarMap.size(), lf);
    Expression2 lfTemplateBody = lf;
    for (int i = 0; i < locs.size(); i++) {
      lfTemplateBody = lfTemplateBody.substitute(locs.get(i), newVarNames.get(i));
    }

    List<Expression2> lfTemplateElts = Lists.newArrayList();
    lfTemplateElts.add(Expression2.constant(StaticAnalysis.LAMBDA));
    lfTemplateElts.add(Expression2.nested(Expression2.constants(newVarNames)));
    lfTemplateElts.add(lfTemplateBody);
    Expression2 lfTemplate = simplifier.apply(Expression2.nested(lfTemplateElts));

    // System.out.println(entry.getWords() + " " + lf);
    // System.out.println("  " + items);
    // System.out.println("  " + lfTemplate);

    Lexeme lexeme = new Lexeme(items);
    List<Type> typeSignature = lexeme.getTypeSignature(getTypeDeclaration());
    LexiconEntryTemplate template = new LexiconEntryTemplate(entry.getCategory().getSyntax(),
        typeSignature, lfTemplate);
    return Pair.of(lexeme, template);
  }
}
