package me.leleawa.rotatedworld.script;

import java.util.List;

/** 各解释器返回的值五花八门（Double、Long、int[]、RubyArray、PyList……），在这里统一成 Java 类型。 */
final class ScriptValues {
    private ScriptValues() {}

    static double num(Object o) {
        if (o instanceof Number n) return n.doubleValue();
        throw new IllegalStateException("script returned " + describe(o) + ", expected a number");
    }

    static boolean bool(Object o) {
        if (o instanceof Boolean b) return b;
        throw new IllegalStateException("script returned " + describe(o) + ", expected a boolean");
    }

    static int[] ints(Object o) {
        if (o instanceof int[] a) return a;
        if (o instanceof long[] a) {
            int[] r = new int[a.length];
            for (int i = 0; i < a.length; i++) r[i] = (int) a[i];
            return r;
        }
        if (o instanceof Object[] a) return ints(List.of(a));
        if (o instanceof List<?> l) {
            int[] r = new int[l.size()];
            for (int i = 0; i < r.length; i++) r[i] = (int) num(l.get(i));
            return r;
        }
        throw new IllegalStateException("script returned " + describe(o) + ", expected a list of integers");
    }

    static double[] doubles(Object o) {
        if (o instanceof double[] a) return a;
        if (o instanceof Object[] a) return doubles(List.of(a));
        if (o instanceof List<?> l) {
            double[] r = new double[l.size()];
            for (int i = 0; i < r.length; i++) r[i] = num(l.get(i));
            return r;
        }
        throw new IllegalStateException("script returned " + describe(o) + ", expected a list of numbers");
    }

    private static String describe(Object o) {
        return o == null ? "null" : o.getClass().getName() + " " + o;
    }
}
