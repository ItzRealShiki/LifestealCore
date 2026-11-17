package de.leon.lifesteal.worldedit;

import de.leon.lifesteal.LifestealCore;
import de.leon.lifesteal.util.Msg;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class BuildToolsModule implements Listener {
    public record Selection(World world, int x1, int y1, int z1, int x2, int y2, int z2) {
        public int minX(){return Math.min(x1,x2);} public int maxX(){return Math.max(x1,x2);}
        public int minY(){return Math.min(y1,y2);} public int maxY(){return Math.max(y1,y2);}
        public int minZ(){return Math.min(z1,z2);} public int maxZ(){return Math.max(z1,z2);}
        public long volume(){return (long)(maxX()-minX()+1)*(maxY()-minY()+1)*(maxZ()-minZ()+1);}
        public boolean contains(org.bukkit.Location location) {
            return location.getWorld()!=null && location.getWorld().equals(world)
                    && location.getBlockX()>=minX()&&location.getBlockX()<=maxX()
                    && location.getBlockY()>=minY()&&location.getBlockY()<=maxY()
                    && location.getBlockZ()>=minZ()&&location.getBlockZ()<=maxZ();
        }
    }
    private record Point(UUID world,int x,int y,int z) {}
    private static final Map<Integer,Material> LEGACY_IDS = Map.ofEntries(
            Map.entry(0,Material.AIR),Map.entry(1,Material.STONE),Map.entry(2,Material.GRASS_BLOCK),
            Map.entry(3,Material.DIRT),Map.entry(4,Material.COBBLESTONE),Map.entry(5,Material.OAK_PLANKS),
            Map.entry(12,Material.SAND),Map.entry(13,Material.GRAVEL),Map.entry(14,Material.GOLD_ORE),
            Map.entry(15,Material.IRON_ORE),Map.entry(16,Material.COAL_ORE),Map.entry(17,Material.OAK_LOG),
            Map.entry(20,Material.GLASS),Map.entry(21,Material.LAPIS_ORE),Map.entry(22,Material.LAPIS_BLOCK),
            Map.entry(24,Material.SANDSTONE),Map.entry(35,Material.WHITE_WOOL),Map.entry(41,Material.GOLD_BLOCK),
            Map.entry(42,Material.IRON_BLOCK),Map.entry(45,Material.BRICKS),Map.entry(48,Material.MOSSY_COBBLESTONE),
            Map.entry(49,Material.OBSIDIAN),Map.entry(56,Material.DIAMOND_ORE),Map.entry(57,Material.DIAMOND_BLOCK),
            Map.entry(58,Material.CRAFTING_TABLE),Map.entry(61,Material.FURNACE),Map.entry(73,Material.REDSTONE_ORE),
            Map.entry(79,Material.ICE),Map.entry(80,Material.SNOW_BLOCK),Map.entry(82,Material.CLAY),
            Map.entry(87,Material.NETHERRACK),Map.entry(88,Material.SOUL_SAND),Map.entry(89,Material.GLOWSTONE),
            Map.entry(98,Material.STONE_BRICKS),Map.entry(103,Material.MELON),Map.entry(110,Material.MYCELIUM),
            Map.entry(112,Material.NETHER_BRICKS),Map.entry(121,Material.END_STONE),Map.entry(129,Material.EMERALD_ORE),
            Map.entry(133,Material.EMERALD_BLOCK),Map.entry(152,Material.REDSTONE_BLOCK),Map.entry(153,Material.NETHER_QUARTZ_ORE),
            Map.entry(155,Material.QUARTZ_BLOCK),Map.entry(159,Material.WHITE_TERRACOTTA),Map.entry(172,Material.TERRACOTTA),
            Map.entry(173,Material.COAL_BLOCK),Map.entry(174,Material.PACKED_ICE)
    );

    private final LifestealCore plugin;
    private final Map<UUID,Point> first = new ConcurrentHashMap<>();
    private final Map<UUID,Point> second = new ConcurrentHashMap<>();
    private final PersistentEditHistory history;
    private final Set<UUID> busy = ConcurrentHashMap.newKeySet();

    public BuildToolsModule(LifestealCore plugin) {
        this.plugin=plugin;
        this.history=new PersistentEditHistory(plugin);
        Bukkit.getPluginManager().registerEvents(this,plugin);
        var command=plugin.getCommand("lwe");
        if(command!=null) {
            command.setExecutor((sender,cmd,label,args)->{
                if(!(sender instanceof Player p)){Msg.err(sender,"Only players can use this command");return true;}
                if(!allowed(p)){Msg.err(p,"Builder.Core or staff permission required");return true;}
                if(args.length==0||args[0].equalsIgnoreCase("help")) {
                    Msg.info(p,"Use the wooden axe to select, then ///set <block>, ///replace <from> <to> or ///undo");
                } else if(args[0].equalsIgnoreCase("wand")) giveWand(p);
                else Msg.info(p,"Usage: /lwe [wand|help]");
                return true;
            });
        }
    }

    public Selection getSelection(Player player) {
        Point a=first.get(player.getUniqueId()), b=second.get(player.getUniqueId());
        if(a==null||b==null||!a.world.equals(b.world)) return null;
        World world=Bukkit.getWorld(a.world); if(world==null)return null;
        return new Selection(world,a.x,a.y,a.z,b.x,b.y,b.z);
    }

    private boolean allowed(Player p) {
        return p.hasPermission("Builder.Core")||p.hasPermission("Admin.Core")||p.hasPermission("Owner.Core");
    }
    private void giveWand(Player p) {
        ItemStack axe=new ItemStack(Material.WOODEN_AXE);
        ItemMeta meta=axe.getItemMeta();
        meta.displayName(Component.text(Msg.sc("WorldEdit Selection Wand")));
        meta.lore(List.of(Component.text(Msg.sc("Left click: position 1"),Msg.NEUTRAL),
                Component.text(Msg.sc("Right click: position 2"),Msg.NEUTRAL)));
        axe.setItemMeta(meta); p.getInventory().addItem(axe);
        Msg.ok(p,"Selection wand added to your inventory");
    }

    @EventHandler(ignoreCancelled=true)
    public void onWandUse(PlayerInteractEvent event) {
        Player p=event.getPlayer();
        if(!allowed(p)||event.getItem()==null||event.getItem().getType()!=Material.WOODEN_AXE)return;
        Action action=event.getAction();
        if(action!=Action.LEFT_CLICK_BLOCK&&action!=Action.RIGHT_CLICK_BLOCK)return;
        Block block=event.getClickedBlock(); if(block==null)return;
        event.setCancelled(true);
        Point point=new Point(block.getWorld().getUID(),block.getX(),block.getY(),block.getZ());
        if(action==Action.LEFT_CLICK_BLOCK) {
            first.put(p.getUniqueId(),point);
            Msg.info(p,"Position 1: {x}, {y}, {z}","x",point.x,"y",point.y,"z",point.z);
        } else {
            second.put(p.getUniqueId(),point);
            Msg.info(p,"Position 2: {x}, {y}, {z}","x",point.x,"y",point.y,"z",point.z);
        }
    }

    @EventHandler
    public void onTripleSlash(PlayerCommandPreprocessEvent event) {
        String raw=event.getMessage();
        if(!raw.startsWith("///"))return;
        Player p=event.getPlayer();
        event.setCancelled(true);
        if(!allowed(p)){Msg.err(p,"Builder.Core or staff permission required");return;}
        String[] args=raw.substring(3).trim().split("\\s+");
        if(args.length==0||args[0].isBlank()){help(p);return;}
        switch(args[0].toLowerCase(Locale.ROOT)) {
            case "wand" -> giveWand(p);
            case "set" -> {
                if(args.length!=2){Msg.info(p,"Usage: ///set <block>");return;}
                Material target=resolve(args[1]);
                if(target==null||!target.isBlock()){Msg.err(p,"Unknown block: "+args[1]);return;}
                edit(p,null,target);
            }
            case "replace" -> {
                if(args.length!=3){Msg.info(p,"Usage: ///replace <from> <to>");return;}
                Material from=resolve(args[1]),to=resolve(args[2]);
                if(from==null||to==null||!from.isBlock()||!to.isBlock()){Msg.err(p,"Unknown block. Use a block name or supported legacy ID");return;}
                edit(p,from,to);
            }
            case "undo" -> restoreHistory(p,false);
            case "redo" -> restoreHistory(p,true);
            case "size" -> {
                Selection s=getSelection(p);if(s==null){Msg.err(p,"Select both positions in the same world first");return;}
                Msg.info(p,"Selection size: {size} blocks","size",s.volume());
            }
            case "help" -> help(p);
            default -> help(p);
        }
    }

    private void help(Player p) {
        Msg.info(p,"Commands: ///wand, ///set <block>, ///replace <from> <to>, ///undo, ///redo, ///size");
    }

    private Material resolve(String token) {
        String value=token.trim().toLowerCase(Locale.ROOT);
        if(value.startsWith("minecraft:"))value=value.substring("minecraft:".length());
        try { return LEGACY_IDS.get(Integer.parseInt(value)); } catch(NumberFormatException ignored) {}
        return Material.matchMaterial(value.replace(' ','_'));
    }

    private void edit(Player p,Material from,Material to) {
        Selection s=getSelection(p);
        if(s==null){Msg.err(p,"Select both positions in the same world first");return;}
        long volume=s.volume();
        long limit=Math.max(1,plugin.getConfig().getLong("worldedit.max-blocks",50000));
        if(volume>limit){Msg.err(p,"Selection exceeds the {limit} block limit","limit",limit);return;}
        UUID id=p.getUniqueId();
        if(!busy.add(id)){Msg.err(p,"You already have an edit running");return;}
        List<PersistentEditHistory.Change> changed=new ArrayList<>();
        int width=s.maxX()-s.minX()+1,height=s.maxY()-s.minY()+1;
        int depth=s.maxZ()-s.minZ()+1;
        int[] index={0};
        new BukkitRunnable() {
            @Override public void run() {
                int processed=0;
                while(index[0]<volume&&processed<1200) {
                    int n=index[0]++;
                    int x=s.minX()+n%width;
                    int y=s.minY()+(n/width)%height;
                    int z=s.minZ()+n/(width*height);
                    Block block=s.world().getBlockAt(x,y,z);
                    if(from==null||block.getType()==from) {
                        if(block.getType()!=to) {
                            String before=block.getBlockData().getAsString();
                            block.setType(to,false);
                            changed.add(new PersistentEditHistory.Change(x,y,z,before,block.getBlockData().getAsString()));
                        }
                    }
                    processed++;
                }
                if(index[0]>=volume) {
                    busy.remove(id);
                    try {
                        if(!changed.isEmpty()) history.save(id,s.world().getUID(),from==null?"SET":"REPLACE",changed);
                        if(p.isOnline())Msg.ok(p,"Edit complete: {count} blocks changed","count",changed.size());
                    } catch(Exception failure) {
                        for(PersistentEditHistory.Change change:changed) {
                            try { s.world().getBlockAt(change.x(),change.y(),change.z())
                                    .setBlockData(Bukkit.createBlockData(change.before()),false); }
                            catch(IllegalArgumentException ignored) { }
                        }
                        plugin.getLogger().log(java.util.logging.Level.SEVERE,"Could not persist WorldEdit history; the edit was rolled back.",failure);
                        if(p.isOnline())Msg.err(p,"The edit could not be saved to history and was rolled back");
                    }
                    cancel();
                }
            }
        }.runTaskTimer(plugin,1L,1L);
    }

    private void restoreHistory(Player p,boolean redo) {
        UUID id=p.getUniqueId();
        if(!busy.add(id)){Msg.err(p,"You already have an edit running");return;}
        PersistentEditHistory.History entry;
        List<PersistentEditHistory.Change> changes;
        try {
            entry=history.latest(id,p.getWorld().getUID(),redo?"UNDONE":"APPLIED");
            if(entry==null){busy.remove(id);Msg.err(p,redo?"There is no edit to redo":"There is no edit to undo");return;}
            changes=history.read(entry);
        } catch(Exception failure) {
            busy.remove(id);
            plugin.getLogger().log(java.util.logging.Level.SEVERE,"Could not read persistent WorldEdit history.",failure);
            Msg.err(p,"History could not be loaded; check the server log");
            return;
        }
        org.bukkit.World targetWorld=Bukkit.getWorld(entry.worldId());
        if(targetWorld==null){busy.remove(id);Msg.err(p,"The history world is not loaded");return;}
        int[] index={redo?0:changes.size()-1};
        new BukkitRunnable() {
            @Override public void run() {
                int processed=0;
                while(index[0]>=0&&index[0]<changes.size()&&processed<1200) {
                    PersistentEditHistory.Change change=changes.get(index[0]);
                    String data=redo?change.after():change.before();
                    try {
                        targetWorld.getBlockAt(change.x(),change.y(),change.z())
                                .setBlockData(Bukkit.createBlockData(data),false);
                    } catch(IllegalArgumentException invalid) {
                        plugin.getLogger().warning("Skipped invalid block data in history "+entry.id()+": "+data);
                    }
                    index[0]+=redo?1:-1;
                    processed++;
                }
                if(index[0]<0||index[0]>=changes.size()) {
                    try {
                        history.setStatus(entry.id(),redo?"APPLIED":"UNDONE");
                        if(p.isOnline())Msg.ok(p,redo?"Last edit redone ({count} blocks)":"Last edit undone ({count} blocks)",
                                "count",changes.size());
                    } catch(Exception failure) {
                        plugin.getLogger().log(java.util.logging.Level.SEVERE,"Could not update WorldEdit history status.",failure);
                        if(p.isOnline())Msg.err(p,"Blocks were restored, but history status could not be saved");
                    }
                    busy.remove(id);cancel();
                }
            }
        }.runTaskTimer(plugin,1L,1L);
    }}
