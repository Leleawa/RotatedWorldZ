package me.leleawa.rotatedworld.script;

import org.jruby.RubyInstanceConfig;
import org.jruby.embed.LocalContextScope;
import org.jruby.embed.ScriptingContainer;

/** Ruby（JRuby）。脚本用 {@code java_import 'pkg.Class'} 访问 Java 类。 */
final class JRubyInvoker implements ScriptInvoker {
    private final ScriptingContainer container = new ScriptingContainer(LocalContextScope.CONCURRENT);
    private final Object topSelf;

    JRubyInvoker(String source, String name, ClassLoader loader) {
        container.setClassLoader(loader);
        container.setCompileMode(RubyInstanceConfig.CompileMode.OFF);
        container.runScriptlet(source);
        topSelf = container.getProvider().getRuntime().getTopSelf();
    }

    @Override
    public synchronized Object call(String function, Object... args) {
        return container.callMethod(topSelf, function, args);
    }
}
