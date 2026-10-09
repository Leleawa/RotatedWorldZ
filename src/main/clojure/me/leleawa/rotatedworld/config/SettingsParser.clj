;; 配置解析。原来是 14 行手写的 c.getInt(...).coerceIn(...)；这里配置的形状是一份数据（schema），
;; 解析、默认值、范围限制都由这份数据驱动。加一个配置项 = 在 schema 里加一行 + Settings 里加一个字段。
;;
;; `:gen-class` 把这个命名空间编译成实现 SettingsReader 的 Java 类 me.leleawa.rotatedworld.config.SettingsParser。
(ns me.leleawa.rotatedworld.config.SettingsParser
  (:import (me.leleawa.rotatedworld.api Settings)
           (java.util Map))
  (:gen-class :implements [me.leleawa.rotatedworld.api.SettingsReader]))

(def schema
  "config.yml 的键、类型、默认值、范围。顺序必须和 Settings 记录的字段顺序一致。
   范围里的 :view-distance 表示服务端的 view-distance（客户端视野不能超过它）。"
  [{:key "enabled-by-default"      :type :bool   :default true}
   {:key "view-radius"             :type :int    :default 8    :min 2  :max :view-distance}
   {:key "recenter-threshold"      :type :int    :default 64   :min 16 :max 160}
   {:key "prefetch-chunks"         :type :int    :default 4    :min 0  :max 16}
   {:key "fast-speed"              :type :double :default 0.8}
   {:key "fast-edge-distance"      :type :int    :default 112  :min 32 :max 176}
   {:key "fast-target-margin"      :type :int    :default 80   :min 32 :max 176}
   {:key "prefetch-ahead-chunks"   :type :int    :default 14   :min 0  :max 24}
   {:key "columns-per-tick"        :type :int    :default 24   :min 1}
   {:key "chunk-requests-per-tick" :type :int    :default 24   :min 1}
   {:key "debug"                   :type :bool   :default false}
   {:key "safe-spawn.on-respawn"   :type :bool   :default true}
   {:key "safe-spawn.on-join"      :type :bool   :default false}
   {:key "safe-spawn.radius"       :type :int    :default 32   :min 4  :max 96}])

(defn- coerce
  "把 YAML 读出来的值变成 schema 要求的类型；类型不对就用默认值。"
  [type v default]
  (case type
    :bool   (if (instance? Boolean v) v default)
    :int    (if (number? v) (long v) default)
    :double (if (number? v) (double v) default)))

(defn- clamp [v lo hi]
  (cond-> v
    lo (max lo)
    hi (min hi)))

(defn read-value
  "按 schema 里的一项从 values 里取值。"
  [values view-distance {:keys [key type default min max]}]
  (let [bound #(if (= % :view-distance) (clojure.core/max 2 view-distance) %)
        v (coerce type (get values key) default)]
    (case type
      :int    (int (clamp v (bound min) (bound max)))
      :double (double v)
      :bool   (boolean v))))

(defn parse
  "values：带点的键 -> 值。返回按 schema 顺序排列的字段值。"
  [values view-distance]
  (mapv #(read-value values view-distance %) schema))

(defn -read [_this ^Map values view-distance]
  (clojure.lang.Reflector/invokeConstructor Settings (to-array (parse (into {} values) view-distance))))
