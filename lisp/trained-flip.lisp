;; Example code for UAI paper:

; 1. Define model family
(define flip-family (params)
  (lambda ()
    (define coin (amb (list 0 1)))
    (make-indicator-classifier coin params)
    coin)
  )

; 2. Create training data
(define data (list
    (list (list) (lambda (x) (require (= x 1))))
    (list (list) (lambda (x) (require (= x 1))))
    (list (list) (lambda (x) (require (= x 1))))
    (list (list) (lambda (x) (require (= x 0))))
    ))

; 3. Optimize parameters
(define init-parameters
    (make-indicator-classifier-parameters (list (list 0 1))))
(define parameters (opt
    flip-family init-parameters data))

; Instantiate the trained model
(define trained-flip (flip-family parameters))
(get-marginals (trained-flip))
; Evaluates to: (list (list 0 1)
;                     (list 0.251 0.749))