package service.util;

import arc.Core;
import arc.util.Log;
import arc.util.Strings;
import mindustry.core.UI;
import mindustry.game.Gamemode;
import mindustry.maps.Map;
import mindustry.maps.MapException;

import static mindustry.Vars.*;

public class GameUtil {
    //host command.
    public static void hostGame(){
        if(state.isGame()){
            Log.err("Server is already hosting. auto-host cancelled.");
            return;
        }

        Gamemode preset = Gamemode.survival;

        Map result = maps.getShuffleMode().next(preset, state.map);

        logic.reset();
        if(result != null){
            try{
                world.loadMap(result, result.applyRules(preset));
                state.rules = result.applyRules(preset);
                logic.play();

                Log.info("Map loaded.");

                netServer.openServer();
            }catch(MapException e){
                Log.err("@: @", e.map.plainName(), e.getMessage());
            }
        }
    }

    public static String plainName(String name){
        return Strings.stripGlyphs(Strings.stripColors(name));
    }

    public static String correctString(String string){
        if (string == null || string.isEmpty() || string.equals("unknown")) return "无";
        return plainName(string);
    }

    public static String modeName(Gamemode mode){
        return switch (mode) {
            case pvp -> "PvP";
            case editor -> "编辑器";
            case attack -> "进攻";
            case sandbox -> "沙盒";
            default -> "生存";
        };
    }

    public static String modeName(String mode){
        return switch (mode.toLowerCase()) {
            case "pvp" -> "PvP";
            case "editor" -> "编辑器";
            case "attack" -> "进攻";
            case "sandbox" -> "沙盒";
            default -> "生存";
        };
    }

    public static String currentMode(){
        if (state.rules.modeName == null || state.rules.modeName.isEmpty()){
            return GameUtil.modeName(state.rules.mode());
        }else {
            return state.rules.modeName;
        }
    }

    ///total game time (without stop) as a mm:ss/hh:mm:ss
    public static String gameTime(){
        return UI.formatTime((float) state.tick);
    }
}
