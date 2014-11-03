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
(define optimization-params (list (list "epochs" 30) (list "l2-regularization" 0.0001)))

;; Train the parameters (using max-margin)
(define training-data (make-mm-data training-sentences))
(define best-parameters (opt-mm joint-label-sequence-family initial-parameters training-data optimization-params))

;; Train the parameters (using loglikelihood & stochastic gradient).
;(define training-data (make-loglikelihood-data training-sentences))
;(define best-parameters (opt joint-label-sequence-family initial-parameters training-data optimization-params))

;; Instantiate the model with the trained parameters.
(define trained-sequence-model (apply joint-label-sequence-family best-parameters))
(display "Done.")

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
