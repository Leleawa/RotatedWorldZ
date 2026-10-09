package me.leleawa.rotatedworld.script;

import bsh.EvalError;
import bsh.Interpreter;

/** BeanShell：Java 语法，解释执行。脚本可以直接 import 所有层的 Java 类。 */
final class BeanShellInvoker implements ScriptInvoker {
    private final Interpreter interpreter = new Interpreter();

    BeanShellInvoker(String source, ClassLoader loader) throws EvalError {
        interpreter.setClassLoader(loader);
        interpreter.eval(source);
    }

    @Override
    public synchronized Object call(String function, Object... args) {
        try {
            StringBuilder call = new StringBuilder(function).append('(');
            for (int i = 0; i < args.length; i++) {
                interpreter.set("rwArg" + i, args[i]);
                call.append(i == 0 ? "" : ", ").append("rwArg").append(i);
            }
            return interpreter.eval(call.append(')').toString());
        } catch (EvalError e) {
            throw new IllegalStateException("BeanShell " + function + " failed: " + e.getMessage(), e);
        }
    }
}
