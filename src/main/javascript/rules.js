// JavaScript 层（Rhino）："移动过快"的卡顿放行（原来在 Kotlin 的 PlayerListener 里）。
//
// 原版拿上一次区域 tick 的位置来比，Folia 区域线程卡一下、玩家又在高速下落时就会误判。
// 按服务端上次接受移动以来经过的时间，算出物理上可能移动的最大距离，范围内放行。
// dx / dy 是客户端水平方向（服务端 x / y），dz 是客户端竖直方向（服务端 z，下落终端速度 3.92 格/tick）。
// 最多按 2 秒算，免得站着不动很久之后突然瞬移也被放行。
function allowTooQuick(nanosSinceAcceptedMove, dx, dy, dz) {
    var ticks = Math.min(nanosSinceAcceptedMove / 50000000, 40) + 2;
    var horizontal = Math.sqrt(dx * dx + dy * dy);
    return horizontal <= ticks * 1.0 + 1 && Math.abs(dz) <= ticks * 3.92 + 2;
}

// 摔落伤害陪审团的一员
function fallDamage(fall, creative, spectator, flying, gliding, inWater, slowFalling) {
    if (creative || spectator || flying || gliding || inWater || slowFalling) return 0;
    return Math.max(Math.ceil(fall - 3), 0);
}
