package me.leleawa.rotatedworld;

/**
 * 启动 Flix 层。Flix 编译器把入口放在<em>默认包</em>的 {@code Main} 类里，具名包里没法直接引用，所以用反射。
 * Flix 代码运行时会把 MaterialRules 和 RetentionPolicy 交给 {@code Services}。
 */
final class FlixBridge {
    private FlixBridge() {}

    static void start() {
        try {
            Class<?> main = Class.forName("Main", true, FlixBridge.class.getClassLoader());
            main.getMethod("main", String[].class).invoke(null, (Object) new String[0]);
        } catch (ReflectiveOperationException | LinkageError e) {
            throw new IllegalStateException("Flix 层启动失败", e);
        }
    }
}
