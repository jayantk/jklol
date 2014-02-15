;; list functions
(define map (lambda (f seq) (if (nil? seq) (list) (cons (f (car seq)) (map f (cdr seq))))))
(define lifted-map (lambda (f seq) (if (nil? seq) (lifted-list) (lifted-cons (f (lifted-car seq)) (lifted-map f (lifted-cdr seq))))))
(define zip (list1 list2) (if (nil? list1) (list) (cons (list (car list1) (car list2)) (zip (cdr list1) (cdr list2)))))

(define cadr (x) (car (cdr x)))
(define caddr (x) (car (cdr (cdr x))))

(define length (lambda (seq) (if (nil? seq) 0 (+ (length (cdr seq)) 1))))
(define first-n (lambda (seq n) (if (= n 0) (list) (cons (car seq) (first-n (cdr seq) (- n 1))))))
(define remainder-n (lambda (seq n) (if (= n 0) seq (remainder-n (cdr seq) (- n 1)))))
(define get-ith-element (lambda (seq i) (if (nil? seq) (list) 
                                          (if (= i 0) (car seq)
                                            (get-ith-element (cdr seq) (- i 1))))))

(define lifted-get-ith-element (lambda (seq i) (if (nil? seq) (lifted-list) 
                                                 (if (= i 0) (lifted-car seq)
                                                   (lifted-get-ith-element (lifted-cdr seq) (- i 1))))))

(define lifted-cadr (x) (lifted-car (lifted-cdr x)))

;; Appends two lists.
(define append (lambda (l1 l2)
                 (if (nil? l1)
                     l2
                     (cons (car l1) (append (cdr l1) l2)))))

;; Generates a list containing the elements n (n-1) ... 1
(define 1-to-n (lambda (n) (if (= n 0) (list) (cons n (1-to-n (- n 1))))))

;; Forces all nondeterministic executions to satisfy the given condition
(define require (lambda (condition) (add-weight (not condition) 0.0)))

;; Math functions
(define square (lambda (n) (* n n)))

;; Training data stuff
