package me.leleawa.rotatedworld;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * 启动 Fantom 层。Fantom 编译成 pod（fcode），运行时（{@code fan.sys.*}，已打进 jar）在第一次使用时才生成类。
 * 运行时需要一个 "Fantom home" 目录，所以把 Gradle 打好的最小 home（{@code /fantom/home.zip}：sys.pod + 我们的 pod）
 * 解压到插件的数据目录。
 */
final class FantomBridge {
    private FantomBridge() {}

    static void start(Path dataFolder) {
        Thread thread = Thread.currentThread();
        ClassLoader previous = thread.getContextClassLoader();
        try (InputStream zip = FantomBridge.class.getResourceAsStream("/fantom/home.zip")) {
            if (zip == null) throw new IllegalStateException("/fantom/home.zip 不在 jar 里");
            Path home = dataFolder.resolve("fantom-home");
            unzip(zip, home);
            System.setProperty("fan.home", home.toAbsolutePath().toString());
            // Fantom 按需定义类；让它能看到这个插件的类（me.leleawa.rotatedworld.api ...）
            thread.setContextClassLoader(FantomBridge.class.getClassLoader());
            fan.sys.Type.find("rwSpot::SpotSearch").method("init").call();
        } catch (IOException | RuntimeException | LinkageError e) {
            throw new IllegalStateException("Fantom 层启动失败", e);
        } finally {
            thread.setContextClassLoader(previous);
        }
    }

    private static void unzip(InputStream in, Path target) throws IOException {
        Path root = target.toAbsolutePath().normalize();
        try (ZipInputStream zin = new ZipInputStream(in)) {
            for (ZipEntry e = zin.getNextEntry(); e != null; e = zin.getNextEntry()) {
                Path out = root.resolve(e.getName()).normalize();
                if (!out.startsWith(root)) throw new IOException("Illegal zip entry " + e.getName());
                if (e.isDirectory()) {
                    Files.createDirectories(out);
                } else {
                    Files.createDirectories(out.getParent());
                    Files.copy(zin, out, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }
}
