;; MLN definition ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; The set of unary predicates and terms they can be
;; applied to.
(define objects (list "Anna" "Bob" "John" "Smith"))
(define unary-predicates (list "Smokes" "Cancer"))

;; Instantiate the set of all unary propositions. This is a list 
;; of tuples, e.g., (list (list "Smokes" "Anna") (list "Smokes" "Bob") ...)
(define unary-propositions (outer-product (list unary-predicates objects)))

;; Create a random variable for each unary proposition.
(define unary-proposition-vars (lifted-map (lambda (x) (amb (list #t #f) (list 1 1)))
                                            unary-propositions))

;; Function for looking up a proposition's variable in the
;; instantiated set of unary propositions.
(define get-propv (lambda (proposition) 
                    (lifted-get-ith-element unary-proposition-vars 
                                            (find-index proposition unary-propositions))))

;; Implementations of quantifiers.
(define forall (formula weight)
  (lifted-map (lambda (term) (add-weight (formula term) weight)) objects))

(define exists (formula weight)
  (add-weight (lifted-foldr or (lifted-map formula objects) #f) weight))

;; Formulas ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Bob probably smokes, but not John.
(add-weight (get-propv (list "Smokes" "Bob")) 3.0)
(add-weight (get-propv (list "Smokes" "John")) 0.5)

;; Cancer is unlikely a priori.
(forall (lambda (term) (get-propv (list "Cancer" term))) 0.9)

;; It's unlikely that both Anna and Bob smoke.
(add-weight (and (get-propv (list "Smokes" "Bob"))
                 (get-propv (list "Smokes" "Anna")))
                 0.3)

;; Smoking causes cancer
(define => (lambda (x y) (not (and x (not y)))))
(define smoking-causes-cancer (lambda (term) (=> (get-propv (list "Smokes" term))
                                                 (get-propv (list "Cancer" term)))))
(forall smoking-causes-cancer 2.0)

;; Someone smokes.
(exists (lambda (term) (get-propv (list "Smokes" term))) 2.0)


;; Queries ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(display "What is the most likely truth value of every proposition?")
(display unary-propositions)
(display (get-best-value unary-proposition-vars))
(display "")
(display "What is the probability that Bob smokes?")
(display (get-marginals (get-propv (list "Smokes" "Bob"))))
(display "")
(display "What is the probability that Bob has cancer?")
(display (get-marginals (get-propv (list "Cancer" "Bob"))))
(display "")
(display "What is the probability that Smith smokes?")
(display (get-marginals (get-propv (list "Smokes" "Smith"))))
(display "")
(display "What is the probability that Smith has cancer?")
(display (get-marginals (get-propv (list "Cancer" "Smith"))))
(display "")
