package service.struct;

public class BlockData {
    public String block;
    public boolean synthetic;
    public boolean solid;
    public int size = 1;
    public int color = 255;

    public static BlockData empty = new BlockData();
}
