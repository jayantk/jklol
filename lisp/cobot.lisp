;; Train a sequence tagger that tags each input with a pair of labels.
;; You must load cobot-data.lisp first to get the definition of 
;; training-sentences.

(define tagged-tokens (flatten training-sentences))

(define word-list (map car tagged-tokens))
(define pos-list (map cadr tagged-tokens))
(define label1-list (map caddr tagged-tokens))
(define label2-list (map cadddr tagged-tokens))

;; The set of features. Currently, these are just indicator
;; features for the words and pos-tags in the vocabulary, but
;; something more fancy could be used.
(define feature-dict (append word-list pos-list))
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

;; This code generates indicator features for the words in the input sequence.
(define generate-token-features (token-list) (map (lambda (tagged-token)
                                                    (let ((tagged-word (car tagged-token))
                                                          (tagged-pos (cadr tagged-token)))
                                                    (feature-func (list (list tagged-word 1.0) 
                                                                        (list tagged-pos 1.0))))) token-list))

(define featurized-inputs (map (lambda (x) (list (generate-token-features x))) training-sentences))

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
(define optimization-params (list (list "epochs" 10) (list "l2-regularization" 0.0)))

;; Train the parameters (using stochastic gradient).
(define best-parameters (opt joint-label-sequence-family initial-parameters training-data optimization-params))
;; Instantiate the model with the trained parameters.
(define trained-sequence-model (apply joint-label-sequence-family best-parameters))

;; Print out some sample tags for stuff.
(display "Best tags for:")
(display (map car (car training-sentences)))
(display (get-best-value (trained-sequence-model (generate-token-features (car training-sentences)))))
(display "Best tags for:" )
(display (map car (cadr training-sentences)))
(display (get-best-value (trained-sequence-model (generate-token-features (cadr training-sentences)))))

(define test-data
  (list (list 
         (list "Bring" "VB")
         (list "me" "PRP")
         (list "some" "DT")
         (list "coffee" "NN")
         (list "or" "CC")
         (list "tea" "NN"))
        (list 
         (list "Get" "VB")
         (list "me" "PRP")
         (list "my" "PRP$")
         (list "pencil" "NN")
         (list "from" "IN")
         (list "the" "DT")
         (list "office" "NN"))))

(define do-test (lambda (test-sent) 
                  (display "Best tags for (TEST):" )
                  (display (map car test-sent))
                  (display (get-best-value (trained-sequence-model (generate-token-features test-sent))))))

(map do-test test-data)

