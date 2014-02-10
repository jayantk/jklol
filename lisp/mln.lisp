

;; INFRASTRUCTURE ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Function to instantiate every unary proposition. This function
;; simply enumerates every (predicate, term) pair and puts it in a list.
(define instantiate-unary-propositions (lambda (terms predicates)
                                        (if (nil? predicates)
                                            (list)
                                          (append (instantiate-unary-proposition terms (car predicates))
                                                (instantiate-unary-propositions terms (cdr predicates))))))

;; Helper function for the above function. This enumerates
;; all terms for a single predicate.
(define instantiate-unary-proposition (lambda (terms predicate)
                                        (if (nil? terms)
                                            (list)
                                          (cons (list predicate (car terms)) (instantiate-unary-proposition (cdr terms) predicate)))))

;; Function for looking up a proposition in the collection of all propositions.
(define get-proposition-index (lambda (proposition proposition-list) 
                                (get-proposition-index-helper proposition 0 proposition-list)))

(define get-proposition-index-helper (lambda (proposition cur-index proposition-list)
                                       (if (nil? proposition-list)
                                           (list)
                                         (if (= proposition (car proposition-list))
                                             cur-index
                                           (get-proposition-index-helper proposition (+ cur-index 1) (cdr proposition-list))))))

(define get-proposition-var (lambda (proposition proposition-list proposition-var-list)
  (get-ith-element proposition-var-list (get-proposition-index proposition proposition-list))))

;; MLN definition ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; The set of unary predicates and terms they can be
;; applied to.
(define terms (list "Anna" "Bob" "John" "Smith"))
(define unary-predicates (list "Smokes" "Cancer"))

;; Instantiate the set of all unary propositions.
(define unary-propositions (instantiate-unary-propositions terms unary-predicates))
;; Create a random variable for each unary proposition.
(define unary-proposition-vars (map (lambda (x) (amb (list #t #f) (list 1 1)))
                                            unary-propositions))

;; Function for looking up a proposition var in the instantiated set of
;; unary propositions.
(define get-propv (lambda (proposition) (get-proposition-var proposition unary-propositions unary-proposition-vars)))

;; Formulas ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Set weights for people smoking.
(add-weight (get-propv (list "Smokes" "Bob")) 3.0)
(add-weight (get-propv (list "Smokes" "Anna")) 1.5)
(add-weight (get-propv (list "Smokes" "John")) 0.5)
(add-weight (get-propv (list "Smokes" "Smith")) 0.75)

;; Cancer is unlikely a priori.
(map (lambda (term) (add-weight (get-propv (list "Cancer" term)) 0.9)) terms)

;; It's unlikely that both Anna and Bob smoke.
(add-weight (and (get-propv (list "Smokes" "Bob"))
                 (get-propv (list "Smokes" "Anna")))
                 0.3)

;; Smoking causes cancer
(define => (lambda (x y) (not (and x (not y)))))
(define smoking-causes-cancer (lambda (term) (add-weight (=> (get-propv (list "Smokes" term))
                                                             (get-propv (list "Cancer" term))) 2.0)))
(map smoking-causes-cancer terms)


;; Output ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(display "What is the most likely truth value of every proposition?")
(display unary-propositions)
(display (get-best-assignment unary-proposition-vars))
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
