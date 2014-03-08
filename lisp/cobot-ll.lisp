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

;; Hamming cost for the label sequence
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

(define make-loglikelihood-data (training-sentences)
  (define featurized-inputs (map (lambda (x) (list (generate-token-list-features x))) training-sentences))
  ;; Convert the labels into the verification procedures, then
  ;; create training data in the input/output format.
  (define zipped-labels (map (lambda (x) (zip (map caddr x) (map cadddr x))) training-sentences))
  (define label-detection-procs (map (lambda (x) (lambda (predicted) (require-seq-equal predicted x))) zipped-labels))
  (zip featurized-inputs label-detection-procs))

(define make-mm-data (training-sentences)
  (let ((featurized-inputs (map (lambda (x) (list (generate-token-list-features x))) training-sentences))
        (zipped-labels (map (lambda (x) (zip (map caddr x) (map cadddr x))) training-sentences))
        (label-detection-procs (map (lambda (x) (lambda (predicted) (require-seq-equal predicted x))) zipped-labels))
        (cost-procs (map (lambda (x) (lambda (predicted) (make-seq-cost predicted x))) zipped-labels)))
    (zip3 featurized-inputs cost-procs label-detection-procs)))

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
