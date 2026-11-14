package de.leon.lifesteal.diagnostics;

import de.leon.lifesteal.LifestealCore;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public final class ServerControlCommand implements TabExecutor {
    private final LifestealCore core;
    private final AtomicBoolean operationPending = new AtomicBoolean(false);

    public ServerControlCommand(LifestealCore core) {
        this.core = core;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("Owner.Core")) {
            sender.sendMessage(Component.text("ʏᴏᴜ ᴅᴏ ɴᴏᴛ ʜᴀᴠᴇ ᴘᴇʀᴍɪssɪᴏɴ ᴛᴏ ᴅᴏ ᴛʜɪs.", NamedTextColor.RED));
            return true;
        }
        if (args.length != 0) {
            sender.sendMessage(Component.text("ᴜsᴀɢᴇ: /" + label, NamedTextColor.YELLOW));
            return true;
        }

        boolean restart = command.getName().equalsIgnoreCase("restart");
        if (!operationPending.compareAndSet(false, true)) {
            sender.sendMessage(Component.text("ᴀ sᴇʀᴠᴇʀ sʜᴜᴛᴅᴏᴡɴ ᴏᴘᴇʀᴀᴛɪᴏɴ ɪs ᴀʟʀᴇᴀᴅʏ ʀᴜɴɴɪɴɢ.", NamedTextColor.RED));
            return true;
        }

        try {
            Bukkit.broadcast(Component.text(
                    restart ? "sᴀᴠɪɴɢ ᴀʟʟ sᴇʀᴠᴇʀ ᴅᴀᴛᴀ ʙᴇғᴏʀᴇ ʀᴇsᴛᴀʀᴛ..." : "sᴀᴠɪɴɢ ᴀʟʟ sᴇʀᴠᴇʀ ᴅᴀᴛᴀ ʙᴇғᴏʀᴇ sʜᴜᴛᴅᴏᴡɴ...",
                    NamedTextColor.YELLOW));

            // Flush vanilla world and player data before invoking the normal Paper restart flow.
            // LifestealCore's database-backed state is saved by onDisable(), so do not save it twice here.
            boolean saved = Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "minecraft:save-all flush");
            if (!saved) {
                operationPending.set(false);
                sender.sendMessage(Component.text("sᴀᴠᴇ ғᴀɪʟᴇᴅ. ᴛʜᴇ ᴏᴘᴇʀᴀᴛɪᴏɴ ᴡᴀs ᴄᴀɴᴄᴇʟʟᴇᴅ.", NamedTextColor.RED));
                core.getLogger().severe("Server restart/stop cancelled because minecraft:save-all flush could not be executed.");
                return true;
            }

            Bukkit.broadcast(Component.text(
                    restart ? "sᴀᴠᴇ ᴄᴏᴍᴘʟᴇᴛᴇ. ʀᴇsᴛᴀʀᴛɪɴɢ sᴇʀᴠᴇʀ..." : "sᴀᴠᴇ ᴄᴏᴍᴘʟᴇᴛᴇ. sᴛᴏᴘᴘɪɴɢ sᴇʀᴠᴇʀ...",
                    NamedTextColor.GREEN));

            if (restart) {
                core.getServer().restart();
            } else {
                core.getServer().shutdown();
            }
        } catch (Throwable error) {
            operationPending.set(false);
            core.getLogger().log(java.util.logging.Level.SEVERE, "Could not safely " + (restart ? "restart" : "stop") + " the server.", error);
            sender.sendMessage(Component.text("ᴏᴘᴇʀᴀᴛɪᴏɴ ᴄᴀɴᴄᴇʟʟᴇᴅ ʙᴇᴄᴀᴜsᴇ sᴀᴠɪɴɢ ғᴀɪʟᴇᴅ. ᴄʜᴇᴄᴋ ᴛʜᴇ sᴇʀᴠᴇʀ ᴄᴏɴsᴏʟᴇ.", NamedTextColor.RED));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return List.of();
    }
}
