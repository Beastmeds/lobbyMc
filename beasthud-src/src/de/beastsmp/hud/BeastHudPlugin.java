package de.beastsmp.hud;

import kr.toxicity.hud.api.BetterHud;
import kr.toxicity.hud.api.bukkit.event.PluginReloadedEvent;
import kr.toxicity.hud.api.player.HudPlayer;
import kr.toxicity.hud.api.update.UpdateEvent;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.*;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Directional;
import org.bukkit.boss.*;
import org.bukkit.command.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Transformation;
import org.joml.Vector3f;
import org.joml.Quaternionf;
import java.io.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

public final class BeastHudPlugin extends JavaPlugin implements Listener, CommandExecutor, TabCompleter {
    enum Theme {
        HALLOWEEN("Halloween", "beastsmp_halloween", "beast_bats", Material.JACK_O_LANTERN, NamedTextColor.LIGHT_PURPLE, BarColor.PURPLE),
        STANDARD("Standard", "beastsmp_standard", "beast_sparkles", Material.NETHER_STAR, NamedTextColor.AQUA, BarColor.BLUE),
        WEIHNACHTEN("Weihnachten", "beastsmp_weihnachten", "beast_snow", Material.SNOWBALL, NamedTextColor.WHITE, BarColor.WHITE);
        final String label,hud,effect; final Material icon; final NamedTextColor color; final BarColor bar;
        Theme(String l,String h,String e,Material i,NamedTextColor c,BarColor b){label=l;hud=h;effect=e;icon=i;color=c;bar=b;}
        static Theme parse(String s){for(Theme t:values())if(t.name().equalsIgnoreCase(s)||t.label.equalsIgnoreCase(s))return t; if(s.equalsIgnoreCase("weihnacht")||s.equalsIgnoreCase("winter"))return WEIHNACHTEN;return null;}
    }
    static final class Profile {
        volatile Theme theme=Theme.HALLOWEEN;
        final Set<Integer> found=ConcurrentHashMap.newKeySet();
        volatile long nextEffect=0;
    }
    static final class DesignMenu implements InventoryHolder {
        Inventory inventory;
        public Inventory getInventory(){return inventory;}
    }
    record Pumpkin(int id, BlockDisplay display, Interaction hitbox) {}
    private final Map<UUID,Profile> profiles=new ConcurrentHashMap<>();
    private final Map<UUID,BossBar> bars=new HashMap<>();
    private final List<Pumpkin> pumpkins=new ArrayList<>();
    private NamespacedKey questKey;
    private File storageFile;
    private YamlConfiguration storage;
    private PlaceholderExpansion expansion;
    private int questTotal=10;
    private BeastLobbyFeatures lobbyFeatures;
    private BeastThemePacks themePacks;

    @Override public void onEnable(){
        saveDefaultConfig(); questTotal=getConfig().getInt("quest-count",10);
        questKey=new NamespacedKey(this,"pumpkin-id");
        storageFile=new File(getDataFolder(),"players.yml");storage=YamlConfiguration.loadConfiguration(storageFile);
        if(storage.isConfigurationSection("players"))for(String key:storage.getConfigurationSection("players").getKeys(false))try{
            Profile p=new Profile();Theme t=Theme.parse(storage.getString("players."+key+".theme","halloween"));if(t!=null)p.theme=t;
            p.found.addAll(storage.getIntegerList("players."+key+".found"));profiles.put(UUID.fromString(key),p);
        }catch(IllegalArgumentException ex){getLogger().warning("Invalid saved player id: "+key);}
        getServer().getPluginManager().registerEvents(this,this);
        for(String name:List.of("design","kuerbisse","beasthud")){Objects.requireNonNull(getCommand(name)).setExecutor(this);getCommand(name).setTabCompleter(this);}
        expansion=new PlaceholderExpansion(){
            public String getIdentifier(){return "beast";} public String getAuthor(){return "BeastSMP";} public String getVersion(){return "1.0.0";} public boolean persist(){return true;}
            public String onRequest(OfflinePlayer player,String param){
                if(player==null)return "";Profile p=profile(player.getUniqueId());
                return switch(param.toLowerCase(Locale.ROOT)){case "quest_progress"->p.found.size()+" / "+questTotal;case "quest_found"->""+p.found.size();case "quest_total"->""+questTotal;case "design"->p.theme.label;default->null;};
            }
        };expansion.register();
        lobbyFeatures=new BeastLobbyFeatures(this);lobbyFeatures.enable();
        themePacks=new BeastThemePacks(this);themePacks.enable();
        getServer().getScheduler().runTaskLater(this,()->{
            createQuest();for(Player p:Bukkit.getOnlinePlayers())apply(p,false);
        },40L);
        getServer().getScheduler().runTaskTimer(this,()->{
            long now=System.currentTimeMillis();
            for(Player p:Bukkit.getOnlinePlayers()){
                Profile data=profile(p.getUniqueId());
                if(now>=data.nextEffect){showEffect(p);data.nextEffect=now+ThreadLocalRandom.current().nextLong(25000,45001);}
            }
        },200L,20L);
        getLogger().info("Three HUD designs, personal greetings and a "+questTotal+"-pumpkin quest enabled.");
    }
    @Override public void onDisable(){
        if(themePacks!=null)themePacks.disable();
        if(lobbyFeatures!=null)lobbyFeatures.disable();
        savePlayers();bars.values().forEach(BossBar::removeAll);bars.clear();
        for(Pumpkin p:pumpkins){p.display.remove();p.hitbox.remove();}pumpkins.clear();
        if(expansion!=null)expansion.unregister();
    }
    Profile profile(UUID id){return profiles.computeIfAbsent(id,k->new Profile());}
    private void savePlayers(){
        for(var entry:profiles.entrySet()){
            String path="players."+entry.getKey();storage.set(path+".theme",entry.getValue().theme.name().toLowerCase(Locale.ROOT));
            storage.set(path+".found",entry.getValue().found.stream().sorted().toList());
        }
        try{storage.save(storageFile);}catch(IOException ex){getLogger().log(java.util.logging.Level.SEVERE,"Could not save HUD profiles",ex);}
    }
    private HudPlayer hudPlayer(Player p){return BetterHud.getInstance().getPlayerManager().getHudPlayer(p.getUniqueId());}
    private void apply(Player p,boolean welcome){
        HudPlayer hp=hudPlayer(p);if(hp==null){getLogger().warning("HUD player not ready: "+p.getName());return;}
        Profile data=profile(p.getUniqueId());
        for(Theme t:Theme.values()){
            var hud=BetterHud.getInstance().getHudManager().getHud(t.hud);
            if(hud==null){getLogger().warning("HUD definition missing: "+t.hud);continue;}
            hud.remove(hp);if(t==data.theme)hud.add(hp);
        }
        hp.setHudEnabled(true);hp.save();
        BossBar old=bars.remove(p.getUniqueId());if(old!=null)old.removeAll();
        BossBar bar=Bukkit.createBossBar("BeastSMP",data.theme.bar,BarStyle.SOLID);bar.setProgress(1.0);bar.setVisible(false);bar.addPlayer(p);bars.put(p.getUniqueId(),bar);
        data.nextEffect=System.currentTimeMillis()+ThreadLocalRandom.current().nextLong(18000,30001);
        updateVisibility(p);
        if(themePacks!=null)themePacks.send(p);
        if(welcome)greet(p);
    }
    private void showEffect(Player p){
        HudPlayer hp=hudPlayer(p);if(hp==null)return;
        var popup=BetterHud.getInstance().getPopupManager().getPopup(profile(p.getUniqueId()).theme.effect);
        if(popup!=null)popup.show(UpdateEvent.EMPTY,hp);
    }
    private void greet(Player p){
        Theme t=profile(p.getUniqueId()).theme;
        p.showTitle(Title.title(Component.text("Willkommen zurück",t.color),Component.text(p.getName()+"!",NamedTextColor.WHITE),Title.Times.times(Duration.ofMillis(400),Duration.ofSeconds(3),Duration.ofMillis(700))));
        p.sendMessage(Component.text("Willkommen zurück, "+p.getName()+"!",t.color));
        p.sendMessage(Component.text("Dein Design: "+t.label+" · Wechseln mit /design",NamedTextColor.GRAY));
        showEffect(p);
        p.playSound(p.getLocation(),Sound.BLOCK_AMETHYST_BLOCK_CHIME,.35f,1.2f);
    }
    @EventHandler public void reloaded(PluginReloadedEvent event){getServer().getScheduler().runTask(this,()->{if(themePacks!=null)themePacks.rebuild();});getServer().getScheduler().runTaskLater(this,()->{for(Player p:Bukkit.getOnlinePlayers())apply(p,false);},20L);}
    @EventHandler public void join(PlayerJoinEvent e){
        profile(e.getPlayer().getUniqueId());
        getServer().getScheduler().runTaskLater(this,()->{if(e.getPlayer().isOnline())apply(e.getPlayer(),true);},70L);
    }
    @EventHandler public void quit(PlayerQuitEvent e){
        if(themePacks!=null)themePacks.forget(e.getPlayer());
        BossBar b=bars.remove(e.getPlayer().getUniqueId());if(b!=null)b.removeAll();savePlayers();
    }
    void menu(Player p){lobbyFeatures.openDesigns(p);}
    void choose(Player p,Theme t){profile(p.getUniqueId()).theme=t;savePlayers();apply(p,false);lobbyFeatures.refreshHotbar(p);showEffect(p);p.sendMessage(Component.text("Design gewählt: "+t.label,t.color));p.playSound(p.getLocation(),Sound.UI_BUTTON_CLICK,.4f,1.2f);}
    private void updateVisibility(Player player){
        Profile profile=profile(player.getUniqueId());
        for(Pumpkin p:pumpkins){boolean show=profile.theme==Theme.HALLOWEEN&&!profile.found.contains(p.id);
            for(Entity entity:List.of(p.display,p.hitbox)){if(show)player.showEntity(this,entity);else player.hideEntity(this,entity);}
        }
    }
    private Location safeLocation(World w,double cx,double cy,double cz,int dx,int dz){
        for(int radius=0;radius<=3;radius++)for(int ox=-radius;ox<=radius;ox++)for(int oz=-radius;oz<=radius;oz++){
            if(radius>0&&Math.max(Math.abs(ox),Math.abs(oz))!=radius)continue;
            int x=(int)Math.floor(cx)+dx+ox,z=(int)Math.floor(cz)+dz+oz;
            for(int d=0;d<=8;d++)for(int sign:d==0?new int[]{1}:new int[]{-1,1}){
                int y=(int)Math.floor(cy)+d*sign;
                if(y<=w.getMinHeight()||y+1>=w.getMaxHeight())continue;
                if(w.getBlockAt(x,y-1,z).getType().isSolid()&&w.getBlockAt(x,y,z).isPassable()&&!w.getBlockAt(x,y,z).isLiquid()&&w.getBlockAt(x,y+1,z).isPassable())return new Location(w,x+.15,y+.05,z+.15);
            }
        }
        return null;
    }
    private void createQuest(){
        World w=Bukkit.getWorld(getConfig().getString("world","world"));if(w==null){getLogger().severe("Quest world is missing");return;}
        // Clean only our tagged displays/interactions left by an earlier plugin version.
        for(Entity e:new ArrayList<>(w.getEntities()))if((e instanceof BlockDisplay||e instanceof Interaction)&&e.getPersistentDataContainer().has(questKey,PersistentDataType.INTEGER))e.remove();
        int[][] offsets={{-12,0},{-8,14},{6,18},{18,9},{22,-6},{9,-20},{-12,-22},{-23,-10},{-28,12},{0,30}};
        double cx=getConfig().getDouble("spawn.x",-139.91855815080723),cy=getConfig().getDouble("spawn.y",-56),cz=getConfig().getDouble("spawn.z",-25.980154437064236);
        for(int id=1;id<=questTotal;id++){
            String path="pumpkins."+id;Location loc;
            if(getConfig().contains(path+".x"))loc=new Location(w,getConfig().getDouble(path+".x"),getConfig().getDouble(path+".y"),getConfig().getDouble(path+".z"));
            else {int[] off=offsets[(id-1)%offsets.length];loc=safeLocation(w,cx,cy,cz,off[0],off[1]);if(loc==null){getLogger().severe("No safe location for pumpkin "+id);continue;}
                getConfig().set(path+".x",loc.getX());getConfig().set(path+".y",loc.getY());getConfig().set(path+".z",loc.getZ());}
            final int qid=id;
            BlockDisplay display=w.spawn(loc,BlockDisplay.class,d->{
                Directional block=(Directional)Material.JACK_O_LANTERN.createBlockData();double dx=cx-loc.getX(),dz=cz-loc.getZ();block.setFacing(Math.abs(dx)>Math.abs(dz)?(dx>0?BlockFace.EAST:BlockFace.WEST):(dz>0?BlockFace.SOUTH:BlockFace.NORTH));d.setBlock(block);
                d.setTransformation(new Transformation(new Vector3f(),new Quaternionf(),new Vector3f(.65f),new Quaternionf()));
                d.setBrightness(new Display.Brightness(15,15));d.setViewRange(.5f);d.setPersistent(false);d.getPersistentDataContainer().set(questKey,PersistentDataType.INTEGER,qid);
            });
            Interaction hitbox=w.spawn(loc.clone().add(.325,0,.325),Interaction.class,e->{e.setInteractionWidth(.9f);e.setInteractionHeight(.9f);e.setResponsive(true);e.setPersistent(false);e.getPersistentDataContainer().set(questKey,PersistentDataType.INTEGER,qid);});
            pumpkins.add(new Pumpkin(id,display,hitbox));getLogger().info("Pumpkin "+id+" at "+loc.getX()+","+loc.getY()+","+loc.getZ());
        }
        saveConfig();getLogger().info("Quest ready: "+pumpkins.size()+" / "+questTotal+" pumpkins, no world blocks changed.");
    }
    private void collect(Player p,Entity e){
        Integer id=e.getPersistentDataContainer().get(questKey,PersistentDataType.INTEGER);if(id==null)return;
        if(profile(p.getUniqueId()).theme!=Theme.HALLOWEEN){p.sendMessage(Component.text("Wähle für die Kürbis-Suche /design halloween",NamedTextColor.GOLD));return;}
        if(p.getWorld()!=e.getWorld()||p.getLocation().distanceSquared(e.getLocation())>36)return;
        Profile profile=profile(p.getUniqueId());if(!profile.found.add(id))return;
        savePlayers();updateVisibility(p);
        p.sendMessage(Component.text("Kürbis gefunden! "+profile.found.size()+" / "+questTotal,NamedTextColor.GOLD));
        p.playSound(p.getLocation(),Sound.ENTITY_EXPERIENCE_ORB_PICKUP,.7f,1.2f);showEffect(p);
        if(profile.found.size()>=questTotal){
            p.showTitle(Title.title(Component.text("Alle Kürbisse gefunden!",NamedTextColor.GOLD),Component.text("Halloween-Quest abgeschlossen",NamedTextColor.LIGHT_PURPLE)));
            p.playSound(p.getLocation(),Sound.UI_TOAST_CHALLENGE_COMPLETE,.5f,1.1f);
            p.spawnParticle(Particle.TOTEM_OF_UNDYING,p.getLocation().add(0,1,0),35,.6,.7,.6,.02);
        }
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=false) public void interact(PlayerInteractEntityEvent e){if(e.getRightClicked().getPersistentDataContainer().has(questKey,PersistentDataType.INTEGER)){e.setCancelled(true);collect(e.getPlayer(),e.getRightClicked());}}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=false) public void interactAt(PlayerInteractAtEntityEvent e){if(e.getRightClicked().getPersistentDataContainer().has(questKey,PersistentDataType.INTEGER)){e.setCancelled(true);collect(e.getPlayer(),e.getRightClicked());}}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=false) public void attack(EntityDamageByEntityEvent e){if(e.getEntity().getPersistentDataContainer().has(questKey,PersistentDataType.INTEGER)){e.setCancelled(true);if(e.getDamager() instanceof Player p)collect(p,e.getEntity());}}
    void questInfo(Player p){Profile data=profile(p.getUniqueId());p.sendMessage(Component.text("Halloween-Kürbissuche: "+data.found.size()+" / "+questTotal,NamedTextColor.GOLD));p.sendMessage(Component.text("Suche die leuchtenden Kürbisse rund um den Lobby-Spawn und klicke sie an. Jeder zählt nur einmal. Aktiviere dafür das Halloween-Design mit /design halloween.",NamedTextColor.GRAY));}
    @Override public boolean onCommand(CommandSender sender,Command command,String label,String[] args){
        if(command.getName().equals("beasthud")){
            if(!(sender instanceof ConsoleCommandSender)&&!sender.hasPermission("beasthud.admin")){sender.sendMessage("Nutze /design oder /kuerbisse.");return true;}
            if(args.length>=2&&args[0].equalsIgnoreCase("preview")){
                Player p=Bukkit.getPlayerExact(args[1]);if(p==null){sender.sendMessage("Player offline");return true;}
                if(args.length>=3){Theme t=Theme.parse(args[2]);if(t==null){sender.sendMessage("Unknown design");return true;}choose(p,t);}greet(p);sender.sendMessage("Preview shown to "+p.getName());return true;
            }
            if(args.length==2&&args[0].equalsIgnoreCase("inspect")){
                Player p=Bukkit.getPlayerExact(args[1]);if(p==null){sender.sendMessage("Player offline");return true;}
                Profile data=profile(p.getUniqueId());HudPlayer hp=hudPlayer(p);sender.sendMessage("BeastHUD player="+p.getName()+" theme="+data.theme+" quest="+data.found.size()+"/"+questTotal+" huds="+(hp==null?"none":hp.getHuds().stream().map(h->h.getName()).toList())+" bossbar="+bars.containsKey(p.getUniqueId()));return true;
            }
            sender.sendMessage("BeastHUD: "+pumpkins.size()+"/"+questTotal+" pumpkins; "+profiles.size()+" profiles; three designs.");for(Pumpkin p:pumpkins)sender.sendMessage("#"+p.id+" "+p.display.getLocation().toVector());return true;
        }
        if(!(sender instanceof Player p)){sender.sendMessage("Only players can open the design menu.");return true;}
        if(command.getName().equals("kuerbisse")){questInfo(p);return true;}
        if(args.length==0){menu(p);return true;}
        Theme theme=Theme.parse(args[0]);if(theme==null)p.sendMessage(Component.text("Designs: halloween, standard, weihnachten",NamedTextColor.GRAY));else choose(p,theme);return true;
    }
    @Override public List<String> onTabComplete(CommandSender sender,Command c,String alias,String[] args){if(c.getName().equals("design")&&args.length==1)return List.of("halloween","standard","weihnachten").stream().filter(s->s.startsWith(args[0].toLowerCase(Locale.ROOT))).toList();return List.of();}
}
