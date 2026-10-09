package me.leleawa.rotatedworld.script;

import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.lib.jse.CoerceJavaToLua;
import org.luaj.vm2.lib.jse.CoerceLuaToJava;
import org.luaj.vm2.lib.jse.JsePlatform;

/** Lua（LuaJ）。脚本用 {@code luajava.bindClass("pkg.Class")} 访问 Java 类。 */
final class LuaInvoker implements ScriptInvoker {
    private final Globals globals = JsePlatform.standardGlobals();

    LuaInvoker(String source, String name) {
        globals.load(new PluginLuajavaLib());
        globals.load(source, name).call();
    }

    @Override
    public synchronized Object call(String function, Object... args) {
        LuaValue[] lua = new LuaValue[args.length];
        for (int i = 0; i < args.length; i++) lua[i] = CoerceJavaToLua.coerce(args[i]);
        LuaValue v = globals.get(function).invoke(LuaValue.varargsOf(lua)).arg1();
        if (v.isboolean()) return v.toboolean();
        if (v.isnumber()) return v.todouble();
        return CoerceLuaToJava.coerce(v, Object.class);
    }
}
