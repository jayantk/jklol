package com.jayantkrish.jklol.lisp;

import java.util.List;

/**
 * A function that can be applied to arguments and returns
 * a value.
 *  
 * @author jayantk
 */
public interface FunctionValue {

  Object apply(List<Object> argumentValues, EvalContext context);
}
