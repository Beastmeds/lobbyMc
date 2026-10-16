package de.beastsmp.presence;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.api.event.ProxyPingEvent;
import net.md_5.bungee.api.ServerPing;
import net.md_5.bungee.event.EventHandler;
import net.md_5.bungee.event.EventPriority;
import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** Adds the active local simulated players to the public ping without changing routing counts. */
public final class BeastPresence extends Plugin implements Listener {
    private record Snapshot(long updated,List<ServerPing.PlayerInfo> players) {}
    private volatile Snapshot snapshot=new Snapshot(0,List.of());
    private Path source;
    public void onEnable(){
        source=Path.of(System.getProperty("beastsmp.presence.file","/root/lobbyMc/plugins/BeastBots/presence.json"));
        getProxy().getPluginManager().registerListener(this,this);
        getProxy().getScheduler().schedule(this,this::read,0,1,TimeUnit.SECONDS);
        getLogger().info("Public presence enabled; source="+source);
    }
    private void read(){
        try{
            JsonObject json=JsonParser.parseString(Files.readString(source)).getAsJsonObject();
            long updated=json.get("updated").getAsLong();List<ServerPing.PlayerInfo> players=new ArrayList<>();Set<UUID> seen=new HashSet<>();
            for(JsonElement item:json.getAsJsonArray("players")){
                var p=item.getAsJsonObject();String name=p.get("name").getAsString();UUID id=UUID.fromString(p.get("uuid").getAsString());
                if(name.matches("[A-Za-z0-9_]{3,16}")&&seen.add(id)&&players.size()<40)players.add(new ServerPing.PlayerInfo(name,id));
            }
            snapshot=new Snapshot(updated,List.copyOf(players));
        }catch(Exception e){snapshot=new Snapshot(0,List.of());}
    }
    @EventHandler(priority=EventPriority.HIGHEST) public void ping(ProxyPingEvent event){
        Snapshot state=snapshot;long age=System.currentTimeMillis()-state.updated;
        if(age<0||age>15000||state.players.isEmpty())return;
        ServerPing response=event.getResponse();if(response==null||response.getPlayers()==null)return;
        var players=response.getPlayers();int total=getProxy().getOnlineCount()+state.players.size();players.setOnline(total);players.setMax(Math.max(players.getMax(),total));
        List<ServerPing.PlayerInfo> sample=new ArrayList<>();
        getProxy().getPlayers().stream().limit(20).forEach(p->sample.add(new ServerPing.PlayerInfo(p.getName(),p.getUniqueId())));
        sample.addAll(state.players);players.setSample(sample.toArray(ServerPing.PlayerInfo[]::new));
    }
}
