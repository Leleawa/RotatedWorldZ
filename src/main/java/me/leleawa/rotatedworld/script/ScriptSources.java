package me.leleawa.rotatedworld.script;

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 脚本源码从哪来：{@code <插件数据目录>/scripts/<目录>/} 里有就用它（改脚本不用重新构建，重启服务器即可），
 * 否则用构建时从 {@code src/main/<目录>/} 打进 jar 的那份。
 */
final class ScriptSources {
    private ScriptSources() {}

    static String read(Path dataFolder, String dir, String file) throws IOException {
        Path override = dataFolder.resolve("scripts").resolve(dir).resolve(file);
        if (Files.isRegularFile(override)) {
            return Files.readString(override, StandardCharsets.UTF_8);
        }
        try (InputStream in = ScriptSources.class.getResourceAsStream("/scripts/" + dir + "/" + file)) {
            if (in == null) throw new IOException("Script not found: scripts/" + dir + "/" + file);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /** 插件 jar（或类目录）的位置；需要 classpath 的解释器（Kotlin Script、Jython）用它。 */
    static Path codeSource() {
        try {
            return Path.of(ScriptSources.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        } catch (URISyntaxException | RuntimeException e) {
            return null;
        }
    }
}
