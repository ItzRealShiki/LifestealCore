package de.leon.lifesteal.worldguard;

import de.leon.lifesteal.LifestealCore;
import de.leon.lifesteal.gui.Items;
import de.leon.lifesteal.gui.Menu;
import de.leon.lifesteal.gui.MenuListener;
import de.leon.lifesteal.util.Msg;
import de.leon.lifesteal.worldedit.BuildToolsModule;
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

public final class RegionProtectionModule implements Listener {
    private static final List<String> FLAGS = List.of("build","interact","pvp","explosions","mob-spawning","KOTH_PVP_ARENA","KOTH_CAPTURE");
    private static final Map<String,Material> FLAG_ICONS = Map.of(
            "build",Material.BRICKS,"interact",Material.LEVER,"pvp",Material.IRON_SWORD,
            "explosions",Material.TNT,"mob-spawning",Material.SPAWNER,
            "koth-pvp-arena",Material.IRON_SWORD,"koth-capture",Material.BEACON);
    private static final class Region {
        String id,worldName; UUID world,owner;
        int minX,minY,minZ,maxX,maxY,maxZ;
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
    private final BuildToolsModule selections;
    private final File file;
    private final YamlConfiguration yaml;
    private boolean storageHealthy=true;
    private final Map<String,Region> regions=new LinkedHashMap<>();

    public RegionProtectionModule(LifestealCore plugin,BuildToolsModule selections){
        this.plugin=plugin;this.selections=selections;
        file=new File(plugin.getDataFolder(),"regions.yml");yaml=loadYamlWithBackup();
        load();Bukkit.getPluginManager().registerEvents(this,plugin);
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
        ConfigurationSection root=yaml.getConfigurationSection("regions");if(root==null)return;
        for(String id:root.getKeys(false))try{
            ConfigurationSection s=root.getConfigurationSection(id);if(s==null)continue;
            Region r=new Region();r.id=id;r.world=UUID.fromString(s.getString("world",""));
            r.worldName=s.getString("world-name","world");r.owner=UUID.fromString(s.getString("owner",""));
            r.minX=s.getInt("min.x");r.minY=s.getInt("min.y");r.minZ=s.getInt("min.z");
            r.maxX=s.getInt("max.x");r.maxY=s.getInt("max.y");r.maxZ=s.getInt("max.z");
            for(String flag:FLAGS)r.flags.put(flag,s.getBoolean("flags."+flag,false));
            for(String member:s.getStringList("members"))try{r.members.add(UUID.fromString(member));}catch(IllegalArgumentException ignored){}
            regions.put(id.toLowerCase(Locale.ROOT),r);
        }catch(IllegalArgumentException ignored){plugin.getLogger().warning("Ignoring invalid region "+id+" in regions.yml");}
    }
    private void save(){
        yaml.set("regions",null);
        for(Region r:regions.values()){
            String p="regions."+r.id;yaml.set(p+".world",r.world.toString());yaml.set(p+".world-name",r.worldName);
            yaml.set(p+".owner",r.owner.toString());yaml.set(p+".min.x",r.minX);yaml.set(p+".min.y",r.minY);yaml.set(p+".min.z",r.minZ);
            yaml.set(p+".max.x",r.maxX);yaml.set(p+".max.y",r.maxY);yaml.set(p+".max.z",r.maxZ);
            for(String flag:FLAGS)yaml.set(p+".flags."+flag,r.flag(flag));
            yaml.set(p+".members",r.members.stream().map(UUID::toString).toList());
        }
        if(!storageHealthy){plugin.getLogger().severe("Regions were not saved because regions.yml could not be safely recovered.");return;}
        File temporary=new File(file.getParentFile(),"regions.yml.tmp"),backup=new File(file.getParentFile(),"regions.yml.bak");
        try{
            yaml.save(temporary);YamlConfiguration verify=new YamlConfiguration();verify.load(temporary);
            if(file.isFile()&&file.length()>0)Files.copy(file.toPath(),backup.toPath(),StandardCopyOption.REPLACE_EXISTING);
            try{Files.move(temporary.toPath(),file.toPath(),StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}
            catch(AtomicMoveNotSupportedException ignored){Files.move(temporary.toPath(),file.toPath(),StandardCopyOption.REPLACE_EXISTING);}
        }catch(IOException|InvalidConfigurationException e){plugin.getLogger().severe("Could not safely save regions.yml: "+e.getMessage());if(temporary.exists())temporary.delete();}
    }

    private Region at(Location location){
        return regions.values().stream().filter(r->r.contains(location)).min(Comparator.comparingLong(Region::volume)).orElse(null);
    }
    public boolean hasFlagAt(Location location, String flag){ String key=flag.toUpperCase(Locale.ROOT); return regions.values().stream().anyMatch(r->r.contains(location)&&r.flag(key));}

    private boolean canManage(Player p,Region r){return admin(p)||r.owner.equals(p.getUniqueId());}
    private boolean admin(Player p){return p.hasPermission("Admin.Core")||p.hasPermission("Owner.Core");}
    private boolean builder(Player p){return admin(p)||p.hasPermission("Builder.Core");}
    private boolean canBuild(Player p,Region r){return r==null||r.flag("build")||r.member(p.getUniqueId());}
    private boolean canInteract(Player p,Region r){return r==null||r.flag("interact")||r.member(p.getUniqueId());}

    private boolean execute(CommandSender sender,String[] args){
        if(!(sender instanceof Player p)){Msg.err(sender,"Only players can use this command");return true;}
        if(args.length==0){MenuListener.open(p,new RegionList(p.getUniqueId(),false,0));return true;}
        switch(args[0].toLowerCase(Locale.ROOT)){
            case "admin"->{if(!admin(p))Msg.err(p,"Admin.Core required");else MenuListener.open(p,new RegionList(p.getUniqueId(),true,0));}
            case "create"->{
                if(!builder(p)){Msg.err(p,"Builder.Core or staff permission required");return true;}
                if(args.length!=2){Msg.info(p,"Usage: /lwg create <id>");return true;}create(p,args[1]);
            }
            case "delete"->{
                if(args.length!=2){Msg.info(p,"Usage: /lwg delete <id>");return true;}
                Region r=regions.get(args[1].toLowerCase(Locale.ROOT));
                if(r==null){Msg.err(p,"Region not found");return true;}
                if(!canManage(p,r)){Msg.err(p,"You cannot manage this region");return true;}
                regions.remove(r.id.toLowerCase(Locale.ROOT));save();Msg.ok(p,"Region {id} deleted","id",r.id);
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
            default->Msg.info(p,"Usage: /lwg [create <id>|admin|delete <id>|addmember|removemember]");
        }
        return true;
    }

    private void create(Player p,String rawId){
        String id=rawId.toLowerCase(Locale.ROOT);
        if(!id.matches("[a-z0-9_-]{2,24}")){Msg.err(p,"Region IDs must be 2-24 characters (a-z, 0-9, _ or -)");return;}
        if(regions.containsKey(id)){Msg.err(p,"That region already exists");return;}
        BuildToolsModule.Selection s=selections.getSelection(p);
        if(s==null){Msg.err(p,"Select both positions with the wooden axe first");return;}
        long max=Math.max(1,plugin.getConfig().getLong("worldguard.max-region-blocks",1000000));
        if(s.volume()>max){Msg.err(p,"Region exceeds the {limit} block limit","limit",max);return;}
        Region r=new Region();r.id=id;r.world=s.world().getUID();r.worldName=s.world().getName();r.owner=p.getUniqueId();
        r.minX=s.minX();r.minY=s.minY();r.minZ=s.minZ();r.maxX=s.maxX();r.maxY=s.maxY();r.maxZ=s.maxZ();
        for(String flag:FLAGS)r.flags.put(flag,false);
        regions.put(id,r);save();Msg.ok(p,"Region {id} created ({blocks} blocks)","id",id,"blocks",s.volume());
        MenuListener.open(p,new RegionDetail(id,false));
    }

    @EventHandler(ignoreCancelled=true) public void onBreak(BlockBreakEvent e){
        Region r=at(e.getBlock().getLocation());if(!canBuild(e.getPlayer(),r))e.setCancelled(true);
    }
    @EventHandler(ignoreCancelled=true) public void onPlace(BlockPlaceEvent e){
        Region r=at(e.getBlock().getLocation());if(!canBuild(e.getPlayer(),r))e.setCancelled(true);
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
        Location target=e.getEntity().getLocation(); if(!hasFlagAt(target,"pvp")&&!hasFlagAt(target,"koth-pvp-arena")&&at(target)!=null)e.setCancelled(true);
    }
    @EventHandler(ignoreCancelled=true) public void onEntityExplosion(EntityExplodeEvent e){
        e.blockList().removeIf(b->{Region r=at(b.getLocation());return r!=null&&!r.flag("explosions");});
    }
    @EventHandler(ignoreCancelled=true) public void onBlockExplosion(BlockExplodeEvent e){
        e.blockList().removeIf(b->{Region r=at(b.getLocation());return r!=null&&!r.flag("explosions");});
    }
    @EventHandler(ignoreCancelled=true) public void onSpawn(CreatureSpawnEvent e){
        if(e.getSpawnReason()!=CreatureSpawnEvent.SpawnReason.NATURAL)return;
        Region r=at(e.getLocation());if(r!=null&&!r.flag("mob-spawning"))e.setCancelled(true);
    }

    private final class RegionList extends Menu{
        final UUID viewer;final boolean all;final int page;final List<Region> list;
        RegionList(UUID viewer,boolean all,int page){this.viewer=viewer;this.all=all;this.page=page;
            list=regions.values().stream().filter(r->all||r.owner.equals(viewer)).sorted(Comparator.comparing(r->r.id)).toList();
            create(54,Component.text(all?"Region Administration":"Your Regions"));
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
        RegionDetail(String id,boolean adminView){this.id=id;this.adminView=adminView;create(27,Component.text("Region Settings"));render();}
        void render(){Region r=regions.get(id);if(r==null)return;int[] slots={9,10,11,12,14,15,16};
            for(int i=0;i<FLAGS.size();i++){String flag=FLAGS.get(i);boolean on=r.flag(flag);
                set(slots[i],Items.of(on?Material.LIME_DYE:Material.GRAY_DYE,flagName(flag),on?Msg.GOOD:Msg.BAD,"Status: "+(on?"Enabled":"Disabled"),"Click to toggle"));}
            set(20,Items.of(Material.PLAYER_HEAD,"Members",Msg.NEUTRAL,r.members.size()+" members","View or remove members"));
            set(22,Items.of(Material.TNT,"Delete Region",Msg.BAD,"Permanently delete this region"));
            set(26,Items.of(Material.ARROW,"Back",Msg.NEUTRAL));
        }
        @Override public void onClick(Player p,org.bukkit.event.inventory.InventoryClickEvent e){if(!isTop(e))return;Region r=regions.get(id);if(r==null){p.closeInventory();return;}
            if(!canManage(p,r)){p.closeInventory();return;}int s=e.getRawSlot();if(s==26){MenuListener.open(p,new RegionList(p.getUniqueId(),adminView,0));return;}
            if(s==20){MenuListener.open(p,new RegionMembers(id,adminView));return;}
            if(s==22){regions.remove(id);save();Msg.ok(p,"Region {id} deleted","id",id);MenuListener.open(p,new RegionList(p.getUniqueId(),adminView,0));return;}
            int[] slots={10,11,12,14,15};for(int i=0;i<slots.length;i++)if(s==slots[i]){String flag=FLAGS.get(i);r.flags.put(flag,!r.flag(flag));save();render();return;}
        }
    }

    private final class RegionMembers extends Menu{
        final String id;final boolean adminView;
        RegionMembers(String id,boolean adminView){this.id=id;this.adminView=adminView;create(54,Component.text("Region Members"));Region r=regions.get(id);if(r==null)return;
            int slot=0;for(UUID uid:r.members){OfflinePlayer op=Bukkit.getOfflinePlayer(uid);String name=op.getName()==null?uid.toString().substring(0,8):op.getName();
                set(slot++,Items.head(op,Component.text(name),List.of(Component.text(Msg.sc("Click to remove"),Msg.NEUTRAL))));if(slot>=45)break;}
            set(49,Items.of(Material.ARROW,"Back",Msg.NEUTRAL));}
        @Override public void onClick(Player p,org.bukkit.event.inventory.InventoryClickEvent e){if(!isTop(e))return;Region r=regions.get(id);if(r==null||!canManage(p,r)){p.closeInventory();return;}
            int s=e.getRawSlot();if(s==49){MenuListener.open(p,new RegionDetail(id,adminView));return;}
            if(s>=0&&s<45&&s<r.members.size()){UUID uid=new ArrayList<>(r.members).get(s);r.members.remove(uid);save();Msg.info(p,"Region member removed");MenuListener.open(p,new RegionMembers(id,adminView));}}
    }

    private static String flagName(String flag){return switch(flag){case "mob-spawning"->"Mob Spawning";case "koth-pvp-arena"->"KOTH PvP Arena";case "koth-capture"->"KOTH Capture";case "pvp"->"PvP";case "build"->"Public Build";case "interact"->"Public Interact";case "explosions"->"Explosions";default->flag;};}
}
