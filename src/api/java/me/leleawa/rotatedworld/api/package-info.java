/**
 * 纯计算语言层（Frege、Flix、Clojure、Haxe、Fantom）实现的契约。
 *
 * <p>这里只用 JDK 类型、没有 default 方法：构建时这些层的 classpath 上根本没有 Bukkit / PacketEvents，
 * 所以它们不可能意外依赖服务端。
 */
package me.leleawa.rotatedworld.api;
