(define word-factor (lambda (label word) 
                      (add-weight (and (= word "car") (= label "N")) 2)
                      (add-weight (and (= word "goes") (= label "V")) 3)))

(define transition-factor (lambda (cur-label next-label)
                            (add-weight (and (= next-label "N") (= cur-label "N")) 2))) 

(define sequence-tag (lambda (input-seq) 
                       (if (nil? input-seq) 
                           (list)
                           (begin 
                            (define cur-label (amb (list "N" "V") (list 1 1)))
                            (word-factor cur-label (car input-seq))
                            (if (not (nil? (cdr input-seq)))
                                (begin (define next-label (sequence-tag (cdr input-seq)))
                                       (transition-factor cur-label next-label)
                                       cur-label)
                              cur-label)))))

(display (get-best-assignment (sequence-tag (list "car"))))
(display (get-best-assignment (sequence-tag (list "goes" "car"))))
(display (get-best-assignment (sequence-tag (list "the" "car"))))
