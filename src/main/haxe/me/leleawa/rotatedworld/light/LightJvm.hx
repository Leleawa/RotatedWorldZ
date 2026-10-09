package me.leleawa.rotatedworld.light;

import haxe.io.Bytes;
import haxe.io.BytesData;

/**
 * 给 JVM 调用的入口：在 JVM 上 BytesData 就是 byte[]，Bytes.ofData / getData 不复制，
 * 所以 Scala 可以直接把 PacketEvents 的光照数组传进来。
 */
class LightJvm {
	public static function rotate(src:BytesData):BytesData {
		return Nibbles.rotate(Bytes.ofData(src)).getData();
	}

	public static function uniformValue(a:BytesData):Int {
		return Nibbles.uniformValue(Bytes.ofData(a));
	}

	public static function filled(value:Int):BytesData {
		return Nibbles.filled(value).getData();
	}

	public static function get(a:BytesData, idx:Int):Int {
		return Nibbles.get(Bytes.ofData(a), idx);
	}

	public static function set(a:BytesData, idx:Int, v:Int):Void {
		Nibbles.set(Bytes.ofData(a), idx, v);
	}
}
