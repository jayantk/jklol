
(define label-list (list "N" "V" "DT"))

(define word-classifier (lambda (parameters) 
                          (define factor (make-classifier label-list parameters))
                          (lambda (label word) (factor label word))))


(define loss-func (lambda (parameters)
                    (define classifier (word-classifier parameters))
                    (get-marginal (classifier "N" "ball"))))
                    

(define best-parameters (opt loss-func))



(define word-factor (lambda (label word) 
                      (add-weight (and (= word "car") (= label "N")) 2)
                      (add-weight (and (= word "goes") (= label "V")) 3)))

(define transition-factor (lambda (cur-label next-label)
                            (add-weight (and (= next-label "N") (= cur-label "DT")) 2)
                            (add-weight (and (= next-label "V") (= cur-label "N")) 2))) 

(define sequence-tag (lambda (input-seq) 
                       (define cur-label (amb label-list))
                       (word-factor cur-label (car input-seq))
                       (if (not (nil? (cdr input-seq)))
                           (begin (define remaining-tags (sequence-tag (cdr input-seq)))
                                  (define next-label (car remaining-tags))
                                  (transition-factor cur-label next-label)
                                  (cons cur-label remaining-tags))
                           (list cur-label))))

(get-best-assignment (sequence-tag (list "the" "car" "goes")))