package de.leon.lifesteal.team;

import de.leon.lifesteal.LifestealCore;
import de.leon.lifesteal.api.TeamProvider;
import org.bukkit.plugin.ServicePriority;
import de.leon.lifesteal.gui.Items;
import de.leon.lifesteal.gui.Menu;
import de.leon.lifesteal.gui.MenuListener;
import de.leon.lifesteal.util.Msg;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Statistic;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryAction;
import io.papermc.paper.event.player.AsyncChatEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;

import java.io.File;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.*;

public final class TeamModule implements Listener, TeamProvider {
    private static final List<String> RIGHTS = List.of("invite", "kick", "manage", "ec-deposit", "ec-withdraw", "chat");
    private enum Role { OWNER, CO_OWNER, MANAGER, OFFICER, MEMBER, TRIAL }
    private static final class Member {
        String name; Role role;
        Member(String name, Role role) { this.name = name; this.role = role; }
    }
    private static final class Team {
        final UUID id; String name; UUID owner;
        final LinkedHashMap<UUID, Member> members = new LinkedHashMap<>();
        final ItemStack[] chest = new ItemStack[54];
        final Map<Role, Set<String>> roleRights = new EnumMap<>(Role.class);
        long kills, deaths;
        Team(UUID id, String name, UUID owner) { this.id=id; this.name=name; this.owner=owner; }
    }

    private final LifestealCore plugin;
    private final File file;
    private final YamlConfiguration yaml;
    private final Map<UUID, Team> teams = new LinkedHashMap<>();
    private final Map<UUID, UUID> invites = new HashMap<>();
    private final Map<UUID, UUID> chestOpeners = new HashMap<>();
    private final Set<UUID> teamChat = new HashSet<>();

    public TeamModule(LifestealCore plugin) {
        this.plugin=plugin;
        file=new File(plugin.getDataFolder(), "teams.yml");
        yaml=loadYamlWithBackup();
        load();
        Bukkit.getPluginManager().registerEvents(this, plugin);
        Bukkit.getServicesManager().register(TeamProvider.class, this, plugin, ServicePriority.Normal);
        var cmd=plugin.getCommand("team");
        if(cmd!=null) { TeamExecutor executor=new TeamExecutor(); cmd.setExecutor(executor); cmd.setTabCompleter(executor); }
    }

    private YamlConfiguration loadYamlWithBackup() {
        YamlConfiguration loaded = new YamlConfiguration();
        if (!file.isFile()) return loaded;
        try {
            loaded.load(file);
            return loaded;
        } catch (IOException | InvalidConfigurationException ex) {
            File backup = new File(file.getParentFile(), "teams.yml.bak");
            if (backup.isFile()) {
                YamlConfiguration recovered = new YamlConfiguration();
                try {
                    recovered.load(backup);
                    plugin.getLogger().warning("teams.yml is damaged; recovering team data from teams.yml.bak.");
                    Files.copy(backup.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    return recovered;
                } catch (IOException | InvalidConfigurationException backupError) {
                    plugin.getLogger().log(java.util.logging.Level.SEVERE,
                            "Both teams.yml and teams.yml.bak could not be loaded. Original files are being preserved.", backupError);
                    return loaded;
                }
            }
            plugin.getLogger().log(java.util.logging.Level.SEVERE,
                    "teams.yml is damaged and no valid backup exists. The file will be preserved to avoid overwriting team data.", ex);
            return loaded;
        }
    }

    public void shutdown() {
        save();
    }

    private void load() {
        ConfigurationSection root=yaml.getConfigurationSection("teams");
        if(root!=null) for(String raw:root.getKeys(false)) try {
            UUID id=UUID.fromString(raw);
            ConfigurationSection s=root.getConfigurationSection(raw);
            if(s==null) continue;
            UUID owner=UUID.fromString(s.getString("owner",""));
            Team t=new Team(id,s.getString("name","Team"),owner);
            t.kills=s.getLong("stats.kills"); t.deaths=s.getLong("stats.deaths");
            for (Role role : Role.values()) {
                Set<String> rights = new HashSet<>();
                ConfigurationSection roleSection = s.getConfigurationSection("roles." + role.name() + ".rights");
                if (roleSection != null) for (String right : roleSection.getKeys(false)) if (roleSection.getBoolean(right)) rights.add(right);
                if (roleSection == null) rights.addAll(defaultRights(role));
                t.roleRights.put(role, rights);
            }
            ConfigurationSection ms=s.getConfigurationSection("members");
            if(ms!=null) for(String key:ms.getKeys(false)) try {
                UUID uid=UUID.fromString(key); ConfigurationSection m=ms.getConfigurationSection(key);
                if(m==null) continue;
                Role role; try { role=Role.valueOf(m.getString("role","MEMBER").toUpperCase(Locale.ROOT)); }
                catch(IllegalArgumentException ex) { role=Role.MEMBER; }
                if (role == null) role = Role.MEMBER;
                if (role == Role.OWNER && !uid.equals(owner)) role = Role.MEMBER;
                Member member=new Member(m.getString("name",key),role);
                // Legacy per-member rights are intentionally migrated to role-based defaults.
                t.members.put(uid,member);
            } catch(IllegalArgumentException ignored) {}
            List<?> stored=s.getList("enderchest");
            if(stored!=null) for(int i=0;i<Math.min(54,stored.size());i++)
                if(stored.get(i) instanceof ItemStack item && !item.getType().isAir()) t.chest[i]=item.clone();
            if(t.members.containsKey(owner) && !t.members.isEmpty()) teams.put(id,t);
        } catch(IllegalArgumentException ignored) { plugin.getLogger().warning("Ignoring invalid team entry "+raw); }
        ConfigurationSection is=yaml.getConfigurationSection("invites");
        if(is!=null) for(String key:is.getKeys(false)) try { invites.put(UUID.fromString(key),UUID.fromString(is.getString(key,""))); }
        catch(IllegalArgumentException ignored) {}
    }

    private void save() {
        yaml.set("teams",null);
        for(Team t:teams.values()) {
            String p="teams."+t.id;
            yaml.set(p+".name",t.name); yaml.set(p+".owner",t.owner.toString());
            yaml.set(p+".stats.kills",t.kills); yaml.set(p+".stats.deaths",t.deaths);
            yaml.set(p+".roles", null);
            for (Role role : Role.values()) yaml.set(p+".roles."+role.name()+".rights", new TreeSet<>(t.roleRights.getOrDefault(role, defaultRights(role))));
            yaml.set(p+".members",null);
            for(var e:t.members.entrySet()) {
                String m=p+".members."+e.getKey(); Member v=e.getValue();
                yaml.set(m+".name",v.name); yaml.set(m+".role",v.role.name());
            }
            yaml.set(p+".enderchest",Arrays.asList(t.chest));
        }
        yaml.set("invites",null);
        for(var e:invites.entrySet()) yaml.set("invites."+e.getKey(),e.getValue().toString());
        File parent = file.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            plugin.getLogger().severe("Could not create plugin data folder for teams.yml");
            return;
        }
        File temporary = new File(parent, "teams.yml.tmp");
        File backup = new File(parent, "teams.yml.bak");
        try {
            yaml.save(temporary);
            YamlConfiguration verification = new YamlConfiguration();
            verification.load(temporary);
            if (file.isFile() && file.length() > 0) {
                Files.copy(file.toPath(), backup.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            try {
                Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch(IOException | InvalidConfigurationException e) {
            plugin.getLogger().severe("Could not safely save teams.yml: "+e.getMessage());
            if (temporary.exists()) temporary.delete();
        }
    }

    private Team teamOf(UUID id) { for(Team t:teams.values()) if(t.members.containsKey(id)) return t; return null; }
    private Member member(Team t,UUID id) { return t==null?null:t.members.get(id); }
    @Override public boolean sameTeam(UUID a, UUID b) {
        Team first=teamOf(a), second=teamOf(b);
        return first!=null&&second!=null&&first.id.equals(second.id);
    }
    @Override public String teamName(UUID player) {
        Team team=teamOf(player);
        return team==null?null:team.name;
    }

    /** Lifetime player statistics of every current team member, including progress from before joining. */
    private long teamKills(Team team) {
        return team.members.keySet().stream()
                .mapToLong(id -> Bukkit.getOfflinePlayer(id).getStatistic(Statistic.PLAYER_KILLS)).sum();
    }

    private long teamDeaths(Team team) {
        return team.members.keySet().stream()
                .mapToLong(id -> Bukkit.getOfflinePlayer(id).getStatistic(Statistic.DEATHS)).sum();
    }
    private long playerStat(UUID id, TeamStat stat) {
        OfflinePlayer player = Bukkit.getOfflinePlayer(id);
        return switch (stat) {
            case PLAYTIME -> player.getStatistic(Statistic.PLAY_ONE_MINUTE) / 20L;
            case KILLS -> player.getStatistic(Statistic.PLAYER_KILLS);
            case DEATHS -> player.getStatistic(Statistic.DEATHS);
            case MONEY -> Math.round(plugin.economy().balance(id, de.leon.lifesteal.economy.Currency.MONEY) * 100.0);
            case GEMS -> Math.round(plugin.economy().balance(id, de.leon.lifesteal.economy.Currency.GEMS) * 100.0);
        };
    }
    private String statValue(UUID id, TeamStat stat) {
        long value = playerStat(id, stat);
        if (stat == TeamStat.PLAYTIME) return formatDuration(value);
        if (stat == TeamStat.MONEY || stat == TeamStat.GEMS) return String.format(Locale.US, "%.2f", value / 100.0);
        return Long.toString(value);
    }
    private String formatDuration(long seconds) {
        long days=seconds/86400, hours=(seconds%86400)/3600, minutes=(seconds%3600)/60;
        return days+"d "+hours+"h "+minutes+"m";
    }
    private enum TeamStat { PLAYTIME, KILLS, DEATHS, MONEY, GEMS }
    private Set<String> defaultRights(Role role) {
        return switch (role) {
            case OWNER -> new HashSet<>(RIGHTS);
            case CO_OWNER -> new HashSet<>(RIGHTS);
            case MANAGER -> new HashSet<>(List.of("invite", "kick", "manage", "ec-deposit", "ec-withdraw", "chat"));
            case OFFICER -> new HashSet<>(List.of("invite", "ec-deposit", "ec-withdraw", "chat"));
            case MEMBER -> new HashSet<>(List.of("ec-deposit", "ec-withdraw", "chat"));
            case TRIAL -> new HashSet<>(List.of("chat"));
        };
    }
    private boolean right(Team t,UUID id,String permission) {
        Member m=member(t,id); if(m==null) return false;
        if(m.role==Role.OWNER || t.owner.equals(id)) return true;
        return t.roleRights.getOrDefault(m.role, defaultRights(m.role)).contains(permission);
    }
    private int rolePower(Role role) {
        return switch (role) { case OWNER -> 6; case CO_OWNER -> 5; case MANAGER -> 4; case OFFICER -> 3; case MEMBER -> 2; case TRIAL -> 1; };
    }
    private boolean canManage(Player actor,Team t,UUID target) {
        Member a=member(t,actor.getUniqueId()), b=member(t,target);
        return a!=null&&b!=null&&!target.equals(t.owner)&&right(t,actor.getUniqueId(),"manage")
                &&rolePower(a.role)>rolePower(b.role);
    }
    private static boolean admin(Player p) { return p.hasPermission("Admin.Core")||p.hasPermission("Owner.Core"); }

    private void create(Player p,String name) {
        if(teamOf(p.getUniqueId())!=null) { Msg.err(p,"You are already in a team"); return; }
        if(!name.matches("[A-Za-z0-9_]{3,16}")) { Msg.err(p,"Team names must be 3-16 letters, numbers or underscores"); return; }
        if(teams.values().stream().anyMatch(t->t.name.equalsIgnoreCase(name))) { Msg.err(p,"That team name is already taken"); return; }
        Team t=new Team(UUID.randomUUID(),name,p.getUniqueId()); t.members.put(p.getUniqueId(),new Member(p.getName(),Role.OWNER));
        for (Role role : Role.values()) t.roleRights.put(role, defaultRights(role));
        teams.put(t.id,t); save(); Msg.ok(p,"Team {team} created","team",name); open(p);
    }
    private void open(Player p) {
        Team t=teamOf(p.getUniqueId());
        if(t==null) { MenuListener.open(p,new NoTeamMenu()); return; }
        MenuListener.open(p,new Home(t.id));
    }
    private void invite(Player p,Player target) {
        Team t=teamOf(p.getUniqueId());
        if(t==null||!right(t,p.getUniqueId(),"invite")) { Msg.err(p,"You cannot invite players"); return; }
        if(teamOf(target.getUniqueId())!=null) { Msg.err(p,"That player is already in a team"); return; }
        invites.put(target.getUniqueId(),t.id); save();
        Msg.ok(p,"Invite sent to {player}","player",target.getName());
        Msg.info(target,"{player} invited you to team {team}. Use /team accept","player",p.getName(),"team",t.name);
    }
    private void accept(Player p) {
        UUID id=invites.remove(p.getUniqueId()); Team t=id==null?null:teams.get(id);
        if(t==null) { Msg.err(p,"You have no pending team invite"); return; }
        if(teamOf(p.getUniqueId())!=null) { Msg.err(p,"You are already in a team"); return; }
        t.members.put(p.getUniqueId(),new Member(p.getName(),Role.MEMBER)); save();
        Msg.ok(p,"You joined team {team}","team",t.name);
        for(UUID uid:t.members.keySet()) { Player online=Bukkit.getPlayer(uid); if(online!=null&&!online.equals(p)) Msg.info(online,"{player} joined your team","player",p.getName()); }
    }
    private void leave(Player p) {
        Team t=teamOf(p.getUniqueId());
        if(t==null) { Msg.err(p,"You are not in a team"); return; }
        if(t.owner.equals(p.getUniqueId())) { Msg.err(p,"The owner must delete the team instead of leaving"); return; }
        t.members.remove(p.getUniqueId()); teamChat.remove(p.getUniqueId()); save(); Msg.info(p,"You left team {team}","team",t.name);
    }
    private void disband(Team t) { teams.remove(t.id); invites.values().removeIf(t.id::equals); save(); }

    @EventHandler public void onJoin(PlayerJoinEvent e) {
        Team t=teamOf(e.getPlayer().getUniqueId()); if(t==null) return;
        t.members.get(e.getPlayer().getUniqueId()).name=e.getPlayer().getName(); save();
    }
    @EventHandler public void onQuit(PlayerQuitEvent e) {
        UUID playerId = e.getPlayer().getUniqueId();
        chestOpeners.entrySet().removeIf(entry -> entry.getValue().equals(playerId));
    }
    @EventHandler public void onDeath(PlayerDeathEvent e) {
        Team victim=teamOf(e.getEntity().getUniqueId()); if(victim!=null) victim.deaths++;
        Player killer=e.getEntity().getKiller(); Team attacker=killer==null?null:teamOf(killer.getUniqueId());
        if(attacker!=null&&(victim==null||!attacker.id.equals(victim.id))) attacker.kills++;
        if(victim!=null||attacker!=null) save();
    }

    @EventHandler public void onTeamChat(AsyncChatEvent event) {
        UUID id=event.getPlayer().getUniqueId();
        if (!teamChat.contains(id)) return;
        event.setCancelled(true);
        Component message=event.message();
        Bukkit.getScheduler().runTask(plugin, () -> {
            Team team=teamOf(id);
            if (team==null) { teamChat.remove(id); return; }
            Component formatted=Component.text("[TEAM] "+event.getPlayer().getName()+": ").append(message);
            for (UUID memberId : team.members.keySet()) {
                Player online=Bukkit.getPlayer(memberId);
                if (online!=null) online.sendMessage(formatted);
            }
        });
    }

    private void toggleTeamChat(Player player) {
        Team team=teamOf(player.getUniqueId());
        if(team==null){Msg.err(player,"You are not in a team");return;}
        if(!right(team,player.getUniqueId(),"chat")){Msg.err(player,"Your role cannot use team chat");return;}
        if(teamChat.remove(player.getUniqueId())) Msg.info(player,"Team chat disabled");
        else {teamChat.add(player.getUniqueId());Msg.ok(player,"Team chat enabled");}
    }

    private final class TeamExecutor implements TabExecutor {
        @Override public boolean onCommand(CommandSender sender,Command cmd,String label,String[] args) {
            if(!(sender instanceof Player p)) { Msg.err(sender,"Only players can use this command"); return true; }
            if(label.equalsIgnoreCase("tc")) { toggleTeamChat(p); return true; }
            if(args.length==0) { open(p); return true; }
            switch(args[0].toLowerCase(Locale.ROOT)) {
                case "create" -> { if(args.length!=2) Msg.info(p,"Usage: /team create <name>"); else create(p,args[1]); }
                case "invite" -> { if(args.length!=2) Msg.info(p,"Usage: /team invite <player>"); else { Player target=Bukkit.getPlayerExact(args[1]); if(target==null) Msg.err(p,"Player is not online"); else invite(p,target); } }
                case "accept" -> accept(p);
                case "chat" -> toggleTeamChat(p);
                case "admin" -> { if(!admin(p)) Msg.err(p,"Admin.Core required"); else MenuListener.open(p,new AdminList(0)); }
                default -> Msg.info(p,"Usage: /team [create|invite|accept|chat|admin]");
            }
            return true;
        }
        @Override public List<String> onTabComplete(CommandSender sender,Command cmd,String alias,String[] args) {
            if(args.length==1) {
                List<String> options=new ArrayList<>(List.of("create","invite","accept","chat"));
                if(sender.hasPermission("Admin.Core")||sender.hasPermission("Owner.Core")) options.add("admin");
                return options.stream().filter(s->s.startsWith(args[0].toLowerCase(Locale.ROOT))).toList();
            }
            if(args.length==2&&args[0].equalsIgnoreCase("invite")) return Bukkit.getOnlinePlayers().stream().map(Player::getName)
                    .filter(n->n.toLowerCase(Locale.ROOT).startsWith(args[1].toLowerCase(Locale.ROOT))).toList();
            return List.of();
        }
    }

    private void openChest(Player p) {
        Team t=teamOf(p.getUniqueId());
        if(t==null) { Msg.err(p,"You are not in a team"); return; }
        if(!right(t,p.getUniqueId(),"ec-deposit")&&!right(t,p.getUniqueId(),"ec-withdraw")) { Msg.err(p,"You cannot access the team chest"); return; }
        UUID opener = chestOpeners.putIfAbsent(t.id, p.getUniqueId());
        if (opener != null && !opener.equals(p.getUniqueId())) {
            Msg.err(p, "The team ender chest is currently in use");
            return;
        }
        if (opener != null && MenuListener.openMenu(p) instanceof TeamChest) return;
        MenuListener.open(p,new TeamChest(t.id));
    }

    private final class Home extends Menu {
        final UUID id;
        Home(UUID id) { this.id=id; create(27,Component.text("Team"));
            Team t=teams.get(id);
            set(4,Items.of(Material.NAME_TAG,"Invite Player",Msg.NEUTRAL,"Invite an online player"));
            set(10,Items.of(Material.ENDER_CHEST,"Open EC",Msg.NEUTRAL,"Shared 6-row storage"));
            set(12,Items.of(Material.PLAYER_HEAD,"Members",Msg.NEUTRAL,"View team members"));
            set(14,Items.of(Material.GOLD_INGOT,"Statistics",Msg.NEUTRAL,"Playtime, kills, deaths, money and gems"));
            set(16,Items.of(Material.NETHER_STAR,"Roles",Msg.NEUTRAL,"Configure permissions by role"));
            set(22,Items.of(t!=null&&t.owner.equals(Bukkit.getPlayer(id)==null?null:Bukkit.getPlayer(id).getUniqueId())?Material.TNT:Material.BARRIER,
                    t!=null&&t.owner.equals(id)?"Delete Team":"Leave Team",Msg.BAD,"Confirmation required")); }
        @Override public void onClick(Player p,InventoryClickEvent e) {
            if(!isTop(e)) return; Team t=teamOf(p.getUniqueId()); if(t==null||!t.id.equals(id)) { p.closeInventory(); return; }
            switch(e.getRawSlot()) {
                case 4 -> MenuListener.open(p,new InvitePlayers(id));
                case 10 -> openChest(p);
                case 12 -> MenuListener.open(p,new Members(id));
                case 14 -> MenuListener.open(p,new Stats(id));
                case 16 -> { if(t.owner.equals(p.getUniqueId())) MenuListener.open(p,new Roles(id)); else Msg.err(p,"Only the owner can edit team roles"); }
                case 22 -> MenuListener.open(p,new ConfirmTeamAction(id,t.owner.equals(p.getUniqueId())));
            }
        }
    }

    private final class Members extends Menu {
        final UUID id;
        Members(UUID id) { this.id=id; create(54,Component.text("Team Members")); Team t=teams.get(id); if(t==null)return;
            int slot=0; for(var e:t.members.entrySet()) { Member m=e.getValue(); OfflinePlayer op=Bukkit.getOfflinePlayer(e.getKey());
                set(slot++,Items.head(op,Component.text(m.name),List.of(Component.text(Msg.sc("Role: "+m.role),Msg.NEUTRAL),Component.text(Msg.sc("Click to manage"),Msg.NEUTRAL))));
                if(slot==45)break; } set(49,Items.of(Material.ARROW,"Back",Msg.NEUTRAL)); }
        @Override public void onClick(Player p,InventoryClickEvent e) {
            if(!isTop(e))return; if(e.getRawSlot()==49){MenuListener.open(p,new Home(id));return;}
            Team t=teams.get(id); if(t==null||e.getRawSlot()<0||e.getRawSlot()>=45)return;
            if(e.getRawSlot()>=t.members.size())return; UUID target=new ArrayList<>(t.members.keySet()).get(e.getRawSlot());
            if(!canManage(p,t,target)){Msg.err(p,"You cannot manage this member");return;}
            MenuListener.open(p,new MemberEditor(id,target));
        }
    }

    private final class MemberEditor extends Menu {
        final UUID id,target;
        MemberEditor(UUID id,UUID target) { this.id=id;this.target=target;create(27,Component.text("Member Settings"));
            Team t=teams.get(id);Member m=member(t,target);if(m!=null){
                set(4,Items.head(Bukkit.getOfflinePlayer(target),Component.text(m.name),List.of(Component.text(Msg.sc("Role: "+m.role),Msg.NEUTRAL))));
                set(11,Items.of(Material.NAME_TAG,"Change Role",Msg.NEUTRAL,"Permissions are controlled by roles"));
                set(15,Items.of(Material.BARRIER,"Kick Member",Msg.BAD,"Remove this player from your team"));
                set(22,Items.of(Material.ARROW,"Back",Msg.NEUTRAL));
            }
        }
        @Override public void onClick(Player p,InventoryClickEvent e) {
            if(!isTop(e))return;
            if(e.getRawSlot()==22){MenuListener.open(p,new Members(id));return;}
            Team t=teams.get(id);Member m=member(t,target);
            if(t==null||m==null||!canManage(p,t,target)){p.closeInventory();return;}
            if(e.getRawSlot()==11){MenuListener.open(p,new AssignRole(id,target));return;}
            if(e.getRawSlot()==15){t.members.remove(target);save();Msg.info(p,"Member removed from your team");MenuListener.open(p,new Members(id));}
        }
    }

    private final class AssignRole extends Menu {
        final UUID id,target;
        AssignRole(UUID id,UUID target){this.id=id;this.target=target;create(54,Component.text("Assign Team Role"));
            Team t=teams.get(id);Member m=member(t,target);if(t==null||m==null)return;
            int slot=10;
            for(Role role:Role.values()){if(role==Role.OWNER)continue;
                set(slot++,Items.of(role==m.role?Material.LIME_DYE:Material.NAME_TAG,role.name(),role==m.role?Msg.GOOD:Msg.NEUTRAL,"Click to assign this role"));
            }
            set(49,Items.of(Material.ARROW,"Back",Msg.NEUTRAL));
        }
        @Override public void onClick(Player p,InventoryClickEvent e){if(!isTop(e))return;if(e.getRawSlot()==49){MenuListener.open(p,new MemberEditor(id,target));return;}
            Team t=teams.get(id);Member m=member(t,target);if(t==null||m==null||!canManage(p,t,target)){p.closeInventory();return;}
            int slot=10;for(Role role:Role.values()){if(role==Role.OWNER)continue;if(e.getRawSlot()==slot++){m.role=role;save();MenuListener.open(p,new MemberEditor(id,target));return;}}
        }
    }

    private final class Stats extends Menu {
        final UUID id;
        Stats(UUID id) { this.id=id;create(27,Component.text("Team Statistics"));Team t=teams.get(id);if(t!=null){
            set(10,Items.of(Material.CLOCK,"Playtime",Msg.NEUTRAL,Long.toString(teamStatTotal(t,TeamStat.PLAYTIME))+" seconds"));
            set(11,Items.of(Material.IRON_SWORD,"Kills",Msg.GOOD,Long.toString(teamStatTotal(t,TeamStat.KILLS))));
            set(12,Items.of(Material.SKELETON_SKULL,"Deaths",Msg.BAD,Long.toString(teamStatTotal(t,TeamStat.DEATHS))));
            set(14,Items.of(Material.GOLD_INGOT,"Money",Msg.GOOD,String.format(Locale.US,"%.2f",teamStatTotal(t,TeamStat.MONEY)/100.0)));
            set(15,Items.of(Material.EMERALD,"Gems",Msg.GOOD,String.format(Locale.US,"%.2f",teamStatTotal(t,TeamStat.GEMS)/100.0)));
            set(22,Items.of(Material.ARROW,"Back",Msg.NEUTRAL)); } }
        @Override public void onClick(Player p,InventoryClickEvent e) {if(!isTop(e))return;if(e.getRawSlot()==22){Team t=teamOf(p.getUniqueId());if(t!=null)MenuListener.open(p,new Home(t.id));return;}
            Team t=teams.get(id);if(t==null)return;TeamStat stat=switch(e.getRawSlot()){case 10->TeamStat.PLAYTIME;case 11->TeamStat.KILLS;case 12->TeamStat.DEATHS;case 14->TeamStat.MONEY;case 15->TeamStat.GEMS;default->null;};
            if(stat!=null)MenuListener.open(p,new Rankings(id,stat,0));}
    }

    private long teamStatTotal(Team team,TeamStat stat){return team.members.keySet().stream().mapToLong(uid->playerStat(uid,stat)).sum();}

    private final class Rankings extends Menu {
        final UUID id;final TeamStat stat;final int page;final List<UUID> ranking;
        Rankings(UUID id,TeamStat stat,int page){this.id=id;this.stat=stat;this.page=Math.max(0,page);Team t=teams.get(id);
            ranking=t==null?List.of():t.members.keySet().stream().sorted(Comparator.comparingLong((UUID uid)->playerStat(uid,stat)).reversed()).toList();
            create(54,Component.text(stat.name()+" Rankings"));int start=this.page*45;
            for(int i=start;i<Math.min(start+45,ranking.size());i++){UUID uid=ranking.get(i);OfflinePlayer op=Bukkit.getOfflinePlayer(uid);
                set(i-start,Items.head(op,Component.text((i+1)+". "+(op.getName()==null?uid.toString():op.getName())),
                        List.of(Component.text(Msg.sc(statValue(uid,stat)),Msg.NEUTRAL))));}
            if(this.page>0)set(45,Items.of(Material.ARROW,"Previous",Msg.NEUTRAL));set(49,Items.of(Material.ARROW,"Back",Msg.NEUTRAL));
            if((this.page+1)*45<ranking.size())set(53,Items.of(Material.ARROW,"Next",Msg.NEUTRAL));
        }
        @Override public void onClick(Player p,InventoryClickEvent e){if(!isTop(e))return;
            if(e.getRawSlot()==49){MenuListener.open(p,new Stats(id));return;}
            if(e.getRawSlot()==45&&page>0){MenuListener.open(p,new Rankings(id,stat,page-1));return;}
            if(e.getRawSlot()==53&&(page+1)*45<ranking.size()){MenuListener.open(p,new Rankings(id,stat,page+1));}
        }
    }

    private final class NoTeamMenu extends Menu {
        NoTeamMenu(){create(27,Component.text("Team Menu"));
            set(11,Items.of(Material.NAME_TAG,"Create Team",Msg.GOOD,"Create a new team"));
            set(15,Items.of(Material.WRITABLE_BOOK,"Accept Invitation",Msg.NEUTRAL,"Join your pending team invitation"));
            set(22,Items.of(Material.BARRIER,"Close",Msg.NEUTRAL));
        }
        @Override public void onClick(Player p,InventoryClickEvent e){if(!isTop(e))return;
            if(e.getRawSlot()==11){Msg.info(p,"Enter a team name in chat (or type cancel)");plugin.chatInput().request(p,name->create(p,name));}
            else if(e.getRawSlot()==15)accept(p);else if(e.getRawSlot()==22)p.closeInventory();
        }
    }

    private final class InvitePlayers extends Menu {
        final UUID id;final List<Player> candidates;
        InvitePlayers(UUID id){this.id=id;candidates=Bukkit.getOnlinePlayers().stream().filter(target->teamOf(target.getUniqueId())==null).sorted(Comparator.comparing(Player::getName,String.CASE_INSENSITIVE_ORDER)).toList();
            create(54,Component.text("Invite Player"));for(int i=0;i<Math.min(45,candidates.size());i++){Player target=candidates.get(i);
                set(i,Items.head(target,Component.text(target.getName()),List.of(Component.text(Msg.sc("Click to invite"),Msg.NEUTRAL))));}
            set(49,Items.of(Material.ARROW,"Back",Msg.NEUTRAL));
        }
        @Override public void onClick(Player p,InventoryClickEvent e){if(!isTop(e))return;if(e.getRawSlot()==49){MenuListener.open(p,new Home(id));return;}
            Team t=teams.get(id);if(t==null||!right(t,p.getUniqueId(),"invite")){p.closeInventory();return;}
            int slot=e.getRawSlot();if(slot>=0&&slot<Math.min(45,candidates.size())){Player target=candidates.get(slot);invite(p,target);MenuListener.open(p,new Home(id));}
        }
    }

    private final class ConfirmTeamAction extends Menu {
        final UUID id;final boolean delete;
        ConfirmTeamAction(UUID id,boolean delete){this.id=id;this.delete=delete;create(27,Component.text(delete?"Delete Team?":"Leave Team?"));
            set(11,Items.of(Material.LIME_DYE,"Confirm",Msg.GOOD,delete?"Permanently delete team and storage":"Leave team and lose your role"));
            set(15,Items.of(Material.RED_DYE,"Cancel",Msg.BAD,"Return without changes"));
        }
        @Override public void onClick(Player p,InventoryClickEvent e){if(!isTop(e))return;
            if(e.getRawSlot()==15){MenuListener.open(p,new Home(id));return;}
            if(e.getRawSlot()!=11)return;Team t=teams.get(id);if(t==null||!t.members.containsKey(p.getUniqueId())){p.closeInventory();return;}
            if(delete){if(!t.owner.equals(p.getUniqueId())){p.closeInventory();return;}String name=t.name;disband(t);p.closeInventory();Msg.ok(p,"Team {team} deleted","team",name);}
            else{if(t.owner.equals(p.getUniqueId())){Msg.err(p,"The owner must delete the team");return;}leave(p);p.closeInventory();}
        }
    }

    private final class Roles extends Menu {
        final UUID id;
        Roles(UUID id){this.id=id;create(54,Component.text("Team Roles"));Team t=teams.get(id);if(t==null)return;
            int slot=10;for(Role role:Role.values()){if(role==Role.OWNER)continue;
                set(slot++,Items.of(Material.NAME_TAG,role.name(),Msg.NEUTRAL,"Members: "+t.members.values().stream().filter(m->m.role==role).count(),"Click to edit role permissions"));}
            set(49,Items.of(Material.ARROW,"Back",Msg.NEUTRAL));
        }
        @Override public void onClick(Player p,InventoryClickEvent e){if(!isTop(e))return;if(e.getRawSlot()==49){MenuListener.open(p,new Home(id));return;}
            Team t=teams.get(id);if(t==null||!t.owner.equals(p.getUniqueId())){p.closeInventory();return;}
            int slot=10;for(Role role:Role.values()){if(role==Role.OWNER)continue;if(e.getRawSlot()==slot++){MenuListener.open(p,new RoleEditor(id,role));return;}}
        }
    }

    private final class RoleEditor extends Menu {
        final UUID id;final Role role;
        RoleEditor(UUID id,Role role){this.id=id;this.role=role;create(27,Component.text(role.name()+" Permissions"));Team t=teams.get(id);if(t==null)return;
            int[] slots={10,11,12,14,15,16};String[] labels={"Invite","Kick","Manage","EC Deposit","EC Withdraw","Team Chat"};
            for(int i=0;i<RIGHTS.size();i++){boolean enabled=t.roleRights.getOrDefault(role,defaultRights(role)).contains(RIGHTS.get(i));
                set(slots[i],Items.of(enabled?Material.LIME_DYE:Material.GRAY_DYE,labels[i],enabled?Msg.GOOD:Msg.BAD,"Click to toggle"));}
            set(22,Items.of(Material.ARROW,"Back",Msg.NEUTRAL));
        }
        @Override public void onClick(Player p,InventoryClickEvent e){if(!isTop(e))return;if(e.getRawSlot()==22){MenuListener.open(p,new Roles(id));return;}
            Team t=teams.get(id);if(t==null||!t.owner.equals(p.getUniqueId())){p.closeInventory();return;}
            int[] slots={10,11,12,14,15,16};for(int i=0;i<slots.length;i++)if(e.getRawSlot()==slots[i]){
                Set<String> rights=t.roleRights.computeIfAbsent(role,k->defaultRights(role));String permission=RIGHTS.get(i);
                if(!rights.add(permission))rights.remove(permission);save();MenuListener.open(p,new RoleEditor(id,role));return;}
        }
    }

    /** A 54-slot shared inventory. All transfers are explicit and persisted after each click. */
    private final class TeamChest extends Menu {
        final UUID id;
        TeamChest(UUID id) { this.id=id;create(54,Component.text("Team Ender Chest"));Team t=teams.get(id);if(t!=null)for(int i=0;i<54;i++)set(i,t.chest[i]==null?null:t.chest[i].clone()); }
        boolean allowed(Player p,String permission){Team t=teams.get(id);Team own=teamOf(p.getUniqueId());return t!=null&&own!=null&&own.id.equals(id)&&right(t,p.getUniqueId(),permission);}
        void persist(){Team t=teams.get(id);if(t==null)return;for(int i=0;i<54;i++){ItemStack x=getInventory().getItem(i);t.chest[i]=x==null||x.getType().isAir()?null:x.clone();}save();}
        @Override public void onClick(Player p,InventoryClickEvent e) {
            Team t=teams.get(id);Team own=teamOf(p.getUniqueId());if(t==null||own==null||!own.id.equals(id)){p.closeInventory();return;}
            Inventory top=getInventory();
            if(e.getClickedInventory()==null)return;
            if(e.getClickedInventory().equals(p.getInventory())) {
                if(!e.isShiftClick()||!allowed(p,"ec-deposit"))return;
                ItemStack source=e.getCurrentItem();if(source==null||source.getType().isAir())return;
                Map<Integer,ItemStack> left=top.addItem(source.clone());int remaining=left.values().stream().mapToInt(ItemStack::getAmount).sum();
                int moved=source.getAmount()-remaining;if(moved<=0)return;
                if(remaining==0)e.setCurrentItem(null);else{ItemStack rest=source.clone();rest.setAmount(remaining);e.setCurrentItem(rest);}persist();return;
            }
            if(!e.getClickedInventory().equals(top))return;int slot=e.getRawSlot();if(slot<0||slot>=54)return;
            ItemStack current=top.getItem(slot),cursor=e.getCursor();boolean carrying=cursor!=null&&!cursor.getType().isAir();
            if(e.isShiftClick()) {
                if(current==null||current.getType().isAir()||!allowed(p,"ec-withdraw"))return;
                Map<Integer,ItemStack> left=p.getInventory().addItem(current.clone());int remaining=left.values().stream().mapToInt(ItemStack::getAmount).sum();
                int moved=current.getAmount()-remaining;if(moved<=0)return;
                if(remaining==0)top.setItem(slot,null);else{ItemStack rest=current.clone();rest.setAmount(remaining);top.setItem(slot,rest);}persist();return;
            }
            if(carrying) {
                if(!allowed(p,"ec-deposit"))return;
                int amount=e.isRightClick()?1:cursor.getAmount();
                if(current==null||current.getType().isAir()) {
                    ItemStack placed=cursor.clone();placed.setAmount(amount);top.setItem(slot,placed);
                    if(amount==cursor.getAmount())e.setCursor(null);else{ItemStack rest=cursor.clone();rest.setAmount(cursor.getAmount()-amount);e.setCursor(rest);}
                } else if(current.isSimilar(cursor)&&current.getAmount()<current.getMaxStackSize()) {
                    int moved=Math.min(amount,current.getMaxStackSize()-current.getAmount());current.setAmount(current.getAmount()+moved);top.setItem(slot,current);
                    if(moved==cursor.getAmount())e.setCursor(null);else{ItemStack rest=cursor.clone();rest.setAmount(cursor.getAmount()-moved);e.setCursor(rest);}
                } else {
                    if(!allowed(p,"ec-withdraw")) return;
                    top.setItem(slot,cursor.clone());e.setCursor(current.clone());
                }
            } else if(current!=null&&!current.getType().isAir()) {
                if(!allowed(p,"ec-withdraw"))return;
                int amount=e.isRightClick()?(current.getAmount()+1)/2:current.getAmount();
                ItemStack taken=current.clone();taken.setAmount(amount);e.setCursor(taken);
                if(amount==current.getAmount())top.setItem(slot,null);else{current.setAmount(current.getAmount()-amount);top.setItem(slot,current);}
            } else return;
            persist();
        }
        @Override public void onClose(Player p){
            persist();
            chestOpeners.remove(id, p.getUniqueId());
        }
    }

    private final class AdminList extends Menu {
        final int page;final List<Team> snapshot;
        AdminList(int page){this.page=Math.max(0,page);snapshot=teams.values().stream().sorted(Comparator.comparing(t->t.name.toLowerCase(Locale.ROOT))).toList();
            create(54,Component.text("Team Administration"));int start=this.page*45;
            for(int i=start;i<Math.min(start+45,snapshot.size());i++){Team t=snapshot.get(i);set(i-start,Items.of(Material.SHIELD,t.name,Msg.NEUTRAL,"Members: "+t.members.size(),"Kills: "+teamKills(t),"Deaths: "+teamDeaths(t),"Click to manage"));}
            if(this.page>0)set(45,Items.of(Material.ARROW,"Previous",Msg.NEUTRAL));set(49,Items.of(Material.BARRIER,"Close",Msg.NEUTRAL));
            if((this.page+1)*45<snapshot.size())set(53,Items.of(Material.ARROW,"Next",Msg.NEUTRAL));}
        @Override public void onClick(Player p,InventoryClickEvent e){if(!admin(p)){p.closeInventory();return;}int s=e.getRawSlot();
            if(s==49){p.closeInventory();return;}if(s==45&&page>0){MenuListener.open(p,new AdminList(page-1));return;}
            if(s==53&&(page+1)*45<snapshot.size()){MenuListener.open(p,new AdminList(page+1));return;}
            if(s>=0&&s<45&&page*45+s<snapshot.size())MenuListener.open(p,new AdminDetail(snapshot.get(page*45+s).id));}
    }
    private final class AdminDetail extends Menu {
        final UUID id;
        AdminDetail(UUID id){this.id=id;create(27,Component.text("Team Management"));Team t=teams.get(id);if(t!=null){
            set(11,Items.of(Material.SHIELD,t.name,Msg.NEUTRAL,"Members: "+t.members.size()));
            set(13,Items.of(Material.IRON_SWORD,"Team Kills",Msg.GOOD,Long.toString(t.kills)));
            set(15,Items.of(Material.SKELETON_SKULL,"Team Deaths",Msg.BAD,Long.toString(t.deaths)));
            set(20,Items.of(Material.TNT,"Disband Team",Msg.BAD,"Delete team and its shared chest"));set(26,Items.of(Material.ARROW,"Back",Msg.NEUTRAL));}}
        @Override public void onClick(Player p,InventoryClickEvent e){if(!admin(p)){p.closeInventory();return;}
            if(e.getRawSlot()==26){MenuListener.open(p,new AdminList(0));return;}
            if(e.getRawSlot()==20){Team t=teams.get(id);if(t!=null){String n=t.name;disband(t);Msg.ok(p,"Team {team} deleted","team",n);}MenuListener.open(p,new AdminList(0));}}
    }
}
