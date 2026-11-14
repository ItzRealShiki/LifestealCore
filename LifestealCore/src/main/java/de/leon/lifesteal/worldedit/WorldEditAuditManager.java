package de.leon.lifesteal.worldedit;

import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.WorldEditException;
import com.sk89q.worldedit.event.extent.EditSessionEvent;
import com.sk89q.worldedit.extent.AbstractDelegateExtent;
import com.sk89q.worldedit.extent.Extent;
import com.sk89q.worldedit.extension.platform.Actor;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.util.eventbus.Subscribe;
import com.sk89q.worldedit.world.block.BlockStateHolder;
import com.sk89q.worldguard.bukkit.protection.events.DisallowedPVPEvent;
import de.leon.lifesteal.LifestealCore;
import de.leon.lifesteal.data.Database;
import de.leon.lifesteal.gui.Menu;
import de.leon.lifesteal.gui.MenuListener;
import de.leon.lifesteal.util.Msg;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import java.io.BufferedWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.*;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

public final class WorldEditAuditManager implements TabExecutor, Listener {
    private static final String ADMIN="Admin.Core", OWNER="Owner.Core", BUILDER="Builder.Core";
    private static final int PAGE=45;
    private static final DateTimeFormatter DATE=DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss").withZone(ZoneId.systemDefault());
    private final LifestealCore plugin; private final Database db;
    private final List<Row> pending=new CopyOnWriteArrayList<>();
    private final AtomicBoolean flushing=new AtomicBoolean();
    private final Set<String> undoing=java.util.concurrent.ConcurrentHashMap.newKeySet();
    private int flushTask=-1, pruneTask=-1;
    private enum View { WORLDEDIT, WORLDGUARD }
    private record Row(String batch,String uuid,String actor,String world,int x,int y,int z,String before,String after,long time){}
    private record UndoRow(int x,int y,int z,String before,String after){}
    private record UndoData(String batch,String world,List<UndoRow> rows){}
    private record Batch(String id,String actor,String world,int count,long time){}
    private record Guard(long id,String attacker,String defender,String world,int x,int y,int z,long time){}

    public WorldEditAuditManager(LifestealCore plugin,Database db)throws Exception{
        this.plugin=plugin;this.db=db;
        db.sync(c->{
            String id=db.type()==Database.Type.SQLITE?"INTEGER PRIMARY KEY AUTOINCREMENT":"BIGINT PRIMARY KEY AUTO_INCREMENT";
            try(Statement s=c.createStatement()){
                s.executeUpdate("CREATE TABLE IF NOT EXISTS "+db.t("worldedit_audit")+" (id "+id+",batch_id VARCHAR(36) NOT NULL,actor_uuid VARCHAR(36),actor_name VARCHAR(16) NOT NULL,world_name VARCHAR(128) NOT NULL,x INT NOT NULL,y INT NOT NULL,z INT NOT NULL,before_state VARCHAR(512) NOT NULL,after_state VARCHAR(512) NOT NULL,created BIGINT NOT NULL)");
                s.executeUpdate("CREATE TABLE IF NOT EXISTS "+db.t("worldedit_undone")+" (batch_id VARCHAR(36) PRIMARY KEY,actor_name VARCHAR(16),undone_by VARCHAR(16) NOT NULL,restored INT NOT NULL,skipped INT NOT NULL,created BIGINT NOT NULL)");
                s.executeUpdate("CREATE TABLE IF NOT EXISTS "+db.t("worldguard_audit")+" (id "+id+",attacker_name VARCHAR(16) NOT NULL,defender_name VARCHAR(16) NOT NULL,world_name VARCHAR(128) NOT NULL,x INT NOT NULL,y INT NOT NULL,z INT NOT NULL,created BIGINT NOT NULL)");
            }return null;
        });
        WorldEdit.getInstance().getEventBus().register(this);
        Bukkit.getPluginManager().registerEvents(this,plugin);
        flushTask=Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin,this::flush,40L,40L);
        pruneTask=Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin,this::prune,20L*3600L,20L*3600L);
        prune();
    }

    @Subscribe public void editSession(EditSessionEvent e){
        if(e.getStage()!=EditSession.Stage.BEFORE_CHANGE||e.getActor()==null||e.getWorld()==null)return;
        Actor actor=e.getActor();String name=actor.getName();Player p=Bukkit.getPlayerExact(name);
        e.setExtent(new AuditExtent(e.getExtent(),UUID.randomUUID().toString(),p==null?null:p.getUniqueId().toString(),name,e.getWorld().getName()));
    }
    private final class AuditExtent extends AbstractDelegateExtent{
        private final String batch,uuid,actor,world;
        AuditExtent(Extent extent,String batch,String uuid,String actor,String world){super(extent);this.batch=batch;this.uuid=uuid;this.actor=actor;this.world=world;}
        @Override public <T extends BlockStateHolder<T>> boolean setBlock(BlockVector3 p,T block)throws WorldEditException{
            String before=getExtent().getBlock(p).getAsString();boolean changed=super.setBlock(p,block);
            if(changed)pending.add(new Row(batch,uuid,actor,world,p.getBlockX(),p.getBlockY(),p.getBlockZ(),before,block.getAsString(),System.currentTimeMillis()));
            return changed;
        }
    }
    @EventHandler(priority=EventPriority.MONITOR)
    public void deniedPvp(DisallowedPVPEvent e){
        if(e.isCancelled()||e.getAttacker()==null||e.getDefender()==null)return;
        Player a=e.getAttacker(),d=e.getDefender();var l=a.getLocation();
        db.run(c->{try(PreparedStatement q=c.prepareStatement("INSERT INTO "+db.t("worldguard_audit")+" (attacker_name,defender_name,world_name,x,y,z,created) VALUES (?,?,?,?,?,?,?)")){
            q.setString(1,a.getName());q.setString(2,d.getName());q.setString(3,l.getWorld().getName());q.setInt(4,l.getBlockX());q.setInt(5,l.getBlockY());q.setInt(6,l.getBlockZ());q.setLong(7,System.currentTimeMillis());q.executeUpdate();
        }});
    }
    private void flush(){
        if(pending.isEmpty()||!flushing.compareAndSet(false,true))return;
        List<Row> rows=new ArrayList<>(pending);
        db.query(c->{try(PreparedStatement q=c.prepareStatement("INSERT INTO "+db.t("worldedit_audit")+" (batch_id,actor_uuid,actor_name,world_name,x,y,z,before_state,after_state,created) VALUES (?,?,?,?,?,?,?,?,?,?)")){
            for(Row r:rows){q.setString(1,r.batch());q.setString(2,r.uuid());q.setString(3,r.actor());q.setString(4,r.world());q.setInt(5,r.x());q.setInt(6,r.y());q.setInt(7,r.z());q.setString(8,r.before());q.setString(9,r.after());q.setLong(10,r.time());q.addBatch();}q.executeBatch();
        }return true;}).whenComplete((ok,err)->{if(err==null)pending.removeAll(rows);else plugin.getLogger().severe("WorldEdit audit save failed: "+err.getMessage());flushing.set(false);});
    }
    private void prune(){
        long cutoff=System.currentTimeMillis()-Math.max(1,plugin.getConfig().getInt("audit.retention-days",90))*86_400_000L;
        db.run(c->{for(String table:List.of("worldedit_audit","worldguard_audit"))try(PreparedStatement q=c.prepareStatement("DELETE FROM "+db.t(table)+" WHERE created<?")){q.setLong(1,cutoff);q.executeUpdate();}
            try(Statement q=c.createStatement()){q.executeUpdate("DELETE FROM "+db.t("worldedit_undone")+" WHERE batch_id NOT IN (SELECT DISTINCT batch_id FROM "+db.t("worldedit_audit")+")");}});
    }

    @Override public boolean onCommand(CommandSender s,Command cmd,String label,String[] args){
        if(cmd.getName().equalsIgnoreCase("undo")){
            if(!(s instanceof Player p)){Msg.err(s,"Use this command in-game");return true;}
            if(args.length==0){if(!p.hasPermission(BUILDER)&&!p.hasPermission(ADMIN)&&!p.hasPermission(OWNER)){Msg.err(p,"Builder.Core is required");return true;}undo(p,p.getName(),p.getUniqueId().toString());}
            else{if(!p.hasPermission(ADMIN)&&!p.hasPermission(OWNER)){Msg.err(p,"Admin.Core is required");return true;}Player target=Bukkit.getPlayerExact(args[0]);undo(p,args[0],target==null?"":target.getUniqueId().toString());}
            return true;
        }
        if(!(s instanceof Player p)){
            if(args.length>0&&args[0].equalsIgnoreCase("export")&&s.hasPermission(OWNER)){export(s,view(args.length>1?args[1]:"worldedit"));return true;}
            if(args.length>0&&args[0].equalsIgnoreCase("purge")&&s.hasPermission(OWNER)){prune();Msg.ok(s,"Audit cleanup started");return true;}
            Msg.err(s,"Use /audit in-game");return true;
        }
        if(label.equalsIgnoreCase("wgaudit")){if(staff(p))WorldEditAuditManager.this.open(p,View.WORLDGUARD,0);return true;}
        if(label.equalsIgnoreCase("weaudit")||args.length==0||args[0].equalsIgnoreCase("worldedit")||args[0].equalsIgnoreCase("we")){if(staff(p))WorldEditAuditManager.this.open(p,View.WORLDEDIT,0);return true;}
        if(args[0].equalsIgnoreCase("worldguard")||args[0].equalsIgnoreCase("wg")){if(staff(p))WorldEditAuditManager.this.open(p,View.WORLDGUARD,0);return true;}
        if(args[0].equalsIgnoreCase("export")){if(!p.hasPermission(OWNER)){Msg.err(p,"Owner.Core is required for exports");return true;}export(p,view(args.length>1?args[1]:"worldedit"));return true;}
        if(args[0].equalsIgnoreCase("purge")){if(!p.hasPermission(OWNER)){Msg.err(p,"Owner.Core is required");return true;}prune();Msg.ok(p,"Audit cleanup started");return true;}
        Msg.info(p,"Usage: /audit <worldedit|worldguard|export|purge>");return true;
    }
    private boolean staff(Player p){if(p.hasPermission(ADMIN)||p.hasPermission(OWNER))return true;Msg.err(p,"Admin.Core is required");return false;}
    private View view(String s){return s.equalsIgnoreCase("wg")||s.equalsIgnoreCase("worldguard")?View.WORLDGUARD:View.WORLDEDIT;}
    private void open(Player p,View v,int page){AuditMenu menu=new AuditMenu(p.getUniqueId(),v,page);MenuListener.WorldEditAuditManager.this.open(p,menu);Bukkit.getScheduler().runTask(plugin,menu::load);}

    private final class AuditMenu extends Menu{
        private final UUID viewer;private final View view;private final int page;private boolean next;
        private List<Batch> batches=List.of();private List<Guard> guards=List.of();
        AuditMenu(UUID viewer,View view,int page){this.viewer=viewer;this.view=view;this.page=Math.max(0,page);create(54,Component.text(Msg.sc(view==View.WORLDEDIT?"WORLDEDIT AUDIT":"WORLDGUARD AUDIT"),Msg.NEUTRAL));}
        private boolean active(){Player p=Bukkit.getPlayer(viewer);return p!=null&&p.isOnline()&&MenuListener.openMenu(p)==this;}
        private ItemStack item(Material mat,String name,NamedTextColor color,String... lore){ItemStack i=new ItemStack(mat);ItemMeta m=i.getItemMeta();m.displayName(Component.text(Msg.sc(name),color));List<Component> lines=new ArrayList<>();for(String l:lore)lines.add(Msg.item(l,Msg.NEUTRAL));m.lore(lines);i.setItemMeta(m);return i;}
        private void controls(){set(45,item(Material.ARROW,"Previous",NamedTextColor.AQUA));set(47,item(view==View.WORLDEDIT?Material.SHIELD:Material.BRICKS,view==View.WORLDEDIT?"WorldGuard audit":"WorldEdit audit",NamedTextColor.AQUA));
            set(49,item(Material.BARRIER,"Close",NamedTextColor.RED));if(next)set(50,item(Material.ARROW,"Next",NamedTextColor.AQUA));set(53,item(Material.BOOK,"Export",NamedTextColor.GREEN,"/audit export "+view.name().toLowerCase(Locale.ROOT)));}
        void load(){
            if(view==View.WORLDEDIT)db.query(c->{List<Batch> list=new ArrayList<>();try(PreparedStatement q=c.prepareStatement("SELECT batch_id,actor_name,world_name,COUNT(*),MAX(created) FROM "+db.t("worldedit_audit")+" GROUP BY batch_id,actor_name,world_name ORDER BY MAX(id) DESC LIMIT ? OFFSET ?")){q.setInt(1,PAGE+1);q.setInt(2,page*PAGE);try(ResultSet r=q.executeQuery()){while(r.next())list.add(new Batch(r.getString(1),r.getString(2),r.getString(3),r.getInt(4),r.getLong(5)));}}return list;})
                .thenAccept(list->Bukkit.getScheduler().runTask(plugin,()->{if(!active())return;next=list.size()>PAGE;batches=list.stream().limit(PAGE).toList();for(int i=0;i<batches.size();i++){Batch b=batches.get(i);set(i,item(Material.BRICKS,b.actor(),NamedTextColor.YELLOW,"World: "+b.world(),"Blocks: "+b.count(),"Date: "+DATE.format(Instant.ofEpochMilli(b.time())),"Batch: "+b.id().substring(0,8),"Left-click: details","Right-click: Owner.Core delete"));}controls();}));
            else db.query(c->{List<Guard> list=new ArrayList<>();try(PreparedStatement q=c.prepareStatement("SELECT id,attacker_name,defender_name,world_name,x,y,z,created FROM "+db.t("worldguard_audit")+" ORDER BY id DESC LIMIT ? OFFSET ?")){q.setInt(1,PAGE+1);q.setInt(2,page*PAGE);try(ResultSet r=q.executeQuery()){while(r.next())list.add(new Guard(r.getLong(1),r.getString(2),r.getString(3),r.getString(4),r.getInt(5),r.getInt(6),r.getInt(7),r.getLong(8)));}}return list;})
                .thenAccept(list->Bukkit.getScheduler().runTask(plugin,()->{if(!active())return;next=list.size()>PAGE;guards=list.stream().limit(PAGE).toList();for(int i=0;i<guards.size();i++){Guard g=guards.get(i);set(i,item(Material.SHIELD,g.attacker()+" → "+g.defender(),NamedTextColor.YELLOW,"WorldGuard blocked PvP","World: "+g.world(),"Position: "+g.x()+", "+g.y()+", "+g.z(),"Date: "+DATE.format(Instant.ofEpochMilli(g.time())),"Left-click: details","Right-click: Owner.Core delete"));}controls();}));
        }
        @Override public void onClick(Player p,InventoryClickEvent e){
            if(e.getClickedInventory()==null||e.getClickedInventory()!=getInventory())return;e.setCancelled(true);if(!p.getUniqueId().equals(viewer)||!staff(p)){p.closeInventory();return;}
            int s=e.getRawSlot();if(s==49){p.closeInventory();return;}if(s==45&&page>0){WorldEditAuditManager.this.open(p,view,page-1);return;}if(s==50&&next){WorldEditAuditManager.this.open(p,view,page+1);return;}
            if(s==47){WorldEditAuditManager.this.open(p,view==View.WORLDEDIT?View.WORLDGUARD:View.WORLDEDIT,0);return;}if(s==53){Msg.info(p,"Use /audit export "+view.name().toLowerCase(Locale.ROOT));return;}
            if(s<0||s>=PAGE)return;
            if(view==View.WORLDEDIT&&s<batches.size()){Batch b=batches.get(s);if(e.isRightClick()){if(!p.hasPermission(OWNER)){Msg.err(p,"Only Owner.Core can delete audit data");return;}MenuListener.WorldEditAuditManager.this.open(p,new ConfirmDelete(viewer,view,b.id(),b.actor()));}else Msg.info(p,"Batch {id}: {n} blocks by {p} in {w}","id",b.id(),"n",b.count(),"p",b.actor(),"w",b.world());}
            if(view==View.WORLDGUARD&&s<guards.size()){Guard g=guards.get(s);if(e.isRightClick()){if(!p.hasPermission(OWNER)){Msg.err(p,"Only Owner.Core can delete audit data");return;}MenuListener.WorldEditAuditManager.this.open(p,new ConfirmDelete(viewer,view,Long.toString(g.id()),g.attacker()));}else Msg.info(p,"WorldGuard blocked PvP: {a} → {d} at {w} {x},{y},{z}","a",g.attacker(),"d",g.defender(),"w",g.world(),"x",g.x(),"y",g.y(),"z",g.z());}
        }
    }
    private final class ConfirmDelete extends Menu{
        private final UUID owner;private final View view;private final String id,actor;
        ConfirmDelete(UUID owner,View view,String id,String actor){this.owner=owner;this.view=view;this.id=id;this.actor=actor;create(27,Component.text(Msg.sc("VERY CRITICAL • DELETE AUDIT"),Msg.BAD));set(11,button(Material.TNT,"Confirm deletion",NamedTextColor.RED));set(15,button(Material.LIME_DYE,"Cancel",NamedTextColor.GREEN));}
        @Override public void onClick(Player p,InventoryClickEvent e){if(e.getClickedInventory()==null||e.getClickedInventory()!=getInventory())return;e.setCancelled(true);if(!p.getUniqueId().equals(owner)||!p.hasPermission(OWNER)){p.closeInventory();return;}if(e.getRawSlot()==15){WorldEditAuditManager.this.open(p,view,0);return;}if(e.getRawSlot()!=11)return;
            db.run(c->{if(view==View.WORLDEDIT){try(PreparedStatement q=c.prepareStatement("DELETE FROM "+db.t("worldedit_audit")+" WHERE batch_id=?")){q.setString(1,id);q.executeUpdate();}try(PreparedStatement q=c.prepareStatement("DELETE FROM "+db.t("worldedit_undone")+" WHERE batch_id=?")){q.setString(1,id);q.executeUpdate();}}
                else try(PreparedStatement q=c.prepareStatement("DELETE FROM "+db.t("worldguard_audit")+" WHERE id=?")){q.setLong(1,Long.parseLong(id));q.executeUpdate();}
                try(PreparedStatement q=c.prepareStatement("INSERT INTO "+db.t("admin_logs")+" (category,actor_name,target_name,action,details,created) VALUES ('AUDIT',?,?,'VERY CRITICAL AUDIT DELETION',?,?)")){q.setString(1,p.getName());q.setString(2,actor);q.setString(3,view.name()+" record "+id);q.setLong(4,System.currentTimeMillis());q.executeUpdate();}});
            p.closeInventory();Msg.ok(p,"Audit deletion started and logged");}
    }
    private ItemStack button(Material mat,String name,NamedTextColor color){ItemStack i=new ItemStack(mat);ItemMeta m=i.getItemMeta();m.displayName(Component.text(Msg.sc(name),color));i.setItemMeta(m);return i;}

    private void export(CommandSender sender,View view){
        db.query(c->{List<String[]> rows=new ArrayList<>();String sql=view==View.WORLDEDIT
            ?"SELECT batch_id,actor_name,world_name,x,y,z,before_state,after_state,created FROM "+db.t("worldedit_audit")+" ORDER BY id"
            :"SELECT id,attacker_name,defender_name,world_name,x,y,z,created FROM "+db.t("worldguard_audit")+" ORDER BY id";
            try(Statement s=c.createStatement();ResultSet r=s.executeQuery(sql)){int n=view==View.WORLDEDIT?9:8;while(r.next()){String[] row=new String[n];for(int i=0;i<n;i++)row[i]=r.getString(i+1);rows.add(row);}}return rows;
        }).thenAccept(rows->{try{Path dir=plugin.getDataFolder().toPath().resolve("audit-exports");Files.createDirectories(dir);String name=(view==View.WORLDEDIT?"worldedit-":"worldguard-")+System.currentTimeMillis()+".csv";
            try(BufferedWriter w=Files.newBufferedWriter(dir.resolve(name),StandardCharsets.UTF_8)){w.write(view==View.WORLDEDIT?"batch,actor,world,x,y,z,before,after,created\n":"id,attacker,defender,world,x,y,z,created\n");for(String[] row:rows){for(int i=0;i<row.length;i++){if(i>0)w.write(',');w.write("\""+(row[i]==null?"":row[i]).replace("\"","\"\"")+"\"");}w.write('\n');}}
            Bukkit.getScheduler().runTask(plugin,()->Msg.ok(sender,"Export created: audit-exports/{file} ({n} rows)","file",name,"n",rows.size()));
        }catch(Exception ex){plugin.getLogger().severe("Audit export failed: "+ex.getMessage());Bukkit.getScheduler().runTask(plugin,()->Msg.err(sender,"Audit export failed; check console"));}}).exceptionally(ex->{Bukkit.getScheduler().runTask(plugin,()->Msg.err(sender,"Could not load audit data"));return null;});
    }

    private void undo(Player actor,String target,String uuid){
        db.query(c->{String batch=null;try(PreparedStatement q=c.prepareStatement("SELECT batch_id FROM "+db.t("worldedit_audit")+" WHERE (actor_uuid=? OR actor_name=?) AND batch_id NOT IN (SELECT batch_id FROM "+db.t("worldedit_undone")+") ORDER BY id DESC LIMIT 1")){q.setString(1,uuid);q.setString(2,target);try(ResultSet r=q.executeQuery()){if(r.next())batch=r.getString(1);}}
            if(batch==null)return null;String world=null;List<UndoRow> rows=new ArrayList<>();try(PreparedStatement q=c.prepareStatement("SELECT world_name,x,y,z,before_state,after_state FROM "+db.t("worldedit_audit")+" WHERE batch_id=? ORDER BY id DESC")){q.setString(1,batch);try(ResultSet r=q.executeQuery()){while(r.next()){if(world==null)world=r.getString(1);rows.add(new UndoRow(r.getInt(2),r.getInt(3),r.getInt(4),r.getString(5),r.getString(6)));}}}return new UndoData(batch,world,rows);
        }).thenAccept(data->Bukkit.getScheduler().runTask(plugin,()->{if(!actor.isOnline())return;if(data==null){Msg.err(actor,"No saved WorldEdit edit is available to undo");return;}if(!undoing.add(data.batch())){Msg.err(actor,"This edit is already being undone");return;}
            World world=Bukkit.getWorld(data.world());if(world==null){undoing.remove(data.batch());Msg.err(actor,"The edit world is not loaded");return;}int restored=0,skipped=0;
            for(UndoRow r:data.rows())try{Block b=world.getBlockAt(r.x(),r.y(),r.z());if(!b.getBlockData().getAsString().equals(r.after())){skipped++;continue;}b.setBlockData(Bukkit.createBlockData(r.before()),false);restored++;}catch(IllegalArgumentException ex){skipped++;}
            final int done=restored,conflicts=skipped;
            db.query(c->{try(PreparedStatement q=c.prepareStatement("INSERT INTO "+db.t("worldedit_undone")+" (batch_id,actor_name,undone_by,restored,skipped,created) VALUES (?,?,?,?,?,?)")){q.setString(1,data.batch());q.setString(2,target);q.setString(3,actor.getName());q.setInt(4,done);q.setInt(5,conflicts);q.setLong(6,System.currentTimeMillis());q.executeUpdate();}
                try(PreparedStatement q=c.prepareStatement("INSERT INTO "+db.t("admin_logs")+" (category,actor_name,target_name,action,details,created) VALUES ('WORLDEDIT',?,?,'PERSISTENT UNDO',?,?)")){q.setString(1,actor.getName());q.setString(2,target);q.setString(3,"Batch="+data.batch()+", restored="+done+", skipped="+conflicts);q.setLong(4,System.currentTimeMillis());q.executeUpdate();}return true;
            }).whenComplete((ok,err)->Bukkit.getScheduler().runTask(plugin,()->{undoing.remove(data.batch());if(err!=null){Msg.err(actor,"Blocks restored but undo record could not be saved; check console");return;}Msg.ok(actor,"WorldEdit undo: {n} restored, {s} skipped","n",done,"s",conflicts);if(conflicts>0)Msg.info(actor,"Skipped blocks had changed since the saved edit");Msg.info(actor,"Block states only; container contents and block-entity data are not restored");}));
        })).exceptionally(err->{Bukkit.getScheduler().runTask(plugin,()->Msg.err(actor,"Could not load persistent undo history"));plugin.getLogger().severe("Persistent undo failed: "+err.getMessage());return null;});
    }
    @Override public List<String> onTabComplete(CommandSender s,Command c,String a,String[] args){
        if(c.getName().equalsIgnoreCase("undo")){if(args.length!=1||!s.hasPermission(ADMIN)&&!s.hasPermission(OWNER))return List.of();String p=args[0].toLowerCase(Locale.ROOT);return Bukkit.getOnlinePlayers().stream().map(Player::getName).filter(n->n.toLowerCase(Locale.ROOT).startsWith(p)).sorted(String.CASE_INSENSITIVE_ORDER).toList();}
        if(args.length==1)return List.of("worldedit","worldguard","export","purge").stream().filter(x->x.startsWith(args[0].toLowerCase(Locale.ROOT))).toList();
        if(args.length==2&&args[0].equalsIgnoreCase("export"))return List.of("worldedit","worldguard");return List.of();
    }
    public void shutdown(){
        if(flushTask!=-1)Bukkit.getScheduler().cancelTask(flushTask);if(pruneTask!=-1)Bukkit.getScheduler().cancelTask(pruneTask);
        try{db.sync(c->null);}catch(Exception ignored){}flush();try{db.sync(c->null);}catch(Exception ignored){}
        WorldEdit.getInstance().getEventBus().unregister(this);HandlerList.unregisterAll(this);
    }
}