package me.leleawa.rotatedworld.kernel;

import java.util.function.Supplier;

/**
 * 服务器线程（主线程、区域线程、netty、全局调度器）的上下文类加载器是服务器自己的，看不见插件 jar 里的类。
 * 有些语言的运行时按名字找类时用的正是它：Kawa（gnu.bytecode.ObjectType.getContextClass）、Clojure（RT.baseLoader）……
 * 调用这些运行时之前，用这里的方法临时把上下文类加载器换成插件的。
 */
public final class PluginContext {
    private static final ClassLoader PLUGIN = PluginContext.class.getClassLoader();

    private PluginContext() {}

    public static <T> T call(Supplier<T> body) {
        Thread thread = Thread.currentThread();
        ClassLoader previous = thread.getContextClassLoader();
        if (previous == PLUGIN) return body.get();
        thread.setContextClassLoader(PLUGIN);
        try {
            return body.get();
        } finally {
            thread.setContextClassLoader(previous);
        }
    }

    public static void run(Runnable body) {
        call(() -> {
            body.run();
            return null;
        });
    }

    /** 包装成一个每次运行都先换好上下文类加载器的 Runnable（给调度器用）。 */
    public static Runnable wrap(Runnable body) {
        return () -> run(body);
    }
}
