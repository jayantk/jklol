

(define entities (lifted-list "/en/plano" "/en/Texas"))
(define words (list "city" "place"))
(define true-false (list #t #f))
(define latent-dimensionality 100)

;; Constant functions ;;;;;;;;;;;;;;;;;;;;;;

;; Returns the set containing only entity-name
(define mention (entity-name)
  (lifted-map (lambda (x) (= entity-name x)) entities))

;; Returns the intersection of two sets of entities. Each set
;; is represented by a list of binary indicator variables for
;; the presence of each entity.
(define intersect (set1 set2)
  (lifted-map (lambda (x) (and (lifted-car x) (lifted-car (lifted-cdr x)))) (lifted-zip set1 set2)))

;; Returns true if any element in set1 is also present in set2.
(define elementof (set1 set2)
  (lifted-foldr or (intersect set1 set2) #f))

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

;(define word-rel (word)
;  (define func (input-set)
;    (let ((vars (lifted-map make-entity-var entities)))
;      (if (is-mention input-set)

(define expression-family (parameters)
  (let ((word-parameters (car parameters))
        (entity-parameters (cadr parameters))
        (word-cat (word-family word-parameters entity-parameters)))
    (define expression-evaluator (expression)
      (eval expression))
    expression-evaluator))

;(define training-inputs
;  (list (quote (elementof (mention "/en/plano") (word-cat "city")))
;        (quote (elementof (mention "/en/plano") (intersect (word-cat "city") ((word-rel "in") (mention "/en/Texas")))))))

;(define training-labels 
;  (list #t 
;        #f))

(define training-inputs
  (list (quote (elementof (mention "/en/plano") (word-cat "city")))
        (quote (not (elementof (mention "/en/Texas") (word-cat "city"))))
        ))

(define training-data (zip (map (lambda (x) (list x)) training-inputs)
                           (map (lambda (x) (lambda (prediction) (require (= prediction #t)))) training-inputs)))

(define expression-parameters (list (map (lambda (x) (make-vector-parameters latent-dimensionality)) words)
                                    (map (lambda (x) (make-vector-parameters latent-dimensionality)) entities)))

(display expression-parameters)

(define best-params (opt expression-family expression-parameters training-data))

(display best-params)

(define expression-eval (expression-family best-params))

(display "city")
(display (get-marginals (expression-eval (quote (lifted-car  (word-cat "city"))))))
(display (get-marginals (expression-eval (quote (lifted-cadr (word-cat "city"))))))

(display "(intersect plano city)")
(display (get-marginals (expression-eval (quote (lifted-car (intersect (mention "/en/plano") (word-cat "city")))))))
(display (get-marginals (expression-eval (quote (lifted-cadr (intersect (mention "/en/plano") (word-cat "city")))))))

(display "(elementof plano city)")
(display (get-marginals (expression-eval (quote (elementof (mention "/en/plano") (word-cat "city"))))))

(display "(elementof Texas city)")
(display (get-marginals (expression-eval (quote (elementof (mention "/en/Texas") (word-cat "city"))))))