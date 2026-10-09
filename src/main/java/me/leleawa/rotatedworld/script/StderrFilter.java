package me.leleawa.rotatedworld.script;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.Charset;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 有的解释器在初始化时直接往 System.err 打印（ABCL 的 Debug.trace 没有开关），Paper 会因此警告"插件用了 System.err"。
 * 初始化期间临时套一层过滤：只拦下已知无害的那几行（改记到插件日志的 FINE 级别），其余原样转发。
 * 八个解释器是并行加载的，所以别的线程这时打印的东西必须照常输出。
 */
final class StderrFilter extends OutputStream {
    private final PrintStream target;
    private final Logger log;
    private final String[] harmless;
    private final ByteArrayOutputStream line = new ByteArrayOutputStream();

    private StderrFilter(PrintStream target, Logger log, String[] harmless) {
        this.target = target;
        this.log = log;
        this.harmless = harmless;
    }

    /** 在 body 运行期间拦下包含 harmless 中任一片段的整行。 */
    static <T> T during(Logger log, Supplier<T> body, String... harmless) {
        PrintStream original = System.err;
        StderrFilter filter = new StderrFilter(original, log, harmless);
        System.setErr(new PrintStream(filter, true, Charset.defaultCharset()));
        try {
            return body.get();
        } finally {
            filter.flush();
            System.setErr(original);
        }
    }

    @Override
    public synchronized void write(int b) {
        line.write(b);
        if (b == '\n') emit();
    }

    @Override
    public synchronized void flush() {
        if (line.size() > 0) emit();
        target.flush();
    }

    private void emit() {
        String text = line.toString(Charset.defaultCharset());
        line.reset();
        for (String h : harmless) {
            if (text.contains(h)) {
                log.log(Level.FINE, "（已拦下的解释器输出）" + text.strip());
                return;
            }
        }
        target.print(text);
    }
}
