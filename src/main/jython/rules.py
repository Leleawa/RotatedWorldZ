# Jython 层（Python 2.7）：击退（原来在 Kotlin 的 PlayerListener 里）。
#
# 服务端里玩家的速度是过期的，击退方向也是按服务端坐标算的，所以伤害事件里把它全部丢掉，
# 在客户端坐标系里重新算：水平远离攻击者 + 客户端向上。
import math


def knockback(victim_x, victim_z, attacker_x, attacker_z, r1, r2):
    dx = victim_x - attacker_x
    dz = victim_z - attacker_z
    if math.sqrt(dx * dx + dz * dz) < 1e-4:
        # 两人重合：随便挑个方向
        dx, dz = r1 - 0.5, r2 - 0.5
    n = math.sqrt(dx * dx + dz * dz)
    return [dx / n * 0.4, 0.36, dz / n * 0.4]


# 摔落伤害陪审团的一员
def fall_damage(fall, creative, spectator, flying, gliding, in_water, slow_falling):
    if any([creative, spectator, flying, gliding, in_water, slow_falling]):
        return 0.0
    return max(math.ceil(fall - 3.0), 0.0)
