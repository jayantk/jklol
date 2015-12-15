package com.jayantkrish.jklol.ccg.enumeratelf;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Set;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.jayantkrish.jklol.ccg.lambda.ExpressionParser;
import com.jayantkrish.jklol.ccg.lambda.Type;
import com.jayantkrish.jklol.ccg.lambda.TypeDeclaration;
import com.jayantkrish.jklol.ccg.lambda2.Expression2;
import com.jayantkrish.jklol.ccg.lambda2.ExpressionSimplifier;
import com.jayantkrish.jklol.ccg.lambda2.StaticAnalysis;

public class LogicalFormEnumerator {

  private final List<UnaryEnumerationRule> unaryRules;
  private final List<BinaryEnumerationRule> binaryRules;
  private final TypeDeclaration typeDeclaration;
  
  private final List<EnumerationRuleFilter> filters;

  public LogicalFormEnumerator(List<UnaryEnumerationRule> unaryRules,
      List<BinaryEnumerationRule> binaryRules, List<EnumerationRuleFilter> filters,
      TypeDeclaration typeDeclaration) {
    this.unaryRules = ImmutableList.copyOf(unaryRules);
    this.binaryRules = ImmutableList.copyOf(binaryRules);
    this.filters = ImmutableList.copyOf(filters);
    this.typeDeclaration = Preconditions.checkNotNull(typeDeclaration);
  }
  
  public static LogicalFormEnumerator fromRuleStrings(String[][] unaryRules,
      String[][] binaryRules, List<EnumerationRuleFilter> filters,
      ExpressionSimplifier simplifier, TypeDeclaration types) {
    ExpressionParser<Type> typeParser = ExpressionParser.typeParser();
    ExpressionParser<Expression2> lfParser = ExpressionParser.expression2();

    List<UnaryEnumerationRule> unaryRuleList = Lists.newArrayList();
    for (int i = 0; i < unaryRules.length; i++) {
      Type type = typeParser.parse(unaryRules[i][0]);
      Expression2 lf = lfParser.parse(unaryRules[i][1]);
      unaryRuleList.add(new UnaryEnumerationRule(type, lf, simplifier, types));
    }

    List<BinaryEnumerationRule> binaryRuleList = Lists.newArrayList();
    for (int i = 0; i < binaryRules.length; i++) {
      Type type1 = typeParser.parse(binaryRules[i][0]);
      Type type2 = typeParser.parse(binaryRules[i][1]);
      Expression2 lf = lfParser.parse(binaryRules[i][2]);

      binaryRuleList.add(new BinaryEnumerationRule(type1, type2, lf, simplifier, types));
    }

    return new LogicalFormEnumerator(unaryRuleList, binaryRuleList, filters, types);
  }
  
  public List<Expression2> enumerate(List<Expression2> startNodes, int max) {
    return enumerate(startNodes, Collections.emptyList(), max);
  }

  public List<Expression2> enumerate(List<Expression2> startNodes, List<EnumerationRuleFilter> addedFilters, int max) {
    List<EnumerationRuleFilter> allFilters = Lists.newArrayList(filters);
    allFilters.addAll(addedFilters);
    
    Queue<LfNode> queue = new LinkedList<LfNode>();
    for (int i = 0; i < startNodes.size(); i++) {
      Expression2 startNode = startNodes.get(i);
      Type type = StaticAnalysis.inferType(startNode, typeDeclaration);
      boolean[] usedStartNodes = new boolean[startNodes.size()];
      usedStartNodes[i] = true;
      queue.add(new LfNode(startNode, type, usedStartNodes));
    }
    
    Set<LfNode> queuedNodes = Sets.newHashSet(queue);
    Set<LfNode> exploredNodes = Sets.newHashSet();
    while (queue.size() > 0 && queuedNodes.size() < max) {
      LfNode node = queue.poll();
      
      for (UnaryEnumerationRule rule : unaryRules) {
        if (rule.isApplicable(node)) {
          LfNode result = rule.apply(node);
          
          if (passesFilters(result, node, allFilters)) {
            enqueue(result, queue, queuedNodes);
          }
        }
      }
      
      for (BinaryEnumerationRule rule : binaryRules) {
        for (LfNode exploredNode : exploredNodes) {
          if (rule.isApplicable(node, exploredNode)) {
            LfNode result = rule.apply(node, exploredNode);
          
            if (passesFilters(result, node, allFilters) && passesFilters(result, exploredNode, allFilters)) {
              enqueue(result, queue, queuedNodes);
            }
          }
          
          if (rule.isApplicable(exploredNode, node)) {
            LfNode result = rule.apply(exploredNode, node);

            if (passesFilters(result, node, allFilters) && passesFilters(result, exploredNode, allFilters)) {
              enqueue(result, queue, queuedNodes);
            }
          }
        }
      }
      
      exploredNodes.add(node);
    }

    List<Expression2> expressions = Lists.newArrayList();
    for (LfNode node : queuedNodes) {
      expressions.add(node.getLf());
    }
    return expressions;
  }
  
  private static boolean passesFilters(LfNode result, LfNode from, List<EnumerationRuleFilter> filters) {
    for (EnumerationRuleFilter filter : filters) {
      if (!filter.apply(from, result)) {
        return false;
      }
    }
    return true;
  }

  private static void enqueue(LfNode node, Queue<LfNode> queue, Set<LfNode> queuedNodes) {
    if (!queuedNodes.contains(node)) {
      queuedNodes.add(node);
      queue.add(node);
    }
  }
}
