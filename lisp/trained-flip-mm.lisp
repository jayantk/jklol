;; Example code for UAI paper:

; 1. Define model family
(define flip-family (params)
  (lambda ()
    (define coin (amb (list 0 1)))
    (make-indicator-classifier coin params)
    coin)
  )

; 2. Create training data
(define make-cost (label) (lambda (x) (add-weight (not (= x label)) 2.7)))
(define make-output (label) (lambda (x) (require (= x label))))

(define labels (list 1 1 1 0))

(define data (map (lambda (label) (list (list) (make-cost label) (make-output label))) labels))

; 3. Optimize parameters
(define init-parameters
    (make-indicator-classifier-parameters (list (list 0 1))))
(define parameters (opt-mm flip-family init-parameters data))

; Instantiate the trained model
(define trained-flip (flip-family parameters))
(get-marginals (trained-flip))
; Evaluates to: (list (list 0 1)
;                     (list 0.251 0.749))