package de.leon.lifesteal.worldedit;

import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.LocalSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.entity.Actor;
import com.sk89q.worldedit.event.extent.EditSessionEvent;
import com.sk89q.worldedit.extent.AbstractDelegateExtent;
import com.sk89q.worldedit.extent.Extent;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.util.eventbus.Subscribe;
import com.sk89q.worldedit.world.block.BlockStateHolder;
import com.sk89q.worldedit.WorldEditException;
import de.leon.lifesteal.LifestealCore;
import de.leon.lifesteal.data.Database;
import de.leon.lifesteal.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

public final class WorldEditAuditManager implements TabExecutor {
    private final LifestealCore plugin;
    private final Database db;
    private final List<AuditRow> pending = new CopyOnWriteArrayList<>();
    private int flushTask = -1;

    public WorldEditAuditManager(LifestealCore plugin, Database db) throws Exception {
        this.plugin = plugin;
        this.db = db;
        db.sync(c -> {
            String id = db.type() == Database.Type.SQLITE
                    ? "INTEGER PRIMARY KEY AUTOINCREMENT" : "BIGINT PRIMARY KEY AUTO_INCREMENT";
            try (Statement s = c.createStatement()) {
                s.executeUpdate("CREATE TABLE IF NOT EXISTS " + db.t("worldedit_audit")
                        + " (id " + id + ", batch_id VARCHAR(36) NOT NULL, actor_uuid VARCHAR(36),"
                        + " actor_name VARCHAR(16) NOT NULL, world_name VARCHAR(128) NOT NULL,"
                        + " x INT NOT NULL, y INT NOT NULL, z INT NOT NULL,"
                        + " before_state VARCHAR(512) NOT NULL, after_state VARCHAR(512) NOT NULL,"
                        + " created BIGINT NOT NULL)");
            }
            return null;
        });
        WorldEdit.getInstance().getEventBus().register(this);
        flushTask = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, this::flush, 40L, 40L);
    }

    @Subscribe
    public void onEditSession(EditSessionEvent event) {
        if (event.getStage() != EditSession.Stage.BEFORE_CHANGE || event.getActor() == null || event.getWorld() == null) return;
        Actor actor = event.getActor();
        String name = actor.getName();
        Player player = Bukkit.getPlayerExact(name);
        String uuid = player == null ? null : player.getUniqueId().toString();
        event.setExtent(new AuditExtent(event.getExtent(), UUID.randomUUID().toString(),
                uuid, name, event.getWorld().getName()));
    }

    private final class AuditExtent extends AbstractDelegateExtent {
        private final String batch;
        private final String actorUuid;
        private final String actorName;
        private final String world;

        private AuditExtent(Extent extent, String batch, String actorUuid, String actorName, String world) {
            super(extent);
            this.batch = batch;
            this.actorUuid = actorUuid;
            this.actorName = actorName;
            this.world = world;
        }

        @Override
        public <T extends BlockStateHolder<T>> boolean setBlock(BlockVector3 position, T block)
                throws WorldEditException {
            String before = getExtent().getBlock(position).getAsString();
            boolean changed = super.setBlock(position, block);
            if (changed) pending.add(new AuditRow(batch, actorUuid, actorName, world,
                    position.getBlockX(), position.getBlockY(), position.getBlockZ(),
                    before, block.getAsString(), System.currentTimeMillis()));
            return changed;
        }
    }

    private record AuditRow(String batch, String uuid, String name, String world,
                            int x, int y, int z, String before, String after, long created) { }

    private void flush() {
        if (pending.isEmpty()) return;
        List<AuditRow> rows = new ArrayList<>(pending);
        pending.removeAll(rows);
        db.run(c -> {
            try (PreparedStatement q = c.prepareStatement("INSERT INTO " + db.t("worldedit_audit")
                    + " (batch_id,actor_uuid,actor_name,world_name,x,y,z,before_state,after_state,created)"
                    + " VALUES (?,?,?,?,?,?,?,?,?,?)")) {
                for (AuditRow row : rows) {
                    q.setString(1, row.batch());
                    q.setString(2, row.uuid());
                    q.setString(3, row.name());
                    q.setString(4, row.world());
                    q.setInt(5, row.x());
                    q.setInt(6, row.y());
                    q.setInt(7, row.z());
                    q.setString(8, row.before());
                    q.setString(9, row.after());
                    q.setLong(10, row.created());
                    q.addBatch();
                }
                q.executeBatch();
            }
        });
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            Msg.err(sender, "This command can only be used in-game");
            return true;
        }
        Player target = player;
        if (args.length > 0) {
            if (!player.hasPermission("Admin.Core") && !player.hasPermission("Owner.Core")) {
                Msg.err(player, "Admin.Core is required to undo another player's edits");
                return true;
            }
            target = Bukkit.getPlayerExact(args[0]);
            if (target == null) {
                Msg.err(player, "That player is not online; their active WorldEdit session is required");
                return true;
            }
        } else if (!player.hasPermission("Builder.Core") && !player.hasPermission("Admin.Core")
                && !player.hasPermission("Owner.Core")) {
            Msg.err(player, "Builder.Core is required");
            return true;
        }

        try {
            Actor actor = BukkitAdapter.adapt(target);
            LocalSession session = WorldEdit.getInstance().getSessionManager().get(actor);
            EditSession undone = session.undo(null, actor);
            if (undone != null) undone.close();
            if (args.length == 0) Msg.ok(player, "Your latest WorldEdit edit was undone");
            else Msg.ok(player, "The latest WorldEdit edit of {p} was undone", "p", target.getName());
        } catch (Exception ex) {
            plugin.getLogger().warning("WorldEdit undo failed for " + target.getName() + ": " + ex.getMessage());
            Msg.err(player, "Undo failed. Check the console for details");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length != 1 || !sender.hasPermission("Admin.Core") && !sender.hasPermission("Owner.Core")) return List.of();
        String prefix = args[0].toLowerCase(Locale.ROOT);
        return Bukkit.getOnlinePlayers().stream().map(Player::getName)
                .filter(n -> n.toLowerCase(Locale.ROOT).startsWith(prefix))
                .sorted(String.CASE_INSENSITIVE_ORDER).toList();
    }

    public void shutdown() {
        if (flushTask != -1) Bukkit.getScheduler().cancelTask(flushTask);
        flush();
        WorldEdit.getInstance().getEventBus().unregister(this);
    }
}
