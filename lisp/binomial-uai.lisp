(define flip (lambda (p) (amb (list 0 1) (list (- 1.0 p) p))))

(define binomial (lambda (n p) (if (= n 0) 
                                   0
                                 (+ (flip p) (binomial (- n 1) p)))))

(get-marginals (binomial 3 0.3))
