-- Lua 层（LuaJ）：摔落伤害（原来在 Kotlin 的 PlayerListener 里），也是摔落伤害陪审团的首席陪审员（平票听它的）。
--
-- vanilla 按服务端 y 算摔落，而服务端 y 在客户端里是水平方向，所以原版的摔落伤害全部取消，
-- netty 线程按客户端高度（服务端 z）累计摔落距离，落地时在这里算伤害。
function fallDamage(fall, creative, spectator, flying, gliding, inWater, slowFalling)
  if creative or spectator or flying or gliding or inWater or slowFalling then
    return 0
  end
  local damage = math.ceil(fall - 3.0)
  if damage <= 0 then
    return 0
  end
  return damage
end
