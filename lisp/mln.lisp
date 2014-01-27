

(define terms (list "Anna" "Bob"))

(define unary-predicates (list "Smokes" "Cancer"))

;; Instantiate amb variables for the truth value of
;; every unary proposition.
(define instantiate-unary-propositions (lambda (terms predicates)
                                        (if (nil? predicates)
                                            (list)
                                          (append (instantiate-unary-proposition terms (car predicates))
                                                (instantiate-unary-propositions terms (cdr predicates))))))

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

(define unary-propositions (instantiate-unary-propositions terms unary-predicates))
(define unary-proposition-vars (map (lambda (i) (amb (list "T" "F") (list 1 1)))
                                            (1-to-n (length unary-propositions))))

; (display (get-proposition-index (list "Smokes" "Bob") unary-propositions))

(add-weight (= (get-proposition-var (list "Smokes" "Bob") unary-propositions unary-proposition-vars) "T")
            2.0)

(add-weight (and (= (get-proposition-var (list "Smokes" "Bob") unary-propositions unary-proposition-vars) "T")
                 (= (get-proposition-var (list "Smokes" "Anna") unary-propositions unary-proposition-vars) "T"))
                 0.3)

(display unary-propositions)
(display (get-best-assignment unary-proposition-vars))
; (get-best-assignment unary-propositions)