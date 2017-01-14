package com.jayantkrish.jklol.ccg.lambda2;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.jayantkrish.jklol.ccg.lambda.Type;

public class ConstraintSet {

  public static ConstraintSet EMPTY = new ConstraintSet(Collections.emptyList(),
      Collections.emptyMap(), true);

  private final List<SubtypeConstraint> constraints;
  private final Map<Integer, Type> bindings;
  private final boolean solvable;
  
  public ConstraintSet(List<SubtypeConstraint> constraints, Map<Integer, Type> bindings,
      boolean solvable) {
    this.constraints = constraints;
    this.bindings = bindings;
    this.solvable = solvable;
  }
  
  public Map<Integer, Type> getBindings() {
    return bindings;
  }

  public ConstraintSet union(ConstraintSet other) {
    List<SubtypeConstraint> newConstraints = Lists.newArrayList();
    newConstraints.addAll(constraints);
    newConstraints.addAll(other.constraints);
    
    Map<Integer, Type> newBindings = Maps.newHashMap(bindings);
    newBindings.putAll(other.bindings);

    return new ConstraintSet(newConstraints, newBindings, solvable && other.solvable);
  }
  
  public ConstraintSet add(SubtypeConstraint constraint) {
    List<SubtypeConstraint> newConstraints = Lists.newArrayList(constraints);
    newConstraints.add(constraint);
    
    return new ConstraintSet(newConstraints, bindings, solvable);
  }
  
  public ConstraintSet solveIncremental() {
    List<SubtypeConstraint> current = constraints;
    List<SubtypeConstraint> next = Lists.newArrayList();
    boolean done = false;
    boolean solvable = this.solvable;
    
    Map<Integer, Type> nextBindings = Maps.newHashMap(bindings);
    while (!done) {
      done = true;
      for (SubtypeConstraint c : current) {
        Type subtype = c.subtype.substitute(nextBindings);
        Type supertype = c.supertype.substitute(nextBindings);

        if (subtype.isAtomic() && supertype.isAtomic()) {
          if (!subtype.hasTypeVariables()) {
            if (!supertype.hasTypeVariables()) {
              // Verify that these types can be unified.
              // TODO: subtypes
              if (!subtype.getAtomicTypeName().equals(supertype.getAtomicTypeName())) {
                solvable = false;
              } 
            } else {
              // Can safely drop this constraint and assume that 
              // typeVariable equals subtype.
              nextBindings.put(supertype.getAtomicTypeVar(), subtype);
              done = false;
            }
          } else {
            next.add(new SubtypeConstraint(subtype, supertype));
          }
        } else if (!subtype.isAtomic() && !supertype.isAtomic()) {
          next.add(new SubtypeConstraint(supertype.getArgumentType(), subtype.getArgumentType()));
          next.add(new SubtypeConstraint(subtype.getReturnType(), supertype.getReturnType()));
          done = false;
        } else {
          next.add(new SubtypeConstraint(subtype, supertype));
        }
      }
      current = next;
      next = Lists.newArrayList();
    }
    
    return new ConstraintSet(current, nextBindings, solvable);
  }

  public ConstraintSet solveFinal() {
    List<SubtypeConstraint> current = constraints;
    List<SubtypeConstraint> next = Lists.newArrayList();
    boolean done = false;
    boolean solvable = this.solvable;
    
    Map<Integer, Type> nextBindings = Maps.newHashMap(bindings);
    while (!done) {
      done = true;
      for (SubtypeConstraint c : current) {
        Type subtype = c.subtype.substitute(nextBindings);
        Type supertype = c.supertype.substitute(nextBindings);

        if (subtype.isAtomic() && subtype.hasTypeVariables() && !supertype.hasTypeVariables()) {
          // TODO: subtypes
          nextBindings.put(subtype.getAtomicTypeVar(), supertype);
          done = false;
        } else {
          next.add(new SubtypeConstraint(subtype, supertype));
        }
      }
      current = next;
      next = Lists.newArrayList();
    }
    return new ConstraintSet(current, nextBindings, solvable);
  }
  
  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("[ConstraintSet\n");
    for (SubtypeConstraint constraint : constraints) {
      sb.append("  ");
      sb.append(constraint.toString());
      sb.append("\n");
    }
    sb.append(bindings);
    sb.append("]");
    return sb.toString();
  }

  public static class SubtypeConstraint {
    final Type subtype;
    final Type supertype;

    public SubtypeConstraint(Type subtype, Type supertype) {
      this.subtype = Preconditions.checkNotNull(subtype);
      this.supertype = Preconditions.checkNotNull(supertype);
    }
    
    @Override
    public String toString() {
      return subtype + " ⊑ " + supertype;
    }
  }
}

