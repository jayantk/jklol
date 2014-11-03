;; MLN definition ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; The set of unary predicates and objects they can be
;; applied to.
(define objects (list "Anna" "Bob" "John" "Smith"))
(define unary-predicates (list "Smokes" "Cancer"))

;; Instantiate the set of all unary propositions. This is a list 
;; of tuples, e.g., (list (list "Smokes" "Anna") (list "Smokes" "Bob") ...)
(define unary-propositions (outer-product (list unary-predicates objects)))

;; Create a random variable for every unary proposition.
(define unary-proposition-vars (lifted-map (lambda (x) (amb (list #t #f) (list 1 1)))
                                            unary-propositions))

;; Function for getting the random variable associated with a 
;; proposition. A proposition is a list, e.g., (list "Smokes" "Anna").
(define get-propv (lambda (proposition) 
                    (lifted-get-ith-element unary-proposition-vars 
                                            (find-index proposition unary-propositions))))

;; Instantiates a formula with a given weight for every object.
(define instantiate-formula (formula weight)
  (lifted-map (lambda (object) (add-weight (formula object) weight)) objects))

;; Implementations of quantifiers. These functions return a random
;; variable which is true iff the quantified formula is true.
(define forall (formula)
  (lifted-foldr and (lifted-map formula objects) #t))

(define exists (formula)
  (lifted-foldr or (lifted-map formula objects) #f))

;; Formulas ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Bob probably smokes, but not John.
(add-weight (get-propv (list "Smokes" "Bob")) 3.0)
(add-weight (get-propv (list "Smokes" "John")) 0.5)

;; It's unlikely that both Anna and Bob smoke.
(add-weight (and (get-propv (list "Smokes" "Bob"))
                 (get-propv (list "Smokes" "Anna")))
                 0.3)

;; Cancer is unlikely a priori.
(instantiate-formula (lambda (object) (get-propv (list "Cancer" object))) 0.9)

;; Smoking causes cancer.
(define => (lambda (x y) (not (and x (not y)))))
(define smoking-causes-cancer (lambda (object) (=> (get-propv (list "Smokes" object))
                                                 (get-propv (list "Cancer" object)))))
(instantiate-formula smoking-causes-cancer 2.0)

;; Someone smokes.
(add-weight (exists (lambda (object) (get-propv (list "Smokes" object)))) 2.0)

;; Queries ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(display "What is the most likely truth value of every proposition?")
(display unary-propositions)
(display (get-best-value unary-proposition-vars))
(display "")
(display "What is the probability that Bob smokes?")
(display (get-marginals (get-propv (list "Smokes" "Bob"))))
(display "")
(display "What is the probability that someone has cancer?")
(display (get-marginals (exists (lambda (object) (get-propv (list "Smokes" object))))))
