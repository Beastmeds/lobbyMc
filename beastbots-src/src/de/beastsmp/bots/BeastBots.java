package de.beastsmp.bots;

import io.github.hello09x.fakeplayer.core.Main;
import io.github.hello09x.fakeplayer.core.manager.FakeplayerManager;
import com.destroystokyo.paper.profile.ProfileProperty;
import com.google.gson.JsonParser;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import net.kyori.adventure.text.Component;
import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.*;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.*;
import java.util.concurrent.*;

/** Managed server-side players; no external accounts, chat automation or world edits. */
public final class BeastBots extends JavaPlugin implements Listener,CommandExecutor,TabCompleter {
    private FakeplayerManager manager;
    private final Map<String,CompletableFuture<ProfileProperty>> skinCache=new ConcurrentHashMap<>();
    private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();
    private final Set<String> spawning=ConcurrentHashMap.newKeySet();
    private volatile Set<String> desired=Set.of();
    private final Set<String> yielded=ConcurrentHashMap.newKeySet();
    private final Set<UUID> managed=new HashSet<>();
    private final Set<String> reserved=new HashSet<>();
    private boolean closing;
    public void onEnable(){
        saveDefaultConfig();manager=Main.getInjector().getInstance(FakeplayerManager.class);
        Objects.requireNonNull(getCommand("beastbots")).setExecutor(this);getCommand("beastbots").setTabCompleter(this);
        Bukkit.getPluginManager().registerEvents(this,this);updateDesired();
        Bukkit.getScheduler().runTaskTimer(this,this::reconcile,60L,20L);
        getLogger().info("BeastBots enabled; configured="+desired.size()+" invisible="+getConfig().getBoolean("bots.invisible",true));
    }
    public void onDisable(){closing=true;if(manager!=null)for(Player p:ours())safeRemove(p.getName(),"BeastBots shutdown");}
    private int maximum(){return Math.max(0,Math.min(40,getConfig().getInt("performance.max-active-players",10)));}
    private List<String> names(){return getConfig().getStringList("bots.names").stream().filter(n->n.matches("[A-Za-z0-9_]{3,16}")).distinct().toList();}
    private void updateDesired(){
        int amount=Math.max(0,Math.min(maximum(),getConfig().getInt("bots.amount",10)));
        Set<String> next=new LinkedHashSet<>();if(getConfig().getBoolean("enabled",true))for(String n:names().stream().limit(amount).toList())next.add(n.toLowerCase(Locale.ROOT));desired=Set.copyOf(next);
    }
    private List<Player> ours(){return manager.getAll().stream().filter(Player::isOnline).filter(p->managed.contains(p.getUniqueId())||desired.contains(p.getName().toLowerCase(Locale.ROOT))).toList();}
    private boolean ours(Player p){return p.hasMetadata("beastbot")||manager.isFake(p)&&(managed.contains(p.getUniqueId())||desired.contains(p.getName().toLowerCase(Locale.ROOT)));}
    private boolean safeRemove(String name,String reason){
        Player p=manager.get(name);if(p==null||!manager.isFake(p))return false;
        // CE's kick+cleanup path removes Paper 1.21.11 players twice; cleanup alone unregisters once.
        manager.cleanup(p);managed.remove(p.getUniqueId());return true;
    }
    private void mark(Player p){p.setViewDistance(Math.max(2,Math.min(4,getConfig().getInt("performance.player-view-distance",2))));p.setSendViewDistance(Math.max(2,Math.min(4,getConfig().getInt("performance.player-view-distance",2))));p.setSimulationDistance(2);p.setMetadata("beastbot",new FixedMetadataValue(this,true));p.setMetadata("NPC",new FixedMetadataValue(this,true));managed.add(p.getUniqueId());}
    private void configure(Player p){
        mark(p);p.setPersistent(false);p.setInvulnerable(true);p.setCollidable(false);p.setCanPickupItems(false);p.setInvisible(getConfig().getBoolean("bots.invisible",true));p.setGravity(false);p.setAllowFlight(true);p.setFlying(true);p.setGameMode(GameMode.ADVENTURE);p.getInventory().clear();
        p.playerListName(Component.text(p.getName()));
        for(Player viewer:Bukkit.getOnlinePlayers())if(!manager.isFake(viewer)){
            if(getConfig().getBoolean("tab.enabled",true))viewer.listPlayer(p);else viewer.unlistPlayer(p);
        }
    }
    private Location spawn(){
        World world=Bukkit.getWorld(getConfig().getString("spawn.world","world"));if(world==null)throw new IllegalStateException("Configured lobby world is missing");
        return new Location(world,getConfig().getDouble("spawn.x",world.getSpawnLocation().getX()),getConfig().getDouble("spawn.y",world.getSpawnLocation().getY()),getConfig().getDouble("spawn.z",world.getSpawnLocation().getZ()));
    }
    private void reconcile(){
        if(closing)return;
        for(Player p:new ArrayList<>(ours()))if(!desired.contains(p.getName().toLowerCase(Locale.ROOT))){managed.remove(p.getUniqueId());safeRemove(p.getName(),"BeastBots amount changed");}
        for(Player p:ours())if(!p.hasMetadata("beastbot"))configure(p);
        for(String name:names()){
            String key=name.toLowerCase(Locale.ROOT);if(!desired.contains(key)||spawning.contains(key)||yielded.contains(key)||reserved.contains(key)||Bukkit.getPlayerExact(name)!=null)continue;
            if(Main.getInjector().getInstance(io.github.hello09x.fakeplayer.core.manager.naming.NameManager.class).isRealPlayerUUID(Bukkit.getOfflinePlayer(name).getUniqueId())){reserved.add(key);getLogger().warning("Skipping name with existing real-player data: "+name);continue;}
            spawning.add(key);
            try{
                manager.spawnAsync(Bukkit.getConsoleSender(),name,spawn(),0).whenComplete((p,error)->{
                    if(closing)return;
                    Bukkit.getScheduler().runTask(this,()->{
                        spawning.remove(key);
                        if(error!=null){if(!desired.contains(key)||yielded.contains(key))return;getLogger().log(java.util.logging.Level.WARNING,"Cannot create "+name,error);return;}
                        if(closing||!desired.contains(key)||yielded.contains(key)){safeRemove(name,"Configuration changed during spawn");return;}
                        configure(p);Bukkit.getScheduler().runTaskLater(this,()->{if(p.isOnline())configure(p);},100L);
                        String skin=getConfig().getString("skins."+name,"");
                        if(getConfig().getBoolean("bots.use-skins",true)&&!skin.isBlank()){
                            applySkin(p,skin);

                        }
                        getLogger().info("Created managed player "+name+" online="+Bukkit.getOnlinePlayers().size());
                    });
                });
            }catch(Exception error){spawning.remove(key);getLogger().log(java.util.logging.Level.WARNING,"Spawn failed: "+name,error);}
            break; // One spawn per second keeps login and skin processing bounded.
        }
    }
    private void applySkin(Player p,String name){
        if(!name.matches("[A-Za-z0-9_]{3,16}")){getLogger().warning("Invalid skin source: "+name);return;}
        skinCache.computeIfAbsent(name.toLowerCase(Locale.ROOT),key->CompletableFuture.supplyAsync(()->{
            try{
                HttpResponse<String> lookup=http.send(HttpRequest.newBuilder(URI.create("https://api.mojang.com/users/profiles/minecraft/"+name)).timeout(Duration.ofSeconds(10)).GET().build(),HttpResponse.BodyHandlers.ofString());
                if(lookup.statusCode()!=200)throw new IllegalStateException("Mojang profile HTTP "+lookup.statusCode());
                String id=JsonParser.parseString(lookup.body()).getAsJsonObject().get("id").getAsString();
                if(!id.matches("[a-fA-F0-9]{32}"))throw new IllegalStateException("Invalid profile identifier");
                HttpResponse<String> response=http.send(HttpRequest.newBuilder(URI.create("https://sessionserver.mojang.com/session/minecraft/profile/"+id+"?unsigned=false")).timeout(Duration.ofSeconds(10)).GET().build(),HttpResponse.BodyHandlers.ofString());
                if(response.statusCode()!=200)throw new IllegalStateException("Mojang textures HTTP "+response.statusCode());
                for(var value:JsonParser.parseString(response.body()).getAsJsonObject().getAsJsonArray("properties")){
                    var property=value.getAsJsonObject();if(property.get("name").getAsString().equals("textures"))return new ProfileProperty("textures",property.get("value").getAsString(),property.has("signature")?property.get("signature").getAsString():null);
                }throw new IllegalStateException("Profile has no textures");
            }catch(Exception error){throw new CompletionException(error);}
        })).whenComplete((property,error)->{
            if(closing)return;
            Bukkit.getScheduler().runTask(this,()->{
                if(!p.isOnline())return;if(error!=null){getLogger().warning("Skin fallback for "+p.getName()+": "+error.getCause());return;}
                var profile=p.getPlayerProfile();profile.setProperty(property);p.setPlayerProfile(profile);getLogger().info("Signed skin loaded for "+p.getName());
            });
        });
    }
    @EventHandler(priority=EventPriority.LOWEST)public void joining(PlayerJoinEvent e){if(ours(e.getPlayer())){mark(e.getPlayer());e.getPlayer().setInvisible(getConfig().getBoolean("bots.invisible",true));}}
    @EventHandler(priority=EventPriority.HIGHEST)public void joined(PlayerJoinEvent e){
        if(ours(e.getPlayer()))e.joinMessage(null);
        else Bukkit.getScheduler().runTaskLater(this,()->{if(e.getPlayer().isOnline())for(Player p:ours())if(getConfig().getBoolean("tab.enabled",true))e.getPlayer().listPlayer(p);else e.getPlayer().unlistPlayer(p);},30L);
    }
    @EventHandler(priority=EventPriority.HIGHEST)public void quit(PlayerQuitEvent e){if(ours(e.getPlayer())){e.quitMessage(null);managed.remove(e.getPlayer().getUniqueId());}else yielded.remove(e.getPlayer().getName().toLowerCase(Locale.ROOT));}
    @EventHandler(priority=EventPriority.LOWEST)public void prelogin(AsyncPlayerPreLoginEvent e){
        String key=e.getName().toLowerCase(Locale.ROOT);if(!desired.contains(key))return;
        try{
            boolean real=Bukkit.getScheduler().callSyncMethod(this,()->{
                Player p=manager.get(e.getName());if(p!=null&&p.getUniqueId().equals(e.getUniqueId()))return false;
                yielded.add(key);if(p!=null&&ours(p))safeRemove(p.getName(),"Real player joining");return true;
            }).get(5,TimeUnit.SECONDS);
            if(!real)return;
        }catch(Exception error){getLogger().warning("Could not yield simulated name "+e.getName()+": "+error.getMessage());return;}
        Bukkit.getScheduler().runTaskLater(this,()->{Player p=Bukkit.getPlayerExact(e.getName());if(p==null||manager.isFake(p))yielded.remove(key);},600L);
    }
    @EventHandler(priority=EventPriority.HIGHEST)public void login(PlayerLoginEvent e){
        if(manager.isFake(e.getPlayer())&&(!desired.contains(e.getPlayer().getName().toLowerCase(Locale.ROOT))||yielded.contains(e.getPlayer().getName().toLowerCase(Locale.ROOT)))){e.disallow(PlayerLoginEvent.Result.KICK_OTHER,"Bot configuration changed");return;}
        if(e.getResult()==PlayerLoginEvent.Result.KICK_FULL&&Bukkit.getOnlinePlayers().stream().filter(p->!manager.isFake(p)).count()<Bukkit.getMaxPlayers())e.allow();
    }
    public boolean onCommand(CommandSender sender,Command cmd,String label,String[] args){
        if(!sender.hasPermission("beastbots.admin")){sender.sendMessage("Keine Berechtigung.");return true;}
        String sub=args.length==0?"help":args[0].toLowerCase(Locale.ROOT);
        switch(sub){
            case "help"->sender.sendMessage("/beastbots add <Name> | remove <Name> | setamount <0-"+maximum()+"> | list | reload | status");
            case "status"->{sender.sendMessage("BeastBots: simuliert="+ours().size()+" gewünscht="+desired.size()+" echte Backend-Spieler="+Bukkit.getOnlinePlayers().stream().filter(p->!manager.isFake(p)).count()+" unsichtbar="+getConfig().getBoolean("bots.invisible",true));for(Player p:ours())sender.sendMessage(p.getName()+" UUID="+p.getUniqueId()+" online="+p.isOnline()+" invisible="+p.isInvisible()+" op="+p.isOp()+" view="+p.getViewDistance()+" simulation="+p.getSimulationDistance()+" skin="+p.getPlayerProfile().getProperties().stream().anyMatch(v->v.getName().equals("textures")));}
            case "list"->sender.sendMessage("Simulierte Spieler: "+String.join(", ",ours().stream().map(Player::getName).toList()));
            case "reload"->{reloadConfig();reserved.clear();updateDesired();for(Player p:ours())configure(p);reconcile();sender.sendMessage("BeastBots-Konfiguration neu geladen.");}
            case "setamount"->{try{int value=Integer.parseInt(args[1]);if(value<0||value>maximum())throw new IllegalArgumentException();getConfig().set("bots.amount",value);saveConfig();updateDesired();reconcile();sender.sendMessage("Simulierte Spieler: "+value);}catch(Exception error){sender.sendMessage("/beastbots setamount <0-"+maximum()+">");}}
            case "add"->{if(args.length!=2||!args[1].matches("[A-Za-z0-9_]{3,16}")){sender.sendMessage("/beastbots add <Name mit 3-16 Zeichen>");break;}List<String> list=new ArrayList<>(names());if(list.stream().anyMatch(n->n.equalsIgnoreCase(args[1]))){sender.sendMessage("Name bereits konfiguriert.");break;}if(desired.size()>=maximum()){sender.sendMessage("Maximale Anzahl erreicht.");break;}list.add(Math.min(desired.size(),list.size()),args[1]);getConfig().set("bots.names",list);getConfig().set("bots.amount",Math.min(maximum(),desired.size()+1));saveConfig();updateDesired();reconcile();sender.sendMessage("Hinzugefügt: "+args[1]);}
            case "remove"->{if(args.length!=2){sender.sendMessage("/beastbots remove <Name>");break;}List<String> list=new ArrayList<>(names());if(!list.removeIf(n->n.equalsIgnoreCase(args[1]))){sender.sendMessage("Name nicht gefunden.");break;}getConfig().set("bots.names",list);getConfig().set("bots.amount",Math.min(Math.max(0,desired.size()-(desired.contains(args[1].toLowerCase(Locale.ROOT))?1:0)),list.size()));saveConfig();updateDesired();reconcile();sender.sendMessage("Entfernt: "+args[1]);}
            default->sender.sendMessage("/beastbots help");
        }return true;
    }
    public List<String> onTabComplete(CommandSender s,Command c,String a,String[] args){if(!s.hasPermission("beastbots.admin"))return List.of();if(args.length==1)return List.of("help","add","remove","setamount","list","reload","status").stream().filter(n->n.startsWith(args[0].toLowerCase(Locale.ROOT))).toList();if(args.length==2&&args[0].equalsIgnoreCase("remove"))return names();return List.of();}
}
