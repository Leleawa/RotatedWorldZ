package me.leleawa.rotatedworld.script;

import org.armedbear.lisp.AbstractString;
import org.armedbear.lisp.DoubleFloat;
import org.armedbear.lisp.Fixnum;
import org.armedbear.lisp.Interpreter;
import org.armedbear.lisp.Lisp;
import org.armedbear.lisp.LispInteger;
import org.armedbear.lisp.LispObject;
import org.armedbear.lisp.SimpleString;
import org.armedbear.lisp.Symbol;
import java.util.logging.Logger;

/** Common Lisp（Armed Bear Common Lisp）。脚本用 JSTATIC / JCALL 访问 Java 类。 */
final class AbclInvoker implements ScriptInvoker {
    private final Interpreter interpreter;

    AbclInvoker(String source, Logger log) {
        // ABCL 初始化时想用反射打开 JDK 的虚拟线程工厂（需要 --add-opens java.base/java.lang），打不开就往 System.err
        // 打一行。无害：只是 Lisp 的 make-thread 不能建虚拟线程，我们的脚本也不开线程。
        interpreter = StderrFilter.during(log, Interpreter::createInstance, "Failed to introspect virtual threading methods");
        interpreter.eval("(progn " + source + "\n)");
    }

    @Override
    public synchronized Object call(String function, Object... args) {
        LispObject[] lisp = new LispObject[args.length];
        for (int i = 0; i < args.length; i++) lisp[i] = toLisp(args[i]);
        LispObject fn = interpreter.eval("(function " + function + ")");
        return toJava(fn.execute(lisp));
    }

    private static LispObject toLisp(Object o) {
        if (o == null || Boolean.FALSE.equals(o)) return Lisp.NIL;
        if (Boolean.TRUE.equals(o)) return Lisp.T;
        if (o instanceof Double || o instanceof Float) return new DoubleFloat(((Number) o).doubleValue());
        if (o instanceof Number n) return LispInteger.getInstance(n.longValue());
        if (o instanceof String s) return new SimpleString(s);
        throw new IllegalArgumentException("cannot pass " + o.getClass() + " to Lisp");
    }

    private static Object toJava(LispObject v) {
        if (v == Lisp.NIL) return Boolean.FALSE;
        if (v == Lisp.T) return Boolean.TRUE;
        if (v instanceof DoubleFloat d) return d.value;
        if (v instanceof Fixnum f) return (long) f.value;
        if (v instanceof AbstractString s) return s.getStringValue();
        if (v instanceof Symbol s) return s.getName().toLowerCase();
        return v.javaInstance();
    }
}
