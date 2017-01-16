package com.jayantkrish.jklol.ccg.lambda2;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.jayantkrish.jklol.ccg.lambda.Type;
import com.jayantkrish.jklol.ccg.lambda.TypeDeclaration;

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
    newConstraints.add(constraint.substitute(bindings));
    
    return new ConstraintSet(newConstraints, bindings, solvable);
  }
  
  public ConstraintSet makeAtomic(int nextVarId) {
    List<SubtypeConstraint> current = constraints;
    List<SubtypeConstraint> next = Lists.newArrayList();
    boolean done = false;
    boolean solvable = this.solvable;
    
    Map<Integer, Type> nextBindings = Maps.newHashMap(bindings);
    while (!done) {
      System.out.println(current.size() + " " + current);
      
      done = true;
      for (SubtypeConstraint c : current) {
        Type subtype = c.subtype.substitute(nextBindings);
        Type supertype = c.supertype.substitute(nextBindings);
        
        // System.out.println(subtype + " "+ subtype.isFunctional());
        // System.out.println(supertype + " "+ supertype.isFunctional());
        
        if (subtype.isFunctional() && supertype.isFunctional()) {
          // Replace this constraint with the entailed subtype
          // constraints on argument/return types.
          next.add(new SubtypeConstraint(supertype.getArgumentType(), subtype.getArgumentType()));
          next.add(new SubtypeConstraint(subtype.getReturnType(), supertype.getReturnType()));
          done = false;
        } else if (!subtype.isFunctional() && supertype.isFunctional()) {
          if (subtype.hasTypeVariables()) {
            /*
            Type newArgVar = Type.createTypeVariable(nextVarId++);
            Type newReturnVar = Type.createTypeVariable(nextVarId++);
            Type nextType = Type.createFunctional(newArgVar, newReturnVar, false);
            */
            
            nextBindings.put(subtype.getAtomicTypeVar(), supertype);
            // next.add(c);
            done = false;
          } else {
            solvable = false;
          }
        } else if (subtype.isFunctional() && !supertype.isFunctional()) {
          if (supertype.hasTypeVariables()) {
            /*
            Type newArgVar = Type.createTypeVariable(nextVarId++);
            Type newReturnVar = Type.createTypeVariable(nextVarId++);
            Type nextType = Type.createFunctional(newArgVar, newReturnVar, false);
            */

            nextBindings.put(supertype.getAtomicTypeVar(), supertype);
            // next.add(c);
            done = false;
          } else {
            solvable = false;
          }
        } else {
          // Retain all atomic constraints.
          next.add(new SubtypeConstraint(subtype, supertype));
        }
      }

      current = next;
      next = Lists.newArrayList();
    }

    return new ConstraintSet(current, nextBindings, solvable);
  }
  
  public ConstraintSet solveAtomic(TypeDeclaration typeDeclaration) {
    List<SubtypeConstraint> current = constraints;
    List<SubtypeConstraint> next = Lists.newArrayList();
    Map<Integer, Type> lowerBounds = Maps.newHashMap();
    Map<Integer, Type> upperBounds = Maps.newHashMap();
    boolean done = false;
    boolean solvable = this.solvable;
    
    Set<Integer> allVars = Sets.newHashSet();
    for (SubtypeConstraint c : current) {
      Type subtype = c.subtype;
      Type supertype = c.supertype;
      Preconditions.checkState(subtype.isAtomic() && supertype.isAtomic());
      
      if (subtype.hasTypeVariables()) {
        // lowerBounds.put(subtype.getAtomicTypeVar(), TypeDeclaration.BOTTOM);
        allVars.add(subtype.getAtomicTypeVar());
      } 
      if (supertype.hasTypeVariables()) {
        // upperBounds.put(supertype.getAtomicTypeVar(), TypeDeclaration.TOP);
        allVars.add(supertype.getAtomicTypeVar());
      }
    }

    Map<Integer, Type> nextBindings = Maps.newHashMap(bindings);
    while (!done) {
      done = true;
      System.out.println(current.size() + " " + current);
      System.out.println("  " + nextBindings);
      Set<Integer> onLeft = Sets.newHashSet();
      Set<Integer> onRight = Sets.newHashSet();
      for (SubtypeConstraint c : current) {
        Type subtype = c.subtype;
        Type supertype = c.supertype;

        if (!subtype.hasTypeVariables() && !supertype.hasTypeVariables()) {
          if (!typeDeclaration.isAtomicSubtype(subtype.getAtomicTypeName(),
              supertype.getAtomicTypeName())) {
            solvable = false;
          }
        } else if (!subtype.hasTypeVariables() && supertype.hasTypeVariables()) {        
          int supertypeVar = supertype.getAtomicTypeVar();
          Type bound = subtype;
          if (lowerBounds.containsKey(supertypeVar)) {
            bound = typeDeclaration.meet(lowerBounds.get(supertypeVar), bound);
          } 
          lowerBounds.put(supertype.getAtomicTypeVar(), bound);

        } else if (subtype.hasTypeVariables() && !supertype.hasTypeVariables()) {
          int subtypeVar = subtype.getAtomicTypeVar();
          Type bound = supertype;
          if (upperBounds.containsKey(subtypeVar)) {
            bound = typeDeclaration.join(upperBounds.get(subtypeVar), bound);
          }
          upperBounds.put(subtype.getAtomicTypeVar(), bound);
        } else {
          onLeft.add(subtype.getAtomicTypeVar());
          onRight.add(supertype.getAtomicTypeVar());
          next.add(c);
        }
      }
      
      for (Integer var : lowerBounds.keySet()) {
        if (!onRight.contains(var) && !nextBindings.containsKey(var)) {
          nextBindings.put(var, lowerBounds.get(var));
          done = false;
        }
      }
      
      for (Integer var : upperBounds.keySet()) {
        if (!onLeft.contains(var) && !nextBindings.containsKey(var)) {
          nextBindings.put(var, upperBounds.get(var));
          done = false;
        }
      }
      
      /*
      for (Integer var : allVars) {
        if (!lowerBounds.get(var) && !upperBounds.get(var)) {
          if (onRight.contains(var) && !onLeft.contains(var)) {
            nextBindings.put()
          }
          if (onLeft.contains(var) && !onRight.contains(var)) {
            
          }
        }
      }
      */
      
      current = Lists.newArrayList();
      for (SubtypeConstraint c : next) {
        current.add(c.substitute(nextBindings));
      }
      next = Lists.newArrayList();
    }

    return new ConstraintSet(current, nextBindings, solvable);
  }

  /*
  public ConstraintSet solveIncremental() {
    List<SubtypeConstraint> current = constraints;
    List<SubtypeConstraint> next = Lists.newArrayList();
    boolean done = false;
    boolean solvable = this.solvable;
    
    Map<Integer, Type> lowerBounds = Maps.newHashMap(lowerBounds);
    Map<Integer, Type> upperBounds = Maps.newHashMap(upperBounds);
    Map<Integer, Type> nextBindings = Maps.newHashMap(bindings);
    while (!done) {
      done = true;
      for (SubtypeConstraint c : current) {
        Type subtype = c.subtype.substitute(nextBindings);
        Type supertype = c.supertype.substitute(nextBindings);

        if (subtype.isAtomic() && supertype.isAtomic()) {
          if (!subtype.hasTypeVariables() && !supertype.hasTypeVariables()) {
            // Verify that the subtype is actually a subtype.
            // If not, the equations are not solvable. 
            if (!typeDeclaration.isAtomicSubtype(subtype.getAtomicTypeName(),
                supertype.getAtomicTypeName())) {
              solvable = false;
            }
          } else if (!subtype.hasTypeVariables() && supertype.hasTypeVariables()) {
              // Can safely drop this constraint and assume that 
              // typeVariable equals subtype.
              nextBindings.put(supertype.getAtomicTypeVar(), subtype);
              done = false;
          } else if (subtype.hasTypeVariables() && !supertype.hasTypeVariables()) {
            
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
  */

  /*
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
  */
  
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
    
    public SubtypeConstraint substitute(Map<Integer, Type> replacements) {
      return new SubtypeConstraint(subtype.substitute(replacements),
          supertype.substitute(replacements));
    }
    
    @Override
    public String toString() {
      return subtype + " ⊑ " + supertype;
    }
  }
}

