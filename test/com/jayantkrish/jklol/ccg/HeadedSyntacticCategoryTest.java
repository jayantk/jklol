package com.jayantkrish.jklol.ccg;

import java.util.Set;

import junit.framework.TestCase;

import com.google.common.collect.Sets;

/**
 * Unit tests for {@link HeadedSyntacticCategory}.
 * 
 * @author jayantk
 */
public class HeadedSyntacticCategoryTest extends TestCase {
  String adjective = "(N{1}/N{1}){0}";
  String transVerb = "((S[ng]{0}\\N{1}){0}/N{2}){0}";
  String transVerb2 = "((S[b]{0}\\N{1}){0}/N{2}){0}";
  String transVerb3 = "((S{0}\\N{1}){0}/N{2}){0}";
  String missingHeads = "((S\\N{1})/N{2})";
  String missingHeads2 = "(N{1}/N{1})";
  String verbMod = "(((S[1]{1}\\N[9]{2}){1}/N{0}){1}/((S[1]{1}\\N[9]{2}){1}/N{0}){1}){3}";
  String verbModCanonical = "(((S[0]{0}\\N[1]{1}){0}/N{2}){0}/((S[0]{0}\\N[1]{1}){0}/N{2}){0}){3}";
  
  String noParens = "S[ng]{0}\\N[num]{1}";
  
  String prep = "((NP{1}\\NP{1}){0}/NP{2}){0}";
  String prepCanonical = "((NP{0}\\NP{0}){1}/NP{2}){1}";
  
  public void testParseFrom() {
    HeadedSyntacticCategory cat = HeadedSyntacticCategory.parseFrom(transVerb);
    
    assertEquals(0, cat.getHeadVariable());
    assertEquals(SyntacticCategory.DEFAULT_FEATURE_VALUE, cat.getRootFeature());
    assertEquals(2, cat.getArgumentType().getHeadVariable());
    assertEquals(SyntacticCategory.DEFAULT_FEATURE_VALUE, cat.getArgumentType().getRootFeature());
    assertTrue(cat.getArgumentType().getSyntax().isAtomic());
    assertEquals(0, cat.getReturnType().getHeadVariable());
    assertEquals(SyntacticCategory.DEFAULT_FEATURE_VALUE, cat.getReturnType().getRootFeature());
    assertEquals(0, cat.getReturnType().getReturnType().getHeadVariable());
    assertEquals("ng", cat.getReturnType().getReturnType().getRootFeature());
    assertEquals(1, cat.getReturnType().getArgumentType().getHeadVariable());
    
    assertEquals("S", cat.getReturnType().getReturnType().getSyntax().getValue());
  }
  
  public void testParseFrom2() {
    HeadedSyntacticCategory cat = HeadedSyntacticCategory.parseFrom(verbMod);
    
    assertEquals(3, cat.getHeadVariable());
    assertEquals(1, cat.getArgumentType().getHeadVariable());
    assertEquals(0, cat.getArgumentType().getArgumentType().getHeadVariable());
    assertEquals(1, cat.getArgumentType().getReturnType().getHeadVariable());
    assertEquals(1, cat.getArgumentType().getReturnType().getReturnType().getHeadVariable());
    assertEquals(1, cat.getReturnType().getHeadVariable());
    assertEquals(1, cat.getReturnType().getReturnType().getHeadVariable());
    assertEquals(0, cat.getReturnType().getArgumentType().getHeadVariable());
  }
  
  public void testParseFrom3() {
    HeadedSyntacticCategory cat2 = HeadedSyntacticCategory.parseFrom(noParens);
    assertFalse(cat2.isAtomic());
    assertEquals("S", cat2.getReturnType().getSyntax().getValue());
    assertEquals("ng", cat2.getReturnType().getSyntax().getRootFeature());
    assertEquals("num", cat2.getArgumentType().getSyntax().getRootFeature());
  }
  
  public void testParseFrom4() {
    HeadedSyntacticCategory cat = HeadedSyntacticCategory.parseFrom(missingHeads);
    HeadedSyntacticCategory expected = HeadedSyntacticCategory.parseFrom(transVerb3);
    
    assertEquals(cat, expected);
  }
  
  public void testParseFrom5() {
    HeadedSyntacticCategory cat = HeadedSyntacticCategory.parseFrom(missingHeads2);
    HeadedSyntacticCategory expected = HeadedSyntacticCategory.parseFrom(adjective);

    assertEquals(cat, expected);
  }

  public void testCanonicalize() {
    HeadedSyntacticCategory cat = HeadedSyntacticCategory.parseFrom(transVerb);
    assertEquals(cat, cat.getCanonicalForm());
  }
  
  public void testCanonicalize2() {
    HeadedSyntacticCategory cat = HeadedSyntacticCategory.parseFrom(verbMod);
    HeadedSyntacticCategory canonicalCat = HeadedSyntacticCategory.parseFrom(verbModCanonical);

    assertEquals(canonicalCat, cat.getCanonicalForm());
  }
  
  public void testCanonicalize3() {
    HeadedSyntacticCategory cat = HeadedSyntacticCategory.parseFrom(prep);
    HeadedSyntacticCategory canonicalCat = HeadedSyntacticCategory.parseFrom(prepCanonical);

    assertEquals(canonicalCat, cat.getCanonicalForm());
  }

  
  public void testUnify() {
    HeadedSyntacticCategory verbCat = HeadedSyntacticCategory.parseFrom(transVerb);
    HeadedSyntacticCategory verb2Cat = HeadedSyntacticCategory.parseFrom(transVerb2);
    HeadedSyntacticCategory verb3Cat = HeadedSyntacticCategory.parseFrom(transVerb3);
    HeadedSyntacticCategory vmodCat = HeadedSyntacticCategory.parseFrom(verbMod);
    HeadedSyntacticCategory vmodArgCat = vmodCat.getArgumentType();
    HeadedSyntacticCategory vmodCatCanonical = HeadedSyntacticCategory.parseFrom(verbModCanonical);
    HeadedSyntacticCategory vmodArgCatCanonical = vmodCatCanonical.getArgumentType();
    
    assertTrue(vmodArgCat.isUnifiableWith(verb2Cat));
    assertTrue(verb2Cat.isUnifiableWith(vmodArgCat));
    assertFalse(verb3Cat.isUnifiableWith(verb2Cat));
    assertTrue(verb3Cat.isUnifiableWith(vmodArgCat));
    assertTrue(verbCat.isUnifiableWith(vmodArgCat));
    assertFalse(verb2Cat.isUnifiableWith(verbCat));
    assertFalse(verbCat.isUnifiableWith(verb2Cat));
    assertTrue(vmodArgCatCanonical.isUnifiableWith(vmodArgCat));
    assertTrue(vmodArgCat.isUnifiableWith(vmodArgCatCanonical));

    assertTrue(vmodCatCanonical.isUnifiableWith(vmodCat));
    assertTrue(vmodCat.isUnifiableWith(vmodCatCanonical));
    assertTrue(vmodCat.isUnifiableWith(vmodCat));
  }
  
  public void testGetSubcategories() {
    HeadedSyntacticCategory cat = HeadedSyntacticCategory.parseFrom(verbMod);
    
    Set<HeadedSyntacticCategory> subcats = cat.getSubcategories(Sets.newHashSet("pss", "dcl"));
    assertEquals(9, subcats.size());
  }
}
