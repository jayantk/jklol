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
  ;; sequence-model is a function that tags a list of feature vectors
  ;; with a pair of labels for each input vector
  (define sequence-model (feature-vector-seq)
    ;; define both labels for the current input vector
    (let ((cur-label1 (amb label1-list))
          (cur-label2 (amb label2-list))
          (cur-input-features (car feature-vector-seq)))
      ;; Create factors connecting the current input feature vector
      ;; to each label.
      (make-featurized-classifier cur-label1 cur-input-features classifier1-parameters)
      (make-featurized-classifier cur-label2 cur-input-features classifier2-parameters)
      ;; Create a factor connecting the current pair of labels.
      (make-indicator-classifier (lifted-list cur-label1 cur-label2) label-parameters)

      (if (nil? (cdr feature-vector-seq))
          ;; This is the last input vector, so simply return the
          ;; labels in a list.
          (lifted-list (lifted-list cur-label1 cur-label2))
        ;; Need to recurse on the remaining inputs.
        (let ((remaining-labels (sequence-model (cdr feature-vector-seq)))
              (next-labels (lifted-car remaining-labels))
              (next-label1 (lifted-car next-labels))
              (next-label2 (lifted-cadr next-labels)))
          ;; Create factors for the label transitions for both
          ;; label sets.
          (make-indicator-classifier (lifted-list cur-label1 next-label1) transition1-parameters)
          (make-indicator-classifier (lifted-list cur-label2 next-label2) transition2-parameters)
          ;; Return the list of labels.
          (lifted-cons (lifted-list cur-label1 cur-label2) remaining-labels)))))
  ;; The family of sequence models returns the procedure for tagging
  ;; a sequence.
  sequence-model)

(define inputs (list (list "the" "man")
                     (list "fast" "food" "is" "good")
                     ))

(define labels (list (list (list "DT" "NN") (list "B" "I"))
                     (list (list "JJ" "NN" "VB" "JJ") (list "B" "I" "O" "O"))
                     ))

;; This code generates indicator features for the words in the input sequence.
(define generate-token-features (token-list) (map (lambda (token) (feature-func (list (list token 1.0)))) token-list))
(define featurized-inputs (map (lambda (x) (list (generate-token-features x))) inputs))

;; Procedure for conditioning the output distribution given the true labels.
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

;; Procedure for conditioning the output distribution given the true labels.
(define make-seq-cost (predicted actual)
  (if (nil? actual)
      #t
    (let ((cur-predicted-labels (lifted-car predicted))
          (cur-predicted-label1 (lifted-car cur-predicted-labels))
          (cur-predicted-label2 (lifted-cadr cur-predicted-labels))
          (cur-actual-label1 (car (car actual)))
          (cur-actual-label2 (cadr (car actual))))
      (add-weight (not (= cur-actual-label1 cur-predicted-label1)) (exp 1.0))
      (add-weight (not (= cur-actual-label2 cur-predicted-label2)) (exp 1.0))
      (make-seq-cost (lifted-cdr predicted) (cdr actual)))))

;; Convert the labels into the verification procedures, then
;; create training data in the input/output format.
(define zipped-labels (map (lambda (x) (zip (car x) (cadr x))) labels))
(define label-detection-procs (map (lambda (x) (lambda (predicted) (require-seq-equal predicted x))) zipped-labels))
(define training-data (zip featurized-inputs label-detection-procs))

(define max-margin-cost-procs (map (lambda (x) (lambda (predicted) (make-seq-cost predicted x))) zipped-labels))
(define max-margin-training-data (zip3 featurized-inputs max-margin-cost-procs label-detection-procs))

;; Create initial, all-zero parameters for the model. When multiple
;; classifiers are used in a model family, the parameters for each
;; classifier are given in a list.
(define initial-parameters (list
  (make-featurized-classifier-parameters (list label1-list) feature-dict)
  (make-featurized-classifier-parameters (list label2-list) feature-dict)
  (make-indicator-classifier-parameters (list label1-list label1-list))
  (make-indicator-classifier-parameters (list label2-list label2-list))
  (make-indicator-classifier-parameters (list label1-list label2-list))))

;; These parameters control the number of iterations of gradient descent, etc.
;; They're optional, and the last argument to opt can be omitted.
(define optimization-params (list (list "epochs" 10) (list "l2-regularization" 1.0)))

;; Train the parameters (using stochastic gradient).
(define best-parameters (opt joint-label-sequence-family initial-parameters training-data optimization-params))


(define best-mm-parameters (opt-mm joint-label-sequence-family initial-parameters max-margin-training-data optimization-params))
;; Instantiate the model with the trained parameters.

(define crf-sequence-model (apply joint-label-sequence-family best-parameters))
(define m3n-sequence-model (apply joint-label-sequence-family best-mm-parameters))

;; Print out some sample tags for stuff.
(display "Best CRF tags for the man:")
(display (get-best-value (crf-sequence-model (generate-token-features (list "the" "man")))))
(display "Best CRF tags for the man eats fast food:")
(display (get-best-value (crf-sequence-model (generate-token-features (list "the" "man" "eats" "fast" "food")))))
(display "")
(display "Best M3N tags for the man:")
(display (get-best-value (m3n-sequence-model (generate-token-features (list "the" "man")))))
(display "Best M3N tags for the man eats fast food:")
(display (get-best-value (m3n-sequence-model (generate-token-features (list "the" "man" "eats" "fast" "food")))))

; (display "CRF parameters:")
; (display best-parameters)
; (display "")
; (display "M3N parameters:")
; (display best-mm-parameters)