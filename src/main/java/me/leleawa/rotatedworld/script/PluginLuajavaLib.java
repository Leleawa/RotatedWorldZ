package me.leleawa.rotatedworld.script;

import org.luaj.vm2.lib.jse.LuajavaLib;

/**
 * LuaJ 的 {@code luajava} 库，但在插件的类加载器里找类（系统类加载器看不到插件的类）。
 * LuaJ 用反射创建库类，所以要 public + 无参构造。
 */
public final class PluginLuajavaLib extends LuajavaLib {
    public PluginLuajavaLib() {}

    @Override
    protected Class classForName(String className) throws ClassNotFoundException {
        return Class.forName(className, true, PluginLuajavaLib.class.getClassLoader());
    }
}
