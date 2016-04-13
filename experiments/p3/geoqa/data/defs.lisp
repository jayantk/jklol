 
(define and:<t*,t> (continuation . rest) (continuation (apply and rest)))
(define equal?:<⊤,<⊤,t>> (continuation x1 x2) (continuation (= x1 x2)))

(define exists:<<e,t>,t> (continuation f-c)
  (get-denotation (lambda (x) (continuation (not (nil? x))))
		  f-c))

(define get-denotation (continuation f-c)
  (lambda (world)
    ((filter-c continuation f-c (get-entities world)) world)))

(define list-to-set-c (k l)
  (k (list-to-set l)))

(define make-category (predicate-name)
  (lambda (k x) (resolve-cat (lambda (v)
			       (lambda (w2)
				 ((k (kb-get w2 predicate-name x)) w2)))
			     predicate-name x)))

(define make-relation (predicate-name)
  (lambda (k x1 x2) (resolve-cat (lambda (v)
				   (lambda (w2)
				     ((k (kb-get w2 predicate-name (cons x1 x2))) w2)))
				 predicate-name (cons x1 x2))))


;; Functions for manipulating diagram uncertainty
; Queues continuation k applied to each value in l for execution.
(define amb-c (k l) (lambda (world) ((queue-k k l) (list world)) ))

; Queues continuation k applied v and each world in worlds for execution.
(define amb-world-c (k v worlds) (lambda (world) ((queue-k k (list v)) worlds) ))

(define resolve-c (k test-proc candidate-proc)
  (lambda (world)
    (if (not (test-proc world))
	((amb-world-c (lambda (value)
			(lambda (next-world)
			  ((k (test-proc next-world)) next-world)
			  )
			)
		      "amb-world-value"
		      (candidate-proc world)) world)
	((k (test-proc world)) world)
	)))

(define resolve-cat (k predicate entity)
  (resolve-c k (lambda (world) (not (nil? (kb-get world predicate entity))))
	     (lambda (world) (list (kb-set world predicate entity #t) (kb-set world predicate entity #f)))))


(define filter-c (continuation f-c items)
  (filter-helper-c continuation f-c items (list)))

(define filter-helper-c (continuation f-c items result)
  (lambda (world)
    (if (nil? items)
	((continuation result) world)
	(let ((first (car items))
	      (rest (cdr items)))
	  ((f-c (lambda (value)
		  (lambda (world2)
		    (if value
			((filter-helper-c continuation f-c rest (cons first result)) world2)
			((filter-helper-c continuation f-c rest result) world2))))
		first)
	   world))
	)))
