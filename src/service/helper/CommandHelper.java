package service.helper;

import arc.func.Func;
import arc.math.Mathf;
import arc.struct.ObjectMap;
import arc.struct.Seq;
import arc.util.Log;
import arc.util.Strings;
import arc.util.serialization.JsonReader;
import arc.util.serialization.JsonValue;
import mindustry.gen.Call;
import mindustry.gen.Groups;
import mindustry.gen.Player;
import mindustry.maps.Map;
import mindustry.net.Administration;
import mindustry.server.ServerControl;
import service.util.GameUtil;
import service.util.MapUtil;
import service.util.PlayerUtil;


import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.ExecutionException;

import static mindustry.Vars.*;
import static mindustry.net.Administration.Config;
import static service.BridgeService.chatHistory;

public class CommandHelper {
    public static ObjectMap<String, Func<String[], String>> textCommands = new ObjectMap<>();
    public static ObjectMap<String, Func<String[], String>> imageCommands = new ObjectMap<>();

    static {
        registerTextCommand("game_status", (String[] args) -> {
            StringBuilder str = new StringBuilder();
            str.append(Strings.format("服务器名：@", PlayerUtil.getPlainString(Config.serverName.string())));
            if (state.isMenu()) str.append("\n  ").append("当前未游玩任何地图。");
            if (state.isGame()){
                str.append("\n  ").append("当前游玩：");
                str.append(Strings.format("[@]", GameUtil.currentMode()));
                str.append(state.map.name());
                switch (state.rules.mode()){
                    case survival -> {
                        if (state.rules.waves){
                            if (state.rules.winWave > 0){
                                str.append(Strings.format(" | 第 @/@ 波", state.wave, state.rules.winWave));
                            }else {
                                str.append(Strings.format(" | 第 @ 波", state.wave));
                            }
                        }
                    }
                    case attack -> str.append(Strings.format(" | 剩余 @ 核心", state.rules.waveTeam.cores().size));
                    case pvp -> str.append(Strings.format(" | @ 队比赛中", state.teams.active.size));
                }

                int size = PlayerUtil.getOnlinePlayerInfos().size;
                str.append("\n  ").append(Strings.format("当前游玩时长：@。", GameUtil.gameTime()));
                str.append("\n  ").append(size == 0? "当前无在线玩家。": Strings.format("当前共有 @ 名在线玩家", size));
                if (state.isPaused())str.append("游戏已暂停。");
            }

            return str.toString();
        });
        registerTextCommand("game_players", (String[] args) -> {
            StringBuilder str = new StringBuilder();
            if (state.isMenu()) str.append("\n   ").append("当前未启动服务器。");
            if (state.isGame()){
                if (Groups.player.isEmpty()){
                    str.append("当前无玩家在线。");
                }else {
                    return PlayerUtil.getPlayerInfoList(PlayerUtil.getOnlinePlayerInfos());
                }
            }
            return str.toString();
        });
        registerTextCommand("map_reload", (String[] args) -> {
            maps.reload();
            return Strings.format("重载地图成功。当前共有 @ 张地图。", maps.customMaps().size);
        });
        registerTextCommand("map_list", (String[] args) -> {
            int itemPerPage = 10;
            int mapSize = maps.customMaps().size;
            int maxPage = Mathf.ceil((float) mapSize / itemPerPage);
            int page = 1;
            if (args.length == 1) {
                if (Strings.canParsePositiveInt(args[0])) {
                    page = Mathf.clamp(Integer.parseInt(args[0]), 1, maxPage);
                }else {
                    return "请输入有效的页数。";
                }
            }

            StringBuilder str = new StringBuilder();
            str.append(Strings.format("第 @/@ 页", page, maxPage));
            int startIdx = (page - 1) * itemPerPage;
            int endIdx = Mathf.clamp(startIdx + itemPerPage, startIdx, mapSize);
            for (int i = startIdx; i < endIdx; i++) {
                Map map = maps.customMaps().get(i);
                str.append("\n  ");
                str.append(Strings.format("[#@] ", i + 1));
                str.append(map.name());
            }

            return Strings.format(str.toString());
        });
        registerTextCommand("map_info", (String[] args) -> {
            int mapSize = maps.customMaps().size;
            int mapIndex;

            if (args.length == 1) {
                if (Strings.canParsePositiveInt(args[0])) {
                    mapIndex = Integer.parseInt(args[0]);
                    if (mapIndex < 10000){
                        mapIndex = Mathf.clamp(mapIndex, 1, mapSize) - 1;
                        Map map = maps.customMaps().get(mapIndex);
                        StringBuilder str = new StringBuilder();
                        str.append(Strings.format("[#@] ", mapIndex + 1)).append(map.name());
                        str.append("\n").append(GameUtil.correctString(map.description()));
                        str.append("\n  ").append("作者：").append(GameUtil.correctString(map.author()));
                        str.append("\n  ").append("大小：").append(Strings.format("@ x @", map.width, map.height));
                        str.append("\n  ").append("模式：").append(GameUtil.modeName(map.rules().mode()));
                        if (map.mod != null) str.append("\n  ").append("模组：").append(Strings.format("@:@", map.mod.name, map.mod.meta.version));
                        str.append("\n  ").append("版本：").append(Strings.format("@.0/v@", map.version, map.build));
                        return str.toString();
                    }else {
                        try {
                            String info = ResourceSiteHelper.mapInfoFromResourceSiteAsync(mapIndex).get();

                            if (info != null) {
                                JsonReader reader = new JsonReader();
                                JsonValue json = reader.parse(info);
                                JsonValue tag = json.get("tags");
                                StringBuilder str = new StringBuilder();
                                str.append(Strings.format("[#@] ", mapIndex + 1)).append(json.getString("name"));
                                str.append("\n").append(GameUtil.correctString(tag.getString("description")));
                                str.append("\n  作者：").append(GameUtil.correctString(tag.getString("author")));
                                str.append("\n  大小：").append(Strings.format("@ x @", tag.getString("width"), tag.getString("height")));
                                str.append("\n  模式：").append(GameUtil.modeName(json.getString("mode")));
                                if (tag.get("mods").asStringArray().length > 0)
                                    str.append("\n  模组：").append(Arrays.toString(tag.get("mods").asStringArray()));
                                str.append("\n  版本：").append(Strings.format("@.0/v@", tag.getString("saveVersion"), tag.getString("build")));
                                return str.toString();
                            } else {
                                return "地图信息获取失败。";
                            }

                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            return "操作被中断。";

                        } catch (ExecutionException e) {
                            Throwable cause = e.getCause();
                            if (cause instanceof java.net.SocketTimeoutException) {
                                return "连接超时，请稍后重试。";
                            } else if (cause instanceof java.net.ConnectException) {
                                return "连接失败，请检查网络。";
                            } else if (cause instanceof java.net.UnknownHostException) {
                                return "无法连接到服务器，域名解析失败。";
                            } else if (cause instanceof IOException) {
                                if (cause.getMessage().contains("HTTP 400")){
                                    return "未在WayZer地图站上查询到编号为 " + mapIndex + " 的地图。";
                                }
                                return "网络异常：" + cause.getMessage();
                            } else {
                                Log.err("执行异常：", cause);
                                return "地图查询失败：" + cause.getClass().getSimpleName();
                            }

                        } catch (NullPointerException e) {
                            Log.err("解析异常：", e);
                            return "地图数据解析异常。";

                        } catch (Throwable t) {
                            Log.err("未知异常：", t);
                            return "地图查询过程中出现未知错误。";
                        }

                    }
                }else {
                    for (int i = 0; i < mapSize; i++) {
                        Map map = maps.customMaps().get(i);
                        if (MapUtil.plainName(map).contains(args[0])){
                            StringBuilder str = new StringBuilder();
                            str.append(Strings.format("[#@] ", i + 1)).append(map.name());
                            str.append("\n").append(GameUtil.correctString(map.description()));
                            str.append("\n  ").append("作者：").append(GameUtil.correctString(map.author()));
                            str.append("\n  ").append("大小：").append(Strings.format("@ x @", map.width, map.height));
                            str.append("\n  ").append("模式：").append(GameUtil.modeName(map.rules().mode()));
                            if (map.mod != null) str.append("\n  ").append("模组：").append(Strings.format("@:@", map.mod.name, map.mod.meta.version));
                            str.append("\n  ").append("版本：").append(Strings.format("@.0/v@", map.version, map.build));
                            return str.toString();
                        };
                    }
                    return "未找到地图。";
                }
            }

            return "查询地图时出现异常。";
        });
        registerTextCommand("map_host", (String[] args) -> {
            int mapIndex = 0;
            int mapSize = maps.customMaps().size;

            if (args.length == 1 && Strings.canParsePositiveInt(args[0])) {
                mapIndex = Strings.parseInt(args[0]);
            }

            if (mapIndex < 10000){
                mapIndex = Mathf.clamp(mapIndex, 1, mapSize) - 1;
                Map map = maps.customMaps().get(mapIndex);
                try {
                    ServerControl.instance.play(false, () ->
                            world.loadMap(map, map.applyRules(ServerControl.instance.lastMode)));
                    return "已尝试强制切换地图为：" + map.name();
                }catch (Exception e) {
                    ServerControl.instance.play(false, () -> world.loadMap(maps.defaultMaps().first()));
                    return "切换地图时出现异常。";
                }
            }else if (mapIndex < 100000) {
                try {
                    Map map = ResourceSiteHelper.mapFromResourceSiteAsync(mapIndex).get();
                    if (map != null) {
                        ServerControl.instance.play(false, () ->
                                world.loadMap(map, map.applyRules(ServerControl.instance.lastMode)));
                        return "已尝试强制切换地图为：" + map.name();
                    } else {
                        return "地图不存在或解析失败。";
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return "操作被中断。";

                } catch (ExecutionException e) {
                    Throwable cause = e.getCause();
                    if (cause instanceof java.net.SocketTimeoutException) {
                        return "地图下载超时，请稍后重试。";
                    } else if (cause instanceof java.io.FileNotFoundException) {
                        return "未找到地图。";
                    } else if (cause instanceof java.io.IOException) {
                        return "下载失败：" + cause.getMessage();
                    } else {
                        Log.err("地图加载异常：", cause);
                        return "地图加载失败：" + cause.getClass().getSimpleName();
                    }

                } catch (Exception e) {
                    Log.err("未知错误：", e);
                    ServerControl.instance.play(false, () -> world.loadMap(maps.defaultMaps().first()));
                    return "地图切换失败，发生未知错误。";
                }

            } else {
                return "地图ID无效。";
            }
        });
        registerTextCommand("player_kick", (String[] args) -> {
            if(!state.isGame()){
                return "当前未启动服务器。";
            }

            if (args.length > 0){
                String playerName = args[0];
                String kickReason = args.length > 1? args[1] : "";

                Player target = Groups.player.find(p -> PlayerUtil.getPlainString(p.name()).contains(args[0]));
                if(target != null){
                    String reason = kickReason.isEmpty() ? "。" : "，原因：" + kickReason;
                    Call.sendMessage("<QQ>[red]已踢出玩家：" + target.name() + reason);
                    target.kick(kickReason);
                    return "成功踢出玩家：" + playerName + reason;
                }else {
                    return "未找到玩家：" + playerName;
                }
            }
            return "请填写要踢出的玩家。";
        });
        registerTextCommand("player_ban_list", (String[] args) -> {
            if (PlayerUtil.getBannedPlayerInfos().isEmpty()){
                return "服务器无封禁玩家。";
            }else{
                return PlayerUtil.getPlayerInfoList(PlayerUtil.getBannedPlayerInfos());
            }
        });
        registerTextCommand("player_ban_name", (String[] args) -> {
            if(!state.isGame()){
                return "当前未启动服务器。";
            }

            if (args.length > 0){
                String playerName = args[0];
                String kickReason = args.length > 1? args[1] : "";

                Player target = Groups.player.find(p -> PlayerUtil.getPlainString(p.name()).contains(args[0]));
                if(target != null){
                    netServer.admins.banPlayerID(target.uuid());
                    String reason = kickReason.isEmpty() ? "。" : "，原因：" + kickReason;
                    Call.sendMessage("<QQ>[red]已封禁玩家：" + target.name() + reason);
                    target.kick(kickReason);
                    return "成功封禁玩家：" + playerName + reason;
                }else {
                    return "未找到玩家：" + playerName;
                }
            }
            return "请填写要封禁的玩家。";
        });
        registerTextCommand("player_unban_name", (String[] args) -> {
            if(!state.isGame()){
                return "当前未启动服务器。";
            }

            if (args.length > 0){
                String playerName = args[0];
                Administration.PlayerInfo target = PlayerUtil.getPlayerUUIDFromName(playerName);

                if(target != null){
                    netServer.admins.unbanPlayerID(target.id);
                    return "成功解封玩家：" + playerName;
                }else {
                    return "未找到玩家：" + playerName;
                }
            }
            return "请填写要解封的玩家。";
        });
        registerTextCommand("chat_broadcast", (String[] args) -> {
            if (state.isMenu()) return "当前未启动服务器。";
            if (state.isGame()) {
                if (args.length > 0) {
                    Call.sendMessage("<QQ>:" + args[0]);
                    chatHistory.insert(0, "<QQ>:" + args[0]);
                    return "已发送消息至服务器：" + args[0];
                }else {
                    return "请在指令后添加消息。";
                }
            }
            return "喊话失败，游戏状态异常。";
        });
        registerTextCommand("chat_history", (String[] args) -> {
            int chatHistoryLength = 10;
            int startIdx = 0;
            int endIdx;
            if (args.length > 0) {
                if (Strings.canParsePositiveInt(args[0])) {
                    startIdx = Mathf.clamp(Integer.parseInt(args[0]), 1, chatHistory.size) - 1;
                }
            }

            startIdx = Mathf.clamp(startIdx, 0, chatHistory.size - 11);
            endIdx = Mathf.clamp(startIdx + chatHistoryLength, 0, chatHistory.size - 1);


            StringBuilder str = new StringBuilder();
            str.append(Strings.format("聊天记录（@~@）：\n", startIdx, endIdx));
            for (int i = startIdx; i < endIdx; i++) {
                str.append(chatHistory.get(Mathf.clamp(chatHistory.size - 1 - i, 0, chatHistory.size - 1))).append("\n");
            }
            return str.toString();
        });

        registerImageCommand("map_info", (String[] args) -> {
            int mapSize = maps.customMaps().size;
            int mapIndex;

            if (args.length == 1) {
                if (Strings.canParsePositiveInt(args[0])) {
                    mapIndex = Integer.parseInt(args[0]);
                    if (mapIndex < 10000){
                        mapIndex = Mathf.clamp(mapIndex, 1, mapSize) - 1;
                        Map map = maps.customMaps().get(mapIndex);
                        return SnapshotHelper.mapSnapshot(map);
                    }
                }
            }

            return "";
        });
    }

    public static void registerTextCommand(String command, Func<String[], String> func) {
        textCommands.put(command, func);
    }

    public static void registerImageCommand(String command, Func<String[], String> func) {
        imageCommands.put(command, func);
    }

    public static String handleTextCommand(String command, String[] arguments) {
        try {
            if (!textCommands.containsKey(command)) return "服务器未找到该指令 >~<";
            return textCommands.get(command, (String[] args) -> "").get(arguments);
        }catch (Exception e) {
            Log.err(e);
            return "执行指令时出错 >~<";
        }
    }

    public static String handleTextCommand(String command) {
        if (command == null || command.isEmpty()) return "执行指令时出错 >~<";
        String[] values = command.split(" ");
        String[] args = Arrays.copyOfRange(values, 1, values.length);
        return handleTextCommand(values[0], args);
    }

    public static String handleImageCommand(String command, String[] arguments) {
        try {
            if (!imageCommands.containsKey(command)) return "";
            return imageCommands.get(command, (String[] args) -> "").get(arguments);
        }catch (Exception e) {
            Log.err(e);
            return "";
        }
    }

    public static String handleImageCommand(String command) {
        if (command == null || command.isEmpty()) return "";
        String[] values = command.split(" ");
        String[] args = Arrays.copyOfRange(values, 1, values.length);
        return handleImageCommand(values[0], args);
    }
}
