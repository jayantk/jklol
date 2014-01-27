;; list functions
(define map (lambda (f seq) (if (nil? seq) (list) (cons (f (car seq)) (map f (cdr seq))))))
(define length (lambda (seq) (if (nil? seq) 0 (+ (length (cdr seq)) 1))))
(define first-n (lambda (seq n) (if (= n 0) (list) (cons (car seq) (first-n (cdr seq) (- n 1))))))
(define remainder-n (lambda (seq n) (if (= n 0) seq (remainder-n (cdr seq) (- n 1)))))
(define get-ith-element (lambda (seq i) (if (nil? seq) (list) 
                                          (if (= i 0) (car seq)
                                            (get-ith-element (cdr seq) (- i 1))))))

;; Appends two lists.
(define append (lambda (l1 l2)
                 (if (nil? l1)
                     l2
                     (cons (car l1) (append (cdr l1) l2)))))

;; Generates a list containing the elements n (n-1) ... 1
(define 1-to-n (lambda (n) (if (= n 0) (list) (cons n (1-to-n (- n 1))))))

;; Forces all nondeterministic executions to satisfy the given condition
(define require (lambda (condition) (add-weight (not condition) 0.0)))

