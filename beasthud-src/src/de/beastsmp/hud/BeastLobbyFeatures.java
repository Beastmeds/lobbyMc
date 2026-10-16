package de.beastsmp.hud;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.messaging.PluginMessageListener;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Lobby-only, per-player atmosphere, themed menus and BungeeCord navigation. */
public final class BeastLobbyFeatures implements Listener, CommandExecutor, TabCompleter, PluginMessageListener {
    enum Effect { AUTO, WINTER, HALLOWEEN, AUS }
    enum Kind { HOME, SERVERS, EVENTS, EFFECTS, DESIGNS, SETTINGS }
    static final class Menu implements InventoryHolder {
        final Kind kind;
        final Map<Integer,Runnable> actions = new HashMap<>();
        Inventory inventory;
        Menu(Kind kind){this.kind=kind;}
        public Inventory getInventory(){return inventory;}
    }
    record Destination(String name,String label,Material icon,String host,int port) {}
    private final BeastHudPlugin plugin;
    private final Map<UUID,Effect> preferences = new HashMap<>();
    private final Map<String,Boolean> reachable = new ConcurrentHashMap<>();
    private final Map<String,Integer> counts = new HashMap<>();
    private final List<Destination> destinations = List.of(
        new Destination("BeastSMP","BeastSMP",Material.GRASS_BLOCK,"127.0.0.1",25566),
        new Destination("SMP","SMP",Material.DIAMOND_PICKAXE,"109.69.175.92",25565),
        new Destination("Lobby","Lobby",Material.NETHER_STAR,"127.0.0.1",25567));
    private NamespacedKey menuKey;
    private File preferencesFile;
    private boolean globalEnabled;
    private Effect globalMode;
    private long particleBatches;
    private int ticks;

    BeastLobbyFeatures(BeastHudPlugin plugin){this.plugin=plugin;}
    void enable(){
        menuKey=new NamespacedKey(plugin,"lobby-menu");
        preferencesFile=new File(plugin.getDataFolder(),"effects.yml");
        YamlConfiguration data=YamlConfiguration.loadConfiguration(preferencesFile);
        for(String key:data.getKeys(false))try{preferences.put(UUID.fromString(key),Effect.valueOf(data.getString(key,"AUTO")));}catch(IllegalArgumentException ignored){}
        globalEnabled=plugin.getConfig().getBoolean("effects.enabled",true);
        globalMode=parse(plugin.getConfig().getString("effects.mode","auto"));if(globalMode==null)globalMode=Effect.AUTO;
        for(String command:List.of("settings","beast","serverauswahl","events","effekte","lobbyeffekte")){
            PluginCommand c=Objects.requireNonNull(plugin.getCommand(command));c.setExecutor(this);c.setTabCompleter(this);
        }
        plugin.getServer().getPluginManager().registerEvents(this,plugin);
        plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin,"BungeeCord");
        plugin.getServer().getMessenger().registerIncomingPluginChannel(plugin,"BungeeCord",this);
        plugin.getServer().getScheduler().runTaskTimer(plugin,()->{
            for(Player p:Bukkit.getOnlinePlayers())atmosphere(p);
            if(++ticks%30==0)requestCounts();
            if(ticks%4==0)for(Player p:Bukkit.getOnlinePlayers())if(p.getOpenInventory().getTopInventory().getHolder() instanceof Menu m&&m.kind==Kind.SERVERS)fillServers(p,m);
        },20L,10L);
        plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin,()->{
            for(Destination d:destinations)try(Socket socket=new Socket()){
                socket.connect(new InetSocketAddress(d.host,d.port),1200);reachable.put(d.name,true);
            }catch(IOException e){reachable.put(d.name,false);}
        },1L,400L);
        plugin.getServer().getScheduler().runTaskLater(plugin,()->{for(Player p:Bukkit.getOnlinePlayers())giveHotbar(p);requestCounts();},90L);
        plugin.getLogger().info("Beast lobby menus and command-controlled seasonal effects enabled.");
    }
    void disable(){savePreferences();}
    private Effect parse(String value){return switch(value.toLowerCase(Locale.ROOT)){
        case "auto","automatisch","an","on"->Effect.AUTO;
        case "winter","christmas","snow","weihnachten","schnee"->Effect.WINTER;
        case "halloween","helloween","fog","nebel"->Effect.HALLOWEEN;
        case "aus","off"->Effect.AUS;default->null;};}
    private BeastHudPlugin.Theme theme(Player p){return plugin.profile(p.getUniqueId()).theme;}
    private Effect preference(Player p){return preferences.getOrDefault(p.getUniqueId(),Effect.AUTO);}
    private Effect effective(Player p){
        if(!globalEnabled)return Effect.AUS;
        Effect value=preference(p);if(value!=Effect.AUTO)return value;
        if(globalMode!=Effect.AUTO)return globalMode;
        return switch(theme(p)){case HALLOWEEN->Effect.HALLOWEEN;case WEIHNACHTEN->Effect.WINTER;case STANDARD->Effect.AUS;};
    }
    private String effectName(Effect e){return switch(e){case AUTO->"Automatic";case WINTER->"Snowfall";case HALLOWEEN->"Halloween fog";case AUS->"Off";};}
    private void setEffect(Player p,Effect effect){
        preferences.put(p.getUniqueId(),effect);savePreferences();
        p.sendMessage(plugin.component(p,"Lobby effects: "+effectName(effect)+(effect==Effect.AUTO?" → "+effectName(effective(p)):""),theme(p).color));
        if(!globalEnabled&&effect!=Effect.AUS)p.sendMessage(plugin.component(p,"Lobby effects are currently disabled server-wide.",NamedTextColor.GRAY));
        p.playSound(p.getLocation(),Sound.UI_BUTTON_CLICK,.25f,1.1f);
    }
    private void savePreferences(){
        YamlConfiguration data=new YamlConfiguration();preferences.forEach((id,e)->data.set(id.toString(),e.name()));
        try{data.save(preferencesFile);}catch(IOException ex){plugin.getLogger().log(java.util.logging.Level.SEVERE,"Could not save lobby effect preferences",ex);}
    }
    private void atmosphere(Player p){
        if(BeastHudPlugin.isSimulated(p))return;
        if(!p.getWorld().getName().equals(plugin.getConfig().getString("world","world")))return;
        Effect effect=effective(p);if(effect==Effect.AUS)return;
        Location l=p.getLocation();double dx=l.getX()-plugin.getConfig().getDouble("spawn.x"),dz=l.getZ()-plugin.getConfig().getDouble("spawn.z");
        if(dx*dx+dz*dz>128*128)return;
        if(effect==Effect.WINTER){
            p.spawnParticle(Particle.SNOWFLAKE,l.clone().add(0,6,0),14,4,2,4,.015);
        }else{
            // Low, local particle fog; no blindness, global weather change or visibility penalty.
            p.spawnParticle(Particle.SMOKE,l.clone().add(0,.25,0),5,3.5,.12,3.5,.004);
            p.spawnParticle(Particle.ASH,l.clone().add(0,1,0),5,4,1.2,4,.008);
            p.spawnParticle(Particle.WITCH,l.clone().add(0,.6,0),2,2,.4,2,.01);
        }
        particleBatches++;
    }
    @EventHandler public void join(PlayerJoinEvent event){
        plugin.getServer().getScheduler().runTaskLater(plugin,()->{if(event.getPlayer().isOnline()){giveHotbar(event.getPlayer());requestCounts();}},90L);
    }
    private ItemStack item(Player p,Material material,String name,NamedTextColor color,String... lore){
        ItemStack item=new ItemStack(material);ItemMeta meta=item.getItemMeta();meta.displayName(plugin.component(p,name,color));
        meta.lore(Arrays.stream(lore).map(line->plugin.component(p,line,NamedTextColor.GRAY)).toList());item.setItemMeta(meta);return item;
    }
    private ItemStack invisible(ItemStack stack,boolean tooltip){
        ItemMeta meta=stack.getItemMeta();meta.setItemModel(new NamespacedKey("beastsmp","menu_blank"));
        if(!tooltip)meta.setHideTooltip(true);stack.setItemMeta(meta);return stack;
    }
    private Component menuTitle(Player p,Kind kind){
        int season=switch(theme(p)){case HALLOWEEN->0;case STANDARD->1;case WEIHNACHTEN->2;};
        int c=0xe100+season*24+kind.ordinal()*4;
        String glyph="\ue000"+(char)c+"\ue010"+(char)(c+1)+"\ue011"+(char)(c+2)+"\ue010"+(char)(c+3);
        return plugin.component(p,glyph,NamedTextColor.WHITE).font(net.kyori.adventure.key.Key.key("beastsmp:lobby_menu"));
    }
    private Menu newMenu(Player p,Kind kind,String heading){
        Menu m=new Menu(kind);m.inventory=Bukkit.createInventory(m,54,menuTitle(p,kind));
        ItemStack filler=invisible(item(p,Material.PAPER," ",NamedTextColor.WHITE),false);
        for(int slot=0;slot<54;slot++)m.inventory.setItem(slot,filler.clone());
        put(m,45,invisible(item(p,Material.PAPER,"Back to the Beast menu",NamedTextColor.WHITE),true),()->openHome(p));
        put(m,49,invisible(item(p,Material.PAPER,"Close",NamedTextColor.RED),true),()->p.closeInventory());
        put(m,53,invisible(item(p,Material.PAPER,"Lobby effects",theme(p).color,"Active: "+effectName(effective(p))),true),()->openEffects(p));
        return m;
    }
    private void put(Menu m,int slot,ItemStack item,Runnable action){m.inventory.setItem(slot,item);m.actions.put(slot,action);}
    private void card(Menu m,int[] slots,ItemStack stack,Runnable action){for(int slot:slots)put(m,slot,invisible(stack.clone(),true),action);}
    private int[] four(int card){
        int row=card/2==0?1:3,col=card%2==0?0:5;int[] result=new int[8];int i=0;
        for(int r=row;r<row+2;r++)for(int c=col;c<col+4;c++)result[i++]=r*9+c;return result;
    }
    private int[] three(int card){int[] result=new int[12];int i=0;for(int r=1;r<=4;r++)for(int c=card*3;c<card*3+3;c++)result[i++]=r*9+c;return result;}
    void openSettings(Player p){
        Menu m=newMenu(p,Kind.SETTINGS,"Settings");
        card(m,three(0),item(p,Material.PAPER,"English",NamedTextColor.AQUA,plugin.language(p).equals("en")?"Selected":"Click to select"),()->{plugin.chooseLanguage(p,"en");openSettings(p);});
        card(m,three(1),item(p,Material.PAPER,"German",NamedTextColor.GOLD,plugin.language(p).equals("de")?"Selected":"Click to select"),()->{plugin.chooseLanguage(p,"de");openSettings(p);});
        card(m,three(2),item(p,Material.PAPER,"Back to lobby",NamedTextColor.WHITE),()->openHome(p));
        p.openInventory(m.inventory);
    }
    void openHome(Player p){
        Menu m=newMenu(p,Kind.HOME,"Lobby menu");
        card(m,four(0),item(p,Material.PAPER,"Server selection",theme(p).color,"Choose your destination on BeastSMP","With live server status"),()->openServers(p));
        card(m,four(1),item(p,Material.PAPER,"Your design",theme(p).color,"Halloween · Standard · Christmas","Current: "+theme(p).label),()->openDesigns(p));
        card(m,four(2),item(p,Material.PAPER,"Events",theme(p).color,"Pumpkin hunt and seasonal events","Your progress is saved"),()->openEvents(p));
        card(m,four(3),item(p,Material.PAPER,"Atmosphere",theme(p).color,"Snow, particle fog or quiet","Active: "+effectName(effective(p))),()->openEffects(p));
        p.openInventory(m.inventory);
    }
    void openDesigns(Player p){
        Menu m=newMenu(p,Kind.DESIGNS,"Designs");int i=0;
        for(BeastHudPlugin.Theme t:BeastHudPlugin.Theme.values())card(m,three(i++),item(p,Material.PAPER,t.label,t.color,theme(p)==t?"Currently selected":"Click to select","Your design is saved"),()->{plugin.choose(p,t);openDesigns(p);});
        p.openInventory(m.inventory);
    }
    void openServers(Player p){requestCounts();Menu m=newMenu(p,Kind.SERVERS,"Server selection");fillServers(p,m);p.openInventory(m.inventory);}
    private void fillServers(Player p,Menu m){
        int i=0;
        for(Destination d:destinations){
            boolean lobby=d.name.equals("Lobby");Boolean online=reachable.get(d.name);
            String status=lobby?"You are already here":online==null?"Checking status":online?"Online":"Currently offline";
            NamedTextColor color=lobby?theme(p).color:Boolean.TRUE.equals(online)?NamedTextColor.GREEN:NamedTextColor.GRAY;
            String players=counts.containsKey(d.name)?"Players: "+counts.get(d.name):"Fetching player count";
            card(m,three(i++),item(p,Material.PAPER,d.label,color,status,players,lobby?"Your lobby":Boolean.TRUE.equals(online)?"Click to connect":"Currently unavailable"),()->connect(p,d));
        }
    }
    private void connect(Player p,Destination destination){
        if(destination.name.equals("Lobby")){p.sendMessage(plugin.component(p,"You are already in the lobby.",theme(p).color));return;}
        if(!Boolean.TRUE.equals(reachable.get(destination.name))){p.sendMessage(plugin.component(p,destination.label+" is currently unavailable.",NamedTextColor.RED));p.playSound(p.getLocation(),Sound.BLOCK_NOTE_BLOCK_BASS,.3f,.8f);return;}
        p.closeInventory();p.sendMessage(plugin.component(p,"Connecting to "+destination.label+" …",theme(p).color));send(p,"Connect",destination.name);
    }
    void openEvents(Player p){
        Menu m=newMenu(p,Kind.EVENTS,"Events");int found=plugin.profile(p.getUniqueId()).found.size(),total=plugin.getConfig().getInt("quest-count",10);
        card(m,three(0),item(p,Material.PAPER,"Halloween · Pumpkin hunt",NamedTextColor.GOLD,"Your progress: "+found+" / "+total,"Find pumpkins around the spawn","Click to enable Halloween and show help"),()->{p.closeInventory();plugin.choose(p,BeastHudPlugin.Theme.HALLOWEEN);plugin.questInfo(p);});
        card(m,three(1),item(p,Material.PAPER,"Seasonal designs",theme(p).color,"Choose your personal Beast style"),()->openDesigns(p));
        card(m,three(2),item(p,Material.PAPER,"Christmas spirit",NamedTextColor.AQUA,"Enable the Christmas design and snowfall","A personal effect just for you"),()->{plugin.choose(p,BeastHudPlugin.Theme.WEIHNACHTEN);setEffect(p,Effect.WINTER);openEvents(p);});
        p.openInventory(m.inventory);
    }
    void openEffects(Player p){
        Menu m=newMenu(p,Kind.EFFECTS,"Lobby effects");
        card(m,four(0),item(p,Material.PAPER,"Automatic",theme(p).color,"Follows your selected design","Selected: "+effectName(preference(p)),"Active: "+effectName(effective(p))),()->{setEffect(p,Effect.AUTO);openEffects(p);});
        card(m,four(1),item(p,Material.PAPER,"Snowfall",NamedTextColor.AQUA,"Gentle snowflakes around you","Only visible to you"),()->{setEffect(p,Effect.WINTER);openEffects(p);});
        card(m,four(2),item(p,Material.PAPER,"Halloween fog",NamedTextColor.LIGHT_PURPLE,"Low particle fog, ash and magic","Only visible to you"),()->{setEffect(p,Effect.HALLOWEEN);openEffects(p);});
        card(m,four(3),item(p,Material.PAPER,"Disable effects",NamedTextColor.RED,"Disable all extra lobby particles",globalEnabled?"Effects are enabled server-wide":"Effects are disabled server-wide"),()->{setEffect(p,Effect.AUS);openEffects(p);});
        p.openInventory(m.inventory);
    }
    void refreshHotbar(Player p){giveHotbar(p);}
    private void giveHotbar(Player p){
        if(BeastHudPlugin.isSimulated(p))return;
        if(!p.getWorld().getName().equals(plugin.getConfig().getString("world","world")))return;
        for(Object[] spec:List.of(new Object[]{0,Material.COMPASS,"Server selection","servers"},new Object[]{4,Material.NETHER_STAR,"BeastSMP · Lobby menu","home"},new Object[]{8,Material.COMPARATOR,"Lobby effects","effects"})){
            ItemStack stack=item(p,(Material)spec[1],(String)spec[2],theme(p).color,"Right-click to open");ItemMeta meta=stack.getItemMeta();meta.getPersistentDataContainer().set(menuKey,PersistentDataType.STRING,(String)spec[3]);stack.setItemMeta(meta);p.getInventory().setItem((Integer)spec[0],stack);
        }
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=false) public void hotbar(PlayerInteractEvent e){
        if(e.getHand()!=EquipmentSlot.HAND||e.getItem()==null||(e.getAction()!=Action.RIGHT_CLICK_AIR&&e.getAction()!=Action.RIGHT_CLICK_BLOCK))return;
        ItemMeta meta=e.getItem().getItemMeta();if(meta==null)return;String action=meta.getPersistentDataContainer().get(menuKey,PersistentDataType.STRING);if(action==null)return;
        e.setCancelled(true);plugin.getServer().getScheduler().runTask(plugin,()->{Player p=e.getPlayer();switch(action){case "servers"->openServers(p);case "home"->openHome(p);case "effects"->openEffects(p);}});
    }
    @EventHandler(priority=EventPriority.HIGHEST) public void drop(PlayerDropItemEvent e){ItemMeta m=e.getItemDrop().getItemStack().getItemMeta();if(m!=null&&m.getPersistentDataContainer().has(menuKey,PersistentDataType.STRING))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=false) public void click(InventoryClickEvent e){
        if(!(e.getView().getTopInventory().getHolder() instanceof Menu menu))return;e.setCancelled(true);if(!(e.getWhoClicked() instanceof Player p))return;
        Runnable action=menu.actions.get(e.getRawSlot());if(action!=null)plugin.getServer().getScheduler().runTask(plugin,()->{if(p.isOnline()){p.playSound(p.getLocation(),Sound.UI_BUTTON_CLICK,.2f,1.1f);action.run();}});
    }
    @EventHandler public void drag(InventoryDragEvent e){if(e.getView().getTopInventory().getHolder() instanceof Menu)e.setCancelled(true);}
    private void requestCounts(){
        Player carrier=Bukkit.getOnlinePlayers().stream().filter(p->!BeastHudPlugin.isSimulated(p)).findFirst().orElse(null);if(carrier==null)return;
        for(Destination d:destinations)send(carrier,"PlayerCount",d.name);
    }
    private void send(Player carrier,String action,String argument){
        try{ByteArrayOutputStream bytes=new ByteArrayOutputStream();DataOutputStream data=new DataOutputStream(bytes);data.writeUTF(action);data.writeUTF(argument);carrier.sendPluginMessage(plugin,"BungeeCord",bytes.toByteArray());}catch(IOException impossible){throw new IllegalStateException(impossible);}
    }
    @Override public void onPluginMessageReceived(String channel,Player player,byte[] message){
        if(!channel.equals("BungeeCord"))return;
        try(DataInputStream in=new DataInputStream(new ByteArrayInputStream(message))){
            if(!in.readUTF().equals("PlayerCount"))return;String name=in.readUTF();int count=in.readInt();
            if(count>=0&&count<=10000&&destinations.stream().anyMatch(d->d.name.equals(name)))counts.put(name,count);
        }catch(IOException ignored){}
    }
    @Override public boolean onCommand(CommandSender sender,Command command,String label,String[] args){
        if(command.getName().equals("lobbyeffekte")){
            if(!(sender instanceof ConsoleCommandSender)&&!sender.hasPermission("beasthud.admin")){sender.sendMessage("This command is for lobby administrators.");return true;}
            if(args.length==3&&args[0].equalsIgnoreCase("menu")){
                Player target=Bukkit.getPlayerExact(args[1]);if(target==null){sender.sendMessage("Player offline");return true;}
                switch(args[2].toLowerCase(Locale.ROOT)){case "home"->openHome(target);case "servers"->openServers(target);case "events"->openEvents(target);case "effects"->openEffects(target);case "design"->plugin.menu(target);default->{sender.sendMessage("Menus: home, servers, events, effects, design");return true;}}
                sender.sendMessage("Menu opened: "+args[2]+" for "+target.getName());return true;
            }
            if(args.length==0||args[0].equalsIgnoreCase("status")){
                sender.sendMessage("Lobby effects: enabled="+globalEnabled+" mode="+globalMode+" emitted-batches="+particleBatches+" saved-preferences="+preferences.size());
                sender.sendMessage("Server status: "+reachable+"; proxy counts="+counts);
                for(Player p:Bukkit.getOnlinePlayers()){
                    InventoryHolder h=p.getOpenInventory().getTopInventory().getHolder();
                    sender.sendMessage("Viewer="+p.getName()+" preference="+preference(p)+" active="+effective(p)+" menu="+(h instanceof Menu m?m.kind:h==null?"none":h.getClass().getSimpleName()));
                }
                return true;
            }
            Effect mode=parse(args[0]);if(mode==null){sender.sendMessage("/lobbyeffects on|off|auto|winter|halloween|status");return true;}
            globalEnabled=mode!=Effect.AUS;if(globalEnabled)globalMode=mode;
            plugin.getConfig().set("effects.enabled",globalEnabled);plugin.getConfig().set("effects.mode",globalMode.name().toLowerCase(Locale.ROOT));plugin.saveConfig();
            sender.sendMessage("Server-wide lobby effects: "+(globalEnabled?effectName(globalMode):"Off"));return true;
        }
        if(!(sender instanceof Player p)){sender.sendMessage("Use this command in-game.");return true;}
        switch(command.getName()){
            case "settings"->{if(args.length>0&&Set.of("en","de","english","german","deutsch").contains(args[0].toLowerCase(Locale.ROOT)))plugin.chooseLanguage(p,Set.of("de","german","deutsch").contains(args[0].toLowerCase(Locale.ROOT))?"de":"en");else openSettings(p);}
            case "beast"->openHome(p);
            case "serverauswahl"->openServers(p);
            case "events"->openEvents(p);
            case "effekte"->{if(args.length==0){openEffects(p);return true;}if(args[0].equalsIgnoreCase("status")){p.sendMessage(plugin.component(p,"Effects: "+effectName(preference(p))+" · Active: "+effectName(effective(p)),theme(p).color));return true;}Effect mode=parse(args[0]);if(mode==null)p.sendMessage(plugin.component(p,"/effects on|off|auto|winter|halloween",NamedTextColor.GRAY));else setEffect(p,mode);}
        }
        return true;
    }
    @Override public List<String> onTabComplete(CommandSender sender,Command c,String label,String[] args){
        if(c.getName().equals("settings")&&args.length==1)return List.of("en","de");
        if((c.getName().equals("effekte")||c.getName().equals("lobbyeffekte"))&&args.length==1)return List.of("on","off","auto","winter","halloween","status").stream().filter(s->s.startsWith(args[0].toLowerCase(Locale.ROOT))).toList();return List.of();
    }
}
