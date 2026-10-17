package de.beastsmp.hud;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.*;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.block.EntityBlockFormEvent;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.joml.Vector3f;
import org.joml.Quaternionf;
import java.io.*;
import java.time.Duration;
import java.util.*;

/** Real, public lobby companions and persistent personal profiles. No fake-player integration. */
final class BeastSocialFeatures implements Listener,CommandExecutor,TabCompleter {
    private enum Pet { NONE,GHOST,SNOWMAN,BAT }
    private record PlayerData(String name,Pet pet,String title,Set<String> achievements) {}
    private record Button(String text,Runnable action) {}
    private final BeastHudPlugin plugin;
    private final Map<UUID,PlayerData> players=new HashMap<>();
    private final Map<UUID,List<Entity>> companions=new HashMap<>();
    private final Map<UUID,UUID> sessions=new HashMap<>();
    private final Map<UUID,Set<Entity>> hidden=new HashMap<>();
    private final File file;
    private final NamespacedKey petKey;
    private int tick;
    BeastSocialFeatures(BeastHudPlugin plugin){this.plugin=plugin;file=new File(plugin.getDataFolder(),"social.yml");petKey=new NamespacedKey(plugin,"companion");}
    void enable(){
        var yaml=YamlConfiguration.loadConfiguration(file);var section=yaml.getConfigurationSection("players");
        if(section!=null)for(String key:section.getKeys(false))try{String path="players."+key;players.put(UUID.fromString(key),new PlayerData(yaml.getString(path+".name","Player"),Pet.valueOf(yaml.getString(path+".pet","NONE")),yaml.getString(path+".title","Newcomer"),new HashSet<>(yaml.getStringList(path+".achievements"))));}catch(IllegalArgumentException e){plugin.getLogger().warning("Skipping invalid companion profile: "+key);}
        // Remove only our tagged entities left behind after an unclean shutdown.
        for(World world:Bukkit.getWorlds())for(Entity entity:world.getEntities())if(ours(entity))entity.remove();
        Bukkit.getPluginManager().registerEvents(this,plugin);
        for(String command:List.of("profile","pet")){var c=Objects.requireNonNull(plugin.getCommand(command));c.setExecutor(this);c.setTabCompleter(this);}
        Bukkit.getScheduler().runTaskTimer(plugin,()->{tick++;for(Player p:Bukkit.getOnlinePlayers())if(!BeastHudPlugin.isSimulated(p))update(p);},20L,4L);
        Bukkit.getScheduler().runTaskTimer(plugin,this::save,1200L,1200L);
    }
    void disable(){for(UUID id:new ArrayList<>(companions.keySet()))remove(id);sessions.clear();hidden.clear();save();}
    private boolean ours(Entity entity){return entity.getPersistentDataContainer().has(petKey,PersistentDataType.STRING);}
    private boolean lobby(Player p){return p.getWorld().getName().equals(plugin.getConfig().getString("world","world"));}
    private String tr(Player p,String en,String de){return plugin.language(p).equals("de")?de:en;}
    private Component message(Player p,String en,String de){return Component.text(tr(p,en,de),NamedTextColor.WHITE);}
    private PlayerData data(Player p){var old=players.get(p.getUniqueId());if(old==null){old=new PlayerData(p.getName(),Pet.NONE,"Newcomer",new HashSet<>());players.put(p.getUniqueId(),old);}return old;}
    private void save(){var yaml=new YamlConfiguration();for(var entry:players.entrySet()){String path="players."+entry.getKey();PlayerData d=entry.getValue();yaml.set(path+".name",d.name);yaml.set(path+".pet",d.pet.name());yaml.set(path+".title",d.title);yaml.set(path+".achievements",d.achievements.stream().sorted().toList());}try{yaml.save(file);}catch(IOException e){plugin.getLogger().log(java.util.logging.Level.SEVERE,"Could not save social profiles",e);}}
    private String petName(Player p,Pet pet){return switch(pet){case NONE->tr(p,"None","Keiner");case GHOST->tr(p,"Ghost","Geist");case SNOWMAN->tr(p,"Snowman","Schneemann");case BAT->tr(p,"Bat","Fledermaus");};}
    private String titleName(Player p,String title){return switch(title){case "Explorer"->tr(p,"Explorer","Entdecker");case "Pumpkin Hunter"->tr(p,"Pumpkin Hunter","Kürbisjäger");case "Animal Friend"->tr(p,"Animal Friend","Tierfreund");case "Regular"->tr(p,"Regular","Stammspieler");default->tr(p,"Newcomer","Neuling");};}
    private List<String> titles(Player p){var d=data(p);List<String> result=new ArrayList<>(List.of("Newcomer"));if(d.achievements.contains("first_pumpkin"))result.add("Explorer");if(d.achievements.contains("all_pumpkins"))result.add("Pumpkin Hunter");if(d.achievements.contains("first_companion"))result.add("Animal Friend");if(d.achievements.contains("one_hour"))result.add("Regular");return result;}
    private void achievements(Player p){var d=data(p);int found=plugin.profile(p.getUniqueId()).found.size();boolean changed=false;
        if(found>0)changed|=d.achievements.add("first_pumpkin");if(found>=plugin.getConfig().getInt("quest-count",10))changed|=d.achievements.add("all_pumpkins");if(d.pet!=Pet.NONE)changed|=d.achievements.add("first_companion");if(p.getStatistic(Statistic.PLAY_ONE_MINUTE)>=72000)changed|=d.achievements.add("one_hour");
        if(changed)save();
    }
    private void remove(UUID id){var entities=companions.remove(id);if(entities!=null){entities.forEach(Entity::remove);hidden.values().forEach(set->set.removeAll(entities));}}
    private void mark(Entity entity,UUID owner){entity.getPersistentDataContainer().set(petKey,PersistentDataType.STRING,owner.toString());entity.setPersistent(false);entity.setInvulnerable(true);entity.setSilent(true);entity.setGravity(false);if(entity instanceof LivingEntity living){living.setCollidable(false);living.setCanPickupItems(false);}if(entity instanceof Mob mob){mob.setAI(false);mob.setAware(false);}}
    private BlockDisplay cube(Location l,Material material,float size,UUID owner,float dx,float dy,float dz){return l.getWorld().spawn(l,BlockDisplay.class,e->{mark(e,owner);e.setBlock(material.createBlockData());e.setTransformation(new Transformation(new Vector3f(dx,dy,dz),new Quaternionf(),new Vector3f(size),new Quaternionf()));e.setTeleportDuration(4);e.setViewRange(1.2f);});}
    private List<Entity> spawn(Player p,Pet pet,Location l){List<Entity> list=new ArrayList<>();UUID owner=p.getUniqueId();
        if(pet==Pet.GHOST){list.add(cube(l,Material.WHITE_STAINED_GLASS,.55f,owner,-.275f,0,-.275f));list.add(cube(l,Material.WHITE_CONCRETE,.4f,owner,-.2f,-.23f,-.2f));list.add(cube(l,Material.BLACK_CONCRETE,.08f,owner,-.16f,.28f,-.32f));list.add(cube(l,Material.BLACK_CONCRETE,.08f,owner,.08f,.28f,-.32f));}
        else if(pet==Pet.SNOWMAN)list.add(l.getWorld().spawn(l,Snowman.class,e->{mark(e,owner);e.setDerp(true);e.setRemoveWhenFarAway(false);}));
        else if(pet==Pet.BAT)list.add(l.getWorld().spawn(l,Bat.class,e->{mark(e,owner);e.setAwake(true);e.setRemoveWhenFarAway(false);}));
        return list;
    }
    private void update(Player p){var d=data(p);if(tick%25==0)achievements(p);
        if(!lobby(p)||p.isDead()||p.getGameMode()==GameMode.SPECTATOR||d.pet==Pet.NONE){remove(p.getUniqueId());return;}
        // Respect vanish-style visibility: companions are public only to viewers who can see their owner.
        Location loc=p.getLocation();double yaw=Math.toRadians(loc.getYaw());Location target=loc.clone().add(Math.cos(yaw)*1.25+Math.sin(yaw)*.8,0,Math.sin(yaw)*1.25-Math.cos(yaw)*.8);
        if(target.getBlock().getType().isSolid()||target.clone().add(0,1,0).getBlock().getType().isSolid())target=loc.clone();
        if(d.pet!=Pet.SNOWMAN)target.add(0,1.15+Math.sin(tick*.25)*.12,0);
        var entities=companions.get(p.getUniqueId());boolean created=entities==null||entities.stream().anyMatch(e->!e.isValid()||e.getWorld()!=p.getWorld());if(created){remove(p.getUniqueId());entities=spawn(p,d.pet,target);companions.put(p.getUniqueId(),entities);}
        target.setYaw(0);target.setPitch(0);for(Entity e:entities)e.teleport(target);
        if(created||tick%5==0)for(Player viewer:p.getWorld().getPlayers())if(!BeastHudPlugin.isSimulated(viewer))for(Entity e:entities){var set=hidden.computeIfAbsent(viewer.getUniqueId(),id->new HashSet<>());if(!viewer.canSee(p)){viewer.hideEntity(plugin,e);set.add(e);}else if(set.remove(e))viewer.showEntity(plugin,e);}
    }
    private void select(Player p,Pet pet){var d=data(p);players.put(p.getUniqueId(),new PlayerData(p.getName(),pet,d.title,d.achievements));remove(p.getUniqueId());achievements(p);save();update(p);p.sendMessage(message(p,"Companion: "+petName(p,pet),"Begleiter: "+petName(p,pet)));pets(p);}
    private void dialog(Player p,String title,List<Component> lines,List<Button> choices){
        UUID ticket=UUID.randomUUID();sessions.put(p.getUniqueId(),ticket);List<ActionButton> buttons=new ArrayList<>();
        for(Button choice:choices)buttons.add(ActionButton.builder(Component.text(choice.text,NamedTextColor.WHITE)).width(210).action(DialogAction.customClick((response,audience)->{if(!(audience instanceof Player who)||!who.getUniqueId().equals(p.getUniqueId()))return;Bukkit.getScheduler().runTask(plugin,()->{if(!p.isOnline()||!ticket.equals(sessions.get(p.getUniqueId())))return;sessions.remove(p.getUniqueId());p.closeDialog();choice.action.run();});},ClickCallback.Options.builder().uses(1).lifetime(Duration.ofMinutes(5)).build())).build());
        var base=DialogBase.builder(Component.text(title,NamedTextColor.AQUA)).canCloseWithEscape(true).body(lines.stream().map(c->DialogBody.plainMessage(c,440)).toList()).build();
        var close=ActionButton.builder(message(p,"Close","Schließen")).width(140).build();p.closeInventory();p.showDialog(Dialog.create(b->b.empty().base(base).type(DialogType.multiAction(buttons).columns(2).exitAction(close).build())));
    }
    void profile(Player p){achievements(p);var d=data(p);long seconds=p.getStatistic(Statistic.PLAY_ONE_MINUTE)/20L;int total=plugin.getConfig().getInt("quest-count",10);var found=plugin.profile(p.getUniqueId()).found.size();
        List<Component> lines=List.of(Component.text(p.getName(),NamedTextColor.GOLD),message(p,"Playtime: "+seconds/3600+"h "+seconds%3600/60+"m","Spielzeit: "+seconds/3600+"h "+seconds%3600/60+"m"),message(p,"Achievements: "+d.achievements.size()+" / 4","Erfolge: "+d.achievements.size()+" / 4"),message(p,"Pumpkins found: "+found+" / "+total,"Gefundene Kürbisse: "+found+" / "+total),message(p,"Title: "+titleName(p,d.title),"Titel: "+titleName(p,d.title)),message(p,"Companion: "+petName(p,d.pet),"Begleiter: "+petName(p,d.pet)));
        dialog(p,tr(p,"Player profile","Spielerprofil"),lines,List.of(new Button(tr(p,"Choose title","Titel wählen"),()->titleMenu(p)),new Button(tr(p,"Achievements","Erfolge"),()->achievementMenu(p)),new Button(tr(p,"Companions","Begleiter"),()->pets(p)),new Button(tr(p,"Back to menu","Zurück zum Menü"),()->p.performCommand("menu"))));
    }
    private void achievementMenu(Player p){achievements(p);var unlocked=data(p).achievements;List<Component> lines=new ArrayList<>();String[][] definitions={{"first_pumpkin","Find your first pumpkin","Finde deinen ersten Kürbis"},{"all_pumpkins","Find all pumpkins","Finde alle Kürbisse"},{"first_companion","Choose a companion","Wähle einen Begleiter"},{"one_hour","Play for one hour","Spiele eine Stunde"}};for(String[] a:definitions)lines.add(Component.text((unlocked.contains(a[0])?"✓ ":"○ ")+tr(p,a[1],a[2]),unlocked.contains(a[0])?NamedTextColor.GREEN:NamedTextColor.GRAY));dialog(p,tr(p,"Achievements","Erfolge"),lines,List.of(new Button(tr(p,"Back to profile","Zurück zum Profil"),()->profile(p))));}
    private void titleMenu(Player p){achievements(p);List<Button> buttons=new ArrayList<>();for(String title:titles(p))buttons.add(new Button(titleName(p,title)+(data(p).title.equals(title)?" ✓":""),()->{var d=data(p);if(!titles(p).contains(title))return;players.put(p.getUniqueId(),new PlayerData(p.getName(),d.pet,title,d.achievements));save();profile(p);}));buttons.add(new Button(tr(p,"Back to profile","Zurück zum Profil"),()->profile(p)));dialog(p,tr(p,"Choose title","Titel wählen"),List.of(message(p,"Earn titles through achievements.","Schalte Titel durch Erfolge frei.")),buttons);}
    void pets(Player p){List<Button> choices=new ArrayList<>();for(Pet pet:Pet.values())choices.add(new Button(petName(p,pet)+(data(p).pet==pet?" ✓":""),()->select(p,pet)));choices.add(new Button(tr(p,"Back to profile","Zurück zum Profil"),()->profile(p)));dialog(p,tr(p,"Companions","Begleiter"),List.of(message(p,"Your companion is visible to other players.","Dein Begleiter ist für andere Spieler sichtbar."),message(p,"Selected: "+petName(p,data(p).pet),"Gewählt: "+petName(p,data(p).pet))),choices);}
    @Override public boolean onCommand(CommandSender sender,Command command,String label,String[] args){if(!(sender instanceof Player p)){sender.sendMessage("Use this command in-game.");return true;}if(BeastHudPlugin.isSimulated(p))return true;if(command.getName().equals("profile"))profile(p);else if(args.length>0){try{select(p,Pet.valueOf(args[0].equalsIgnoreCase("off")?"NONE":args[0].toUpperCase(Locale.ROOT)));}catch(IllegalArgumentException e){pets(p);}}else pets(p);return true;}
    @Override public List<String> onTabComplete(CommandSender s,Command c,String alias,String[] args){return c.getName().equals("pet")&&args.length==1?List.of("ghost","snowman","bat","off").stream().filter(v->v.startsWith(args[0].toLowerCase(Locale.ROOT))).toList():List.of();}
    @EventHandler public void quit(PlayerQuitEvent e){remove(e.getPlayer().getUniqueId());sessions.remove(e.getPlayer().getUniqueId());hidden.remove(e.getPlayer().getUniqueId());save();}
    @EventHandler public void hurt(EntityDamageEvent e){if(ours(e.getEntity()))e.setCancelled(true);}
    @EventHandler public void drops(EntityDeathEvent e){if(ours(e.getEntity())){e.getDrops().clear();e.setDroppedExp(0);}}
    @EventHandler public void snow(EntityBlockFormEvent e){if(ours(e.getEntity()))e.setCancelled(true);}
    @EventHandler public void target(EntityTargetEvent e){if(ours(e.getEntity())||e.getTarget()!=null&&ours(e.getTarget()))e.setCancelled(true);}
    @EventHandler public void interact(PlayerInteractEntityEvent e){if(ours(e.getRightClicked()))e.setCancelled(true);}
    @EventHandler public void chunk(org.bukkit.event.world.ChunkLoadEvent e){for(Entity entity:e.getChunk().getEntities())if(ours(entity)&&companions.values().stream().noneMatch(list->list.contains(entity)))entity.remove();}
}
