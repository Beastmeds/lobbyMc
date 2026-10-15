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
    enum Kind { HOME, SERVERS, EVENTS, EFFECTS }
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
        for(String command:List.of("beast","serverauswahl","events","effekte","lobbyeffekte")){
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
        case "winter","weihnachten","schnee"->Effect.WINTER;
        case "halloween","nebel"->Effect.HALLOWEEN;
        case "aus","off"->Effect.AUS;default->null;};}
    private BeastHudPlugin.Theme theme(Player p){return plugin.profile(p.getUniqueId()).theme;}
    private Effect preference(Player p){return preferences.getOrDefault(p.getUniqueId(),Effect.AUTO);}
    private Effect effective(Player p){
        if(!globalEnabled)return Effect.AUS;
        Effect value=preference(p);if(value!=Effect.AUTO)return value;
        if(globalMode!=Effect.AUTO)return globalMode;
        return switch(theme(p)){case HALLOWEEN->Effect.HALLOWEEN;case WEIHNACHTEN->Effect.WINTER;case STANDARD->Effect.AUS;};
    }
    private String effectName(Effect e){return switch(e){case AUTO->"Automatisch";case WINTER->"Schneefall";case HALLOWEEN->"Halloween-Nebel";case AUS->"Aus";};}
    private void setEffect(Player p,Effect effect){
        preferences.put(p.getUniqueId(),effect);savePreferences();
        p.sendMessage(Component.text("Lobby-Effekte: "+effectName(effect)+(effect==Effect.AUTO?" → "+effectName(effective(p)):""),theme(p).color));
        if(!globalEnabled&&effect!=Effect.AUS)p.sendMessage(Component.text("Die Lobby-Effekte sind derzeit serverweit ausgeschaltet.",NamedTextColor.GRAY));
        p.playSound(p.getLocation(),Sound.UI_BUTTON_CLICK,.25f,1.1f);
    }
    private void savePreferences(){
        YamlConfiguration data=new YamlConfiguration();preferences.forEach((id,e)->data.set(id.toString(),e.name()));
        try{data.save(preferencesFile);}catch(IOException ex){plugin.getLogger().log(java.util.logging.Level.SEVERE,"Could not save lobby effect preferences",ex);}
    }
    private void atmosphere(Player p){
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
    private ItemStack item(Material material,String name,NamedTextColor color,String... lore){
        ItemStack item=new ItemStack(material);ItemMeta meta=item.getItemMeta();meta.displayName(Component.text(name,color));
        meta.lore(Arrays.stream(lore).map(line->Component.text(line,NamedTextColor.GRAY)).toList());item.setItemMeta(meta);return item;
    }
    private Material border(Player p){return switch(theme(p)){case HALLOWEEN->Material.PURPLE_STAINED_GLASS_PANE;case WEIHNACHTEN->Material.LIGHT_BLUE_STAINED_GLASS_PANE;case STANDARD->Material.BLUE_STAINED_GLASS_PANE;};}
    void decorate(Player p,Inventory inv,String heading){
        for(int slot=0;slot<45;slot++){
            boolean edge=slot<9||slot>=36||slot%9==0||slot%9==8;
            inv.setItem(slot,item(edge?border(p):Material.BLACK_STAINED_GLASS_PANE," ",NamedTextColor.DARK_GRAY));
        }
        inv.setItem(4,item(theme(p).icon,"BEASTSMP · "+heading,theme(p).color,"Design: "+theme(p).label));
        inv.setItem(36,item(Material.ARROW,"Zurück zum Beast-Menü",NamedTextColor.WHITE));
        inv.setItem(40,item(Material.BARRIER,"Schließen",NamedTextColor.RED));
        inv.setItem(44,item(Material.SNOWBALL,"Lobby-Effekte",theme(p).color,"Aktiv: "+effectName(effective(p)),"Klicken zum Einstellen"));
    }
    private Menu newMenu(Player p,Kind kind,String heading){
        Menu m=new Menu(kind);m.inventory=Bukkit.createInventory(m,45,Component.text("BeastSMP | "+heading,theme(p).color));decorate(p,m.inventory,heading);
        m.actions.put(36,()->openHome(p));m.actions.put(40,p::closeInventory);m.actions.put(44,()->openEffects(p));return m;
    }
    private void put(Menu m,int slot,ItemStack item,Runnable action){m.inventory.setItem(slot,item);m.actions.put(slot,action);}
    private ItemStack invisible(ItemStack stack,boolean tooltip){
        ItemMeta meta=stack.getItemMeta();meta.setItemModel(new NamespacedKey("beastsmp","menu_blank"));
        if(!tooltip)meta.setHideTooltip(true);stack.setItemMeta(meta);return stack;
    }
    private void card(Menu m,int[] slots,ItemStack stack,Runnable action){
        for(int slot:slots)put(m,slot,invisible(stack.clone(),true),action);
    }
    void openHome(Player p){
        Menu m=new Menu(Kind.HOME);
        String glyph=switch(theme(p)){case HALLOWEEN->"\ue001";case STANDARD->"\ue002";case WEIHNACHTEN->"\ue003";};
        Component title=Component.text("\ue000"+glyph,NamedTextColor.WHITE).font(net.kyori.adventure.key.Key.key("beastsmp:lobby_menu"));
        m.inventory=Bukkit.createInventory(m,45,title);
        ItemStack filler=invisible(item(Material.PAPER," ",NamedTextColor.WHITE),false);
        for(int slot=0;slot<45;slot++)m.inventory.setItem(slot,filler.clone());
        card(m,new int[]{10,11,12,19,20,21},item(Material.PAPER,"Serverauswahl",theme(p).color,"Wähle dein Ziel im BeastSMP-Netzwerk","Mit aktuellem Online-Status"),()->openServers(p));
        card(m,new int[]{14,15,16,23,24,25},item(Material.PAPER,"Dein Design",theme(p).color,"Halloween · Standard · Weihnachten","Aktuell: "+theme(p).label),()->plugin.menu(p));
        card(m,new int[]{28,29,30,37,38,39},item(Material.PAPER,"Events",theme(p).color,"Kürbissuche und saisonale Aktionen","Dein Fortschritt bleibt gespeichert"),()->openEvents(p));
        card(m,new int[]{32,33,34,41,42,43},item(Material.PAPER,"Atmosphäre",theme(p).color,"Schnee, Partikelnebel oder Ruhe","Aktiv: "+effectName(effective(p))),()->openEffects(p));
        put(m,40,invisible(item(Material.PAPER,"Schließen",NamedTextColor.RED),true),()->p.closeInventory());
        p.openInventory(m.inventory);
    }
    void openServers(Player p){requestCounts();Menu m=newMenu(p,Kind.SERVERS,"Serverauswahl");fillServers(p,m);p.openInventory(m.inventory);}
    private void fillServers(Player p,Menu m){
        int[] slots={20,22,24};int i=0;
        for(Destination d:destinations){
            boolean lobby=d.name.equals("Lobby");Boolean online=reachable.get(d.name);
            String status=lobby?"Du bist bereits hier":online==null?"Status wird geprüft":online?"Online":"Derzeit offline";
            NamedTextColor color=lobby?theme(p).color:Boolean.TRUE.equals(online)?NamedTextColor.GREEN:NamedTextColor.GRAY;
            Material icon=lobby||Boolean.TRUE.equals(online)?d.icon:Material.GRAY_DYE;
            String players=counts.containsKey(d.name)?"Spieler: "+counts.get(d.name):"Spielerzahl wird abgefragt";
            put(m,slots[i++],item(icon,d.label,color,status,players,lobby?"Deine Lobby":Boolean.TRUE.equals(online)?"Klicken zum Verbinden":"Aktuell nicht erreichbar"),()->connect(p,d));
        }
    }
    private void connect(Player p,Destination destination){
        if(destination.name.equals("Lobby")){p.sendMessage(Component.text("Du bist bereits in der Lobby.",theme(p).color));return;}
        if(!Boolean.TRUE.equals(reachable.get(destination.name))){p.sendMessage(Component.text(destination.label+" ist derzeit nicht erreichbar.",NamedTextColor.RED));p.playSound(p.getLocation(),Sound.BLOCK_NOTE_BLOCK_BASS,.3f,.8f);return;}
        p.closeInventory();p.sendMessage(Component.text("Verbinde mit "+destination.label+" …",theme(p).color));send(p,"Connect",destination.name);
    }
    void openEvents(Player p){
        Menu m=newMenu(p,Kind.EVENTS,"Events");int found=plugin.profile(p.getUniqueId()).found.size(),total=plugin.getConfig().getInt("quest-count",10);
        put(m,20,item(Material.JACK_O_LANTERN,"Halloween · Kürbissuche",NamedTextColor.GOLD,"Dein Fortschritt: "+found+" / "+total,"Finde die Kürbisse rund um den Spawn","Klicken: Halloween aktivieren und Hilfe anzeigen"),()->{p.closeInventory();plugin.choose(p,BeastHudPlugin.Theme.HALLOWEEN);plugin.questInfo(p);});
        put(m,22,item(Material.PAINTING,"Saisonale Designs",theme(p).color,"Wähle deinen persönlichen Beast-Stil"),()->plugin.menu(p));
        put(m,24,item(Material.SNOW_BLOCK,"Weihnachtsstimmung",NamedTextColor.AQUA,"Weihnachts-Design und Schneefall einschalten","Ein persönlicher Effekt nur für dich"),()->{p.closeInventory();plugin.choose(p,BeastHudPlugin.Theme.WEIHNACHTEN);setEffect(p,Effect.WINTER);});
        p.openInventory(m.inventory);
    }
    void openEffects(Player p){
        Menu m=newMenu(p,Kind.EFFECTS,"Lobby-Effekte");
        put(m,19,item(Material.NETHER_STAR,"Automatisch",theme(p).color,"Folgt deinem ausgewählten Design","Halloween: Nebel · Weihnachten: Schnee","Standard: keine Partikel"),()->{setEffect(p,Effect.AUTO);openEffects(p);});
        put(m,21,item(Material.SNOWBALL,"Schneefall",NamedTextColor.AQUA,"Sanfte Schneeflocken um dich herum","Nur für dich sichtbar"),()->{setEffect(p,Effect.WINTER);openEffects(p);});
        put(m,23,item(Material.SOUL_CAMPFIRE,"Halloween-Nebel",NamedTextColor.LIGHT_PURPLE,"Bodennaher Partikelnebel, Asche und Magie","Nur für dich sichtbar"),()->{setEffect(p,Effect.HALLOWEEN);openEffects(p);});
        put(m,25,item(Material.REDSTONE_TORCH,"Effekte ausschalten",NamedTextColor.RED,"Alle zusätzlichen Lobby-Partikel aus","Weniger Partikel für mehr Ruhe"),()->{setEffect(p,Effect.AUS);openEffects(p);});
        m.inventory.setItem(31,item(Material.PAPER,"Deine Einstellung",theme(p).color,"Gewählt: "+effectName(preference(p)),"Aktiv: "+effectName(effective(p)),globalEnabled?"Lobby-Effekte sind freigeschaltet":"Serverweit momentan deaktiviert"));
        p.openInventory(m.inventory);
    }
    void refreshHotbar(Player p){giveHotbar(p);}
    private void giveHotbar(Player p){
        if(!p.getWorld().getName().equals(plugin.getConfig().getString("world","world")))return;
        for(Object[] spec:List.of(new Object[]{0,Material.COMPASS,"Serverauswahl","servers"},new Object[]{4,Material.NETHER_STAR,"BeastSMP · Lobby-Menü","home"},new Object[]{8,Material.COMPARATOR,"Lobby-Effekte","effects"})){
            ItemStack stack=item((Material)spec[1],(String)spec[2],theme(p).color,"Rechtsklick zum Öffnen");ItemMeta meta=stack.getItemMeta();meta.getPersistentDataContainer().set(menuKey,PersistentDataType.STRING,(String)spec[3]);stack.setItemMeta(meta);p.getInventory().setItem((Integer)spec[0],stack);
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
        Player carrier=Bukkit.getOnlinePlayers().stream().findFirst().orElse(null);if(carrier==null)return;
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
            if(!(sender instanceof ConsoleCommandSender)&&!sender.hasPermission("beasthud.admin")){sender.sendMessage("Dieser Befehl ist für die Lobby-Administration.");return true;}
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
            Effect mode=parse(args[0]);if(mode==null){sender.sendMessage("/lobbyeffekte an|aus|auto|winter|halloween|status");return true;}
            globalEnabled=mode!=Effect.AUS;if(globalEnabled)globalMode=mode;
            plugin.getConfig().set("effects.enabled",globalEnabled);plugin.getConfig().set("effects.mode",globalMode.name().toLowerCase(Locale.ROOT));plugin.saveConfig();
            sender.sendMessage("Lobby-Effekte serverweit: "+(globalEnabled?effectName(globalMode):"Aus"));return true;
        }
        if(!(sender instanceof Player p)){sender.sendMessage("Diesen Befehl im Spiel verwenden.");return true;}
        switch(command.getName()){
            case "beast"->openHome(p);
            case "serverauswahl"->openServers(p);
            case "events"->openEvents(p);
            case "effekte"->{if(args.length==0){openEffects(p);return true;}if(args[0].equalsIgnoreCase("status")){p.sendMessage(Component.text("Effekte: "+effectName(preference(p))+" · Aktiv: "+effectName(effective(p)),theme(p).color));return true;}Effect mode=parse(args[0]);if(mode==null)p.sendMessage(Component.text("/effekte an|aus|auto|winter|halloween",NamedTextColor.GRAY));else setEffect(p,mode);}
        }
        return true;
    }
    @Override public List<String> onTabComplete(CommandSender sender,Command c,String label,String[] args){
        if((c.getName().equals("effekte")||c.getName().equals("lobbyeffekte"))&&args.length==1)return List.of("an","aus","auto","winter","halloween","status").stream().filter(s->s.startsWith(args[0].toLowerCase(Locale.ROOT))).toList();return List.of();
    }
}
