
;; Graphical model definition: list of variables
;; and factor weights.
(define variables (list (list "x" (list 0 1))
                        (list "y" (list "a" "b" "c"))
                        (list "z" (list #t #f))))

(define factors (list
                 (list (list "x" "y") 
                       (list 0.3 0 "a")
                       (list 2.0 1 "b"))
                 (list (list "y")
                       (list 3.0 "b"))))

;; Instantiate random variables for each variable name
(define var-rvs (lifted-map (lambda (v)
  (lifted-list (car v) (amb (car (cdr v))))) variables))

(define get-var-rvs (names)
  (lifted-map (lambda (name) (lifted-alist-find name var-rvs)) names))

;; Instantiates the weights in a single factor.
(define make-factor (factor)
  (let ((var-names (car factor))
        (rvs (get-var-rvs var-names))
        (assignments (cdr factor)))
    (map (lambda (assignment) (add-weight (lifted-list-eq? (cdr assignment) rvs) (car assignment))) 
         assignments)))

(map make-factor factors)

(get-best-value (get-var-rvs (list "x" "y")))
