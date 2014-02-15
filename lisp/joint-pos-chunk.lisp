;; Train a sequence tagger that tags each input with a pair of labels.

;; The set of features. Currently, these are just indicator
;; features for the words in the vocabulary, but something
;; more fancy could be used.
(define feature-dict (list "the" "man" "eats" "food" "fast" "is" "good"))
;; feature-func maps association lists to feature vectors. Association
;; lists have the form (list (list <feature-name> <feature-weight>) (list <feature-name> <feature-weight>) ...)
(define feature-func (make-feature-factory feature-dict))

;; The two types of labels. I'm pretending this is a 
;; joint POS-tagging and NP chunking tagger at the moment.
(define label1-list (list "NN" "JJ" "DT" "VB"))
(define label2-list (list "B" "I" "O"))

;; This function defines a parametric family of graphical models. It
;; accepts a number of parameter vectors, and returns a function that
;; tags a sequence.
(define joint-label-sequence-family (classifier1-parameters classifier2-parameters transition1-parameters transition2-parameters label-parameters)
  ;; sequence-model is a function that tags a sequence.
  (define sequence-model (feature-vector-seq) 
    (let ((cur-label1 (amb label1-list))
          (cur-label2 (amb label2-list))
          (cur-input-features (car feature-vector-seq)))
      (make-featurized-classifier cur-label1 cur-input-features classifier1-parameters)
      (make-featurized-classifier cur-label2 cur-input-features classifier2-parameters)
      (make-indicator-classifier (lifted-list cur-label1 cur-label2) label-parameters)

      (if (nil? (cdr feature-vector-seq))
          (lifted-list (lifted-list cur-label1 cur-label2))
        (let ((remaining-labels (sequence-model (cdr feature-vector-seq)))
              (next-labels (lifted-car remaining-labels))
              (next-label1 (lifted-car next-labels))
              (next-label2 (lifted-cadr next-labels)))

          (make-indicator-classifier (lifted-list cur-label1 next-label1) transition1-parameters)
          (make-indicator-classifier (lifted-list cur-label2 next-label2) transition2-parameters)
          (lifted-cons (lifted-list cur-label1 cur-label2) remaining-labels)))))
  sequence-model)

(define inputs (list (list "the" "man")
                     (list "fast" "food" "is" "good")
                     ))

(define labels (list (list (list "DT" "NN") (list "B" "I"))
                     (list (list "JJ" "NN" "VB" "JJ") (list "B" "I" "O" "O"))
                     ))

;; TODO: Use nontrivial feature generation.
(define generate-token-features (token-list) (map (lambda (token) (feature-func (list (list token 1.0)))) token-list))
(define featurized-inputs (map (lambda (x) (list (generate-token-features x))) inputs))

(define require-seq-equal (predicted actual)
  (if (nil? actual)
      #t
    (let ((cur-predicted-labels (lifted-car predicted))
          (cur-predicted-label1 (lifted-car cur-predicted-labels))
          (cur-predicted-label2 (lifted-cadr cur-predicted-labels))
          (cur-actual-label1 (car (car actual)))
          (cur-actual-label2 (cadr (car actual))))
      (require (= cur-actual-label1 cur-predicted-label1))
      (require (= cur-actual-label2 cur-predicted-label2))
      (require-seq-equal (lifted-cdr predicted) (cdr actual)))))

(define zipped-labels (map (lambda (x) (zip (car x) (cadr x))) labels))
(define label-detection-procs (map (lambda (x) (lambda (predicted) (require-seq-equal predicted x))) zipped-labels))
(define training-data (zip featurized-inputs label-detection-procs))

(define initial-parameters (list
  (make-featurized-classifier-parameters (list label1-list) feature-dict)
  (make-featurized-classifier-parameters (list label2-list) feature-dict)
  (make-indicator-classifier-parameters (list label1-list label1-list))
  (make-indicator-classifier-parameters (list label2-list label2-list))
  (make-indicator-classifier-parameters (list label1-list label2-list))))

(define best-parameters (opt joint-label-sequence-family initial-parameters training-data))
(define trained-sequence-model (apply joint-label-sequence-family best-parameters))

(display "Best tags for the man:")
(display (get-best-value (trained-sequence-model (generate-token-features (list "the" "man")))))
(display "Best tags for the man eats fast food:")
(display (get-best-value (trained-sequence-model (generate-token-features (list "the" "man" "eats" "fast" "food")))))


