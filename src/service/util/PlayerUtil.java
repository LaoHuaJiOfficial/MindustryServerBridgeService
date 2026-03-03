package service.util;

import arc.struct.Seq;
import arc.util.Nullable;
import arc.util.Strings;
import mindustry.gen.Groups;
import mindustry.net.Administration;

import static mindustry.Vars.netServer;

public class PlayerUtil {
    public static Seq<Administration.PlayerInfo> getAdminPlayerInfos() {
        return netServer.admins.getAdmins();
    }

    public static Seq<Administration.PlayerInfo> getBannedPlayerInfos() {
        return netServer.admins.getBanned();
    }

    public static Seq<Administration.PlayerInfo> getOnlinePlayerInfos() {
        Seq<Administration.PlayerInfo> players = new Seq<>();
        Groups.player.each(player -> players.add(netServer.admins.getInfo(player.uuid())));
        return players;
    }

    public static Seq<Administration.PlayerInfo> getAllPlayerInfos() {
        return netServer.admins.playerInfo.values().toSeq();
    }

    public static String getPlayerInfoList(Seq<Administration.PlayerInfo> players) {
        StringBuilder str = new StringBuilder();
        str.append(Strings.format("共查询到 @ 名玩家。", players.size)).append("\n");
        for (Administration.PlayerInfo player : players) {
            str.append(getPlainString(player.lastName)).append("\n").append("    ").append(player.id);
        }
        return str.toString();
    }

    public static @Nullable Administration.PlayerInfo getPlayerUUIDFromName (String name) {
        Administration.PlayerInfo playerInfo = null;
        for (Administration.PlayerInfo player : getAllPlayerInfos()) {
            if (checkName(player.lastName, name)) {
                playerInfo = player;
                break;
            }
            for (String oldName: player.names) {
                if (checkName(oldName, name)) {
                    playerInfo = player;
                    break;
                }
            }
        }
        return playerInfo;
    }
    public static boolean checkName(String playerName, String name) {
        return getPlainString(playerName).contains(name);
    }

    public static String getPlainString(String original) {
        return Strings.stripGlyphs(Strings.stripColors(original));
    }
}
