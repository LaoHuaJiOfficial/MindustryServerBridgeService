package service.helper;

import arc.files.Fi;
import arc.graphics.Color;
import arc.graphics.Pixmap;
import arc.graphics.PixmapIO;
import arc.util.Log;
import arc.util.io.CounterInputStream;
import mindustry.content.Blocks;
import mindustry.game.Team;
import mindustry.io.SaveIO;
import mindustry.io.SaveVersion;
import mindustry.maps.Map;
import mindustry.world.Block;
import mindustry.world.CachedTile;
import mindustry.world.Tile;
import mindustry.world.WorldContext;
import mindustry.world.blocks.environment.Floor;
import mindustry.world.blocks.storage.CoreBlock;
import service.struct.BlockData;
import service.util.GameUtil;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Base64;
import java.util.zip.InflaterInputStream;

import static mindustry.Vars.*;
import static mindustry.Vars.content;
import static service.BridgeService.blockData;
import static service.BridgeService.teamColors;

public class SnapshotHelper {
    public static final Fi previewMapFolder = dataDirectory.child("preview");
    public static final Fi snapshotMapFolder = dataDirectory.child("snapshot");

    public static final int black = Color.rgba8888(0f, 0f, 0f, 1f);
    public static final int shade = Color.rgba8888(0f, 0f, 0f, 0.5f);

    public static String mapSnapshot(Map map){
        try {
            Pixmap pixmap = generatePreview(map);
            Fi snapshot = snapshotMapFolder.child(GameUtil.plainName(map.name()) + ".png");
            PixmapIO.writePng(snapshot, pixmap);
            pixmap.dispose();
            return "data:image/png;base64," + Base64.getEncoder().encodeToString(snapshot.readBytes());
        }catch (Exception e){
            Log.err(e);
        }
        return "";
    }

    /// @see mindustry.io.MapIO
    public static Pixmap generatePreview(Map map) throws IOException{
        map.spawns = 0;
        map.teams.clear();

        try(InputStream is = new InflaterInputStream(map.file.read(bufferSize)); CounterInputStream counter = new CounterInputStream(is); DataInputStream stream = new DataInputStream(counter)){
            SaveIO.readHeader(stream);
            int version = stream.readInt();
            SaveVersion ver = SaveIO.getSaveWriter(version);
            ver.readRegion("meta", stream, counter, ver::readStringMap);

            Pixmap floors = new Pixmap(map.width, map.height);
            Pixmap walls = new Pixmap(map.width, map.height);
            CachedTile tile = new CachedTile(){
                @Override
                public void setBlock(Block type){
                    super.setBlock(type);

                    int c = colorFor(block(), Blocks.air, Blocks.air, team());
                    if(c != black){
                        walls.setRaw(x, floors.height - 1 - y, c);
                        floors.set(x, floors.height - 1 - y + 1, shade);
                    }
                }
            };

            ver.readRegion("content", stream, counter, ver::readContentHeader);
            ver.readRegion("preview_map", stream, counter, in -> ver.readMap(in, new WorldContext(){
                @Override public void resize(int width, int height){}
                @Override public boolean isGenerating(){return false;}
                @Override public void begin(){
                    world.setGenerating(true);
                }
                @Override public void end(){
                    world.setGenerating(false);
                }

                @Override
                public void onReadBuilding(){
                    //read team colors
                    if(tile.build != null){
                        int c = team(tile.build.team());
                        int size = tile.block().size;
                        int offsetx = -(size - 1) / 2;
                        int offsety = -(size - 1) / 2;
                        for(int dx = 0; dx < size; dx++){
                            for(int dy = 0; dy < size; dy++){
                                int drawx = tile.x + dx + offsetx, drawy = tile.y + dy + offsety;
                                walls.set(drawx, floors.height - 1 - drawy, c);
                            }
                        }

                        if(tile.build.block instanceof CoreBlock){
                            map.teams.add(tile.build.team.id);
                        }
                    }
                }

                @Override
                public Tile tile(int index){
                    tile.x = (short)(index % map.width);
                    tile.y = (short)(index / map.width);
                    return tile;
                }

                @Override
                public Tile create(int x, int y, int floorID, int overlayID, int wallID){
                    if(overlayID != 0){
                        floors.set(x, floors.height - 1 - y, colorFor(Blocks.air, Blocks.air, content.block(overlayID), Team.derelict));
                    }else{
                        floors.set(x, floors.height - 1 - y, colorFor(Blocks.air, content.block(floorID), Blocks.air, Team.derelict));
                    }
                    if(content.block(overlayID) == Blocks.spawn){
                        map.spawns ++;
                    }
                    return tile;
                }
            }));

            floors.draw(walls, true);
            walls.dispose();
            return floors;
        }finally{
            content.setTemporaryMapper(null);
        }
    }

    public static int colorFor(Block wall, Block floor, Block overlay, Team team){
        if(wall.synthetic()) return team(team);
        return (((Floor)overlay).wallOre ? block(overlay) : wall.solid ? block(wall) : !overlay.useColor ? block(floor) : block(overlay));
    }

    public static int block(Block block){
        return blockData.get(block.name, BlockData.empty).color;
    }

    public static int team(Team team){
        return teamColors.get(team.id, 255);
    }
}
