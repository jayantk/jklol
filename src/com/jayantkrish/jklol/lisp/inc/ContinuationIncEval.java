package com.jayantkrish.jklol.lisp.inc;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.jayantkrish.jklol.ccg.lambda.ExpressionParser;
import com.jayantkrish.jklol.ccg.lambda2.CpsTransform;
import com.jayantkrish.jklol.ccg.lambda2.Expression2;
import com.jayantkrish.jklol.ccg.lambda2.ExpressionSimplifier;
import com.jayantkrish.jklol.lisp.AmbEval;
import com.jayantkrish.jklol.lisp.AmbEval.AmbFunctionValue;
import com.jayantkrish.jklol.lisp.AmbEval.WrappedBuiltinFunction;
import com.jayantkrish.jklol.lisp.ConsValue;
import com.jayantkrish.jklol.lisp.ConstantValue;
import com.jayantkrish.jklol.lisp.Environment;
import com.jayantkrish.jklol.lisp.EvalContext;
import com.jayantkrish.jklol.lisp.FunctionValue;
import com.jayantkrish.jklol.lisp.LispEval.EvalResult;
import com.jayantkrish.jklol.lisp.LispUtil;
import com.jayantkrish.jklol.lisp.SExpression;
import com.jayantkrish.jklol.training.LogFunction;

/**
 * Incremental evaluation oracle for a nondeterministic lambda
 * calculus that represents nondeterminism using continuations. 
 * A program in this lambda calculus can have many possible futures
 * (each of which is represented by a continuation); these continuations
 * are queued on to the {@code GroundedParser} stack and searched
 * jointly with parses of the sentence.
 * 
 * @author jayantk
 *
 */
public class ContinuationIncEval extends AbstractIncEval {
  protected final AmbEval eval;
  protected final Environment env;
  protected final Function<Expression2, Expression2> cpsTransform;
  
  protected final ExpressionParser<SExpression> sexpParser;
  protected final SExpression defs;
  
  protected final int finalContinuationIndex;
  protected final int queueContinuationsIndex;
  protected final int continuationHolderIndex;

  public static final String FINAL_CONTINUATION = "final-continuation";
  public static final String QUEUE_CONTINUATIONS = "queue-k";
  public static final String CONTINUATION_HOLDER = "continuation-inc-eval:continuation-holder";
  
  public ContinuationIncEval(AmbEval eval, Environment env,
      Function<Expression2, Expression2> cpsTransform, SExpression defs) {
    this.eval = Preconditions.checkNotNull(eval);
    this.env = Preconditions.checkNotNull(env);
    this.cpsTransform = Preconditions.checkNotNull(cpsTransform);

    this.sexpParser = ExpressionParser.sExpression(eval.getSymbolTable());
    this.defs = defs;
    
    this.finalContinuationIndex = this.eval.getSymbolTable().add(FINAL_CONTINUATION);
    this.queueContinuationsIndex = this.eval.getSymbolTable().add(QUEUE_CONTINUATIONS);
    this.continuationHolderIndex = this.eval.getSymbolTable().add(CONTINUATION_HOLDER);
  }
  
  public AmbEval getEval() {
    return eval;
  }
  
  /**
   * Gets the initial environment for evaluation. Note that
   * this environment does not include bindings for
   * {@code FINAL_CONTINUATION} or {@code QUEUE_CONTINUATIONS}. 
   * 
   * @return
   */
  public Environment getInitialEnvironment() {
    return env;
  }
  
  public Function<Expression2, Expression2> getCpsTransform() {
    return cpsTransform;
  }
  
  public SExpression getDefs() {
    return defs;
  }

  @Override
  public void evaluateContinuation(IncEvalState state, IncEvalChart chart, LogFunction log) {
    Environment env = state.getEnvironment();
    ContinuationHolder holder = (ContinuationHolder) env.getValue(
        continuationHolderIndex, eval.getSymbolTable());
    FinalContinuation finalContinuation = holder.finalContinuation;
    finalContinuation.setChart(chart, state, log);
    QueueContinuations queueContinuations = holder.queueContinuations;
    queueContinuations.setChart(chart, state, log);
    AmbFunctionValue currentContinuation = (AmbFunctionValue) state.getContinuation();

    // System.out.println("evaluating: " + state.getContinuation());
    // System.out.println("diagram: " + state.getDiagram());

    currentContinuation.apply(Arrays.asList(state.getDiagram()),
        new EvalContext(log), null);
  }

  /**
   * Override this method in subclasses to implement scoring of search states
   * and accumulating features.
   * 
   * @return
   */
  protected void nextState(IncEvalState prev, IncEvalState next, Object continuation,
      Environment env, Object denotation, Object diagram, Object otherArgs, LogFunction log) {
    next.set(continuation, env, denotation, diagram, prev.getProb(), null);
  }

  @Override
  public Environment getEnvironment() {
    Environment continuationEnv = Environment.extend(env);
    FinalContinuation finalContinuation = new FinalContinuation();
    QueueContinuations queueContinuations = new QueueContinuations();
    ContinuationHolder holder = new ContinuationHolder(finalContinuation, queueContinuations);
    continuationEnv.bindName(finalContinuationIndex, new WrappedBuiltinFunction(finalContinuation));
    continuationEnv.bindName(queueContinuationsIndex, new WrappedBuiltinFunction(queueContinuations));
    continuationEnv.bindName(continuationHolderIndex, holder);

    if (defs != null) {
      eval.eval(defs, continuationEnv, null);
    }

    return continuationEnv;
  }
  
  @Override
  public AmbFunctionValue lfToContinuation(Expression2 lf, Environment env) {
    Expression2 cpsLf = cpsTransform.apply(lf);
    // System.out.println(cpsLf);
    // System.out.println("lfToContinuation: " + lf + " -> " + cpsLf);

    SExpression cpsSexp = sexpParser.parse(cpsLf.toString());
    EvalResult evalResult = eval.eval(cpsSexp, env, null);
    // System.out.println(evalResult.getValue());

    Preconditions.checkState(evalResult.getValue() instanceof AmbFunctionValue,
        "Expected AmbFunctionValue, Got: %s", evalResult.getValue());
    return (AmbFunctionValue) evalResult.getValue();
  }
  
  public class QueueContinuations implements FunctionValue {
    private IncEvalChart chart;
    private IncEvalState current;
    private LogFunction log;

    public QueueContinuations() {}
    
    public void setChart(IncEvalChart chart, IncEvalState current, LogFunction log) {
      this.chart = chart;
      this.current = current;
      this.log = log;
    }

    @Override
    public Object apply(List<Object> args, EvalContext context) {
      LispUtil.checkArgument(args.size() == 2 || args.size() == 3,
          "Expected 2 or 3 arguments, got: %s", args);
      AmbFunctionValue continuation = (AmbFunctionValue) args.get(0);
      List<Object> nextDenotations = ConsValue.consListToList(args.get(1));
      
      List<Object> nextOtherArgs = (args.size() == 3) ?
          ConsValue.consListToList(args.get(2)) : null;
      LispUtil.checkState(nextOtherArgs == null ||
          nextOtherArgs.size() == nextDenotations.size());
      
      return new WrappedBuiltinFunction(new FunctionValue() {
        public Object apply(List<Object> args2, EvalContext context2) {
          LispUtil.checkArgument(args2.size() == 1 || args2.size() == 2,
              "Expected 1 or 2 arguments, got: %s", args2);
          List<Object> nextDiagrams = ConsValue.consListToList(args2.get(0));
          
          if (nextDiagrams.size() == 1 && nextDenotations.size() > 1) {
            nextDiagrams = Collections.nCopies(nextDenotations.size(), nextDiagrams.get(0));
          }
          List<Object> myNextDenotations = nextDenotations;
          if (myNextDenotations.size() == 1 && nextDiagrams.size() > 1) {
            myNextDenotations = Collections.nCopies(nextDiagrams.size(), myNextDenotations.get(0));
          }
          
          LispUtil.checkState(nextDiagrams.size() == myNextDenotations.size(),
              "Unequal number of diagrams and denotations. Got: %s %s", nextDiagrams, myNextDenotations);
          
          for (int i = 0; i < nextDiagrams.size(); i++) {
            Object denotation = myNextDenotations.get(i);
            Object diagram = nextDiagrams.get(i);
            Object otherArg = null;
            if (nextOtherArgs != null) {
              otherArg = nextOtherArgs.get(i);
            }

            IncEvalState next = chart.alloc();
            Object nextCont = continuation.apply(Arrays.asList(denotation), context2, null);
            nextState(current, next, nextCont, Environment.extend(current.getEnvironment()),
                denotation, diagram, otherArg, log);
            chart.offer(current, next);
          }
          
          return ConstantValue.NIL;
        }
      });
    }
  }

  public class FinalContinuation implements FunctionValue {
    private IncEvalChart chart;
    private IncEvalState current;
    private LogFunction log;
    
    public FinalContinuation() {
    }

    public void setChart(IncEvalChart chart, IncEvalState current, LogFunction log) {
      this.chart = chart;
      this.current = current;
      this.log = log;
    }

    public Object apply(List<Object> args1, EvalContext context1) {
      LispUtil.checkArgument(args1.size() == 1);
      Object denotation = args1.get(0);

      return new WrappedBuiltinFunction(new FunctionValue() {
        public Object apply(List<Object> args2, EvalContext context2) {
          LispUtil.checkArgument(args2.size() == 1);
          Object diagram = args2.get(0);
          
          IncEvalState next = chart.alloc();
          nextState(current, next, null, Environment.extend(current.getEnvironment()),
              denotation, diagram, null, log);
          chart.offer(current, next);
          
          return ConstantValue.NIL;
        }
      });
    }
  }
  
  private static class ContinuationHolder {
    public final FinalContinuation finalContinuation;
    public final QueueContinuations queueContinuations;

    public ContinuationHolder(FinalContinuation finalContinuation,
        QueueContinuations queueContinuations) {
      this.finalContinuation = finalContinuation;
      this.queueContinuations = queueContinuations;
    }
  }
  
  public static class SimplifierCpsTransform implements Function<Expression2, Expression2> {
    
    private final ExpressionSimplifier simplifier;
    private final Expression2 lfConversion;
    
    public SimplifierCpsTransform(ExpressionSimplifier simplifier, Expression2 lfConversion) {
      this.simplifier = Preconditions.checkNotNull(simplifier);
      this.lfConversion = lfConversion;
    }
    
    @Override
    public Expression2 apply(Expression2 lf) {
      if (lfConversion != null) {
        lf = Expression2.nested(lfConversion, lf);
      }
      lf = simplifier.apply(lf);

      // System.out.println("lfToContinuation: " + lf);
      return simplifier.apply(CpsTransform.apply(lf, Expression2.constant(FINAL_CONTINUATION)));
    }
  }
}
