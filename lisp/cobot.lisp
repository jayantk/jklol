;; Train a sequence tagger that tags each input with a pair of labels.
;; You must load cobot-data.lisp first to get the definition of 
;; training-sentences.

(define tagged-tokens (flatten training-sentences))

(define word-list (map car tagged-tokens))
(define pos-list (map cadr tagged-tokens))
(define label1-list (map caddr tagged-tokens))
(define label2-list (map cadddr tagged-tokens))

(define feature-offsets (list -3 -2 -1 0 1 2 3))

;; The set of features. Currently, these are just indicator
;; features for the words and pos-tags for various sentence offsets, 
;; but something more fancy could be used.
(define feature-dict (append (outer-product (list word-list feature-offsets))
                             (outer-product (list pos-list feature-offsets))))

;; feature-func maps association lists to feature vectors. Association
;; lists have the form (list (list <feature-name> <feature-weight>) (list <feature-name> <feature-weight>) ...)
(define feature-func (make-feature-factory feature-dict))

;; This function defines a parametric family of graphical models. It
;; accepts a number of parameter vectors, and returns a function that
;; tags a sequence.
(define joint-label-sequence-family (classifier1-parameters classifier2-parameters transition1-parameters transition2-parameters label-parameters)
  ;; sequence-model is a function that tags a list of feature vectors
  ;; with a pair of labels for each input vector
  (define sequence-model (feature-vector-seq)
    (if (nil? feature-vector-seq)
        (lifted-list)
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
            (lifted-cons (lifted-list cur-label1 cur-label2) remaining-labels))))))
  ;; The family of sequence models returns the procedure for tagging
  ;; a sequence.
  sequence-model)

;; This code generates indicator features for the words in the input sequence.
(define generate-token-features (token-list index offsets)
  (if (nil? offsets)
      (list)
    (let ((remaining-features (generate-token-features token-list index offsets))
          (cur-offset (car offsets))
          (cur-index (+ index cur-offset)))
      (if (and (> cur-index 0)
               (< cur-index (length token-list)))
          (append (generate-index-features token-list cur-index cur-offset)
                  remaining-features)
        remaining-features))))

(define generate-index-features (token-list index offset)
  (let ((cur-index (+ index offset)))
    (if (and (> cur-index -1) (< cur-index (length token-list)))
        (let ((cur-token (get-ith-element token-list cur-index))
              (cur-word (car cur-token))
              (cur-pos (cadr cur-token)))
          (list (list (list cur-word offset) 1.0)
                (list (list cur-pos offset) 1.0)))
      (list)
      )))

(define generate-token-list-features (token-list) 
  (map (lambda (index)
         (feature-func (foldr append (map (lambda (o) (generate-index-features token-list index o)) feature-offsets) (list))))
       (generate-int-seq 0 (length token-list))))

(define featurized-inputs (map (lambda (x) (list (generate-token-list-features x))) training-sentences))

;(display (car training-sentences))
;(display (car featurized-inputs))

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

;; Convert the labels into the verification procedures, then
;; create training data in the input/output format.
(define zipped-labels (map (lambda (x) (zip (map caddr x) (map cadddr x))) training-sentences))
(define label-detection-procs (map (lambda (x) (lambda (predicted) (require-seq-equal predicted x))) zipped-labels))
(define training-data (zip featurized-inputs label-detection-procs))

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
(display "Training...")
(define optimization-params (list (list "epochs" 10) (list "l2-regularization" 0.001)))

;; Train the parameters (using stochastic gradient).
(define best-parameters (opt joint-label-sequence-family initial-parameters training-data optimization-params))
;; Instantiate the model with the trained parameters.
(define trained-sequence-model (apply joint-label-sequence-family best-parameters))
(display "Done.")

;; Evaluation code
(define get-word-accuracy (predicted-seq true-seq)
  (if (nil? true-seq)
      0
    (let ((rest-accuracy (get-word-accuracy (cdr predicted-seq) (cdr true-seq)))
          (predicted-label (car predicted-seq))
          (true-label (car true-seq)))
      (if (and (= (car predicted-label) (caddr true-label))
               (= (cadr predicted-label) (cadddr true-label)))
          (+ 1 rest-accuracy)
        rest-accuracy))))

(define do-test (lambda (test-sent) 
                  (let ((test-words (map car test-sent))
                        (predicted-labels (get-best-value (trained-sequence-model (generate-token-list-features test-sent)))))
                    ;(display "Best tags for:" )
                    ;(display test-words)
                    ;(display predicted-labels)
                    (get-word-accuracy predicted-labels test-sent))))

(define num-correct-words (map do-test test-sentences))
(define num-words (map length test-sentences))

(display num-correct-words)
(display num-words)

(define correct-commands (map (lambda (x) (if (and (= (car x) (cadr x)) (not (= (cadr x) 0))) 1 0)) (zip num-correct-words num-words)))
(display correct-commands)

(display "WORD ACCURACY:")
(define correct (foldr + num-correct-words 0))
(define total (foldr + num-words 0))
(display correct)
(display total)

(display "COMMAND ACCURACY:")
(define num-correct-commands (foldr + correct-commands 0))
(define num-commands (foldr + (map (lambda (x) (if (= x 0) 0 1)) num-words) 0))

(display num-correct-commands)
(display num-commands)
