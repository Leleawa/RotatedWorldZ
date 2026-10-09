package me.leleawa.rotatedworld.probe

uses org.bukkit.Material
uses org.bukkit.Tag

/**
 * Gosu 的 enhancement：给 Bukkit 的 Material 加两个属性，WorldProbe 里写 type.Hazard / type.RotatedGround。
 */
enhancement MaterialEnhancement : Material {

  /** 旋转玩家落脚时要避开的方块。 */
  property get Hazard() : boolean {
    switch (this) {
      case LAVA:
      case MAGMA_BLOCK:
      case CACTUS:
      case CAMPFIRE:
      case SOUL_CAMPFIRE:
      case FIRE:
      case SOUL_FIRE:
      case POWDER_SNOW:
      case SWEET_BERRY_BUSH:
      case WITHER_ROSE:
      case POINTED_DRIPSTONE:
        return true
      default:
        return false
    }
  }

  /** 能当旋转视角下的地面（客户端里站在它的南面上）：不透光的实心方块，或者树叶。 */
  property get RotatedGround() : boolean {
    return this.Occluding or Tag.LEAVES.isTagged(this)
  }
}
