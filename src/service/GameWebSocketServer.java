package service;

import arc.files.Fi;
import arc.struct.ObjectMap;
import arc.util.Log;
import arc.util.Strings;
import arc.util.serialization.JsonReader;
import arc.util.serialization.JsonValue;
import arc.util.serialization.JsonWriter;
import mindustry.Vars;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;
import service.helper.CommandHelper;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;

import static mindustry.Vars.maps;
import static service.BridgeService.wsToken;

public class GameWebSocketServer extends WebSocketServer {

    private WebSocket activeConnection = null;
    private String activeToken = null;

    private final ObjectMap<WebSocket, String> pendingFileName = new ObjectMap<>();

    public GameWebSocketServer(int port) {
        super(new InetSocketAddress(port));
    }

    @Override
    public void onStart() {
        Log.info("[BridgeService] WebSocket Server started on " + this.getAddress());
    }

    @Override
    public void onOpen(WebSocket webSocket, ClientHandshake clientHandshake) {
        String path = clientHandshake.getResourceDescriptor();

        if (!path.startsWith("/game")) {
            webSocket.close(1008, "[BridgeService] Invalid Path.");
            Log.info("[BridgeService] Connection closed: Invalid path - " + path);
            return;
        }

        String token = extractToken(path);
        if (!isValidToken(token)) {
            webSocket.close(1008, "[BridgeService] Invalid or missing token.");
            Log.debug("[BridgeService] Connection rejected: Invalid token from " + webSocket.getRemoteSocketAddress());
            return;
        }

        if (activeConnection != null && activeConnection.isOpen()) {
            if (token.equals(activeToken)) {
                activeConnection.close(1012, "[BridgeService] Kicked by new connection.");
                Log.debug("[BridgeService] Old connection replaced with new one from " + webSocket.getRemoteSocketAddress());
            } else {
                webSocket.close(1013, "[BridgeService] Another session already active.");
                Log.debug("[BridgeService] Connection rejected: token mismatch from " + webSocket.getRemoteSocketAddress());
                return;
            }
        }

        activeConnection = webSocket;
        activeToken = token;
        Log.debug("[BridgeService] New connection accepted: " + webSocket.getRemoteSocketAddress());
    }

    @Override
    public void onClose(WebSocket webSocket, int code, String reason, boolean remote) {
        Log.debug("[BridgeService] Connection closed: " + webSocket.getRemoteSocketAddress());
        if (webSocket == activeConnection) {
            activeConnection = null;
        }
    }

    @Override
    public void onMessage(WebSocket webSocket, String s) {
        JsonReader reader = new JsonReader();
        JsonValue value = reader.parse(s);

        if (value.getString("type", "").equalsIgnoreCase("text")){
            Log.debug("[BridgeService] Command from " + webSocket.getRemoteSocketAddress() + " : " + value);
            String response = CommandHelper.handleTextCommand(value.getString("content"));
            String imageBase64 = CommandHelper.handleImageCommand(value.getString("content"));
            if (response.isEmpty()) return;
            value.get("content").set(response);
            if (!imageBase64.isEmpty()) value.addChild("data", new JsonValue(imageBase64));
            sendMessage(value.toJson(JsonWriter.OutputType.json));
            Log.debug(value);
        }

        if (value.getString("type", "").equalsIgnoreCase("file")) {
            Log.debug("[BridgeService] File from " + webSocket.getRemoteSocketAddress() + " : " + value);
            String filename = value.getString("name", "map.msav");
            pendingFileName.put(webSocket, filename);
        }

        //not a good idea, todo
        if (value.getString("type", "").equalsIgnoreCase("buffer")) {
            String filename = pendingFileName.get(webSocket, "unknown.bin");
            byte[] data = value.remove("data").asByteArray();
            value.get("type").set("text");

            JsonValue response = new JsonValue("content");

            try {
                Fi mapPath = Vars.customMapDirectory;
                Fi map = mapPath.child(filename);
                map.writeBytes(data);

                response.set(Strings.format("上传地图 [@] 成功。", filename));
                maps.reload();
            } catch (Exception e) {
                Log.err("[BridgeService] Failed to write file: " + filename, e);
                response.set("上传地图失败。");
            }

            value.addChild(response);
            sendMessage(value.toJson(JsonWriter.OutputType.json));
        }
    }

    @Override
    public void onMessage(WebSocket conn, ByteBuffer message) {
        super.onMessage(conn, message);
    }

    @Override
    public void onError(WebSocket webSocket, Exception e) {
        Log.err("[BridgeService]" + e.getMessage());
    }

    //todo merge method
    public void sendMessage(String msg) {
        if (activeConnection == null) return;
        activeConnection.send(msg);
    }

    public void logMessage(String msg) {
        if (activeConnection == null) return;
        activeConnection.send("{\"type\":\"log\",\"content\":\"" + msg + "\"}");
    }

    public void broadcastMessage(String msg) {
        if (activeConnection == null) return;
        activeConnection.send("{\"type\":\"broadcast\",\"content\":\"" + msg + "\"}");

    }

    private String extractToken(String path) {
        int index = path.indexOf("token=");
        if (index == -1) return null;
        return path.substring(index + 6).split("&")[0];
    }

    private boolean isValidToken(String token) {
        return token != null && token.equals(wsToken.string());
    }
}
