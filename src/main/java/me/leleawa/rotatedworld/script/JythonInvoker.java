package me.leleawa.rotatedworld.script;

import java.nio.file.Path;
import java.util.Properties;
import org.python.core.Py;
import org.python.core.PyObject;
import org.python.core.PySystemState;
import org.python.util.PythonInterpreter;

/** Python 2.7（Jython）。插件 jar 在 {@code sys.path} 上，所以 {@code from pkg import Class} 能导入所有层的类。 */
final class JythonInvoker implements ScriptInvoker {
    private final PythonInterpreter interpreter;

    JythonInvoker(String source, ClassLoader loader, Path pluginJar) {
        Properties props = new Properties();
        props.setProperty("python.cachedir.skip", "true");
        props.setProperty("python.import.site", "false");
        PySystemState.initialize(System.getProperties(), props, new String[0], loader);
        PySystemState state = new PySystemState();
        state.setClassLoader(loader);
        // PyString 是 Python 2 的字节串，只能装 0-255 的字符；插件路径里有中文（C:\...\原桌面\...）时要用 unicode
        if (pluginJar != null) state.path.append(Py.newStringOrUnicode(pluginJar.toString()));
        interpreter = new PythonInterpreter(null, state);
        interpreter.exec(source);
    }

    @Override
    public synchronized Object call(String function, Object... args) {
        PyObject f = interpreter.get(function);
        return f.__call__(Py.javas2pys(args)).__tojava__(Object.class);
    }
}
