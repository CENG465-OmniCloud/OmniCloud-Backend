package com.omnicloud.api.model;

public class Shard {
    private final int index; // 0 to 5
    private final byte[] data;

    public Shard(int index, byte[] data) {
        this.index = index;
        this.data = data;
    }

    public int getIndex() { return index; }
    public byte[] getData() { return data; }
}
