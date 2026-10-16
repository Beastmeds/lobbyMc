package de.beastsmp.hud;

import com.sun.net.httpserver.HttpServer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;
import java.util.zip.*;

/** Hosts one complete, theme-specific resource pack per player on the existing pack port. */
final class BeastThemePacks implements Listener {
    private final Map<UUID,UUID> packIds=new HashMap<>();
    private record Pack(String hash,byte[] sha,byte[] bytes) {}
    private final BeastHudPlugin plugin;
    private final Map<UUID,String> sent=new HashMap<>();
    private final Map<UUID,Long> pending=new HashMap<>();
    private volatile Map<BeastHudPlugin.Theme,Pack> current=Map.of();
    private volatile Map<String,Pack> hosted=Map.of();
    private HttpServer server;
    private ExecutorService executor;
    private boolean building;
    private boolean rebuildQueued;
    private volatile boolean closed;
    BeastThemePacks(BeastHudPlugin plugin){this.plugin=plugin;}
    void enable(){
        try{
            server=HttpServer.create(new InetSocketAddress(8163),0);
            executor=Executors.newFixedThreadPool(3,r->{Thread t=new Thread(r,"BeastSMP pack download");t.setDaemon(true);return t;});
            server.setExecutor(executor);
            server.createContext("/",exchange->{
                try{
                    String method=exchange.getRequestMethod();String path=exchange.getRequestURI().getPath();Pack pack=hosted.get(path);
                    if(!method.equals("GET")&&!method.equals("HEAD")){exchange.sendResponseHeaders(405,-1);return;}
                    if(pack==null){exchange.sendResponseHeaders(404,-1);return;}
                    exchange.getResponseHeaders().set("Content-Type","application/zip");
                    exchange.getResponseHeaders().set("Cache-Control","public, max-age=31536000, immutable");
                    exchange.getResponseHeaders().set("Content-Length",Integer.toString(pack.bytes.length));
                    exchange.sendResponseHeaders(200,method.equals("HEAD")?-1:pack.bytes.length);
                    if(method.equals("GET"))exchange.getResponseBody().write(pack.bytes);
                }finally{exchange.close();}
            });
            server.start();Bukkit.getPluginManager().registerEvents(this,plugin);
            Bukkit.getScheduler().runTaskLater(plugin,()->{if(current.isEmpty())rebuild();},160L);
            plugin.getLogger().info("Seasonal complete resource packs hosted on port 8163.");
        }catch(IOException e){throw new IllegalStateException("Cannot open BeastSMP resource pack server",e);}
    }
    void disable(){closed=true;if(server!=null)server.stop(0);if(executor!=null)executor.shutdownNow();sent.clear();pending.clear();packIds.clear();}
    void forget(Player p){sent.remove(p.getUniqueId());pending.remove(p.getUniqueId());packIds.remove(p.getUniqueId());}
    void send(Player p){
        if(!p.isOnline()||BeastHudPlugin.isSimulated(p))return;Pack pack=current.get(plugin.profile(p.getUniqueId()).theme);if(pack==null)return;
        if(pack.hash.equals(sent.get(p.getUniqueId())))return;
        sent.put(p.getUniqueId(),pack.hash);
        long ticket=System.nanoTime();pending.put(p.getUniqueId(),ticket);
        Bukkit.getScheduler().runTaskLater(plugin,()->{if(p.isOnline()&&Objects.equals(pending.get(p.getUniqueId()),ticket)){pending.remove(p.getUniqueId());p.kick(net.kyori.adventure.text.Component.text("The BeastSMP resource pack is required. Enable server resource packs and reconnect."));}},2400L);
        UUID packId=UUID.nameUUIDFromBytes(pack.sha);packIds.put(p.getUniqueId(),packId);
        p.setResourcePack(packId,"http://82.165.71.206:8163/"+pack.hash+".zip",pack.sha,"Install the required BeastSMP resource pack to play (HUD, menus and seasonal designs).",true);
        plugin.getLogger().info("Theme pack sent: "+p.getName()+" theme="+plugin.profile(p.getUniqueId()).theme+" sha1="+pack.hash);
    }
    @EventHandler public void status(PlayerResourcePackStatusEvent event){
        if(!event.getID().equals(packIds.get(event.getPlayer().getUniqueId())))return;
        plugin.getLogger().info("Theme pack status: "+event.getPlayer().getName()+" "+event.getStatus());
        switch(event.getStatus()){
            case SUCCESSFULLY_LOADED->pending.remove(event.getPlayer().getUniqueId());
            case DECLINED,FAILED_DOWNLOAD,FAILED_RELOAD,INVALID_URL,DISCARDED->{forget(event.getPlayer());event.getPlayer().kick(net.kyori.adventure.text.Component.text("The BeastSMP resource pack is required to play. Enable server resource packs and reconnect."));}
            default->{}
        }
    }
    void rebuild(){
        if(closed)return;if(building){rebuildQueued=true;return;}building=true;
        Bukkit.getScheduler().runTaskAsynchronously(plugin,()->{
            try{
                Path base=plugin.getDataFolder().toPath().resolveSibling("BetterHud").resolve("build.zip");
                Map<BeastHudPlugin.Theme,Pack> packs=new EnumMap<>(BeastHudPlugin.Theme.class);
                for(BeastHudPlugin.Theme theme:BeastHudPlugin.Theme.values()){
                    String key=switch(theme){case HALLOWEEN->"halloween";case STANDARD->"standard";case WEIHNACHTEN->"winter";};
                    Path skin=plugin.getDataFolder().toPath().resolve("esc-themes").resolve(key+".zip");
                    byte[] bytes=merge(base,skin);byte[] digest=MessageDigest.getInstance("SHA-1").digest(bytes);
                    packs.put(theme,new Pack(HexFormat.of().formatHex(digest),digest,bytes));
                }
                if(closed)return;
                Map<String,Pack> routes=new HashMap<>();current.values().forEach(p->routes.put("/"+p.hash+".zip",p));packs.values().forEach(p->routes.put("/"+p.hash+".zip",p));
                hosted=Map.copyOf(routes);current=Map.copyOf(packs);
                Bukkit.getScheduler().runTask(plugin,()->{
                    if(closed)return;building=false;
                    packs.forEach((theme,pack)->plugin.getLogger().info("Ready theme pack: "+theme+" sha1="+pack.hash+" bytes="+pack.bytes.length));
                    Bukkit.getOnlinePlayers().forEach(this::send);
                    if(rebuildQueued){rebuildQueued=false;rebuild();}
                });
            }catch(Exception e){
                plugin.getLogger().log(java.util.logging.Level.SEVERE,"Could not build themed resource packs",e);
                if(!closed)Bukkit.getScheduler().runTask(plugin,()->{building=false;Bukkit.getScheduler().runTaskLater(plugin,this::rebuild,100L);});
            }
        });
    }
    private static byte[] merge(Path base,Path skin)throws IOException{
        Map<String,byte[]> entries=new LinkedHashMap<>();
        for(Path path:List.of(base,skin))try(ZipFile zip=new ZipFile(path.toFile())){
            for(Enumeration<? extends ZipEntry> e=zip.entries();e.hasMoreElements();){ZipEntry item=e.nextElement();if(!item.isDirectory())try(InputStream in=zip.getInputStream(item)){entries.put(item.getName(),in.readAllBytes());}}
        }
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();try(ZipOutputStream zip=new ZipOutputStream(bytes)){
            for(var item:entries.entrySet()){ZipEntry entry=new ZipEntry(item.getKey());entry.setTime(0);zip.putNextEntry(entry);zip.write(item.getValue());zip.closeEntry();}
        }return bytes.toByteArray();
    }
}
