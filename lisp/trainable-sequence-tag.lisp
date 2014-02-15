;; A trainable sequence tagger.


;; The set of labels
(define label-list (list #t #f))
(define word-list (list "A" "B" "C"))

(define sequence-family (word-parameters transition-parameters)
  (define sequence-tag (seq)
    (let ((cur-label (amb label-list)) 
          (cur-word (amb word-list))) 
      (add-weight (not (= cur-word (car seq))) 0.0)
      (make-indicator-classifier (lifted-list cur-word cur-label) word-parameters)
      (if (nil? (cdr seq))
          (lifted-list cur-label)
        (let ((remaining-labels (sequence-tag (cdr seq)))
              (next-label (lifted-car remaining-labels)))
          (make-indicator-classifier (lifted-list cur-label next-label) transition-parameters)
          (lifted-cons cur-label remaining-labels)))))
  sequence-tag)

(define require-seq-equal (predicted actual)
  (if (nil? actual) 
      #t
     (begin (add-weight (not (= (lifted-car predicted) (car actual))) 0.0)
            (require-seq-equal (lifted-cdr predicted) (cdr actual)))))

(define inputs (list (list "A" "B" "C")
                     (list "C" "B" "A")))

(define outputs (list (list #t #f #t)
                      (list #t #t #t)))

(define training-data (zip (map (lambda (x) (list x)) inputs)
                           (map (lambda (x) (lambda (label-seq) (require-seq-equal label-seq x))) outputs)))

(define parameter-spec (list (make-indicator-classifier-parameters (list word-list label-list))
                             (make-indicator-classifier-parameters (list label-list label-list))))
(define best-params (opt sequence-family parameter-spec training-data))
(define sequence-model (apply sequence-family best-params))

;; Make predictions with the trained model.
(define foo (get-best-assignment (sequence-model (list "A" "C" "A" "B"))))
(define bar (get-best-assignment (sequence-model (list "C" "B" "C"))))
(list foo bar)