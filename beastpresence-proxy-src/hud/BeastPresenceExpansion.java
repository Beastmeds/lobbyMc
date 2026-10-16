package de.beastsmp.presence;
import me.clip.placeholderapi.PlaceholderAPI;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import com.google.gson.JsonParser;
import java.nio.file.*;

/** Real proxy connections plus the current managed backend players. */
public final class BeastPresenceExpansion extends PlaceholderExpansion {
    private long checked;
    private int bots;
    public String getIdentifier(){return "beastpresence";}
    public String getAuthor(){return "BeastSMP";}
    public String getVersion(){return "1.0.0";}
    private synchronized int bots(){
        long now=System.currentTimeMillis();if(now-checked<1000)return bots;checked=now;bots=0;
        try{
            var json=JsonParser.parseString(Files.readString(getPlaceholderAPI().getDataFolder().toPath().resolveSibling("BeastBots").resolve("presence.json"))).getAsJsonObject();
            long age=now-json.get("updated").getAsLong();
            if(age>=0&&age<=15000)bots=Math.min(40,json.getAsJsonArray("players").size());
        }catch(Exception ignored){}
        return bots;
    }
    @Override public String onRequest(OfflinePlayer player,String param){
        int active=bots();if(param.equalsIgnoreCase("bots"))return Integer.toString(active);
        if(!param.equalsIgnoreCase("online"))return null;
        String actual=PlaceholderAPI.setPlaceholders(player,"%bungee_total%");
        int real;try{real=Math.max(0,Integer.parseInt(actual));}catch(NumberFormatException e){real=0;}
        return Integer.toString(real+active);
    }
}
