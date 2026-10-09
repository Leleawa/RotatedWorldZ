package me.leleawa.rotatedworld.command

import io.papermc.paper.threadedregions.scheduler.ScheduledTask
import me.leleawa.rotatedworld.geometry.Geometry
import me.leleawa.rotatedworld.kernel.Host
import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.World
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

import java.util.function.Consumer

/**
 * /rotate [on|off|toggle|status|unstuck|reload] [玩家]
 *
 * Groovy 负责流程编排；参数语法交给 Prolog（rules.pl），status 那一行字交给 BeanShell（rules.bsh）。
 * 涉及玩家实体的操作都放到玩家自己的调度器线程上（Folia）。
 */
class RotateCommand implements CommandExecutor {

    private final Host host

    RotateCommand(Host host) {
        this.host = host
    }

    @Override
    boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String[] words = args.collect { it } as String[]
        if (words.length > 0) words[0] = words[0].toLowerCase()
        String[] parsed = host.commandGrammar().parse(words)
        if (parsed == null) return false
        String sub = parsed[0]
        // 指定的玩家不在线时退回到发命令的人（和原来一样）
        Player target = (parsed[1] ? Bukkit.getPlayerExact(parsed[1]) : null) ?: (sender instanceof Player ? sender as Player : null)
        switch (sub) {
            case 'reload':
                host.reloadSettings()
                sender.sendMessage('§a配置已重载')
                return true
            case 'status':
                if (target == null) return notFound(sender)
                status(sender, target)
                return true
            case 'unstuck':
                if (target == null) return notFound(sender)
                if (host.stateOf(target) == null) {
                    sender.sendMessage("§e${target.name} 未旋转")
                    return true
                }
                onPlayerThread(target) {
                    sender.sendMessage(host.moveToSafeSpot(target) ? '§a已移到空位' : '§e当前位置没有卡住（或附近找不到空位）')
                }
                return true
            case ['on', 'off', 'toggle']:
                if (target == null) return notFound(sender)
                boolean enable = sub == 'toggle' ? !host.isEnabled(target.uniqueId) : sub == 'on'
                setEnabled(target, enable, sender)
                return true
            default:
                return false
        }
    }

    private static boolean notFound(CommandSender sender) {
        sender.sendMessage('§c找不到玩家')
        true
    }

    private void status(CommandSender sender, Player target) {
        def st = host.stateOf(target)
        if (st == null) {
            sender.sendMessage("§e${target.name}: 未旋转")
            return
        }
        onPlayerThread(target) {
            Location l = target.location
            sender.sendMessage(host.statusLine().render(target.name, st.outOff, st.inOff, st.generation,
                    l.x, Geometry.selfToClientY(l.z, st.outOff), Geometry.selfToClientZ(l.y),
                    st.sentColumns.size(), st.entities.size(), host.cachedChunks()))
        }
    }

    /** 开关：换一次世界让客户端整个重载。服务器只有一个世界时只能踢出重进。 */
    private void setEnabled(Player p, boolean enable, CommandSender sender) {
        String mode = enable ? '旋转' : '正常'
        if (host.isEnabled(p.uniqueId) == enable) {
            sender.sendMessage("§e${p.name} 已经是${mode}状态")
            return
        }
        Location back = p.location.clone()
        World other = Bukkit.worlds.find { it != p.world }
        if (other == null) {
            host.applyRotation(p, enable)
            p.kick(Component.text('视角已切换，请重新进入服务器'))
            return
        }
        p.teleportAsync(other.spawnLocation).thenRun({
            p.scheduler.runDelayed(host.plugin(), { ScheduledTask t ->
                host.applyRotation(p, enable)
                p.teleportAsync(back)
                sender.sendMessage("§a${p.name} 已切换为${mode}视角")
            } as Consumer<ScheduledTask>, null, 10L)
        } as Runnable)
    }

    private void onPlayerThread(Player p, Closure body) {
        p.scheduler.run(host.plugin(), { ScheduledTask t -> body() } as Consumer<ScheduledTask>, null)
    }
}
