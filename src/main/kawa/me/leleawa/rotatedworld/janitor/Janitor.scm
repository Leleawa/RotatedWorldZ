;; Kawa Scheme 层（提前编译）：缓存清理（原来在 Scala 的 ViewManager 里）。
;;
;; 每 5 秒在全局线程上跑一次：收集每个世界里所有玩家的视野范围，交给 Flix 的 Datalog 规则（RetentionPolicy）
;; 算出哪些缓存的服务端区块还有人要，其余的删掉。没有玩家的世界整个清掉。
;; define-simple-class 编译成普通的 Java 类，Java 插件直接 new 它。
(module-name me.leleawa.rotatedworld.janitor.Janitor)
(import (class me.leleawa.rotatedworld.kernel Host PlayerState)
        (class me.leleawa.rotatedworld.engine ChunkStore)
        (class me.leleawa.rotatedworld.api RetentionPolicy Settings))

;; 对 Java 的 Iterable 逐个调用 proc
(define (each (coll ::java.lang.Iterable) proc) ::void
  (let ((it ::java.util.Iterator (coll:iterator)))
    (let loop ()
      (when (it:hasNext)
        (proc (it:next))
        (loop)))))

(define-simple-class CacheJanitor (java.lang.Runnable)
  (host ::Host)
  (store ::ChunkStore)
  (retention ::RetentionPolicy)

  ((*init* (h ::Host) (s ::ChunkStore) (r ::RetentionPolicy))
   (set! host h)
   (set! store s)
   (set! retention r))

  ((run) ::void
   (let* ((settings ::Settings (host:settings))
          (r ::int (+ (settings:viewRadius) 2))
          (margin ::int (+ (max (settings:prefetchChunks) (settings:prefetchAheadChunks)) 2))
          ;; 世界 -> 视野范围列表 (minX maxX minZ maxZ ...)
          (views ::java.util.HashMap (java.util.HashMap)))
     (each (host:allStates)
           (lambda ((st ::PlayerState))
             (let ((wk ::String st:worldKey))
               (when (and (not (eq? wk #!null)) st:initialized (not (= st:viewX java.lang.Integer:MIN_VALUE)))
                 (let* ((count (quotient (- st:maxY st:minY) 16))
                        (zb st:viewZBase)
                        (old (views:get wk)))
                   (views:put wk (append (list (- st:viewX r) (+ st:viewX r) (- zb margin) (+ zb count margin))
                                         (if (eq? old #!null) '() old))))))))
     (let ((worlds ::String[] (store:worldKeyArray)))
       (do ((i 0 (+ i 1))) ((= i worlds:length))
         (let* ((wk ::String (worlds i))
                (boxes (views:get wk)))
           (if (eq? boxes #!null)
               (store:clearWorld wk)
               (let* ((keys ::long[] (store:keys wk))
                      (n ::int keys:length)
                      (xs ::int[] (int[] length: n))
                      (zs ::int[] (int[] length: n)))
                 (do ((j 0 (+ j 1))) ((= j n))
                   (set! (xs j) (ChunkStore:keyX (keys j)))
                   (set! (zs j) (ChunkStore:keyZ (keys j))))
                 (let ((keep ::boolean[] (retention:retain (int[] @boxes) xs zs)))
                   (do ((j 0 (+ j 1))) ((= j n))
                     (unless (keep j)
                       (store:remove wk (xs j) (zs j)))))))))))))

;; 摔落伤害陪审团里唯一一个编译的陪审员
(define-simple-class KawaJuror (me.leleawa.rotatedworld.api.ScriptedRules$FallJuror)
  ((juror) ::String "Kawa Scheme")
  ((fallDamage (fall ::double) (creative ::boolean) (spectator ::boolean) (flying ::boolean) (gliding ::boolean)
               (in-water ::boolean) (slow-falling ::boolean)) ::double
   (if (or creative spectator flying gliding in-water slow-falling)
       0.0
       (max 0.0 (ceiling (- fall 3.0))))))
