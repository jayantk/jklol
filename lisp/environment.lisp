;; list functions
(define map (lambda (f seq) (if (nil? seq) (list) (cons (f (car seq)) (map f (cdr seq))))))
(define lifted-map (lambda (f seq) (if (nil? seq) (lifted-list) (lifted-cons (f (lifted-car seq)) (lifted-map f (lifted-cdr seq))))))
(define zip (list1 list2) (if (nil? list1) (list) (cons (list (car list1) (car list2)) (zip (cdr list1) (cdr list2)))))

(define cadr (x) (car (cdr x)))
(define caddr (x) (car (cdr (cdr x))))
(define cadddr (x) (car (cdr (cdr (cdr x)))))

(define length (lambda (seq) (if (nil? seq) 0 (+ (length (cdr seq)) 1))))
(define first-n (lambda (seq n) (if (= n 0) (list) (cons (car seq) (first-n (cdr seq) (- n 1))))))
(define remainder-n (lambda (seq n) (if (= n 0) seq (remainder-n (cdr seq) (- n 1)))))
(define get-ith-element (lambda (seq i) (if (nil? seq) (list) 
                                          (if (= i 0) (car seq)
                                            (get-ith-element (cdr seq) (- i 1))))))

(define find-index (elt list) (find-index-helper elt list 0))
(define find-index-helper (elt list i)
  (if (nil? seq)
      -1
    (if (= (car seq) elt)
        i
      (find-index-helper elt (cdr list) (+ i 1)))))

(define lifted-get-ith-element (lambda (seq i) (if (nil? seq) (lifted-list) 
                                                 (if (= i 0) (lifted-car seq)
                                                   (lifted-get-ith-element (lifted-cdr seq) (- i 1))))))

(define lifted-cadr (x) (lifted-car (lifted-cdr x)))

(define lifted-alist-find (elt alist) 
  (if (nil? alist)
      (lifted-list)
    (if (= (lifted-car (lifted-car alist)) elt)
        (lifted-car (lifted-cdr (lifted-car alist)))
      (lifted-alist-find elt (lifted-cdr alist)))))

(define lifted-list-eq? (l1 l2)
  (if (or (nil? l1) (nil? l2))
      (and (nil? l1) (nil? l2))
    (and (= (lifted-car l1) (lifted-car l2))
         (lifted-list-eq? (cdr l1) (cdr l2)))))

;; Appends two lists.
(define append (lambda (l1 l2)
                 (if (nil? l1)
                     l2
                     (cons (car l1) (append (cdr l1) l2)))))

(define flatten (lambda (list-of-lists)
                  (if (nil? list-of-lists)
                      (list)
                    (append (car list-of-lists) (flatten (cdr list-of-lists))))))

;; Generates a list containing the elements n (n-1) ... 1
(define 1-to-n (lambda (n) (if (= n 0) (list) (cons n (1-to-n (- n 1))))))

;; Forces all nondeterministic executions to satisfy the given condition
(define require (lambda (condition) (add-weight (not condition) 0.0)))

;; Math functions
(define square (lambda (n) (* n n)))

;; Training data stuff
(define make-eq-require (output-value) (lambda (x) (require (= x output-value))))