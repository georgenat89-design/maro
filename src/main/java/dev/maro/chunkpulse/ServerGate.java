package dev.maro.chunkpulse;

import java.net.IDN;
import java.util.Locale;

public final class ServerGate {
    private ServerGate() { }
    public static String host(String address) {
        if(address==null)return "";
        String value=address.trim().toLowerCase(Locale.ROOT);
        if(value.startsWith("[")){int end=value.indexOf(']');return end>0?value.substring(1,end):"";}
        if(value.indexOf(':')==value.lastIndexOf(':')&&value.indexOf(':')>=0)value=value.substring(0,value.indexOf(':'));
        while(value.endsWith("."))value=value.substring(0,value.length()-1);
        try{return IDN.toASCII(value);}catch(IllegalArgumentException error){return "";}
    }
    public static boolean allows(String address, String domains, boolean singleplayer) {
        if(singleplayer)return false;
        String host=host(address);if(host.isEmpty())return false;
        for(String domain:domains.split("[,;\\s]+")){
            String allowed=host(domain);
            if(!allowed.isEmpty()&&(host.equals(allowed)||host.endsWith("."+allowed)))return true;
        }
        return false;
    }
}
