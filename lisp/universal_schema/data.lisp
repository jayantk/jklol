
(define entities (list "/en/plano" "/en/Texas" "/en/banana"))
(define words (list "city" "place" "fruit" "in"))
(define true-false (list #t #f))
(define latent-dimensionality 10)

(define entity-tuples (map (lambda (x) (list x entities)) entities))

(display entity-tuples)

;; Constant functions ;;;;;;;;;;;;;;;;;;;;;;

;; Returns the set containing only entity-name
(define mention (entity-name)
  (map (lambda (x) (= entity-name x)) entities))

;; Returns the intersection of two sets of entities. Each set
;; is represented by a list of binary indicator variables for
;; the presence of each entity.
(define intersect (set1 set2)
  (lifted-map (lambda (x) (and (lifted-car x) (lifted-car (lifted-cdr x)))) (lifted-zip set1 set2)))

;; Returns true if any element in set1 is also present in set2.
;; Equivalent to \exists x. x \in set1 ^ x \in set2
(define contains-any? (set1 set2)
  (lifted-foldr or (intersect set1 set2) #f))

(define => (x y)
  (or (not x) y))

;; Returns true if all elements of set1 are present in set2.
;; Equivalent to set1 \subset set2
(define contains-all? (set1 set2)
  (lifted-foldr 
   and
   (lifted-map (lambda (tuple) (=> (lifted-car tuple) (lifted-cadr tuple)))
               (lifted-zip set1 set2))
   #t))

;; Returns true if any element of set1 is true.
(define any-is-true? (set1) 
  (lifted-foldr or set1 #f))

;; Trainable functions ;;;;;;;;;;;;;;;;;;;;;;;

;; Creates a true/false variable for entity-name
(define make-entity-var (entity-name)
  (amb true-false))

;; Functions for looking up parameters corresponding to
;; a particular word / entity.
(define get-params (element element-list parameter-list)
  (let ((ind (find-index element element-list)))
    (get-ith-element parameter-list ind)))

(define get-word-params (word word-parameter-list)
  (get-params word words word-parameter-list))

(define get-entity-params (entity entity-parameter-list)
  (get-params entity entities entity-parameter-list))

(define get-entity-tuple-params (entity-arg1 entity-arg2 entity-tuple-parameter-list)
  (let ((ind (find-index entity-arg1 (map car entity-tuples)))
        (arg-entity-list (cadr (get-ith-element entity-tuples ind)))
        (arg-parameter-list (get-ith-element entity-tuple-parameter-list ind))
        (params (get-params entity-arg2 arg-entity-list arg-parameter-list)))
    params))

(define word-family (word-parameters entity-parameters)
  (define word-func (word)
    (let ((vars (lifted-map (lambda (entity) (lifted-cons entity (make-entity-var entity))) entities))
          (cur-word-params (get-word-params word word-parameters)))
      (lifted-map (lambda (x) (make-inner-product-classifier 
                               (lifted-cdr x)
                               #t
                               cur-word-params
                               (get-entity-params (lifted-car x) entity-parameters)))
                    vars)
      (lifted-map lifted-cdr vars)))
  word-func)

(define word-rel-row-helper (cur-row-entity entity-list word word-rel-params entity-tuple-params)
  (let ((cur-word-rel-params (get-word-params word word-rel-params))
        (vars (lifted-map (lambda (entity) (lifted-cons entity (make-entity-var entity))) entity-list) ))
    (lifted-map (lambda (entity-var-tuple) (make-inner-product-classifier 
                                            (lifted-cdr entity-var-tuple)
                                            #t
                                            cur-word-rel-params
                                            (get-entity-tuple-params cur-row-entity (lifted-car entity-var-tuple) entity-tuple-params)))
                vars)
    (lifted-map lifted-cdr vars)))

(define word-rel-family (word-rel-params entity-tuple-params)
  (define word-rel (word)
    (define word-rel-inner-func (set)
      (let ((rel-vars (lifted-map (lambda (entity-list) 
                                    (word-rel-row-helper (lifted-car entity-list) (lifted-cadr entity-list) 
                                                         word word-rel-params entity-tuple-params))
                                  entity-tuples)))
          (lifted-map (lambda (row) (any-is-true? (intersect set row)))
                      rel-vars)))
    word-rel-inner-func)
  word-rel)

(define expression-family (parameters)
  (let ((word-parameters (car parameters))
        (entity-parameters (cadr parameters))
        (word-rel-parameters (caddr parameters))
        (entity-tuple-parameters (cadddr parameters))
        (word-cat (word-family word-parameters entity-parameters))
        (word-rel (word-rel-family word-rel-parameters entity-tuple-parameters)))
    (define expression-evaluator (expression)
      (eval expression))
    expression-evaluator))

(define training-inputs
  (list (quote (contains-any? (mention "/en/plano") (word-cat "city")))
        (quote (contains-any? (mention "/en/plano") (word-cat "place")))
        (quote (not (contains-any? (mention "/en/plano") (word-cat "fruit"))))
        (quote (not (contains-any? (mention "/en/Texas") (word-cat "city"))))
        (quote (contains-any? (mention "/en/Texas") (word-cat "place")))
        (quote (not (contains-any? (mention "/en/banana") (word-cat "city"))))
        (quote (contains-any? (mention "/en/banana") (word-cat "fruit")))
        (quote (contains-any? (mention "/en/plano") ((word-rel "in") (mention "/en/Texas"))))
        ))

(define training-data (zip (map (lambda (x) (list x)) training-inputs)
                           (map (lambda (x) (lambda (prediction) (require (= prediction #t)))) training-inputs)))

(define expression-parameters (list (map (lambda (x) (make-vector-parameters latent-dimensionality)) words)
                                    (map (lambda (x) (make-vector-parameters latent-dimensionality)) entities)
                                    (map (lambda (x) (make-vector-parameters latent-dimensionality)) words)
                                    (map (lambda (ent-row) (map 
                                                            (lambda (x) (make-vector-parameters latent-dimensionality))
                                                            (cadr ent-row))) entity-tuples)
                                    ))

(define best-params (opt expression-family expression-parameters training-data))

(define expression-eval (expression-family best-params))

(display "(contains-any? plano city)")
(display (get-marginals (expression-eval (quote (contains-any? (mention "/en/plano") (word-cat "city"))))))

(display "(contains-any? Texas city)")
(display (get-marginals (expression-eval (quote (contains-any? (mention "/en/Texas") (word-cat "city"))))))

(display "(contains-all? city place)")
(display (get-marginals (expression-eval (quote (contains-all? (word-cat "city") (word-cat "place"))))))

(display "(contains-all? place city)")
(display (get-marginals (expression-eval (quote (contains-all? (word-cat "place") (word-cat "city"))))))

(display "(contains-any? banana place)")
(display (get-marginals (expression-eval (quote (contains-any? (mention "/en/banana") (word-cat "place"))))))

(display "(in plano Texas)")
(display (get-marginals (expression-eval (quote (contains-any? (mention "/en/plano") ((word-rel "in") (mention "/en/Texas")))))))

(display "(in Texas plano)")
(display (get-marginals (expression-eval (quote (contains-any? (mention "Texas") ((word-rel "in") (mention "/en/plano")))))))

(display "(in city Texas)")
(display (get-marginals (expression-eval (quote (contains-any? (word-cat "city") ((word-rel "in") (mention "/en/Texas")))))))
(display (get-best-value (expression-eval (quote (intersect (word-cat "city") ((word-rel "in") (mention "/en/Texas")))))))

(display "(in place Texas)")
(display (get-marginals (expression-eval (quote (contains-any? (word-cat "place") ((word-rel "in") (mention "/en/Texas")))))))
(display (get-best-value (expression-eval (quote (intersect (word-cat "place") ((word-rel "in") (mention "/en/Texas")))))))
