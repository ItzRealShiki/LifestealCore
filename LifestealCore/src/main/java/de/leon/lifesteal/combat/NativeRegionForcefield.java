package de.leon.lifesteal.combat;

import de.leon.lifesteal.worldguard.RegionProtectionModule;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Client-side glass boundary visualizer backed by LifestealCore's own region engine.
 */
public final class NativeRegionForcefield implements Forcefield {
    private static final BlockData GLASS = Material.RED_STAINED_GLASS.createBlockData();

    private record Pos(int x,int y,int z) { }
    private static final class Shown {
        final UUID world;
        final Set<Pos> blocks=new HashSet<>();
        Shown(UUID world){this.world=world;}
    }

    private final Supplier<FileConfiguration> config;
    private final RegionProtectionModule regions;
    private final Map<UUID,Shown> shown=new HashMap<>();

    public NativeRegionForcefield(Supplier<FileConfiguration> config,RegionProtectionModule regions){
        this.config=config;this.regions=regions;
    }

    @Override
    public boolean isForcefield(Location location){
        return location!=null&&location.getWorld()!=null
                &&regions.hasFlagAt(location,"COMBAT_FORCEFIELD");
    }

    @Override
    public void update(Player player){
        World world=player.getWorld();
        Location location=player.getLocation();
        if(!isForcefield(location)){clear(player);return;}
        int radius=Math.max(2,config.get().getInt("combat.forcefield.radius",3));
        int vertical=Math.max(1,config.get().getInt("combat.forcefield.vertical",4));
        int px=location.getBlockX(),py=location.getBlockY(),pz=location.getBlockZ();
        int minY=Math.max(world.getMinHeight(),py-vertical),maxY=Math.min(world.getMaxHeight()-1,py+vertical);
        Set<Pos> wanted=new HashSet<>();
        for(int x=px-radius;x<=px+radius;x++)for(int z=pz-radius;z<=pz+radius;z++){
            if(!world.isChunkLoaded(x>>4,z>>4))continue;
            for(int y=minY;y<=maxY;y++){
                Location at=new Location(world,x,y,z);
                if(!regions.hasFlagAt(at,"COMBAT_FORCEFIELD"))continue;
                boolean border=!regions.hasFlagAt(new Location(world,x+1,y,z),"COMBAT_FORCEFIELD")
                        ||!regions.hasFlagAt(new Location(world,x-1,y,z),"COMBAT_FORCEFIELD")
                        ||!regions.hasFlagAt(new Location(world,x,y,z+1),"COMBAT_FORCEFIELD")
                        ||!regions.hasFlagAt(new Location(world,x,y,z-1),"COMBAT_FORCEFIELD");
                if(border&&world.getBlockAt(x,y,z).getType().isAir())wanted.add(new Pos(x,y,z));
            }
        }
        Shown current=shown.computeIfAbsent(player.getUniqueId(),ignored->new Shown(world.getUID()));
        if(!current.world.equals(world.getUID())){clear(player);current=new Shown(world.getUID());shown.put(player.getUniqueId(),current);}
        Iterator<Pos> iterator=current.blocks.iterator();
        while(iterator.hasNext()){
            Pos pos=iterator.next();
            if(!wanted.contains(pos)){restore(player,world,pos);iterator.remove();}
        }
        for(Pos pos:wanted)if(current.blocks.add(pos))
            player.sendBlockChange(new Location(world,pos.x(),pos.y(),pos.z()),GLASS);
    }

    @Override
    public void clear(Player player){
        Shown current=shown.remove(player.getUniqueId());
        if(current==null)return;
        World world=org.bukkit.Bukkit.getWorld(current.world);
        if(world==null||!player.isOnline()||!player.getWorld().equals(world))return;
        for(Pos pos:current.blocks)restore(player,world,pos);
    }

    private void restore(Player player,World world,Pos pos){
        player.sendBlockChange(new Location(world,pos.x(),pos.y(),pos.z()),
                world.getBlockAt(pos.x(),pos.y(),pos.z()).getBlockData());
    }
}
