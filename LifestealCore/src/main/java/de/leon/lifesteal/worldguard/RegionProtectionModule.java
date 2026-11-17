package de.leon.lifesteal.worldguard;

import de.leon.lifesteal.LifestealCore;
import de.leon.lifesteal.gui.Items;
import de.leon.lifesteal.gui.Menu;
import de.leon.lifesteal.gui.MenuListener;
import de.leon.lifesteal.util.Msg;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockRedstoneEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.hanging.HangingPlaceEvent;
import org.bukkit.event.vehicle.VehicleDamageEvent;
import org.bukkit.event.vehicle.VehicleDestroyEvent;
import org.bukkit.event.vehicle.VehicleEnterEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.block.Sign;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Projectile;
import org.bukkit.projectiles.ProjectileSource;

import java.io.File;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public final class RegionProtectionModule implements Listener {
    private static final List<String> FLAGS = List.of("BUILD","INTERACT","PVP","EXPLOSIONS","MOB-SPAWNING","KOTH_PVP_ARENA","KOTH_CAPTURE","COMBAT_FORCEFIELD","CRYSTAL","ARENA_RESET","LIQUID-FLOW","REDSTONE","FIRE-SPREAD","ENTITY-INTERACT","VEHICLE-USE","PISTON","HANGING","OP-BYPASS","ELYTRA-ROCKETS","ENDER-PEARLS","ITEM-DROP","ITEM-PICKUP");
    private static final Map<String,Material> FLAG_ICONS = Map.ofEntries(
            Map.entry("BUILD",Material.BRICKS),Map.entry("INTERACT",Material.LEVER),
            Map.entry("PVP",Material.IRON_SWORD),Map.entry("EXPLOSIONS",Material.TNT),
            Map.entry("MOB-SPAWNING",Material.SPAWNER),Map.entry("KOTH_PVP_ARENA",Material.DIAMOND_SWORD),
            Map.entry("KOTH_CAPTURE",Material.BEACON),Map.entry("COMBAT_FORCEFIELD",Material.RED_STAINED_GLASS),
            Map.entry("CRYSTAL",Material.OBSIDIAN),Map.entry("ARENA_RESET",Material.REPEATER),
            Map.entry("LIQUID-FLOW",Material.WATER_BUCKET),Map.entry("REDSTONE",Material.REDSTONE),
            Map.entry("FIRE-SPREAD",Material.FLINT_AND_STEEL),Map.entry("ENTITY-INTERACT",Material.ARMOR_STAND),
            Map.entry("VEHICLE-USE",Material.MINECART),Map.entry("PISTON",Material.PISTON),
            Map.entry("HANGING",Material.ITEM_FRAME),Map.entry("OP-BYPASS",Material.NETHER_STAR),
            Map.entry("ELYTRA-ROCKETS",Material.FIREWORK_ROCKET),Map.entry("ENDER-PEARLS",Material.ENDER_PEARL),
            Map.entry("ITEM-DROP",Material.DROPPER),Map.entry("ITEM-PICKUP",Material.HOPPER));
    private static final class Region {
        String id,worldName,displayName,iconMaterial="MAP"; UUID world,owner;
        int minX,minY,minZ,maxX,maxY,maxZ,priority; String parentId;
        final Set<UUID> members=new LinkedHashSet<>();
        final Map<String,Boolean> flags=new HashMap<>();
        long volume(){return (long)(maxX-minX+1)*(maxY-minY+1)*(maxZ-minZ+1);}
        boolean contains(Location l){return l.getWorld()!=null&&l.getWorld().getUID().equals(world)
                &&l.getBlockX()>=minX&&l.getBlockX()<=maxX&&l.getBlockY()>=minY&&l.getBlockY()<=maxY
                &&l.getBlockZ()>=minZ&&l.getBlockZ()<=maxZ;}
        boolean flag(String name){return flags.getOrDefault(name,false);}
        boolean member(UUID id){return owner.equals(id)||members.contains(id);}
    }

    private final LifestealCore plugin;
    private final Map<UUID,Location> pos1=new HashMap<>(),pos2=new HashMap<>();
    private final File file;
    private final YamlConfiguration yaml;
    private boolean storageHealthy=true;
    private final Map<String,Region> regions=new LinkedHashMap<>();
    private final Map<String,Map<String,String>> arenaBaselines=new HashMap<>();
    private final Map<String,org.bukkit.scheduler.BukkitTask> arenaResetTasks=new HashMap<>();
    private final Map<String,Long> crystalExpiry=new HashMap<>();
    private final File crystalExpiryFile;
    private final YamlConfiguration crystalExpiryYaml=new YamlConfiguration();

    public RegionProtectionModule(LifestealCore plugin){
        this.plugin=plugin;
        file=new File(plugin.getDataFolder(),"regions.yml");yaml=loadYamlWithBackup();
        crystalExpiryFile=new File(plugin.getDataFolder(),"worldguard-crystal-expiry.yml");
        loadCrystalExpiry();
        load();
        for(Region region:regions.values())if(flag(region,"ARENA_RESET"))configureArenaReset(region,true);
        Bukkit.getPluginManager().registerEvents(this,plugin);
        Bukkit.getScheduler().runTaskTimer(plugin,this::processCrystalExpiry,20L,20L);
        var command=plugin.getCommand("lwg");
        if(command!=null)command.setExecutor((sender,cmd,label,args)->execute(sender,args));
    }

    private YamlConfiguration loadYamlWithBackup(){
        YamlConfiguration loaded=new YamlConfiguration();
        if(!file.isFile())return loaded;
        try{loaded.load(file);return loaded;}
        catch(IOException|InvalidConfigurationException ex){
            File backup=new File(file.getParentFile(),"regions.yml.bak");
            if(backup.isFile())try{
                YamlConfiguration recovered=new YamlConfiguration();recovered.load(backup);
                Files.copy(backup.toPath(),file.toPath(),StandardCopyOption.REPLACE_EXISTING);
                plugin.getLogger().warning("regions.yml was damaged; recovered region data from regions.yml.bak.");
                return recovered;
            }catch(IOException|InvalidConfigurationException backupError){
                storageHealthy=false;plugin.getLogger().log(java.util.logging.Level.SEVERE,"regions.yml and its backup are invalid; preserving both files.",backupError);return loaded;
            }
            storageHealthy=false;plugin.getLogger().log(java.util.logging.Level.SEVERE,"regions.yml is invalid and no backup exists; preserving the original file.",ex);return loaded;
        }
    }
    private void load(){
        if(loadDatabase()) return;
        ConfigurationSection root=yaml.getConfigurationSection("regions");
        if(root!=null) for(String id:root.getKeys(false))try{
            ConfigurationSection s=root.getConfigurationSection(id);if(s==null)continue;
            Region r=new Region();r.id=id;r.world=UUID.fromString(s.getString("world",""));
            r.worldName=s.getString("world-name","world");r.owner=UUID.fromString(s.getString("owner",""));
            r.displayName=s.getString("display-name",id);r.iconMaterial=s.getString("icon","MAP");
            r.minX=s.getInt("min.x");r.minY=s.getInt("min.y");r.minZ=s.getInt("min.z");
            r.maxX=s.getInt("max.x");r.maxY=s.getInt("max.y");r.maxZ=s.getInt("max.z");
            for(String flag:FLAGS){
                String lower=flag.toLowerCase(Locale.ROOT);
                if(s.contains("flags."+flag))r.flags.put(flag,s.getBoolean("flags."+flag));
                else if(s.contains("flags."+lower))r.flags.put(flag,s.getBoolean("flags."+lower));
            }
            for(String member:s.getStringList("members"))try{r.members.add(UUID.fromString(member));}catch(IllegalArgumentException ignored){}
            regions.put(id.toLowerCase(Locale.ROOT),r);
        }catch(IllegalArgumentException ignored){plugin.getLogger().warning("Ignoring invalid region "+id+" in regions.yml");}
        // Do not mark a migration complete when both YAML copies were corrupt.
        // This keeps the original files eligible for recovery after an administrator repairs them.
        if(storageHealthy) persistDatabase(true);
    }

    private boolean loadDatabase(){
        try{
            return plugin.database().sync(connection -> {
                boolean migrated=false;
                try(PreparedStatement marker=connection.prepareStatement(
                        "SELECT migration_key FROM "+plugin.database().t("module_migrations")+" WHERE migration_key=?")){
                    marker.setString(1,"regions-yaml-v1");
                    try(ResultSet result=marker.executeQuery()){migrated=result.next();}
                }
                int count=0;
                try(PreparedStatement query=connection.prepareStatement("SELECT * FROM "+plugin.database().t("regions"));
                    ResultSet result=query.executeQuery()){
                    while(result.next()){
                        count++;
                        Region r=new Region();
                        r.id=result.getString("region_id");r.world=UUID.fromString(result.getString("world_uuid"));
                        r.worldName=result.getString("world_name");r.owner=UUID.fromString(result.getString("owner_uuid"));r.displayName=r.id;r.iconMaterial="MAP";
                        r.minX=result.getInt("min_x");r.minY=result.getInt("min_y");r.minZ=result.getInt("min_z");
                        r.maxX=result.getInt("max_x");r.maxY=result.getInt("max_y");r.maxZ=result.getInt("max_z");
                        r.priority=result.getInt("priority");r.parentId=result.getString("parent_id");
                        
                        regions.put(r.id.toLowerCase(Locale.ROOT),r);
                    }
                }
                if(count==0&&!migrated) return false;
                try(PreparedStatement query=connection.prepareStatement("SELECT region_id,player_uuid FROM "+plugin.database().t("region_members")+" WHERE member_type='MEMBER'");
                    ResultSet result=query.executeQuery()){
                    while(result.next()){
                        Region r=regions.get(result.getString("region_id").toLowerCase(Locale.ROOT));
                        if(r!=null)try{r.members.add(UUID.fromString(result.getString("player_uuid")));}catch(IllegalArgumentException ignored){}
                    }
                }
                try(PreparedStatement query=connection.prepareStatement("SELECT region_id,flag_key,flag_value FROM "+plugin.database().t("region_flags"));
                    ResultSet result=query.executeQuery()){
                    while(result.next()){
                        Region r=regions.get(result.getString("region_id").toLowerCase(Locale.ROOT));
                        if(r!=null)r.flags.put(result.getString("flag_key").toUpperCase(Locale.ROOT),Boolean.parseBoolean(result.getString("flag_value")));
                    }
                }
                try(PreparedStatement query=connection.prepareStatement("SELECT region_id,display_name,icon_material FROM "+plugin.database().t("region_metadata"));
                    ResultSet result=query.executeQuery()){
                    while(result.next()){
                        Region r=regions.get(result.getString("region_id").toLowerCase(Locale.ROOT));
                        if(r!=null){r.displayName=result.getString("display_name");if(r.displayName==null||r.displayName.isBlank())r.displayName=r.id;
                            String icon=result.getString("icon_material");if(icon!=null&&Material.matchMaterial(icon)!=null)r.iconMaterial=icon;}
                    }
                }
                return true;
            });
        }catch(Exception failure){
            plugin.getLogger().log(java.util.logging.Level.SEVERE,"Could not load native regions from SQL; attempting the verified YAML fallback.",failure);
            return false;
        }
    }

    private boolean persistDatabase(boolean markMigration){
        try{
            plugin.database().sync(connection -> {
                boolean autoCommit=connection.getAutoCommit();
                connection.setAutoCommit(false);
                try{
                    try(var statement=connection.createStatement()){
                        statement.executeUpdate("DELETE FROM "+plugin.database().t("region_flags"));
                        statement.executeUpdate("DELETE FROM "+plugin.database().t("region_members"));
                        statement.executeUpdate("DELETE FROM "+plugin.database().t("regions"));
                    }
                    long now=System.currentTimeMillis();
                    try(PreparedStatement insert=connection.prepareStatement("INSERT INTO "+plugin.database().t("regions")
                            +" (region_id,world_uuid,world_name,owner_uuid,min_x,min_y,min_z,max_x,max_y,max_z,priority,parent_id,created_at,updated_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)");
                        PreparedStatement member=connection.prepareStatement("INSERT INTO "+plugin.database().t("region_members")
                            +" (region_id,player_uuid,member_type,added_by,added_at) VALUES (?,?,?,?,?)");
                        PreparedStatement flag=connection.prepareStatement("INSERT INTO "+plugin.database().t("region_flags")
                            +" (region_id,flag_key,value_type,flag_value,updated_by,updated_at) VALUES (?,?,?,?,?,?)");
                        PreparedStatement metadata=connection.prepareStatement("REPLACE INTO "+plugin.database().t("region_metadata")
                            +" (region_id,display_name,icon_material) VALUES (?,?,?)")){
                        for(Region r:regions.values()){
                            insert.setString(1,r.id);insert.setString(2,r.world.toString());insert.setString(3,r.worldName);
                            insert.setString(4,r.owner.toString());insert.setInt(5,r.minX);insert.setInt(6,r.minY);insert.setInt(7,r.minZ);
                            insert.setInt(8,r.maxX);insert.setInt(9,r.maxY);insert.setInt(10,r.maxZ);
                            insert.setInt(11,r.priority);insert.setString(12,r.parentId);insert.setLong(13,now);insert.setLong(14,now);insert.addBatch();
                            for(UUID uid:r.members){member.setString(1,r.id);member.setString(2,uid.toString());member.setString(3,"MEMBER");member.setString(4,r.owner.toString());member.setLong(5,now);member.addBatch();}
                            for(var entry:r.flags.entrySet()){flag.setString(1,r.id);flag.setString(2,entry.getKey().toUpperCase(Locale.ROOT));flag.setString(3,"BOOLEAN");flag.setString(4,Boolean.toString(entry.getValue()));flag.setString(5,r.owner.toString());flag.setLong(6,now);flag.addBatch();}
                            metadata.setString(1,r.id);metadata.setString(2,r.displayName==null?r.id:r.displayName);metadata.setString(3,r.iconMaterial==null?"MAP":r.iconMaterial);metadata.addBatch();
                        }
                        insert.executeBatch();member.executeBatch();flag.executeBatch();metadata.executeBatch();
                    }
                    if(markMigration)try(PreparedStatement marker=connection.prepareStatement(
                            plugin.database().insertIgnore()+" INTO "+plugin.database().t("module_migrations")+" (migration_key,completed_at,details) VALUES (?,?,?)")){
                        marker.setString(1,"regions-yaml-v1");marker.setLong(2,now);marker.setString(3,"Imported legacy regions.yml into native region tables");marker.executeUpdate();
                    }
                    connection.commit();
                }catch(SQLException | RuntimeException failure){connection.rollback();throw failure;}
                finally{connection.setAutoCommit(autoCommit);}
                return null;
            });
            return true;
        }catch(Exception failure){
            plugin.getLogger().log(java.util.logging.Level.SEVERE,"Could not persist native regions to SQL; the YAML backup was retained.",failure);
            return false;
        }
    }
    private void audit(Player actor,String action,String target,String severity,String details){
        plugin.database().run(connection->{
            try(PreparedStatement statement=connection.prepareStatement("INSERT INTO "+plugin.database().t("module_audit")
                    +" (audit_id,module_name,actor_uuid,action,target,severity,created_at,details) VALUES (?,?,?,?,?,?,?,?)")){
                statement.setString(1,UUID.randomUUID().toString());statement.setString(2,"WORLDGUARD");
                statement.setString(3,actor==null?null:actor.getUniqueId().toString());statement.setString(4,action);
                statement.setString(5,target);statement.setString(6,severity);statement.setLong(7,System.currentTimeMillis());
                statement.setString(8,details);statement.executeUpdate();
            }
        });
    }

    private void save(){
        yaml.set("regions",null);
        for(Region r:regions.values()){
            String p="regions."+r.id;yaml.set(p+".world",r.world.toString());yaml.set(p+".world-name",r.worldName);
            yaml.set(p+".owner",r.owner.toString());yaml.set(p+".display-name",r.displayName==null?r.id:r.displayName);yaml.set(p+".icon",r.iconMaterial==null?"MAP":r.iconMaterial);yaml.set(p+".min.x",r.minX);yaml.set(p+".min.y",r.minY);yaml.set(p+".min.z",r.minZ);
            yaml.set(p+".max.x",r.maxX);yaml.set(p+".max.y",r.maxY);yaml.set(p+".max.z",r.maxZ);
            yaml.set(p+".flags",null);for(var entry:r.flags.entrySet())yaml.set(p+".flags."+entry.getKey(),entry.getValue());
            yaml.set(p+".members",r.members.stream().map(UUID::toString).toList());
        }
        if(!persistDatabase(false)){plugin.getLogger().severe("Region changes were not written to YAML because the authoritative SQL save failed.");return;}
        if(!storageHealthy){plugin.getLogger().severe("regions.yml is not writable; native region data was saved to SQL only.");return;}
        File temporary=new File(file.getParentFile(),"regions.yml.tmp"),backup=new File(file.getParentFile(),"regions.yml.bak");
        try{
            yaml.save(temporary);YamlConfiguration verify=new YamlConfiguration();verify.load(temporary);
            if(file.isFile()&&file.length()>0)Files.copy(file.toPath(),backup.toPath(),StandardCopyOption.REPLACE_EXISTING);
            try{Files.move(temporary.toPath(),file.toPath(),StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}
            catch(AtomicMoveNotSupportedException ignored){Files.move(temporary.toPath(),file.toPath(),StandardCopyOption.REPLACE_EXISTING);}
        }catch(IOException|InvalidConfigurationException e){plugin.getLogger().severe("Could not safely save regions.yml: "+e.getMessage());if(temporary.exists())temporary.delete();}
    }

    private Region at(Location location){
        return regions.values().stream().filter(r->r.contains(location)).sorted(Comparator.comparingInt((Region r)->r.priority).reversed().thenComparingLong(Region::volume)).findFirst().orElse(null);
    }
    private boolean flag(Region region,String name){
        String key=name.toUpperCase(Locale.ROOT);
        Set<String> visited=new HashSet<>();
        Region current=region;
        while(current!=null&&visited.add(current.id.toLowerCase(Locale.ROOT))){
            if(current.flags.containsKey(key))return current.flags.get(key);
            current=current.parentId==null?null:regions.get(current.parentId.toLowerCase(Locale.ROOT));
        }
        return false;
    }
    public boolean hasFlagAt(Location location, String flag){ if(location==null)return false; return flag(at(location),flag);}

    public record RegionBounds(String id, UUID world, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) { }
    public RegionBounds regionBounds(String id) {
        if(id==null)return null;
        Region r=regions.get(id.toLowerCase(Locale.ROOT));
        return r==null?null:new RegionBounds(r.id,r.world,r.minX,r.minY,r.minZ,r.maxX,r.maxY,r.maxZ);
    }

    public record KothRegionInfo(String id, Location teleportLocation, boolean pvpArena, boolean capture) { }

    public List<KothRegionInfo> kothRegions() {
        List<KothRegionInfo> result = new ArrayList<>();
        for (Region r : regions.values()) {
            boolean arena = flag(r,"KOTH_PVP_ARENA");
            boolean capture = flag(r,"KOTH_CAPTURE");
            if (!arena && !capture) continue;
            World world = Bukkit.getWorld(r.world);
            if (world == null) continue;
            Location center = new Location(world,
                    (r.minX + r.maxX + 1) / 2.0,
                    Math.min(world.getMaxHeight() - 1, r.maxY + 1),
                    (r.minZ + r.maxZ + 1) / 2.0);
            result.add(new KothRegionInfo(r.id, center, arena, capture));
        }
        result.sort(Comparator.comparing(KothRegionInfo::id, String.CASE_INSENSITIVE_ORDER));
        return List.copyOf(result);
    }

    private boolean canManage(Player p,Region r){return admin(p)||r.owner.equals(p.getUniqueId());}
    private boolean admin(Player p){return p.hasPermission("Admin.Core")||p.hasPermission("Owner.Core");}
    private boolean builder(Player p){return admin(p)||p.hasPermission("Builder.Core");}
    private boolean opBypasses(Player p,Region r){return r!=null&&p.isOp()&&!flag(r,"OP-BYPASS");}
    private boolean canBuild(Player p,Region r){return r==null||opBypasses(p,r)||flag(r,"BUILD")||r.member(p.getUniqueId());}
    private boolean canInteract(Player p,Region r){return r==null||opBypasses(p,r)||flag(r,"INTERACT")||r.member(p.getUniqueId());}

    private boolean execute(CommandSender sender,String[] args){
        if(!(sender instanceof Player p)){Msg.err(sender,"Only players can use this command");return true;}
        if(args.length==0){MenuListener.open(p,new RegionList(p.getUniqueId(),false,0));return true;}
        switch(args[0].toLowerCase(Locale.ROOT)){
            case "admin"->{if(!admin(p))Msg.err(p,"Admin.Core required");else MenuListener.open(p,new RegionList(p.getUniqueId(),true,0));}
            case "flag"->{
                if(args.length!=4){Msg.info(p,"Usage: /lwg flag <region> <flag> <true|false>");return true;}
                Region r=regions.get(args[1].toLowerCase(Locale.ROOT));
                String flag=args[2].toUpperCase(Locale.ROOT);
                if(r==null||!FLAGS.contains(flag)){Msg.err(p,"Region or native flag not found");return true;}
                if(!canManage(p,r)){Msg.err(p,"You cannot manage this region");return true;}
                if(!args[3].equalsIgnoreCase("true")&&!args[3].equalsIgnoreCase("false")){Msg.err(p,"Flag value must be true or false");return true;}
                boolean enabled=Boolean.parseBoolean(args[3]);
                r.flags.put(flag,enabled);
                if(flag.equals("ARENA_RESET"))configureArenaReset(r,enabled);
                save();audit(p,"FLAG_UPDATE",r.id,"HIGH",flag+"="+enabled);
                Msg.ok(p,"Flag {flag} updated to {value}","flag",flag,"value",args[3].toLowerCase(Locale.ROOT));
            }
            case "rename"->{
                if(args.length<3){Msg.info(p,"Usage: /lwg rename <region> <new display name>");return true;}
                Region r=regions.get(args[1].toLowerCase(Locale.ROOT));
                if(r==null||!canManage(p,r)){Msg.err(p,"Region not found or access denied");return true;}
                String name=String.join(" ",Arrays.copyOfRange(args,2,args.length)).trim();
                if(name.length()<2||name.length()>48){Msg.err(p,"Display name must be 2-48 characters");return true;}
                r.displayName=name;save();audit(p,"REGION_RENAME",r.id,"MEDIUM",name);Msg.ok(p,"Region display name updated");
            }
            case "icon"->{
                if(args.length!=3){Msg.info(p,"Usage: /lwg icon <region> <material>");return true;}
                Region r=regions.get(args[1].toLowerCase(Locale.ROOT));Material material=Material.matchMaterial(args[2]);
                if(r==null||!canManage(p,r)||material==null||!material.isItem()){Msg.err(p,"Region, access or item material invalid");return true;}
                r.iconMaterial=material.name();save();audit(p,"REGION_ICON",r.id,"LOW",material.name());Msg.ok(p,"Region icon updated");
            }
            case "priority"->{
                if(args.length!=3){Msg.info(p,"Usage: /lwg priority <region> <number>");return true;}
                Region r=regions.get(args[1].toLowerCase(Locale.ROOT));
                if(r==null||!canManage(p,r)){Msg.err(p,"Region not found or access denied");return true;}
                try{r.priority=Integer.parseInt(args[2]);if(r.priority < -1000 || r.priority > 1000)throw new NumberFormatException();}
                catch(NumberFormatException invalid){Msg.err(p,"Priority must be between -1000 and 1000");return true;}
                save();audit(p,"PRIORITY_UPDATE",r.id,"MEDIUM",Integer.toString(r.priority));Msg.ok(p,"Region priority set to {priority}","priority",r.priority);
            }
            case "parent"->{
                if(args.length!=3){Msg.info(p,"Usage: /lwg parent <region> <parent|none>");return true;}
                Region r=regions.get(args[1].toLowerCase(Locale.ROOT));
                if(r==null||!canManage(p,r)){Msg.err(p,"Region not found or access denied");return true;}
                String parent=args[2].equalsIgnoreCase("none")?null:args[2].toLowerCase(Locale.ROOT);
                if(parent!=null){
                    Region target=regions.get(parent);
                    if(target==null||target==r||!target.world.equals(r.world)){Msg.err(p,"Parent must be another region in the same world");return true;}
                    Region cursor=target;Set<String> visited=new HashSet<>();
                    while(cursor!=null&&cursor.parentId!=null&&visited.add(cursor.id)){
                        if(cursor.parentId.equalsIgnoreCase(r.id)){Msg.err(p,"That parent would create an inheritance cycle");return true;}
                        cursor=regions.get(cursor.parentId.toLowerCase(Locale.ROOT));
                    }
                }
                r.parentId=parent;save();audit(p,"PARENT_UPDATE",r.id,"MEDIUM",parent==null?"none":parent);Msg.ok(p,parent==null?"Region parent cleared":"Region parent set to {parent}","parent",parent==null?"none":parent);
            }
            case "pos1"->{pos1.put(p.getUniqueId(),p.getLocation().getBlock().getLocation());Msg.ok(p,"Position 1 selected (native fallback)");}
            case "pos2"->{pos2.put(p.getUniqueId(),p.getLocation().getBlock().getLocation());Msg.ok(p,"Position 2 selected (native fallback)");}
            case "create"->{
                if(!builder(p)){Msg.err(p,"Builder.Core or staff permission required");return true;}
                if(args.length!=2){Msg.info(p,"Usage: /lwg create <id>");return true;}create(p,args[1]);
            }
            case "delete"->{
                if(args.length!=2){Msg.info(p,"Usage: /lwg delete <id>");return true;}
                Region r=regions.get(args[1].toLowerCase(Locale.ROOT));
                if(r==null){Msg.err(p,"Region not found");return true;}
                if(!canManage(p,r)){Msg.err(p,"You cannot manage this region");return true;}
                configureArenaReset(r,false);regions.remove(r.id.toLowerCase(Locale.ROOT));save();audit(p,"REGION_DELETE",r.id,"CRITICAL","Region deleted");Msg.ok(p,"Region {id} deleted","id",r.id);
            }
            case "addmember","removemember"->{
                if(args.length!=3){Msg.info(p,"Usage: /lwg "+args[0]+" <region> <player>");return true;}
                Region r=regions.get(args[1].toLowerCase(Locale.ROOT));Player target=Bukkit.getPlayerExact(args[2]);
                if(r==null||target==null){Msg.err(p,"Region or online player not found");return true;}
                if(!canManage(p,r)){Msg.err(p,"You cannot manage this region");return true;}
                if(args[0].equalsIgnoreCase("addmember")){r.members.add(target.getUniqueId());Msg.ok(p,"Added {player} to {region}","player",target.getName(),"region",r.id);}
                else{r.members.remove(target.getUniqueId());Msg.ok(p,"Removed {player} from {region}","player",target.getName(),"region",r.id);}
                save();audit(p,args[0].equalsIgnoreCase("addmember")?"MEMBER_ADD":"MEMBER_REMOVE",r.id,"MEDIUM",target.getUniqueId().toString());
            }
            default->Msg.info(p,"Usage: /lwg [pos1|pos2|create <id>|admin|delete <id>|addmember|removemember|flag|priority|parent]");
        }
        return true;
    }

    /** Reads WorldEdit selection reflectively so WorldEdit remains optional at runtime and compile time. */
    private Location[] readWorldEditSelection(Player player){
        if(Bukkit.getPluginManager().getPlugin("WorldEdit")==null)return null;
        try{
            Class<?> worldEditClass=Class.forName("com.sk89q.worldedit.WorldEdit");
            Object worldEdit=worldEditClass.getMethod("getInstance").invoke(null);
            Object manager=worldEditClass.getMethod("getSessionManager").invoke(worldEdit);
            Class<?> sessionManager=Class.forName("com.sk89q.worldedit.session.SessionManager");
            Class<?> adapter=Class.forName("com.sk89q.worldedit.bukkit.BukkitAdapter");
            Object localPlayer=adapter.getMethod("adapt",Player.class).invoke(null,player);
            Class<?> actor=Class.forName("com.sk89q.worldedit.extension.platform.Actor");
            Object session=sessionManager.getMethod("getIfPresent",actor).invoke(manager,localPlayer);
            if(session==null)return null;
            Class<?> weWorld=Class.forName("com.sk89q.worldedit.world.World");
            Object selectedWorld=adapter.getMethod("adapt",org.bukkit.World.class).invoke(null,player.getWorld());
            Class<?> localSession=Class.forName("com.sk89q.worldedit.LocalSession");
            Object selection=localSession.getMethod("getSelection",weWorld).invoke(session,selectedWorld);
            Class<?> region=Class.forName("com.sk89q.worldedit.regions.Region");
            Object min=region.getMethod("getMinimumPoint").invoke(selection);
            Object max=region.getMethod("getMaximumPoint").invoke(selection);
            Class<?> vector=Class.forName("com.sk89q.worldedit.math.BlockVector3");
            int minX=(int)vector.getMethod("getBlockX").invoke(min);
            int minY=(int)vector.getMethod("getBlockY").invoke(min);
            int minZ=(int)vector.getMethod("getBlockZ").invoke(min);
            int maxX=(int)vector.getMethod("getBlockX").invoke(max);
            int maxY=(int)vector.getMethod("getBlockY").invoke(max);
            int maxZ=(int)vector.getMethod("getBlockZ").invoke(max);
            Msg.ok(player,"WorldEdit selection detected and will be used.");
            return new Location[]{new Location(player.getWorld(),minX,minY,minZ),new Location(player.getWorld(),maxX,maxY,maxZ)};
        }catch(ClassNotFoundException missing){
            plugin.getLogger().warning("WorldEdit is present but its API could not be found.");
        }catch(ReflectiveOperationException|RuntimeException invalid){
            plugin.getLogger().fine("No usable WorldEdit selection for "+player.getName()+": "+invalid.getMessage());
        }
        return null;
    }
    private void create(Player p,String rawId){
        String id=rawId.toLowerCase(Locale.ROOT);
        if(!id.matches("[a-z0-9_-]{2,24}")){Msg.err(p,"Region IDs must be 2-24 characters (a-z, 0-9, _ or -)");return;}
        if(regions.containsKey(id)){Msg.err(p,"That region already exists");return;}
        Location[] we=readWorldEditSelection(p);
        Location first=we==null?pos1.get(p.getUniqueId()):we[0],second=we==null?pos2.get(p.getUniqueId()):we[1];
        if(first==null||second==null){
            if(Bukkit.getPluginManager().getPlugin("WorldEdit")!=null) Msg.err(p,"No complete WorldEdit selection found. Select two corners with the WorldEdit wand first, or use /lwg pos1 and /lwg pos2.");
            else Msg.err(p,"WorldEdit is not installed. Use /lwg pos1 and /lwg pos2, or install WorldEdit to select with its wand.");
            return;
        }
        if(!first.getWorld().equals(second.getWorld())){Msg.err(p,"Both positions must be in the same world");return;}
        long volume=(long)(Math.abs(first.getBlockX()-second.getBlockX())+1)*(Math.abs(first.getBlockY()-second.getBlockY())+1)*(Math.abs(first.getBlockZ()-second.getBlockZ())+1);
        long max=Math.max(1,plugin.getConfig().getLong("worldguard.max-region-blocks",1000000));
        if(volume>max){Msg.err(p,"Region exceeds the {limit} block limit","limit",max);return;}
        Region r=new Region();r.id=id;r.displayName=rawId;r.iconMaterial="MAP";r.world=first.getWorld().getUID();r.worldName=first.getWorld().getName();r.owner=p.getUniqueId();
        r.minX=Math.min(first.getBlockX(),second.getBlockX());r.minY=Math.min(first.getBlockY(),second.getBlockY());r.minZ=Math.min(first.getBlockZ(),second.getBlockZ());
        r.maxX=Math.max(first.getBlockX(),second.getBlockX());r.maxY=Math.max(first.getBlockY(),second.getBlockY());r.maxZ=Math.max(first.getBlockZ(),second.getBlockZ());
        regions.put(id,r);save();audit(p,"REGION_CREATE",r.id,"HIGH","Blocks="+volume);Msg.ok(p,"Region {id} created ({blocks} blocks)","id",id,"blocks",volume);
        MenuListener.open(p,new RegionDetail(id,false));
    }

    @EventHandler(ignoreCancelled=true) public void onBreak(BlockBreakEvent e){
        Block block=e.getBlock();
        Region r=at(block.getLocation());
        if(!canBuild(e.getPlayer(),r)){e.setCancelled(true);return;}
        if(block.getType()==Material.OBSIDIAN){
            String key=crystalKey(block.getLocation());
            if(crystalExpiry.remove(key)!=null)saveCrystalExpiry();
        }
    }
    @EventHandler(ignoreCancelled=true) public void onPlace(BlockPlaceEvent e){
        Region r=at(e.getBlock().getLocation());
        boolean crystalObsidian=r!=null&&e.getBlockPlaced().getType()==Material.OBSIDIAN&&flag(r,"CRYSTAL");
        if(!canBuild(e.getPlayer(),r)&&!crystalObsidian){e.setCancelled(true);return;}
        if(crystalObsidian){
            Location placed=e.getBlockPlaced().getLocation().clone();
            String key=crystalKey(placed);
            crystalExpiry.put(key,System.currentTimeMillis()+180_000L);
            saveCrystalExpiry();
        }
    }

    private File arenaSnapshotFile(String key){
        return new File(new File(plugin.getDataFolder(),"worldguard/arena-resets"),key+".dat");
    }

    private boolean writeArenaBaseline(String key,Map<String,String> baseline){
        File target=arenaSnapshotFile(key);
        File parent=target.getParentFile();
        if(!parent.exists()&&!parent.mkdirs())return false;
        File temporary=new File(parent,target.getName()+".tmp");
        try(var output=new java.io.DataOutputStream(new java.util.zip.GZIPOutputStream(new java.io.BufferedOutputStream(new java.io.FileOutputStream(temporary))))){
            output.writeInt(baseline.size());
            for(var entry:baseline.entrySet()){output.writeUTF(entry.getKey());output.writeUTF(entry.getValue());}
        }catch(IOException failure){plugin.getLogger().warning("Could not write arena snapshot "+key+": "+failure.getMessage());temporary.delete();return false;}
        try{
            try{Files.move(temporary.toPath(),target.toPath(),StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}
            catch(AtomicMoveNotSupportedException ignored){Files.move(temporary.toPath(),target.toPath(),StandardCopyOption.REPLACE_EXISTING);}
            return true;
        }catch(IOException failure){plugin.getLogger().warning("Could not finalize arena snapshot "+key+": "+failure.getMessage());temporary.delete();return false;}
    }

    private Map<String,String> readArenaBaseline(String key){
        File file=arenaSnapshotFile(key);
        if(!file.isFile())return null;
        try(var input=new java.io.DataInputStream(new java.util.zip.GZIPInputStream(new java.io.BufferedInputStream(new java.io.FileInputStream(file))))){
            int count=input.readInt();
            if(count<0||count>1_000_000)throw new IOException("Invalid snapshot block count: "+count);
            Map<String,String> baseline=new LinkedHashMap<>();
            for(int i=0;i<count;i++)baseline.put(input.readUTF(),input.readUTF());
            return baseline;
        }catch(IOException failure){plugin.getLogger().log(java.util.logging.Level.SEVERE,"Could not read arena snapshot "+key,failure);return null;}
    }

    private static String crystalKey(Location location){
        return location.getWorld().getUID()+":"+location.getBlockX()+":"+location.getBlockY()+":"+location.getBlockZ();
    }

    private void loadCrystalExpiry(){
        if(!crystalExpiryFile.isFile())return;
        try{
            crystalExpiryYaml.load(crystalExpiryFile);
            ConfigurationSection section=crystalExpiryYaml.getConfigurationSection("blocks");
            if(section!=null)for(String key:section.getKeys(false)){
                long due=section.getLong(key,0L);
                if(due>0L)crystalExpiry.put(key,due);
            }
        }catch(IOException|InvalidConfigurationException failure){
            plugin.getLogger().warning("Could not load pending Crystal obsidian removals: "+failure.getMessage());
        }
    }

    private void saveCrystalExpiry(){
        crystalExpiryYaml.set("blocks",null);
        for(var entry:crystalExpiry.entrySet())crystalExpiryYaml.set("blocks."+entry.getKey(),entry.getValue());
        try{
            File temporary=new File(plugin.getDataFolder(),"worldguard-crystal-expiry.yml.tmp");
            crystalExpiryYaml.save(temporary);
            try{Files.move(temporary.toPath(),crystalExpiryFile.toPath(),StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}
            catch(AtomicMoveNotSupportedException ignored){Files.move(temporary.toPath(),crystalExpiryFile.toPath(),StandardCopyOption.REPLACE_EXISTING);}
        }catch(IOException failure){plugin.getLogger().warning("Could not save pending Crystal obsidian removals: "+failure.getMessage());}
    }

    private void processCrystalExpiry(){
        long now=System.currentTimeMillis();
        boolean changed=false;
        Iterator<Map.Entry<String,Long>> iterator=crystalExpiry.entrySet().iterator();
        while(iterator.hasNext()){
            var entry=iterator.next();
            if(entry.getValue()>now)continue;
            String[] parts=entry.getKey().split(":");
            try{
                World world=Bukkit.getWorld(UUID.fromString(parts[0]));
                if(world==null)continue;
                int x=Integer.parseInt(parts[1]),y=Integer.parseInt(parts[2]),z=Integer.parseInt(parts[3]);
                var block=world.getBlockAt(x,y,z);
                if(block.getType()==Material.OBSIDIAN)block.setType(Material.AIR,false);
            }catch(RuntimeException invalid){plugin.getLogger().warning("Discarding invalid Crystal expiry entry: "+entry.getKey());}
            iterator.remove();changed=true;
        }
        if(changed)saveCrystalExpiry();
    }

    private String snapshotArenaBlock(org.bukkit.block.Block block) {
        YamlConfiguration data = new YamlConfiguration();
        data.set("block-data", block.getBlockData().getAsString());
        var state = block.getState();
        if (state instanceof Container container) data.set("container", Arrays.asList(container.getInventory().getContents()));
        if (state instanceof Sign sign) data.set("sign-lines", Arrays.asList(sign.getLines()));
        return Base64.getEncoder().encodeToString(data.saveToString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private void restoreArenaBlock(org.bukkit.block.Block block, String snapshot) {
        try {
            if (snapshot.startsWith("minecraft:") || snapshot.equals("air") || snapshot.startsWith("air[")) {
                block.setBlockData(Bukkit.createBlockData(snapshot), false);
                return;
            }
            YamlConfiguration data = new YamlConfiguration();
            data.loadFromString(new String(Base64.getDecoder().decode(snapshot), java.nio.charset.StandardCharsets.UTF_8));
            String blockData = data.getString("block-data");
            if (blockData == null) return;
            block.setBlockData(Bukkit.createBlockData(blockData), false);
            var state = block.getState();
            if (state instanceof Container container && data.isList("container")) {
                List<?> raw = data.getList("container");
                ItemStack[] contents = new ItemStack[container.getInventory().getSize()];
                for (int i = 0; i < Math.min(contents.length, raw.size()); i++) {
                    Object value = raw.get(i);
                    if (value instanceof ItemStack item) contents[i] = item;
                }
                container.getInventory().setContents(contents);
                state.update(true, false);
            }
            if (state instanceof Sign sign && data.isList("sign-lines")) {
                List<?> lines = data.getList("sign-lines");
                for (int i = 0; i < Math.min(4, lines.size()); i++) sign.setLine(i, String.valueOf(lines.get(i)));
                sign.update(true, false);
            }
        } catch (Exception failure) {
            plugin.getLogger().warning("Could not restore an arena block: " + failure.getMessage());
        }
    }

    private void configureArenaReset(Region region,boolean enabled){
        String key=region.id.toLowerCase(Locale.ROOT);
        org.bukkit.scheduler.BukkitTask old=arenaResetTasks.remove(key);
        if(old!=null)old.cancel();
        if(!enabled){
            arenaBaselines.remove(key);
            File snapshot=arenaSnapshotFile(key);
            if(snapshot.isFile())snapshot.delete();
            return;
        }
        long limit=Math.max(1L,plugin.getConfig().getLong("worldguard.arena-reset-max-blocks",100000L));
        if(region.volume()>limit){
            plugin.getLogger().warning("ARENA_RESET was enabled for "+region.id+" but the region exceeds worldguard.arena-reset-max-blocks ("+limit+").");
            region.flags.put("ARENA_RESET",false);
            return;
        }
        World world=Bukkit.getWorld(region.world);
        if(world==null){region.flags.put("ARENA_RESET",false);return;}
        Map<String,String> baseline=readArenaBaseline(key);
        if(baseline==null){
            baseline=new LinkedHashMap<>();
            for(int x=region.minX;x<=region.maxX;x++)for(int y=region.minY;y<=region.maxY;y++)for(int z=region.minZ;z<=region.maxZ;z++){
            var block=world.getBlockAt(x,y,z);
            baseline.put(x+","+y+","+z,snapshotArenaBlock(block));
            }
            if(!writeArenaBaseline(key,baseline)){
                plugin.getLogger().warning("Could not persist the original arena snapshot for "+region.id+"; ARENA_RESET has been disabled.");
                region.flags.put("ARENA_RESET",false);
                return;
            }
        }
        arenaBaselines.put(key,baseline);
        long minutes=Math.max(1L,plugin.getConfig().getLong("worldguard.arena-reset-minutes",5L));
        long period=minutes*60L*20L;
        arenaResetTasks.put(key,Bukkit.getScheduler().runTaskTimer(plugin,()->{
            Region current=regions.get(key);
            World currentWorld=current==null?null:Bukkit.getWorld(current.world);
            Map<String,String> saved=arenaBaselines.get(key);
            if(current==null||currentWorld==null||saved==null||!flag(current,"ARENA_RESET")){
                org.bukkit.scheduler.BukkitTask task=arenaResetTasks.remove(key);if(task!=null)task.cancel();return;
            }
            for(var entry:saved.entrySet()){
                String[] xyz=entry.getKey().split(",");
                int x=Integer.parseInt(xyz[0]),y=Integer.parseInt(xyz[1]),z=Integer.parseInt(xyz[2]);
                restoreArenaBlock(currentWorld.getBlockAt(x,y,z),entry.getValue());
            }
        },period,period));
    }
    @EventHandler(ignoreCancelled=true) public void onInteract(PlayerInteractEvent e){
        if(e.getClickedBlock()==null||e.getItem()!=null&&e.getItem().getType()==Material.WOODEN_AXE)return;
        Region r=at(e.getClickedBlock().getLocation());
        if(e.getPlayer().isGliding()&&e.getItem()!=null&&e.getItem().getType()==Material.FIREWORK_ROCKET&&r!=null&&flag(r,"ELYTRA-ROCKETS")&&!opBypasses(e.getPlayer(),r)){e.setCancelled(true);Msg.err(e.getPlayer(),"Firework boosting is disabled in this region");return;}
        if(!canInteract(e.getPlayer(),r))e.setCancelled(true);
    }
    @EventHandler(ignoreCancelled=true) public void onPearlTeleport(org.bukkit.event.player.PlayerTeleportEvent e){
        if(e.getCause()!=org.bukkit.event.player.PlayerTeleportEvent.TeleportCause.ENDER_PEARL)return;
        Region from=at(e.getFrom()),to=at(e.getTo());
        if((from!=null&&flag(from,"ENDER-PEARLS")&&!opBypasses(e.getPlayer(),from))||(to!=null&&flag(to,"ENDER-PEARLS")&&!opBypasses(e.getPlayer(),to)))e.setCancelled(true);
    }
    @EventHandler(ignoreCancelled=true) public void onItemDrop(org.bukkit.event.player.PlayerDropItemEvent e){
        Region r=at(e.getPlayer().getLocation());if(r!=null&&flag(r,"ITEM-DROP")&&!opBypasses(e.getPlayer(),r))e.setCancelled(true);
    }
    @EventHandler(ignoreCancelled=true) public void onItemPickup(org.bukkit.event.entity.EntityPickupItemEvent e){
        if(e.getEntity() instanceof Player p){Region r=at(e.getItem().getLocation());if(r!=null&&flag(r,"ITEM-PICKUP")&&!opBypasses(p,r))e.setCancelled(true);}
    }
    @EventHandler(ignoreCancelled=true) public void onLiquidFlow(BlockFromToEvent e){
        Region source=at(e.getBlock().getLocation()),target=at(e.getToBlock().getLocation());
        if((source!=null&&!flag(source,"LIQUID-FLOW"))||(target!=null&&!flag(target,"LIQUID-FLOW")))e.setCancelled(true);
    }
    @EventHandler(ignoreCancelled=true) public void onBurn(BlockBurnEvent e){
        Region r=at(e.getBlock().getLocation());if(r!=null&&!flag(r,"FIRE-SPREAD"))e.setCancelled(true);
    }
    @EventHandler(ignoreCancelled=true) public void onIgnite(BlockIgniteEvent e){
        Region r=at(e.getBlock().getLocation());if(r!=null&&!flag(r,"FIRE-SPREAD"))e.setCancelled(true);
    }
    @EventHandler public void onRedstone(BlockRedstoneEvent e){
        Region r=at(e.getBlock().getLocation());if(r!=null&&!flag(r,"REDSTONE"))e.setNewCurrent(e.getOldCurrent());
    }
    @EventHandler(ignoreCancelled=true) public void onBucketEmpty(PlayerBucketEmptyEvent e){
        Region r=at(e.getBlockClicked().getRelative(e.getBlockFace()).getLocation());
        if(!canBuild(e.getPlayer(),r))e.setCancelled(true);
    }
    @EventHandler(ignoreCancelled=true) public void onBucketFill(PlayerBucketFillEvent e){
        Region r=at(e.getBlockClicked().getLocation());if(!canBuild(e.getPlayer(),r))e.setCancelled(true);
    }
    @EventHandler(ignoreCancelled=true) public void onEntityChangeBlock(EntityChangeBlockEvent e){
        Region r=at(e.getBlock().getLocation());if(r!=null&&!flag(r,"BUILD"))e.setCancelled(true);
    }
    @EventHandler(ignoreCancelled=true) public void onPlayerEntityInteract(PlayerInteractEntityEvent e){
        Region r=at(e.getRightClicked().getLocation());if(r!=null&&!flag(r,"ENTITY-INTERACT")&&!r.member(e.getPlayer().getUniqueId()))e.setCancelled(true);
    }
    @EventHandler(ignoreCancelled=true) public void onHangingPlace(HangingPlaceEvent e){
        Region r=at(e.getBlock().getLocation());if(!canBuild(e.getPlayer(),r))e.setCancelled(true);
    }
    @EventHandler(ignoreCancelled=true) public void onHangingBreak(HangingBreakByEntityEvent e){
        if(e.getRemover() instanceof Player player){Region r=at(e.getEntity().getLocation());if(!canBuild(player,r))e.setCancelled(true);}
    }
    @EventHandler(ignoreCancelled=true) public void onVehicleDamage(VehicleDamageEvent e){
        if(e.getAttacker() instanceof Player player){Region r=at(e.getVehicle().getLocation());if(r!=null&&!flag(r,"VEHICLE-USE")&&!r.member(player.getUniqueId()))e.setCancelled(true);}
    }
    @EventHandler(ignoreCancelled=true) public void onVehicleDestroy(VehicleDestroyEvent e){
        if(e.getAttacker() instanceof Player player){Region r=at(e.getVehicle().getLocation());if(r!=null&&!flag(r,"VEHICLE-USE")&&!r.member(player.getUniqueId()))e.setCancelled(true);}
    }
    @EventHandler(ignoreCancelled=true) public void onVehicleEnter(VehicleEnterEvent e){
        if(e.getEntered() instanceof Player player){Region r=at(e.getVehicle().getLocation());if(r!=null&&!flag(r,"VEHICLE-USE")&&!r.member(player.getUniqueId()))e.setCancelled(true);}
    }
    @EventHandler(ignoreCancelled=true) public void onPistonExtend(BlockPistonExtendEvent e){
        if(e.getBlocks().stream().anyMatch(block->{Region r=at(block.getLocation());return r!=null&&!flag(r,"PISTON");}))e.setCancelled(true);
    }
    @EventHandler(ignoreCancelled=true) public void onPistonRetract(BlockPistonRetractEvent e){
        if(e.getBlocks().stream().anyMatch(block->{Region r=at(block.getLocation());return r!=null&&!flag(r,"PISTON");}))e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled=true) public void onPvp(EntityDamageByEntityEvent e){
        if(!(e.getEntity() instanceof Player))return;
        Player attacker=null;
        if(e.getDamager() instanceof Player p)attacker=p;
        else if(e.getDamager() instanceof Projectile projectile){
            ProjectileSource shooter=projectile.getShooter();if(shooter instanceof Player p)attacker=p;
        }
        if(attacker==null)return;
        Location target=e.getEntity().getLocation();
        Region targetRegion=at(target),attackerRegion=at(attacker.getLocation());
        boolean targetAllowed=targetRegion==null||opBypasses((Player)e.getEntity(),targetRegion)||flag(targetRegion,"PVP")||flag(targetRegion,"KOTH_PVP_ARENA");
        boolean attackerAllowed=attackerRegion==null||opBypasses(attacker,attackerRegion)||flag(attackerRegion,"PVP")||flag(attackerRegion,"KOTH_PVP_ARENA");
        if(!targetAllowed||!attackerAllowed)e.setCancelled(true);
    }
    @EventHandler(ignoreCancelled=true) public void onEntityExplosion(EntityExplodeEvent e){
        e.blockList().removeIf(b->{Region r=at(b.getLocation());return r!=null&&!flag(r,"EXPLOSIONS");});
    }
    @EventHandler(ignoreCancelled=true) public void onBlockExplosion(BlockExplodeEvent e){
        e.blockList().removeIf(b->{Region r=at(b.getLocation());return r!=null&&!flag(r,"EXPLOSIONS");});
    }
    @EventHandler(ignoreCancelled=true) public void onSpawn(CreatureSpawnEvent e){
        if(e.getSpawnReason()!=CreatureSpawnEvent.SpawnReason.NATURAL)return;
        Region r=at(e.getLocation());if(r!=null&&!flag(r,"MOB-SPAWNING"))e.setCancelled(true);
    }

    private final class RegionList extends Menu{
        final UUID viewer;final boolean all;final int page;final List<Region> list;
        RegionList(UUID viewer,boolean all,int page){this.viewer=viewer;this.all=all;this.page=page;
            list=regions.values().stream().filter(r->all||r.owner.equals(viewer)).sorted(Comparator.comparing(r->r.id)).toList();
            create(54,Component.text(Msg.sc(all?"Region Administration":"Your Regions")));
            int start=page*45;for(int i=start;i<Math.min(start+45,list.size());i++){Region r=list.get(i);
                Material icon=Material.matchMaterial(r.iconMaterial==null?"MAP":r.iconMaterial);if(icon==null)icon=Material.MAP;
                set(i-start,Items.of(icon,Msg.sc(r.displayName==null?r.id:r.displayName),Msg.NEUTRAL,"ID: "+r.id,"World: "+r.worldName,"Size: "+r.volume()+" blocks","Owner: "+Bukkit.getOfflinePlayer(r.owner).getName(),"Click to manage"));}
            if(page>0)set(45,Items.of(Material.ARROW,"Previous",Msg.NEUTRAL));set(49,Items.of(Material.BARRIER,"Close",Msg.NEUTRAL));
            if((page+1)*45<list.size())set(53,Items.of(Material.ARROW,"Next",Msg.NEUTRAL));
        }
        @Override public void onClick(Player p,org.bukkit.event.inventory.InventoryClickEvent e){
            if(!isTop(e))return;int s=e.getRawSlot();if(s==49){p.closeInventory();return;}
            if(s==45&&page>0){MenuListener.open(p,new RegionList(viewer,all,page-1));return;}
            if(s==53&&(page+1)*45<list.size()){MenuListener.open(p,new RegionList(viewer,all,page+1));return;}
            if(s>=0&&s<45&&page*45+s<list.size()){Region r=list.get(page*45+s);if(canManage(p,r))MenuListener.open(p,new RegionDetail(r.id,all));else Msg.err(p,"You cannot manage this region");}
        }
    }

    private final class RegionDetail extends Menu{
        final String id;final boolean adminView;
        RegionDetail(String id,boolean adminView){this.id=id;this.adminView=adminView;create(54,Component.text(Msg.sc("Region Settings")));render();}
        void render(){Region r=regions.get(id);if(r==null)return;
            for(int i=0;i<FLAGS.size();i++){String flag=FLAGS.get(i);boolean on=flag(r,flag);Material flagIcon=FLAG_ICONS.getOrDefault(flag,Material.PAPER);
                set(i,Items.of(flagIcon,Msg.sc(flagName(flag)),on?Msg.GOOD:Msg.BAD,"Status: "+(on?"Enabled":"Disabled"),"Click to toggle"));}
            Material icon=Material.matchMaterial(r.iconMaterial==null?"MAP":r.iconMaterial);if(icon==null)icon=Material.MAP;
            set(45,Items.of(icon,Msg.sc("Region Icon"),Msg.NEUTRAL,"Current: "+r.iconMaterial,"Click to cycle icon","Command: /lwg icon "+r.id+" <material>"));
            set(46,Items.of(Material.NAME_TAG,Msg.sc("Rename Region"),Msg.NEUTRAL,"Current: "+Msg.sc(r.displayName==null?r.id:r.displayName),"Command: /lwg rename "+r.id+" <name>"));
            set(47,Items.of(Material.PLAYER_HEAD,Msg.sc("Members"),Msg.NEUTRAL,r.members.size()+" members","View or remove members"));
            set(49,Items.of(Material.TNT,Msg.sc("Delete Region"),Msg.BAD,"Permanently delete this region"));
            set(53,Items.of(Material.ARROW,Msg.sc("Back"),Msg.NEUTRAL));
        }
        @Override public void onClick(Player p,org.bukkit.event.inventory.InventoryClickEvent e){if(!isTop(e))return;Region r=regions.get(id);if(r==null){p.closeInventory();return;}
            if(!canManage(p,r)){p.closeInventory();return;}int s=e.getRawSlot();if(s==53){MenuListener.open(p,new RegionList(p.getUniqueId(),adminView,0));return;}
            if(s==47){MenuListener.open(p,new RegionMembers(id,adminView));return;}
            if(s==49){configureArenaReset(r,false);regions.remove(id);save();audit(p,"REGION_DELETE",r.id,"CRITICAL","Region deleted via GUI");Msg.ok(p,"Region {id} deleted","id",id);MenuListener.open(p,new RegionList(p.getUniqueId(),adminView,0));return;}
            if(s==45){List<Material> icons=List.of(Material.MAP,Material.BEACON,Material.NETHER_STAR,Material.DIAMOND_BLOCK,Material.GOLD_BLOCK,Material.EMERALD_BLOCK,Material.ENDER_CHEST,Material.CHEST,Material.SHIELD,Material.IRON_SWORD,Material.TNT,Material.CRYING_OBSIDIAN);int next=0;for(int i=0;i<icons.size();i++)if(icons.get(i).name().equalsIgnoreCase(r.iconMaterial)){next=(i+1)%icons.size();break;}r.iconMaterial=icons.get(next).name();save();audit(p,"REGION_ICON",r.id,"LOW",r.iconMaterial);render();return;}
            if(s==46){Msg.info(p,"Rename with: /lwg rename "+r.id+" <new name>");return;}
            if(s>=0&&s<FLAGS.size()){String flag=FLAGS.get(s);boolean enabled=!flag(r,flag);r.flags.put(flag,enabled);if(flag.equals("ARENA_RESET"))configureArenaReset(r,enabled);save();audit(p,"FLAG_UPDATE",r.id,"HIGH",flag+"="+enabled);render();return;}
        }
    }

    private final class RegionMembers extends Menu{
        final String id;final boolean adminView;
        RegionMembers(String id,boolean adminView){this.id=id;this.adminView=adminView;create(54,Component.text(Msg.sc("Region Members")));Region r=regions.get(id);if(r==null)return;
            int slot=0;for(UUID uid:r.members){OfflinePlayer op=Bukkit.getOfflinePlayer(uid);String name=op.getName()==null?uid.toString().substring(0,8):op.getName();
                set(slot++,Items.head(op,Component.text(name),List.of(Component.text(Msg.sc("Click to remove"),Msg.NEUTRAL))));if(slot>=45)break;}
            set(49,Items.of(Material.ARROW,"Back",Msg.NEUTRAL));}
        @Override public void onClick(Player p,org.bukkit.event.inventory.InventoryClickEvent e){if(!isTop(e))return;Region r=regions.get(id);if(r==null||!canManage(p,r)){p.closeInventory();return;}
            int s=e.getRawSlot();if(s==49){MenuListener.open(p,new RegionDetail(id,adminView));return;}
            if(s>=0&&s<45&&s<r.members.size()){UUID uid=new ArrayList<>(r.members).get(s);r.members.remove(uid);save();Msg.info(p,"Region member removed");MenuListener.open(p,new RegionMembers(id,adminView));}}
    }

    private static String flagName(String flag){return switch(flag){case "OP-BYPASS"->"OP Bypass";case "ELYTRA-ROCKETS"->"Block Elytra Rockets";case "ENDER-PEARLS"->"Block Ender Pearls";case "ITEM-DROP"->"Block Item Drop";case "ITEM-PICKUP"->"Block Item Pickup";case "MOB-SPAWNING"->"Mob Spawning";case "CRYSTAL"->"Crystal (3m Obsidian)";case "ARENA_RESET"->"Arena Reset";case "LIQUID-FLOW"->"Liquid Flow";case "REDSTONE"->"Redstone";case "FIRE-SPREAD"->"Fire Spread";case "ENTITY-INTERACT"->"Entity Interact";case "VEHICLE-USE"->"Vehicle Use";case "PISTON"->"Piston Movement";case "HANGING"->"Hanging Entities";case "KOTH_PVP_ARENA"->"KOTH PvP Arena";case "KOTH_CAPTURE"->"KOTH Capture";case "PVP"->"PvP";case "BUILD"->"Build";case "INTERACT"->"Interact";case "EXPLOSIONS"->"Explosions";default->flag;};}
}
