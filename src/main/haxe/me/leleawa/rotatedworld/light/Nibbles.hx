package me.leleawa.rotatedworld.light;

import haxe.io.Bytes;

/**
 * 一个 16x16x16 section 的光照：4096 个 4 位值打包在 2048 个字节里，下标 (y shl 8 | z shl 4 | x)。
 *
 * 纯算法，只用 haxe.io.Bytes，不依赖 JVM：同一份代码可以编译到 JS / C++（比如做一个离线的旋转世界预览器），
 * 插件里用的是 --jvm 产物，入口见 LightJvm。
 */
class Nibbles {
	public static inline var BYTES = 2048;

	public static inline function get(a:Bytes, idx:Int):Int {
		return (a.get(idx >> 1) >> ((idx & 1) << 2)) & 15;
	}

	public static inline function set(a:Bytes, idx:Int, v:Int):Void {
		var i = idx >> 1;
		var shift = (idx & 1) << 2;
		a.set(i, (a.get(i) & ~(15 << shift)) | ((v & 15) << shift));
	}

	/** 全 0 或全 15（天空光满）。 */
	public static function filled(value:Int):Bytes {
		var a = Bytes.alloc(BYTES);
		a.fill(0, BYTES, value == 0 ? 0 : 0xFF);
		return a;
	}

	/**
	 * 服务端 section (y, z, x) -> 客户端 (ly = z, lz = 15 - y, lx = x)。
	 * 和 Geometry.localIndex（Frege）是同一个映射；这里展开成循环，因为每个 section 构建时都要跑一遍。
	 */
	public static function rotate(src:Bytes):Bytes {
		var dst = Bytes.alloc(BYTES);
		dst.fill(0, BYTES, 0);
		for (y in 0...16) {
			for (z in 0...16) {
				for (x in 0...16) {
					var v = get(src, (y << 8) | (z << 4) | x);
					if (v != 0) set(dst, (z << 8) | ((15 - y) << 4) | x, v);
				}
			}
		}
		return dst;
	}

	/** 全 0 或全 15 时返回该值（不存数组省内存），否则 -1。 */
	public static function uniformValue(a:Bytes):Int {
		var first = a.get(0);
		if (first != 0 && first != 0xFF) return -1;
		for (i in 1...a.length) {
			if (a.get(i) != first) return -1;
		}
		return first == 0 ? 0 : 15;
	}
}
