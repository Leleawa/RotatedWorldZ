;;; Common Lisp 层（Armed Bear Common Lisp）：对旋转玩家放行哪些服务端检查（原来在 Kotlin 的 PlayerListener 里）。
;;;
;;; 服务端按没旋转的地形模拟移动，终点和客户端给的位置偶尔会差出阈值，原版会判定 moved wrongly 并拉回；
;;; 旋转玩家站在客户端的地面上时，服务端看他是贴着墙悬空的，会被当成飞行踢出。
;;; "新位置卡进方块"（CLIPPED_INTO_BLOCK）照常检查。

(defun failed-move (reason)
  "PlayerFailMoveEvent 的原因 -> \"allow\"、\"ask-javascript\"（是不是卡顿由 JavaScript 判断）或 \"deny\"。"
  (cond ((string= reason "MOVED_WRONGLY") "allow")
        ((string= reason "MOVED_TOO_QUICKLY") "ask-javascript")
        (t "deny")))

(defun forgive-kick (cause)
  "PlayerKickEvent 的原因 -> 要不要取消这次踢出。"
  (string= cause "FLYING_PLAYER"))

;; 摔落伤害陪审团的一员
(defun fall-damage (fall creative spectator flying gliding in-water slow-falling)
  (if (or creative spectator flying gliding in-water slow-falling)
      0d0
      (max 0d0 (float (ceiling (- fall 3d0)) 1d0))))
