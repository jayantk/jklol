package com.jayantkrish.jklol.ccg.lambda2;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.jayantkrish.jklol.ccg.lambda.Type;
import com.jayantkrish.jklol.ccg.lambda.TypeDeclaration;
import com.jayantkrish.jklol.util.CountAccumulator;

public class ConstraintSet {

  private final List<SubtypeConstraint> equalityConstraints;
  private final List<SubtypeConstraint> constraints;
  private final TypeReplacement bindings;
  private final boolean solvable;
  
  private int typeVarCounter = 0;
  
  public ConstraintSet(List<SubtypeConstraint> equalityConstraints,
      List<SubtypeConstraint> constraints, TypeReplacement bindings,
      boolean solvable) {
    this.equalityConstraints = equalityConstraints;
    this.constraints = constraints;
    this.bindings = bindings;
    this.solvable = solvable;
  }
  
  public static ConstraintSet empty() {
    return new ConstraintSet(Lists.newArrayList(), Lists.newArrayList(),
        new TypeReplacement(Maps.newHashMap()), true);
  }
  
  public Map<Integer, Type> getBindings() {
    return bindings.getBindings();
  }

  public boolean isSolvable() {
    return solvable;
  }

  public void add(SubtypeConstraint constraint) {
    constraints.add(constraint.substitute(bindings.getBindings()));
  }
  
  public void addAll(Collection<SubtypeConstraint> constraintCollection) {
    for (SubtypeConstraint c : constraintCollection) {
      constraints.add(c.substitute(bindings.getBindings()));
    }
  }
  
  public void addEquality(Type subt, Type supert) {
    equalityConstraints.add(new SubtypeConstraint(subt, supert));
  }

  public Type getFreshTypeVar() {
    return Type.createTypeVariable(typeVarCounter++);
  }

  public Type upcast(Type t, boolean positiveCtx) {
    return upcast(t, constraints, positiveCtx);
  }

  private Type upcast(Type t, List<SubtypeConstraint> acc, boolean positiveCtx) {
    if (t.isAtomic()) {
      Type v = getFreshTypeVar();
      if (positiveCtx) {
        acc.add(new SubtypeConstraint(t, v));
      } else {
        acc.add(new SubtypeConstraint(v, t));
      }
      return v;
    } else {
      Type argType = upcast(t.getArgumentType(), acc, !positiveCtx);
      Type retType = upcast(t.getReturnType(), acc, positiveCtx);
      return Type.createFunctional(argType, retType, t.acceptsRepeatedArguments());
    }
  }
  
  /*
  public ConstraintSet addEquality(Type subt, Type supert) {
    List<SubtypeConstraint> current = Lists.newArrayList(new SubtypeConstraint(subt, supert));
    List<SubtypeConstraint> next = Lists.newArrayList();
    boolean done = false;
    boolean solvable = this.solvable;

    Map<Integer, Type> nextBindings = Maps.newHashMap(bindings);
    while (!done) {
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
            nextBindings.put(subtype.getAtomicTypeVar(), supertype);
            // next.add(c);
            done = false;
          } else {
            solvable = false;
          }
        } else if (subtype.isFunctional() && !supertype.isFunctional()) {
          if (supertype.hasTypeVariables()) {
            nextBindings.put(supertype.getAtomicTypeVar(), supertype);
            // next.add(c);
            done = false;
          } else {
            solvable = false;
          }
        } else {
          if (subtype.hasTypeVariables()) {
            nextBindings.put(subtype.getAtomicTypeVar(), supertype);
            done = false;
          } else if (supertype.hasTypeVariables()) {
            nextBindings.put(supertype.getAtomicTypeVar(), subtype);
            done = false;
          } else {
            Preconditions.checkState(false, "shouldn't get here");
          }
        }
      }

      current = next;
      next = Lists.newArrayList();
    }
    
    List<SubtypeConstraint> atomicConstraints = Lists.newArrayList();
    for (SubtypeConstraint c : constraints) {
      atomicConstraints.add(c.substitute(nextBindings));
    }
    return new ConstraintSet(atomicConstraints, nextBindings, solvable);
  }
  */
  
  public ConstraintSet makeAtomic(int nextVarId) {
    List<SubtypeConstraint> current = equalityConstraints;
    List<SubtypeConstraint> next = Lists.newArrayList();
    boolean done = false;
    boolean solvable = this.solvable;
    
    // System.out.println(" equality");
    // System.out.println(equalityConstraints);
    
    TypeReplacement nextBindings = new TypeReplacement(bindings);
    while (!done) {
      // System.out.println(current.size() + " " + current);
      
      done = true;
      for (SubtypeConstraint c : current) {
        Type subtype = c.subtype.substitute(nextBindings.getBindings());
        Type supertype = c.supertype.substitute(nextBindings.getBindings());
        
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
            
            nextBindings.add(subtype.getAtomicTypeVar(), supertype);
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

            nextBindings.add(supertype.getAtomicTypeVar(), supertype);
            // next.add(c);
            done = false;
          } else {
            solvable = false;
          }
        } else {
          if (subtype.hasTypeVariables() && supertype.hasTypeVariables()) {
            if (subtype.getAtomicTypeVar() != supertype.getAtomicTypeVar()) {
              nextBindings.add(subtype.getAtomicTypeVar(), supertype);
            }
          } else if (subtype.hasTypeVariables()) {
            nextBindings.add(subtype.getAtomicTypeVar(), supertype);
            done = false;
          } else if (supertype.hasTypeVariables()) {
            nextBindings.add(supertype.getAtomicTypeVar(), subtype);
            done = false;
          } else {
            if (!subtype.equals(supertype)) {
              solvable = false;
            }
          }
        }
      }

      current = next;
      next = Lists.newArrayList();
    }
    
    done = false;
    current = constraints;
    next = Lists.newArrayList();
    while (!done) {
      done = true;
      for (SubtypeConstraint c : current) {
        SubtypeConstraint substituted = c.substitute(nextBindings.getBindings());
        System.out.println(substituted);
        Type subtype = substituted.subtype;
        Type supertype = substituted.supertype;
        
        if (subtype.isFunctional() && supertype.isFunctional()) {
          System.out.println("F F");
          next.add(new SubtypeConstraint(supertype.getArgumentType(), subtype.getArgumentType()));
          next.add(new SubtypeConstraint(subtype.getReturnType(), supertype.getReturnType()));
          done = false;
        } else if (!subtype.isFunctional() && supertype.isFunctional()) {
          System.out.println("A F");
          if (subtype.hasTypeVariables()) {
            Type subtypeReplacement = upcast(supertype, next, false);
            nextBindings.add(subtype.getAtomicTypeVar(), subtypeReplacement);
            System.out.println(subtype.getAtomicTypeVar() + " -> " + subtypeReplacement);
            done = false;
          } else {
            solvable = false;
          }
        } else if (subtype.isFunctional() && !supertype.isFunctional()) {
          System.out.println("F A");
          if (supertype.hasTypeVariables()) {
            Type supertypeReplacement = upcast(subtype, next, true);
            nextBindings.add(supertype.getAtomicTypeVar(), supertypeReplacement);
            done = false;
          } else {
            solvable = false;
          }
        } else {
          System.out.println("A A");
          next.add(substituted);
        }
      }

      current = next;
      next = Lists.newArrayList();
    }

    System.out.println("HERE");
    return new ConstraintSet(Lists.newArrayList(), current, nextBindings, solvable);
  }
  
  public ConstraintSet solveAtomic(TypeDeclaration typeDeclaration) {
    List<SubtypeConstraint> current = constraints;
    List<SubtypeConstraint> next = Lists.newArrayList();
    Map<Integer, Type> lowerBounds = Maps.newHashMap();
    Map<Integer, Type> upperBounds = Maps.newHashMap();
    Set<Integer> boundedVars = Sets.newHashSet();
    boolean done = false;
    boolean solvable = this.solvable;

    TypeReplacement nextBindings = new TypeReplacement(bindings);
    System.out.println("start bindings: " + nextBindings);
    while (!done && solvable) {
      done = true;
      // System.out.println(current.size() + " " + current);
      // System.out.println("  " + nextBindings);
      Set<Integer> onLeft = Sets.newHashSet();
      Set<Integer> onRight = Sets.newHashSet();
      CountAccumulator<Integer> varCounts = CountAccumulator.create();
      Map<Integer, Integer> varCountSubVar = Maps.newHashMap();
      for (SubtypeConstraint c : current) {
        Type subtype = c.subtype;
        Type supertype = c.supertype;
        
        Preconditions.checkState(subtype.isAtomic() && supertype.isAtomic(),
            "solveAtomic() requires atomic subtype constraints, got %s", c);

        if (!subtype.hasTypeVariables() && !supertype.hasTypeVariables()) {
          if (!typeDeclaration.isAtomicSubtype(subtype.getAtomicTypeName(),
              supertype.getAtomicTypeName())) {
            solvable = false;
          }
        } else if (!subtype.hasTypeVariables() && supertype.hasTypeVariables()) {        
          int supertypeVar = supertype.getAtomicTypeVar();
          Type bound = subtype;
          if (lowerBounds.containsKey(supertypeVar)) {
            bound = typeDeclaration.join(lowerBounds.get(supertypeVar), bound);
          } 
          lowerBounds.put(supertype.getAtomicTypeVar(), bound);
          boundedVars.add(supertype.getAtomicTypeVar());

        } else if (subtype.hasTypeVariables() && !supertype.hasTypeVariables()) {
          int subtypeVar = subtype.getAtomicTypeVar();
          Type bound = supertype;
          if (upperBounds.containsKey(subtypeVar)) {
            bound = typeDeclaration.meet(upperBounds.get(subtypeVar), bound);
          }
          upperBounds.put(subtype.getAtomicTypeVar(), bound);
          boundedVars.add(subtype.getAtomicTypeVar());
        } else {
          if (!subtype.equals(supertype)) {
            onLeft.add(subtype.getAtomicTypeVar());
            onRight.add(supertype.getAtomicTypeVar());
            varCounts.increment(subtype.getAtomicTypeVar(), 1);
            varCounts.increment(supertype.getAtomicTypeVar(), 1);
            varCountSubVar.put(subtype.getAtomicTypeVar(), supertype.getAtomicTypeVar());
            varCountSubVar.put(supertype.getAtomicTypeVar(), subtype.getAtomicTypeVar());
            next.add(c);
          }
        }
      }

      for (Integer var : boundedVars) {
        Type lb = lowerBounds.get(var);
        Type ub = upperBounds.get(var);
        if (lb != null && ub != null) {
          if (!typeDeclaration.isAtomicSubtype(lb.getAtomicTypeName(),
              ub.getAtomicTypeName())) {
            solvable = false;
          }
        }
        
        if (lb != null && !onRight.contains(var) && !nextBindings.contains(var)) {
          nextBindings.add(var, lb);
          done = false;
        }

        if (ub != null && !onLeft.contains(var) && !nextBindings.contains(var)) {
          nextBindings.add(var, ub);
          done = false;
        }
      }
      
      for (Integer var : varCounts.keySet()) {
        System.out.println("  " + var);
        if (varCounts.getCount(var) <= 1.0 && !boundedVars.contains(var)
            && !nextBindings.contains(var)
            && !nextBindings.contains(varCountSubVar.get(var))) {
          System.out.println("  " + var + " -> " + varCountSubVar.get(var));
          nextBindings.add(var, Type.createTypeVariable(varCountSubVar.get(var)));
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
      
      System.out.println("next: " + nextBindings);
      current = Lists.newArrayList();
      for (SubtypeConstraint c : next) {
        System.out.println(c);
        current.add(c.substitute(nextBindings.getBindings()));
      }
      next = Lists.newArrayList();
    }

    return new ConstraintSet(equalityConstraints, current, nextBindings, solvable);
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

