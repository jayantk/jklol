package com.jayantkrish.jklol.ccg.enumeratelf;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.jayantkrish.jklol.ccg.lambda.Type;
import com.jayantkrish.jklol.ccg.lambda2.Expression2;
import com.jayantkrish.jklol.ccg.lambda2.StaticAnalysis;

public class LogicalFormEnumerator {

  private final List<UnaryEnumerationRule> unaryRules;
  private final List<BinaryEnumerationRule> binaryRules;
  private final Map<String, String> typeDeclaration;

  public LogicalFormEnumerator(List<UnaryEnumerationRule> unaryRules,
      List<BinaryEnumerationRule> binaryRules) {
    this.unaryRules = unaryRules;
    this.binaryRules = binaryRules;
    this.typeDeclaration = Maps.newHashMap();
  }

  public List<Expression2> enumerate(List<Expression2> startNodes, int max) {
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
          enqueue(rule.apply(node), queue, queuedNodes);
        }
      }
      
      for (BinaryEnumerationRule rule : binaryRules) {
        for (LfNode exploredNode : exploredNodes) {
          if (rule.isApplicable(node, exploredNode)) {
            enqueue(rule.apply(node, exploredNode), queue, queuedNodes);
          }
          
          if (rule.isApplicable(exploredNode, node)) {
            enqueue(rule.apply(exploredNode, node), queue, queuedNodes);
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

  private static void enqueue(LfNode node, Queue<LfNode> queue, Set<LfNode> queuedNodes) {
    // TODO: filtering can happen here.
    if (!queuedNodes.contains(node)) {
      queuedNodes.add(node);
      queue.add(node);
    }
  }
}
