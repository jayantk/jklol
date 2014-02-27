;; Run this program using:
;; java com.jayantkrish.jklol.lisp.cli.AmbLisp lisp/environment.lisp lisp/sequence-tag.lisp
;;
;; Options:
;; --printFactorGraph - Prints the factor graph created by running the program.


;; Define a set of possible labels.
(define label-list (list "N" "V" "DT"))

;; A function that instantiates a factor between a word
;; and a label (i.e., the emission factor).
(define word-factor (lambda (label word) 
                      (add-weight (and (= word "car") (= label "N")) 2)
                      (add-weight (and (= word "goes") (= label "V")) 3)))

;; A function that instantiates a factor between two adjacent
;; labels in the sequence.
(define transition-factor (lambda (cur-label next-label)
                            (add-weight (and (= next-label "N") (= cur-label "DT")) 2)
                            (add-weight (and (= next-label "V") (= cur-label "N")) 2))) 

;; Constructs a sequence model for labeling the elements of input.
(define sequence-tag (lambda (input-seq) 
                       ;; Create a label variable for this element of the sequence
                       (define cur-label (amb label-list))
                       ;; Instantiate the emission factor between this label
                       ;; and the current word
                       (word-factor cur-label (car input-seq))

                       (if (not (nil? (cdr input-seq)))
                           ;; Recurse to tag the rest of the sequence
                           (begin (define remaining-tags (sequence-tag (cdr input-seq)))
                                  ;; Add the transition factor between this label
                                  ;; and the first label of the rest of the sequence
                                  (transition-factor cur-label (lifted-car remaining-tags))
                                  ;; Return the sequence of label variables.
                                  (lifted-cons cur-label remaining-tags))
                           ;; No need to recurse: just return the current label
                           (lifted-list cur-label))))

;; Calling sequence-tag on an input instantiates a graphical model.
;; get-best-assignment performs inference on this model to identify
;; the best assignment to the labels of the sequence.
(get-best-value (sequence-tag (list "the" "car" "goes")))
