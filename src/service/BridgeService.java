package service;

import arc.*;
import arc.files.Fi;
import arc.struct.IntIntMap;
import arc.struct.ObjectMap;
import arc.struct.Seq;
import arc.util.*;
import arc.util.serialization.JsonReader;
import arc.util.serialization.JsonValue;
import mindustry.Vars;
import mindustry.core.NetServer;
import mindustry.game.EventType;
import mindustry.game.Team;
import mindustry.mod.*;
import mindustry.net.Administration.*;
import mindustry.world.Block;
import service.helper.CommandHelper;
import service.struct.BlockData;
import service.util.GameUtil;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;

import static mindustry.Vars.content;

public class BridgeService extends Plugin{
    private static GameWebSocketServer wsServer;
    public static Seq<String> chatHistory = new Seq<>();
    public static ObjectMap<String, BlockData> blockData = new ObjectMap<>();
    public static IntIntMap teamColors = new IntIntMap();

    public static Fi dataDir = Vars.dataDirectory.child("data");

    public static Config
            wsPort = new Config("WebSocket Port", "Port used for WebSocket Server.", 10721),
            wsToken = new Config("WebSocket Token", "Token used for WebSocket connection.", "secret-token");

    @Override
    public void init(){
        Core.app.addListener(new ApplicationListener() {
            @Override
            public void init() {
                int port = wsPort.num();

                wsServer = new GameWebSocketServer(port);
                wsServer.start();
            }

            @Override
            public void dispose() {
                try {
                    wsServer.stop();
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        });

        Events.on(EventType.PlayerChatEvent.class, e -> {
            LocalTime now = LocalTime.now();
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm");
            String formattedTime = now.format(formatter);

            chatHistory.insert(0, "[" + formattedTime + "]" + Strings.stripGlyphs(e.player.plainName()) + ": " + e.message);
            while (chatHistory.size > 100) chatHistory.pop();
        });

        readBlockDataList();
        readTeamList();

        Time.runTask(300, GameUtil::hostGame);
    }

    @Override
    public void registerServerCommands(CommandHandler handler) {
        handler.<NetServer>register("command", "<command> [args]", "Run a command for debug", (command, server) -> {
            String[] args = Arrays.copyOfRange(command, 1, command.length);
            Log.info(command[0] + " " + Arrays.toString(args) + "\n" + CommandHelper.handleTextCommand(command[0], args));
        });
    }

    public void readBlockDataList(){
        try {
            dataDir.findAll().each(fi -> {
                if (fi.name().startsWith("blocklist")){
                    String data = fi.readString();
                    JsonReader reader = new JsonReader();
                    JsonValue blocks = reader.parse(data);
                    for (JsonValue block : blocks.iterator()) {
                        Block b = content.block(block.name);
                        if (b != null) {
                            BlockData bd = new BlockData();
                            bd.synthetic = block.getBoolean("synthetic");
                            bd.solid = block.getBoolean("solid");
                            bd.size = block.getInt("size");
                            bd.color = block.getInt("color");
                            blockData.put(block.name, bd);
                        }
                    }
                }
            });
        }catch (Exception e){
            Log.err(e);
        }
    }

    public void readTeamList(){
        try {
            JsonReader reader = new JsonReader();
            JsonValue teams = reader.parse(dataDir.child("teamlist.json"));
            for (int i = 0; i < Team.all.length; i++){
                teamColors.put(i, teams.getInt(i));
            }
            Log.info("Team Colors: " + teamColors);
        }catch (Exception e){
            Log.err(e);
        }
    }
}
