package de.beastsmp.hud;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.*;
final class BeastChatPreferences implements Listener {
 private final BeastHudPlugin plugin;private int tip;
 BeastChatPreferences(BeastHudPlugin plugin){this.plugin=plugin;}
 void enable(){
  Bukkit.getPluginManager().registerEvents(this,plugin);
  long interval=Math.max(30,plugin.getConfig().getInt("chat.tip-interval-seconds",300))*20L;
  Bukkit.getScheduler().runTaskTimer(plugin,()->{
   String[] tips={"Welcome to BeastSMP! Explore the lobby with /beast.","Make the lobby yours with /settings.","Try Halloween, Standard or Christmas with /design."};String message=tips[tip++%tips.length];
   for(Player viewer:Bukkit.getOnlinePlayers())if(!BeastHudPlugin.isSimulated(viewer)&&plugin.enabled(viewer,PlayerSetting.TIPS))viewer.sendMessage(plugin.component(viewer,message,NamedTextColor.AQUA));
  },interval,interval);
 }
 @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void chat(AsyncChatEvent e){e.viewers().removeIf(a->a instanceof Player p&&!p.getUniqueId().equals(e.getPlayer().getUniqueId())&&!plugin.enabled(p,PlayerSetting.CHAT));}
 private void broadcast(Component message){if(message==null)return;Bukkit.getConsoleSender().sendMessage(message);for(Player p:Bukkit.getOnlinePlayers())if(!BeastHudPlugin.isSimulated(p)&&plugin.enabled(p,PlayerSetting.JOIN_MESSAGES))p.sendMessage(message);}
 @EventHandler(priority=EventPriority.HIGHEST) public void join(PlayerJoinEvent e){if(BeastHudPlugin.isSimulated(e.getPlayer()))return;Component message=e.joinMessage();e.joinMessage(null);broadcast(message);}
 @EventHandler(priority=EventPriority.HIGHEST) public void quit(PlayerQuitEvent e){if(BeastHudPlugin.isSimulated(e.getPlayer()))return;Component message=e.quitMessage();e.quitMessage(null);broadcast(message);}
}
