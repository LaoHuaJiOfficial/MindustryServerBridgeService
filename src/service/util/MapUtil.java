package service.util;

import arc.struct.Seq;
import mindustry.maps.Map;
import mindustry.server.ServerControl;

import static mindustry.Vars.maps;
import static mindustry.Vars.world;

public class MapUtil {
    public static void scheduleHostMap(Map nextMap){
        maps.setNextMapOverride(nextMap);
    }

    public static void hostMap(Map map){
        ServerControl.instance.play(false, () -> world.loadMap(map, map.applyRules(ServerControl.instance.lastMode)));
    }

    public static Seq<Map> searchMap(String name){
        return maps.customMaps().select(map -> plainName(map).contains(plainName(name)));
    }

    public static String plainName(String name){
        return GameUtil.plainName(name).replace('_', ' ').toLowerCase();
    }

    public static String plainName(Map map){
        return map.plainName().replace('_', ' ').toLowerCase();
    }
}
