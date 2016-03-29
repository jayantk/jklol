(define eval-table (table-id query)
  (let ((table (get-table table-id))
	(cells (get-table-cells table)))
    (define predicate-to-set (predicate) (set-filter predicate cells))
    (define get-values (cells) (if (set? cells)
				   (set-map (lambda x (get-value table x)) cells)
				 cells))
    (define eval-query (predicate) (get-values (predicate-to-set predicate))) 

    (define column (colname) (let ((colid (get-table-col table colname))) (lambda x (= (get-col x) colid))))
    (define cellvalue (value) (lambda x (= value (get-value table x))))
    (define samerow (x y) (= (get-row x) (get-row y)))
    (define samecol (x y) (= (get-col x) (get-col y)))
    (define exists (f) (not (= (set-size (set-filter f cells)) 0)))
    (define exists2 (f elts) (not (= (set-size (set-filter f elts)) 0)))

    (define column-set (colname) (get-col-cells table (get-table-col table colname)))
    (define cellvalue-set (value) (predicate-to-set (cellvalue value)))

    (define samerow-set (arg-set) (set-union (set-map (lambda x (get-row-cells table (get-row x))) arg-set)))
    (define intersect (s1 s2) (set-filter (lambda x (set-contains? s1 x)) s2))

    (define first-row (values) (make-set (set-min get-row values)))
    (define last-row (values) (make-set (set-max get-row values)))

    (define next-row (values) (set-union (set-map (lambda x (get-row-cells table (+ 1 (get-row x)))) values)))
    (define prev-row (values) (set-union (set-map (lambda x (get-row-cells table (- 1 (get-row x)))) values)))

    (define samevalue (values) (set-filter (lambda y (not (set-contains? values y))) (set-union (set-map (lambda x (cellvalue-set (get-value table x))) values))))

    (eval query)
    )
)
