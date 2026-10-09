package me.leleawa.rotatedworld.script;

/**
 * 调用一个已加载脚本的全局函数。参数是 Java 的 Double / Long / Boolean，返回值转换成 Java 对象
 * （数字 -> Number，布尔 -> Boolean，数组 / 列表 -> int[]、double[] 或 java.util.List）。
 * 内嵌的解释器都不保证线程安全，所以实现都是 synchronized 的。
 */
interface ScriptInvoker {
    Object call(String function, Object... args);
}
