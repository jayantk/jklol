;; Introduction to AmbLisp.

;; AmbLisp is a variant of LISP (technically, Scheme) intended for
;; defining, training, and performing inference in graphical
;; models. AmbLisp enables these operations by adding several
;; nondeterministic primitives to LISP that let a single program
;; evaluate in multiple ways (potentially returning different values
;; each way). Each such evaluation is assigned a weight, and the
;; probability of an evaluation is its weight divided by the total
;; weight of all possible evaluations.

;; This file is an introduction to AmbLisp, and also
;; a runnable program. Anything following a semicolon on a line is
;; treated as a comment, and is ignored by the interpreter.

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; AmbLisp supports standard LISP operations, such as:

;; Defining and accessing variables:
(define two 2)
two ;; <- evaluates to 2
(define foo "foo")
foo ;; <- evaluates to "foo"

;; Procedures:
(define plus-one (lambda (x) (+ x 1)))
(plus-one two) ;; <- evaluates to 3
;; Equivalent, shorter definition: (define plus-one (x) (+ x 1))

;; Lists:
(define one-to-five (list 1 2 3 4 5))
(car one-to-five) ;; <- evaluates to 1
(cdr one-to-five) ;; <- evaluates to (list 2 3 4 5)

;; Conditionals:
(if (= two 3) ;; <- the condition to test"
    "returned-if-condition-is-true"
  "returned-if-condition-is-false")

;; Arithmetic and Boolean logic:
(+ 1 1) ;; <- evaluates to 2
(or (= 1 2) (= 1 1)) ;; <- evaluates to #t (true)

;; Other supported Lisp constructions include begin and let. So far,
;; all of these operations have been deterministic. There are two
;; nondeterministic operations:

;; amb represents a weighted, nondeterministic choice from a list of
;; possibilities:
(define val (amb (list "a" "b" "c") (list 1.0 2.0 3.0)))
;; val now evaluates to either "a" "b" or "c". If val is "a", the
;; weight of the current evaluation is 1, etc. amb can alse be called
;; with no weights, in which case all weights are assumed to be 1.0:
(amb (list "a" "b"))
;; which is equivalent to (amb (list "a" "b") (list 1.0 1.0))

;; add-weight tests if the current evaluation satisfies some
;; condition. If the condition is satisfied, it multiplies the weight
;; of the current evaluation by an amount:
(add-weight (= val "b") 2.0)
;; At this point, the set of possible values for val and their
;; corresponding weights are: "a" (1.0), "b" (4.0, because add-weight
;; increased the weight of this value), and "c" (3.0).

;; Nondeterministic values can be used exactly like normal values:
(define rand-int (amb (list 1 2 3) (list 2.0 3.0 4.0)))
(* rand-int 2) ;; <- evaluates to either 2, 4 or 6, with the same
               ;; weights as above

;; (Technical note: nondeterministic values can't be used in the
;; condition clause of an if statement. This limitation may be fixed
;; later.)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Inference in AmbLisp: Reasoning about nondeterministic evaluations.

;; We've seen that a program in AmbLisp has many possible weighted
;; evaluations. The probability of a particular evaluation is equal to
;; its weight, divided by the weight of all possible evaluations of
;; the program. Therefore, inference in AmbLisp is thus a matter of
;; reasoning about all possible evaluations and their weights. There
;; are two built-in procedures for performing inference on the set of
;; possible program evaluations:

;; get-marginals computes a probability distribution over the value of
;; an expression. This procedure sums the weights of all
;; nondeterministic evaluations that result in the same value for the
;; expression.
(get-marginals val) ;; <- evaluates to: (list (list "a" "b" "c") (list 0.125 0.5 0.375))

;; Here's a less trivial example where multiple evaluations produce
;; the same value:
(get-marginals (+ (amb (list 0 1)) (amb (list 0 1)))) ;; <- evaluates to:
;; (list (list 0 1 2) (list 0.25 0.5 0.25))

;; get-best-value computes the highest-weighted value of an
;; expression, maximizing over all possible evaluations:
(get-best-value val) ;; <- evaluates to "b"

;; Note that get-best-value is not equivalent to selecting the value
;; with the highest marginal probability. The difference is that
;; get-best-value *maximizes* over all possible evaluations, while
;; get-marginals *sums* over all possible evaluations. For example:
(get-best-value (+ (amb (list 0 1) (list 1.5 1.0))
                   (amb (list 0 1) (list 1.5 1.0))))
;; evaluates to 0, whereas:
(get-marginals (+ (amb (list 0 1) (list 1.5 1.0))
                   (amb (list 0 1) (list 1.5 1.0))))
;; evaluates to (list (list 0 1 2) (list 0.36 0.48 0.16)). Note that 1
;; has the highest marginal probability in the returned list.

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Examples using Inference

;; Probabilistic computations (e.g., graphical models) can be
;; concisely encoded in AmbLisp using a combination of amb and
;; add-weight.

;; Simple probability example:
;;
;; Function that flips a weighted coin. The coin turns up heads (1)
;; with probability p:
(define flip (p) (amb (list 0 1) (list (- 1.0 p) p)))

(get-marginals (flip 0.3)) ;; <- evaluates to (list (list 0 1) (list 0.7 0.3))

;; Flip n weighted coins and return the number of heads. This is
;; equivalent to drawing from a binomial distribution:
(define binomial (n p) 
  (if (= n 0)
      0
    (+ (flip p) (binomial (- n 1) p))))

(get-marginals (binomial 2 0.3)) ;; <- evaluates to:
;; (list (list 0 1 2) (list 0.49 0.42 0.09))


;; Graphical model example:
;;
;; How do we use AmbLisp to build a graphical model? Intuitively, we
;; use amb to create random variables, and add-weight to create
;; factors between them. For example, let's build a simple two
;; variable model:
(define var1 (flip 0.5))
(define var2 (flip 0.5))

;; Thus far, we have two independent variables, each of which is 1
;; with probability 0.5 and 0 with probability 0.5. We can verify this
;; fact using get-marginals:
(get-marginals (list var1 var2))
;; evaluates to:
;; (list (list (list 1 1) (list 0 0) (list 1 0) (list 0 1))
;;       (list 0.25 0.25 0.25 0.25))

;; We can create a factor that relates the value of var1 to var2 using
;; add-weight:
(add-weight (= var1 var2) 3.0)

;; Intuitively, this creates a factor that makes it more likely that
;; var1 and var2 are equal. We can again verify the dependence using
;; get-marginals:
(get-marginals (list var1 var2)) 
;; evaluates to:
;; (list (list (list 0 0) (list 1 1) (list 1 0) (list 0 1))
;;       (list 0.375 0.375 0.125 0.125))
;;
;; Notice that both the values (0 0) and (1 1) now have marginal
;; probabilities that are 3x higher than the two other values. This
;; happens because add-weight increases the weights of the the two
;; evaluations where var1 equals var2.


;; SAT-solving
;;
;; The inference algorithm in AmbLisp is #P-complete, which enables it
;; to solve counting and search problems. (In the worst case, program
;; runtime is exponential in the size of the inference problem, so
;; large problems may take a long time. However, many problems permit
;; polynomial time inference -- it depends on the problem's
;; structure.)

(define make-true-false-var () (amb (list #t #f)))
(define x1 (make-true-false-var))
(define x2 (make-true-false-var))
(define x3 (make-true-false-var))

;; satisfied is the truth value of the following CNF formula.
(define satisfied (and (or (not x2) (not x3) (not x1))
                       (or x1 (not x2) x3)
                       (or x1 x2 x3)))

;; Count the number of satisfying assignments.
(get-marginals satisfied)
;; evaluates to:
;; (list (list TRUE FALSE) (list 0.625 0.375))
;; The number of satisfying assignments is 0.625 * 8 = 5

;; Find a satisfying assignment.
(add-weight (not satisfied) 0.0)
(get-best-value (list x1 x2 x3))
;; evaluates to:
;; (list TRUE FALSE TRUE)
;; There are 5 such values with equal probability, so you might see
;; a different value.


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Learning