;; Requires the user to define the following variables 
;; in a data file before running this program:
;; entities -- e.g. (define entities (list "/en/plano" "/en/Texas" "/en/banana"))
;; words    -- e.g. (define words (list "city" "place" "fruit" "in"))
;; training-inputs -- e.g., 
;;   (define training-inputs
;;     (list (list (quote (exists-func (lambda (x) (and ((mention "/en/plano") x) ((word-cat "city") x))) cur-entities)) entities)
;;           (list (quote (exists-func (lambda (y) (and ((mention "/en/Texas") y) 
;;                                                (exists-func (lambda (x) (and ((mention "/en/plano") x) ((word-cat "city") x)) ((word-rel "in") x y)) cur-entities))) 
;;                               cur-entities)) entities)
;;           ))

(display "Running universal schema...")

(define true-false (list #t #f))
(define latent-dimensionality 10)

;; (define entity-tuples (map (lambda (x) (list x entities)) entities))

(define entity-tuples (list) )

;; Constant functions ;;;;;;;;;;;;;;;;;;;;;;

;; Returns the set containing only entity-name
(define mention (entity-name)
  (lambda (x) (= entity-name x)))

;; Computes the set of entities contained in a predicate
;; (represented as a function)
(define predicate-to-set (predicate candidate-set)
  (lifted-map (lambda (entity) (predicate entity)) candidate-set))

;; Returns true if any element of set1 is true.
(define any-is-true? (set1) 
  (lifted-foldr or set1 #f))

;; Returns true if func returns true for any candidate
;; in candidate-set
(define exists-func (func candidate-set)
  (any-is-true? (predicate-to-set func candidate-set)))

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

;; Trainable functions ;;;;;;;;;;;;;;;;;;;;;;;

;; Creates a true/false variable for entity-name
(define make-entity-var (entity-name)
  (amb true-false))

;; Functions for looking up parameters corresponding to
;; a particular word / entity.
(define get-params (element element-list parameter-list)
  (let ((ind (dictionary-lookup element element-list)))
    (get-ith-parameter parameter-list ind)))

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
  (lambda (word)
    (lambda (entity)
      (let ((var (make-entity-var entity)))
        (make-inner-product-classifier 
         var #t (get-word-params word word-parameters) (get-entity-params entity entity-parameters))
        var))))

(define word-rel-family (word-rel-params entity-tuple-params)
  (define word-rel (word)
    (lambda (entity1 entity2)
      (let ((var (make-entity-var (cons entity1 entity2))))
        (make-inner-product-classifier 
         var #t (get-word-params word word-rel-params)
         (get-entity-tuple-params entity1 entity2 entity-tuple-params))
        var)))
  word-rel)

(define expression-family (parameters)
  (let ((word-parameters (get-ith-parameter parameters 0))
        (entity-parameters (get-ith-parameter parameters 1))
        (word-rel-parameters (get-ith-parameter parameters 2))
        (entity-tuple-parameters (get-ith-parameter parameters 3))
        (word-cat (word-family word-parameters entity-parameters))
        (word-rel (word-rel-family word-rel-parameters entity-tuple-parameters)))
    (define expression-evaluator (expression entities)
      (let ((cur-entities entities))
        ; (display expression)
        (eval expression)))
    expression-evaluator))

(define training-data (zip training-inputs
                           (map (lambda (x) (lambda (prediction) (require (= prediction #t)))) training-inputs)))

(display "Made training data.")

(define expression-parameters (make-parameter-list (list 
                                                    (make-parameter-list (map (lambda (x) (make-vector-parameters latent-dimensionality)) (n-to-1 (dictionary-size words))))
                                                    (make-parameter-list (map (lambda (x) (make-vector-parameters latent-dimensionality)) (n-to-1 (dictionary-size entities))))
                                                    (make-parameter-list (map (lambda (x) (make-vector-parameters latent-dimensionality)) (n-to-1 (dictionary-size words))))
                                                    (make-parameter-list (map (lambda (ent-row) (map 
                                                            (lambda (x) (make-vector-parameters latent-dimensionality))
                                                            (cadr ent-row))) entity-tuples))
                                    )))

(display "Training...")
(define best-params (opt expression-family expression-parameters training-data))

(define expression-eval (lambda (expr) ((expression-family best-params) expr entities)))

;; (display "exists x st. (plano x) and (city x)?")
;; (display (get-marginals (expression-eval (quote (exists-func (lambda (x) (and ((mention "/en/plano") x) ((word-cat "city") x))) entities)))))
;; 
;; (display "exists x,y st. (plano x) and (city x) and (texas y) and (in x y)?")
;; (display (get-marginals (expression-eval (quote (exists-func (lambda (y) (and ((mention "/en/Texas") y) 
;;                                              (exists-func (lambda (x) (and ((mention "/en/plano") x) ((word-cat "city") x)) ((word-rel "in") x y)) entities))) 
;;                             entities)))))
;; 
;; 
;; (display "exists x st. (city x)")
;; (display (get-marginals (expression-eval (quote (and (exists-func (word-cat "city") entities))))))
;; 
;; 
;; (display "exists x st. (city x) and exists y st. (city y)")
;; (display (get-marginals (expression-eval (quote (and (exists-func (word-cat "city") entities)
;;                                                      (exists-func (word-cat "city") entities) )))))
;; 
;; 
;; (display "lambda x. st (city x) (in x y) (texas y)")
;; (display entities)
;; (display (get-best-value (expression-eval (quote (predicate-to-set (lambda (x) (and ((word-cat "city") x)
;;                                                                                    (exists-func (lambda (y) (and ((word-rel "in") x y) ((mention "/en/Texas") y))) entities))) entities)))))
