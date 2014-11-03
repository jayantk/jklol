(define binomial (lambda (n p) (if (= n 0) 
                                   0
                                 (+ (amb (list 0 1) (list (- 1.0 p) p)) (binomial (- n 1) p)))))

(display (get-marginals (binomial 6 0.3)))
                                 

(define outcome-list (list 0 1))

;; A binomial distribution with a trainable coin flip parameter.
(define binomial-family (lambda (parameters)
                          (define instantiated-binomial
                            (lambda (n) 
                              (if (= n 0)
                                  0
                                (begin 
                                 (define outcome (amb outcome-list))
                                 (make-indicator-classifier outcome parameters)
                                 (+ outcome (instantiated-binomial (- n 1)))))))
                          instantiated-binomial))

(define training-data (list (list (list 10) (lambda (output) (require (= output 6)))) 
                            (list (list 7) (lambda (output) (require (= output 4)))) 
                            (list (list 13) (lambda (output) (require (= output 5))))
                            ))

(define best-parameters (opt binomial-family 
                             (make-indicator-classifier-parameters 
                              (list outcome-list))
                             training-data))

(define fit-binomial (binomial-family best-parameters))

(get-marginals (fit-binomial 1))