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
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
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
    private static final List<String> FLAGS = List.of("build","interact","pvp","explosions","mob-spawning","KOTH_PVP_ARENA","KOTH_CAPTURE","COMBAT_FORCEFIELD","CRYSTAL","ARENA_RESET");
    private static final Map<String,Material> FLAG_ICONS = Map.of(
            "build",Material.BRICKS,"interact",Material.LEVER,"pvp",Material.IRON_SWORD,
            "explosions",Material.TNT,"mob-spawning",Material.SPAWNER,
            "KOTH_PVP_ARENA",Material.IRON_SWORD,"KOTH_CAPTURE",Material.BEACON,"COMBAT_FORCEFIELD",Material.RED_STAINED_GLASS,
            "CRYSTAL",Material.OBSIDIAN,"ARENA_RESET",Material.REPEATER);
    private static final class Region {
        String id,worldName; UUID world,owner;
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
            r.minX=s.getInt("min.x");r.minY=s.getInt("min.y");r.minZ=s.getInt("min.z");
            r.maxX=s.getInt("max.x");r.maxY=s.getInt("max.y");r.maxZ=s.getInt("max.z");
            for(String flag:FLAGS)if(s.contains("flags."+flag))r.flags.put(flag,s.getBoolean("flags."+flag));
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
                        r.worldName=result.getString("world_name");r.owner=UUID.fromString(result.getString("owner_uuid"));
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
                        if(r!=null)r.flags.put(result.getString("flag_key"),Boolean.parseBoolean(result.getString("flag_value")));
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
                            +" (region_id,flag_key,value_type,flag_value,updated_by,updated_at) VALUES (?,?,?,?,?,?)")){
                        for(Region r:regions.values()){
                            insert.setString(1,r.id);insert.setString(2,r.world.toString());insert.setString(3,r.worldName);
                            insert.setString(4,r.owner.toString());insert.setInt(5,r.minX);insert.setInt(6,r.minY);insert.setInt(7,r.minZ);
                            insert.setInt(8,r.maxX);insert.setInt(9,r.maxY);insert.setInt(10,r.maxZ);
                            insert.setInt(11,r.priority);insert.setString(12,r.parentId);insert.setLong(13,now);insert.setLong(14,now);insert.addBatch();
                            for(UUID uid:r.members){member.setString(1,r.id);member.setString(2,uid.toString());member.setString(3,"MEMBER");member.setString(4,r.owner.toString());member.setLong(5,now);member.addBatch();}
                            for(var entry:r.flags.entrySet()){flag.setString(1,r.id);flag.setString(2,entry.getKey());flag.setString(3,"BOOLEAN");flag.setString(4,Boolean.toString(entry.getValue()));flag.setString(5,r.owner.toString());flag.setLong(6,now);flag.addBatch();}
                        }
                        insert.executeBatch();member.executeBatch();flag.executeBatch();
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
    private void save(){
        yaml.set("regions",null);
        for(Region r:regions.values()){
            String p="regions."+r.id;yaml.set(p+".world",r.world.toString());yaml.set(p+".world-name",r.worldName);
            yaml.set(p+".owner",r.owner.toString());yaml.set(p+".min.x",r.minX);yaml.set(p+".min.y",r.minY);yaml.set(p+".min.z",r.minZ);
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
    private boolean canBuild(Player p,Region r){return r==null||flag(r,"build")||r.member(p.getUniqueId());}
    private boolean canInteract(Player p,Region r){return r==null||flag(r,"interact")||r.member(p.getUniqueId());}

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
                save();
                Msg.ok(p,"Flag {flag} updated to {value}","flag",flag,"value",args[3].toLowerCase(Locale.ROOT));
            }
            case "priority"->{
                if(args.length!=3){Msg.info(p,"Usage: /lwg priority <region> <number>");return true;}
                Region r=regions.get(args[1].toLowerCase(Locale.ROOT));
                if(r==null||!canManage(p,r)){Msg.err(p,"Region not found or access denied");return true;}
                try{r.priority=Integer.parseInt(args[2]);if(r.priority < -1000 || r.priority > 1000)throw new NumberFormatException();}
                catch(NumberFormatException invalid){Msg.err(p,"Priority must be between -1000 and 1000");return true;}
                save();Msg.ok(p,"Region priority set to {priority}","priority",r.priority);
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
                r.parentId=parent;save();Msg.ok(p,parent==null?"Region parent cleared":"Region parent set to {parent}","parent",parent==null?"none":parent);
            }
            case "pos1"->{pos1.put(p.getUniqueId(),p.getLocation().getBlock().getLocation());Msg.ok(p,"WorldGuard position 1 selected");}
            case "pos2"->{pos2.put(p.getUniqueId(),p.getLocation().getBlock().getLocation());Msg.ok(p,"WorldGuard position 2 selected");}
            case "create"->{
                if(!builder(p)){Msg.err(p,"Builder.Core or staff permission required");return true;}
                if(args.length!=2){Msg.info(p,"Usage: /lwg create <id>");return true;}create(p,args[1]);
            }
            case "delete"->{
                if(args.length!=2){Msg.info(p,"Usage: /lwg delete <id>");return true;}
                Region r=regions.get(args[1].toLowerCase(Locale.ROOT));
                if(r==null){Msg.err(p,"Region not found");return true;}
                if(!canManage(p,r)){Msg.err(p,"You cannot manage this region");return true;}
                configureArenaReset(r,false);regions.remove(r.id.toLowerCase(Locale.ROOT));save();Msg.ok(p,"Region {id} deleted","id",r.id);
            }
            case "addmember","removemember"->{
                if(args.length!=3){Msg.info(p,"Usage: /lwg "+args[0]+" <region> <player>");return true;}
                Region r=regions.get(args[1].toLowerCase(Locale.ROOT));Player target=Bukkit.getPlayerExact(args[2]);
                if(r==null||target==null){Msg.err(p,"Region or online player not found");return true;}
                if(!canManage(p,r)){Msg.err(p,"You cannot manage this region");return true;}
                if(args[0].equalsIgnoreCase("addmember")){r.members.add(target.getUniqueId());Msg.ok(p,"Added {player} to {region}","player",target.getName(),"region",r.id);}
                else{r.members.remove(target.getUniqueId());Msg.ok(p,"Removed {player} from {region}","player",target.getName(),"region",r.id);}
                save();
            }
            default->Msg.info(p,"Usage: /lwg [pos1|pos2|create <id>|admin|delete <id>|addmember|removemember|flag|priority|parent]");
        }
        return true;
    }

    private void create(Player p,String rawId){
        String id=rawId.toLowerCase(Locale.ROOT);
        if(!id.matches("[a-z0-9_-]{2,24}")){Msg.err(p,"Region IDs must be 2-24 characters (a-z, 0-9, _ or -)");return;}
        if(regions.containsKey(id)){Msg.err(p,"That region already exists");return;}
        Location first=pos1.get(p.getUniqueId()),second=pos2.get(p.getUniqueId());
        if(first==null||second==null){Msg.err(p,"Select both positions with /lwg pos1 and /lwg pos2 first");return;}
        if(!first.getWorld().equals(second.getWorld())){Msg.err(p,"Both positions must be in the same world");return;}
        long volume=(long)(Math.abs(first.getBlockX()-second.getBlockX())+1)*(Math.abs(first.getBlockY()-second.getBlockY())+1)*(Math.abs(first.getBlockZ()-second.getBlockZ())+1);
        long max=Math.max(1,plugin.getConfig().getLong("worldguard.max-region-blocks",1000000));
        if(volume>max){Msg.err(p,"Region exceeds the {limit} block limit","limit",max);return;}
        Region r=new Region();r.id=id;r.world=first.getWorld().getUID();r.worldName=first.getWorld().getName();r.owner=p.getUniqueId();
        r.minX=Math.min(first.getBlockX(),second.getBlockX());r.minY=Math.min(first.getBlockY(),second.getBlockY());r.minZ=Math.min(first.getBlockZ(),second.getBlockZ());
        r.maxX=Math.max(first.getBlockX(),second.getBlockX());r.maxY=Math.max(first.getBlockY(),second.getBlockY());r.maxZ=Math.max(first.getBlockZ(),second.getBlockZ());
        for(String flag:FLAGS)r.flags.put(flag,false);
        regions.put(id,r);save();Msg.ok(p,"Region {id} created ({blocks} blocks)","id",id,"blocks",volume);
        MenuListener.open(p,new RegionDetail(id,false));
    }

    @EventHandler(ignoreCancelled=true) public void onBreak(BlockBreakEvent e){
        Region r=at(e.getBlock().getLocation());if(!canBuild(e.getPlayer(),r))e.setCancelled(true);
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
                if(world!=null){
                    int x=Integer.parseInt(parts[1]),y=Integer.parseInt(parts[2]),z=Integer.parseInt(parts[3]);
                    var block=world.getBlockAt(x,y,z);
                    if(block.getType()==Material.OBSIDIAN)block.setType(Material.AIR,false);
                }
            }catch(RuntimeException invalid){plugin.getLogger().warning("Discarding invalid Crystal expiry entry: "+entry.getKey());}
            iterator.remove();changed=true;
        }
        if(changed)saveCrystalExpiry();
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
            baseline.put(x+","+y+","+z,block.getBlockData().getAsString());
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
                currentWorld.getBlockAt(x,y,z).setBlockData(Bukkit.createBlockData(entry.getValue()),false);
            }
        },period,period));
    }
    @EventHandler(ignoreCancelled=true) public void onInteract(PlayerInteractEvent e){
        if(e.getClickedBlock()==null||e.getItem()!=null&&e.getItem().getType()==Material.WOODEN_AXE)return;
        Region r=at(e.getClickedBlock().getLocation());if(!canInteract(e.getPlayer(),r))e.setCancelled(true);
    }
    @EventHandler(ignoreCancelled=true) public void onPvp(EntityDamageByEntityEvent e){
        if(!(e.getEntity() instanceof Player))return;
        Player attacker=null;
        if(e.getDamager() instanceof Player p)attacker=p;
        else if(e.getDamager() instanceof Projectile projectile){
            ProjectileSource shooter=projectile.getShooter();if(shooter instanceof Player p)attacker=p;
        }
        if(attacker==null)return;
        Location target=e.getEntity().getLocation(); if(!hasFlagAt(target,"pvp")&&!hasFlagAt(target,"KOTH_PVP_ARENA")&&at(target)!=null)e.setCancelled(true);
    }
    @EventHandler(ignoreCancelled=true) public void onEntityExplosion(EntityExplodeEvent e){
        e.blockList().removeIf(b->{Region r=at(b.getLocation());return r!=null&&!flag(r,"explosions");});
    }
    @EventHandler(ignoreCancelled=true) public void onBlockExplosion(BlockExplodeEvent e){
        e.blockList().removeIf(b->{Region r=at(b.getLocation());return r!=null&&!flag(r,"explosions");});
    }
    @EventHandler(ignoreCancelled=true) public void onSpawn(CreatureSpawnEvent e){
        if(e.getSpawnReason()!=CreatureSpawnEvent.SpawnReason.NATURAL)return;
        Region r=at(e.getLocation());if(r!=null&&!flag(r,"mob-spawning"))e.setCancelled(true);
    }

    private final class RegionList extends Menu{
        final UUID viewer;final boolean all;final int page;final List<Region> list;
        RegionList(UUID viewer,boolean all,int page){this.viewer=viewer;this.all=all;this.page=page;
            list=regions.values().stream().filter(r->all||r.owner.equals(viewer)).sorted(Comparator.comparing(r->r.id)).toList();
            create(54,Component.text(Msg.sc(all?"Region Administration":"Your Regions")));
            int start=page*45;for(int i=start;i<Math.min(start+45,list.size());i++){Region r=list.get(i);
                set(i-start,Items.of(Material.MAP,r.id,Msg.NEUTRAL,"World: "+r.worldName,"Size: "+r.volume()+" blocks","Owner: "+Bukkit.getOfflinePlayer(r.owner).getName(),"Click to manage"));}
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
        RegionDetail(String id,boolean adminView){this.id=id;this.adminView=adminView;create(27,Component.text(Msg.sc("Region Settings")));render();}
        void render(){Region r=regions.get(id);if(r==null)return;int[] slots={9,10,11,12,13,14,15,16,17,18};
            for(int i=0;i<FLAGS.size();i++){String flag=FLAGS.get(i);boolean on=flag(r,flag);
                set(slots[i],Items.of(on?Material.LIME_DYE:Material.GRAY_DYE,flagName(flag),on?Msg.GOOD:Msg.BAD,"Status: "+(on?"Enabled":"Disabled"),"Click to toggle"));}
            set(20,Items.of(Material.PLAYER_HEAD,"Members",Msg.NEUTRAL,r.members.size()+" members","View or remove members"));
            set(22,Items.of(Material.TNT,"Delete Region",Msg.BAD,"Permanently delete this region"));
            set(26,Items.of(Material.ARROW,"Back",Msg.NEUTRAL));
        }
        @Override public void onClick(Player p,org.bukkit.event.inventory.InventoryClickEvent e){if(!isTop(e))return;Region r=regions.get(id);if(r==null){p.closeInventory();return;}
            if(!canManage(p,r)){p.closeInventory();return;}int s=e.getRawSlot();if(s==26){MenuListener.open(p,new RegionList(p.getUniqueId(),adminView,0));return;}
            if(s==20){MenuListener.open(p,new RegionMembers(id,adminView));return;}
            if(s==22){configureArenaReset(r,false);regions.remove(id);save();Msg.ok(p,"Region {id} deleted","id",id);MenuListener.open(p,new RegionList(p.getUniqueId(),adminView,0));return;}
            int[] slots={9,10,11,12,13,14,15,16,17,18};for(int i=0;i<slots.length;i++)if(s==slots[i]){String flag=FLAGS.get(i);boolean enabled=!flag(r,flag);r.flags.put(flag,enabled);if(flag.equals("ARENA_RESET"))configureArenaReset(r,enabled);save();render();return;}
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

    private static String flagName(String flag){return switch(flag){case "mob-spawning"->"Mob Spawning";case "CRYSTAL"->"Crystal (3m Obsidian)";case "ARENA_RESET"->"Arena Reset";case "KOTH_PVP_ARENA"->"KOTH_PVP_ARENA";case "KOTH_CAPTURE"->"KOTH_CAPTURE";case "pvp"->"PvP";case "build"->"Public Build";case "interact"->"Public Interact";case "explosions"->"Explosions";default->flag;};}
}
