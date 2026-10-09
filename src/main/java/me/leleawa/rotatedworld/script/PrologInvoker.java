package me.leleawa.rotatedworld.script;

import alice.tuprolog.Prolog;
import alice.tuprolog.SolveInfo;
import alice.tuprolog.Struct;
import alice.tuprolog.Term;
import alice.tuprolog.Theory;
import alice.tuprolog.Var;
import alice.tuprolog.lib.DCGLibrary;
import alice.tuprolog.lib.OOLibrary;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Prolog（tuProlog）。"函数" f(a, b) 就是谓词 f(A, B, Result)：最后一个参数是结果。
 * 目标是直接拼成项（不是拼字符串），所以参数里可以有任何字符。
 * 参数：String -> 原子，Number -> 数，Boolean -> true / false，String[] / List -> 列表；
 * 结果：原子 -> String，数 -> Number，列表或复合项 f(X, Y) -> java.util.List（复合项丢掉函子）。
 */
final class PrologInvoker implements ScriptInvoker {
    private final Prolog engine = new Prolog();

    PrologInvoker(String source) throws Exception {
        engine.loadLibrary(new OOLibrary());
        engine.loadLibrary(new DCGLibrary());   // -->  和 phrase/2
        engine.setTheory(Theory.parseWithOperators(source, engine.getOperatorManager()));
    }

    @Override
    public synchronized Object call(String function, Object... args) {
        Term[] goal = new Term[args.length + 1];
        for (int i = 0; i < args.length; i++) goal[i] = toTerm(args[i]);
        goal[args.length] = new Var("Result");
        try {
            SolveInfo info = engine.solve(new Struct(function, goal));
            if (!info.isSuccess()) return null;
            return toJava(info.getVarValue("Result").getTerm());
        } catch (Exception e) {
            throw new IllegalStateException("Prolog " + function + "/" + goal.length + " failed", e);
        }
    }

    private static Term toTerm(Object o) {
        if (o instanceof Boolean b) return new Struct(b ? "true" : "false");
        if (o instanceof Double || o instanceof Float) return new alice.tuprolog.Double(((Number) o).doubleValue());
        if (o instanceof Number n) return new alice.tuprolog.Long(n.longValue());
        if (o instanceof String s) return new Struct(s);
        if (o instanceof String[] a) return toTerm(List.of(a));
        if (o instanceof List<?> l) {
            Term[] items = new Term[l.size()];
            for (int i = 0; i < items.length; i++) items[i] = toTerm(l.get(i));
            return new Struct(items);
        }
        throw new IllegalArgumentException("cannot pass " + o + " to Prolog");
    }

    private static Object toJava(Term t) {
        if (t instanceof alice.tuprolog.Double || t instanceof alice.tuprolog.Float) return ((alice.tuprolog.Number) t).doubleValue();
        if (t instanceof alice.tuprolog.Number n) return n.longValue();
        if (t instanceof Struct s) {
            if (s.isList()) {
                List<Object> out = new ArrayList<>();
                for (Iterator<? extends Term> it = s.listIterator(); it.hasNext(); ) out.add(toJava(it.next()));
                return out;
            }
            if (s.isAtom()) return s.getName();
            List<Object> out = new ArrayList<>();
            for (int i = 0; i < s.getArity(); i++) out.add(toJava(s.getArg(i).getTerm()));
            return out;
        }
        return t.toString();
    }
}
