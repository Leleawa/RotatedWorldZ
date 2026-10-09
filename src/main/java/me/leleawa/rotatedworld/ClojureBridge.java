package me.leleawa.rotatedworld;

import java.util.Map;
import me.leleawa.rotatedworld.api.Settings;
import me.leleawa.rotatedworld.api.SettingsReader;
import me.leleawa.rotatedworld.config.SettingsParser;
import me.leleawa.rotatedworld.kernel.PluginContext;

/**
 * 调用 AOT 编译的 Clojure 代码。Clojure 运行时通过线程上下文类加载器找命名空间，
 * 所以初始化和调用时都临时把它指向这个插件的类加载器（PluginContext）。
 */
final class ClojureBridge {
    private ClojureBridge() {}

    static SettingsReader settingsReader() {
        return PluginContext.call(SettingsParser::new);
    }

    static Settings read(SettingsReader reader, Map<String, Object> values, int serverViewDistance) {
        return PluginContext.call(() -> reader.read(values, serverViewDistance));
    }
}
