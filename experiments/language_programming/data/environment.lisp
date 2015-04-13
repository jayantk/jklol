(define foo "foo")

(define inc (lambda (x) (+ x 1)))

(define fact (lambda (x) (if (< x 2)
                                  x 
                                (* x (fact (- x 1))))))

