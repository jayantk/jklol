(define label-list (list "DT" "NN" "JJ" "VB"))
(define new-label (lambda () (amb label-list (list 1 1 1 1))))

(define word-factor (lambda (label word)
                      (add-weight (and (= word "car") (= label "NN")) 2) 
                      (add-weight (and (= word "big") (= label "JJ")) 2) 
                      (add-weight (and (= word "goes") (= label "VB")) 3)))

(define transition-factor (lambda (left right root)
                            (add-weight (and (= left "DT") (and (= right "NN") (= root "NN"))) 2)
                            (add-weight (and (= left "JJ") (and (= right "NN") (= root "NN"))) 2)))

(define cfg-parse (lambda (input-seq)
                    (define label-var (new-label))
                    (if (= (length input-seq) 1)
                        (word-factor label-var (car input-seq))
                        (begin 
                         (define split-list (1-to-n (- (length input-seq) 1)))
                         (define choice-var (amb split-list))
                         (map (lambda (i) (do-split input-seq i label-var choice-var)) split-list)))
                    label-var))

(define do-split (lambda (seq i root-var choice-var)
                   (define left-seq (first-n seq i))
                   (define right-seq (remainder-n seq i))
                   (define left-parse-root (cfg-parse left-seq))
                   (define right-parse-root (cfg-parse right-seq))
                   (define cur-root (new-label))
                   (transition-factor left-parse-root right-parse-root cur-root)
                   (add-weight (and (= choice-var i) (not (= cur-root root-var))) 0)))

(get-best-assignment (cfg-parse (list "big" "car")) "junction-tree")
