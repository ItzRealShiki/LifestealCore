package de.leon.lifesteal.admin;

import de.leon.lifesteal.LifestealCore;
import de.leon.lifesteal.data.Database;
import de.leon.lifesteal.gui.Menu;
import de.leon.lifesteal.gui.MenuListener;
import de.leon.lifesteal.util.Msg;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.node.types.PermissionNode;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.RegisteredServiceProvider;
import java.sql.*;
import java.util.*;

public final class AdminManager {
 private static final String ADMIN="Admin.Core", OWNER="Owner.Core";
 private final LifestealCore plugin; private final Database db; private final LuckPerms lp;
 public AdminManager(LifestealCore plugin,Database db){
  this.plugin=plugin;this.db=db;RegisteredServiceProvider<LuckPerms> p=Bukkit.getServicesManager().getRegistration(LuckPerms.class);lp=p==null?null:p.getProvider();
  try{db.sync(c->{try(Statement s=c.createStatement()){String id=db.type()==Database.Type.SQLITE?"INTEGER PRIMARY KEY AUTOINCREMENT":"BIGINT PRIMARY KEY AUTO_INCREMENT";
   s.executeUpdate("CREATE TABLE IF NOT EXISTS "+db.t("admin_logs")+" (id "+id+",category VARCHAR(24),actor_name VARCHAR(16),target_name VARCHAR(16),action VARCHAR(80),details TEXT,created BIGINT)");
   s.executeUpdate("CREATE TABLE IF NOT EXISTS "+db.t("admin_log_delete_requests")+" (id "+id+",log_id BIGINT,requester_name VARCHAR(16),status VARCHAR(16),reviewer_name VARCHAR(16),created BIGINT,reviewed BIGINT DEFAULT 0)");
   s.executeUpdate("CREATE TABLE IF NOT EXISTS "+db.t("rank_priorities")+" (group_name VARCHAR(64) PRIMARY KEY,priority INT NOT NULL DEFAULT 100)");
  }return null;});}catch(Exception e){plugin.getLogger().severe("Admin schema failed: "+e.getMessage());}
 }
 private void owners(String m){for(Player p:Bukkit.getOnlinePlayers())if(p.hasPermission(OWNER))Msg.raw(p,Component.text("[LSC] "+m,NamedTextColor.GOLD));}
 private boolean admin(CommandSender s){if(s.hasPermission(ADMIN))return true;Msg.err(s,"You don't have permission to do that");return false;}
 public void log(String category,Player actor,String target,String action,String details){db.run(c->{try(PreparedStatement q=c.prepareStatement("INSERT INTO "+db.t("admin_logs")+" (category,actor_name,target_name,action,details,created) VALUES (?,?,?,?,?,?)")){q.setString(1,category);q.setString(2,actor==null?"CONSOLE":actor.getName());q.setString(3,target);q.setString(4,action);q.setString(5,details);q.setLong(6,System.currentTimeMillis());q.executeUpdate();}});owners("Admin action: "+action+(target==null?"":" → "+target));}
 public TabExecutor adminCommand(){return new TabExecutor(){
  public boolean onCommand(CommandSender s,Command c,String l,String[] a){
   if(a.length>0&&(a[0].equalsIgnoreCase("approve")||a[0].equalsIgnoreCase("reject")))return review(s,a,a[0].equalsIgnoreCase("approve"));
   if(!admin(s))return true;if(!(s instanceof Player p)){s.sendMessage("Use in-game.");return true;}
   if(a.length==0)MenuListener.open(p,new Home());else if(a[0].equalsIgnoreCase("logs"))MenuListener.open(p,new Logs(null,0));else if(a[0].equalsIgnoreCase("check")&&a.length>1)MenuListener.open(p,new Logs(a[1],0));else Msg.info(s,"Usage: /admin [logs|check <player>] | /admin approve|reject <requestId>");return true;
  }
  public List<String> onTabComplete(CommandSender s,Command c,String l,String[] a){return a.length==1?List.of("logs","check","approve","reject"):List.of();}
 };}
 private boolean review(CommandSender s,String[] a,boolean yes){
  if(!s.hasPermission(OWNER)){Msg.err(s,"Only Owner.Core can approve log deletion");return true;}if(a.length!=2){Msg.info(s,"Usage: /admin "+(yes?"approve":"reject")+" <requestId>");return true;}
  long id;try{id=Long.parseLong(a[1]);}catch(Exception e){Msg.err(s,"Invalid request ID");return true;}
  db.query(c->{try(PreparedStatement q=c.prepareStatement("SELECT log_id,status FROM "+db.t("admin_log_delete_requests")+" WHERE id=?")){q.setLong(1,id);try(ResultSet r=q.executeQuery()){return r.next()?new String[]{r.getString(1),r.getString(2)}:null;}}}).thenAccept(row->Bukkit.getScheduler().runTask(plugin,()->{
   if(row==null||!"PENDING".equals(row[1])){Msg.err(s,"Request missing or already reviewed");return;}long log=Long.parseLong(row[0]);
   db.run(c->{try(PreparedStatement q=c.prepareStatement("UPDATE "+db.t("admin_log_delete_requests")+" SET status=?,reviewer_name=?,reviewed=? WHERE id=? AND status='PENDING'")){q.setString(1,yes?"APPROVED":"REJECTED");q.setString(2,s.getName());q.setLong(3,System.currentTimeMillis());q.setLong(4,id);q.executeUpdate();}
    if(yes){try(PreparedStatement q=c.prepareStatement("DELETE FROM "+db.t("admin_logs")+" WHERE id=?")){q.setLong(1,log);q.executeUpdate();}
     try(PreparedStatement q=c.prepareStatement("INSERT INTO "+db.t("admin_logs")+" (category,actor_name,action,details,created) VALUES ('ADMIN',?,'APPROVED LOG DELETION',?,?)")){q.setString(1,s.getName());q.setString(2,"Removed entry #"+log+" after owner approval #"+id);q.setLong(3,System.currentTimeMillis());q.executeUpdate();}}
   });Msg.info(s,yes?"Deletion approved; separate audit record retained.":"Deletion rejected.");
  }));return true;
 }
 public TabExecutor permissionCommand(){return new TabExecutor(){
  public boolean onCommand(CommandSender s,Command c,String l,String[] a){if(!admin(s))return true;if(lp==null){Msg.err(s,"LuckPerms is not installed");return true;}if(a.length!=3||!(a[2].equalsIgnoreCase("add")||a[2].equalsIgnoreCase("remove"))){Msg.info(s,"Usage: /permission <group> <permission.node> <add|remove>");return true;}
   String g=a[0],n=a[1];boolean add=a[2].equalsIgnoreCase("add");lp.getGroupManager().modifyGroup(g,x->{if(add)x.data().add(PermissionNode.builder(n).build());else x.data().remove(PermissionNode.builder(n).build());}).thenRun(()->{log("PERMISSIONS",s instanceof Player p?p:null,null,(add?"Added ":"Removed ")+n,"Group="+g);s.sendMessage("Permission updated for "+g);}).exceptionally(e->{s.sendMessage("LuckPerms error: "+e.getMessage());return null;});return true;}
  public List<String> onTabComplete(CommandSender s,Command c,String l,String[] a){if(a.length==1&&lp!=null)return lp.getGroupManager().getLoadedGroups().stream().map(g->g.getName()).toList();if(a.length==3)return List.of("add","remove");return List.of();}
 };}
 public TabExecutor ranksCommand(){return new TabExecutor(){public boolean onCommand(CommandSender s,Command c,String l,String[] a){if(!admin(s))return true;if(lp==null){Msg.err(s,"LuckPerms is not installed");return true;}if(!(s instanceof Player p)){Msg.err(s,"Players only");return true;}if(a.length!=1||!a[0].equalsIgnoreCase("editor")){Msg.info(s,"Usage: /ranks editor");return true;}MenuListener.open(p,new Ranks());return true;}public List<String> onTabComplete(CommandSender s,Command c,String l,String[] a){return a.length==1?List.of("editor"):List.of();}};}
 private ItemStack item(Material mat,String title,String... lore){ItemStack i=new ItemStack(mat);ItemMeta m=i.getItemMeta();m.displayName(Msg.item(title,NamedTextColor.WHITE));List<Component>x=new ArrayList<>();for(String t:lore)x.add(Msg.item(t,NamedTextColor.GRAY));m.lore(x);i.setItemMeta(m);return i;}
 private abstract class Gui extends Menu{Gui(String t){create(54,Component.text(Msg.sc(t),NamedTextColor.DARK_GRAY));}public void onClick(Player p,InventoryClickEvent e){if(!p.hasPermission(ADMIN)){p.closeInventory();return;}if(e.isLeftClick()||e.isRightClick())click(p,e.getRawSlot(),e.isRightClick());}abstract void click(Player p,int slot,boolean right);}
 private final class Home extends Gui{Home(){super("Admin Control");set(10,item(Material.COMMAND_BLOCK,"Plugins & Diagnostics","/plugin, /console-error"));set(12,item(Material.BOOK,"Audit Logs","View admin history"));set(14,item(Material.PLAYER_HEAD,"Player History","/admin check <player>"));set(16,item(Material.EMERALD,"Economy","/eco money|gems give|remove|check"));set(28,item(Material.NAME_TAG,"Permission Manager","/permission <group> <node> <add|remove>"));set(30,item(Material.NETHER_STAR,"Rank Priority Editor","/ranks editor"));set(32,item(Material.CHEST,"Shop & Items","Existing module commands"));set(34,item(Material.REDSTONE,"Settings","/lsc reload | /lsc save"));set(49,item(Material.BARRIER,"Close"));}void click(Player p,int s,boolean r){if(s==12)MenuListener.open(p,new Logs(null,0));else if(s==10){p.closeInventory();p.performCommand("plugin");}else if(s==30)MenuListener.open(p,new Ranks());else if(s==49)p.closeInventory();else if(s==14||s==28){p.closeInventory();p.sendMessage(s==14?"/admin check <player>":"/permission <group> <permission.node> <add|remove>");}}}
 private record Entry(long id,String category,String actor,String target,String action,String details){}
 private final class Logs extends Gui{final String target;final int page;List<Entry> rows=List.of();Logs(String t,int p){super(t==null?"Admin Audit Logs":"Player Audit History");target=t;page=p;load();}
  void load(){db.query(c->{List<Entry>x=new ArrayList<>();String sql="SELECT id,category,actor_name,target_name,action,details FROM "+db.t("admin_logs")+(target==null?"":" WHERE target_name=? COLLATE NOCASE")+" ORDER BY id DESC LIMIT 45 OFFSET ?";try(PreparedStatement q=c.prepareStatement(sql)){int n=1;if(target!=null)q.setString(n++,target);q.setInt(n,page*45);try(ResultSet r=q.executeQuery()){while(r.next())x.add(new Entry(r.getLong(1),r.getString(2),r.getString(3),r.getString(4),r.getString(5),r.getString(6)));}}return x;}).thenAccept(x->Bukkit.getScheduler().runTask(plugin,()->{rows=x;for(int i=0;i<x.size();i++){Entry e=x.get(i);set(i,item(Material.PAPER,"#"+e.id+" "+e.action,"Category: "+e.category,"By: "+e.actor,"Target: "+Objects.toString(e.target,"-"),Objects.toString(e.details,""),"Right-click: request deletion"));}set(45,item(Material.ARROW,"Previous"));set(49,item(Material.BARRIER,"Close"));set(53,item(Material.ARROW,"Next"));}));}
  void click(Player p,int s,boolean right){if(s==49){p.closeInventory();return;}if(s==45&&page>0){MenuListener.open(p,new Logs(target,page-1));return;}if(s==53&&rows.size()==45){MenuListener.open(p,new Logs(target,page+1));return;}if(s<0||s>=rows.size()||!right)return;Entry e=rows.get(s);if(e.action.startsWith("APPROVED LOG DELETION")){Msg.err(p,"This audit entry is protected");return;}if(Bukkit.getOnlinePlayers().stream().noneMatch(x->x.hasPermission(OWNER))){Msg.err(p,"No Owner.Core member online; deletion is unavailable");return;}
   db.run(c->{try(PreparedStatement q=c.prepareStatement("INSERT INTO "+db.t("admin_log_delete_requests")+" (log_id,requester_name,status,created) VALUES (?,?,'PENDING',?)",Statement.RETURN_GENERATED_KEYS)){q.setLong(1,e.id);q.setString(2,p.getName());q.setLong(3,System.currentTimeMillis());q.executeUpdate();try(ResultSet k=q.getGeneratedKeys()){long req=k.next()?k.getLong(1):-1;Bukkit.getScheduler().runTask(plugin,()->{p.sendMessage("Deletion request #"+req+" submitted.");owners("VERY CRITICAL: "+p.getName()+" requests deletion of log #"+e.id+". Review request #"+req+" with /admin approve or /admin reject.");});}}});
  }}
 private final class Ranks extends Gui{List<String> groups=lp.getGroupManager().getLoadedGroups().stream().map(g->g.getName()).sorted().toList();Ranks(){super("Rank Priority Editor");for(int i=0;i<Math.min(45,groups.size());i++)set(i,item(Material.NAME_TAG,groups.get(i),"Left-click: priority -1","Right-click: priority +1","Lower number = higher TAB position"));set(49,item(Material.BARRIER,"Close"));}void click(Player p,int s,boolean right){if(s==49){p.closeInventory();return;}if(s<0||s>=groups.size())return;String g=groups.get(s);int delta=right?1:-1;db.run(c->{int old=100;try(PreparedStatement q=c.prepareStatement("SELECT priority FROM "+db.t("rank_priorities")+" WHERE group_name=?")){q.setString(1,g);try(ResultSet r=q.executeQuery()){if(r.next())old=r.getInt(1);}}int v=Math.max(1,Math.min(10000,old+delta));String sql=db.type()==Database.Type.SQLITE?"INSERT INTO "+db.t("rank_priorities")+" (group_name,priority) VALUES (?,?) ON CONFLICT(group_name) DO UPDATE SET priority=excluded.priority":"INSERT INTO "+db.t("rank_priorities")+" (group_name,priority) VALUES (?,?) ON DUPLICATE KEY UPDATE priority=VALUES(priority)";try(PreparedStatement q=c.prepareStatement(sql)){q.setString(1,g);q.setInt(2,v);q.executeUpdate();}});log("RANKS",p,null,"Changed TAB priority","Group="+g+", adjustment="+delta);p.sendMessage("Rank priority updated. Lower number = higher TAB position. Custom TAB must read "+db.t("rank_priorities")+".");}}
}
