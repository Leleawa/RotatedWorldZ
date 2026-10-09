package me.leleawa.rotatedworld.script;

import org.mozilla.javascript.Context;
import org.mozilla.javascript.Function;
import org.mozilla.javascript.ScriptableObject;

/** JavaScript（Rhino，解释模式：运行时不生成类）。 */
final class RhinoInvoker implements ScriptInvoker {
    private final ClassLoader loader;
    private final ScriptableObject scope;

    RhinoInvoker(String source, String name, ClassLoader loader) {
        this.loader = loader;
        Context cx = enter();
        try {
            scope = cx.initStandardObjects();
            cx.evaluateString(scope, source, name, 1, null);
        } finally {
            Context.exit();
        }
    }

    private Context enter() {
        Context cx = Context.enter();
        cx.setOptimizationLevel(-1);
        cx.setApplicationClassLoader(loader);
        return cx;
    }

    @Override
    public synchronized Object call(String function, Object... args) {
        Context cx = enter();
        try {
            Object[] js = new Object[args.length];
            for (int i = 0; i < args.length; i++) js[i] = Context.javaToJS(args[i], scope);
            Function f = (Function) scope.get(function, scope);
            return Context.jsToJava(f.call(cx, scope, scope, js), Object.class);
        } finally {
            Context.exit();
        }
    }
}
