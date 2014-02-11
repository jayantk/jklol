(define binomial (lambda (n p) (if (= n 0) 
                                   0
                                 (+ (amb (list 0 1) (list (- 1.0 p) p)) (binomial (- n 1) p))))) 

(get-marginals (binomial 6 0.3))
                                 