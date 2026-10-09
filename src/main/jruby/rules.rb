# JRuby 层：区块柱的发送顺序（原来在 Scala 的 ViewManager 里）。
# 半径变化时才调用一次，结果由 Scala 缓存。
require 'java'

# (dx, dz) 按到中心的距离由近到远，展平成 [dx0, dz0, dx1, dz1, ...]。
# Ruby 的 sort_by 不稳定，所以把原来的下标也放进排序键，距离相同时保持生成顺序（和原版一致）。
def spiral(r)
  (-r..r).to_a.product((-r..r).to_a)
         .each_with_index
         .sort_by { |(dx, dz), i| [dx * dx + dz * dz, i] }
         .flat_map(&:first)
         .to_java(:int)
end

# 预取服务端区块时 x 方向的顺序：0, 1, -1, 2, -2 ...
def columns_x(r)
  (0..r).flat_map { |i| i.zero? ? [0] : [i, -i] }.to_java(:int)
end

# 摔落伤害陪审团的一员
def fall_damage(fall, creative, spectator, flying, gliding, in_water, slow_falling)
  return 0.0 if [creative, spectator, flying, gliding, in_water, slow_falling].any?
  [(fall - 3.0).ceil, 0].max.to_f
end
