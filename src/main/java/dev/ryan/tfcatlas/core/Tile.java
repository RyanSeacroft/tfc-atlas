package dev.ryan.tfcatlas.core;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.zip.Deflater;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public record Tile(Key key, Cell[] cells) {
    public static final int SIDE = 32, GRID = 8, FORMAT = 6;
    // Soil adds a field, but existing terrain samples can be upgraded in place.
    public static final int CACHE_GENERATION = 5;

    public record Key(int x, int z, int step) {
        public Key {
            if (step < 1 || step > 65536 || Integer.bitCount(step) != 1) {
                throw new IllegalArgumentException("Invalid sampling step");
            }
        }

        public int span() {
            return SIDE * GRID * step;
        }

        public int blockX() {
            return x * span();
        }

        public int blockZ() {
            return z * span();
        }

        public String fileName() {
            return (step <= 128 / GRID ? "fine3_" : "") + x + "_" + z + "_" + step + ".gz";
        }

        public static Key at(int blockX, int blockZ, int step) {
            int s = SIDE * GRID * step;
            return new Key(Math.floorDiv(blockX, s), Math.floorDiv(blockZ, s), step);
        }
    }

    public Cell atBlock(int x, int z) {
        int i = Math.floorDiv(x - key.blockX(), GRID * key.step()),
                j = Math.floorDiv(z - key.blockZ(), GRID * key.step());
        return i < 0 || j < 0 || i >= SIDE || j >= SIDE ? null : cells[i + j * SIDE];
    }

    public void write(Path path) throws IOException {
        Files.createDirectories(path.getParent());
        Path temp = Files.createTempFile(path.getParent(), "tile-", ".tmp");
        try {
            try (DataOutputStream d =
                    new DataOutputStream(
                            new BufferedOutputStream(
                                    new GZIPOutputStream(Files.newOutputStream(temp)) {
                                        {
                                            def.setLevel(Deflater.BEST_SPEED);
                                        }
                                    }))) {
                d.writeInt(0x54464154);
                d.writeInt(FORMAT);
                d.writeInt(key.x);
                d.writeInt(key.z);
                d.writeInt(key.step);
                for (Cell c : cells) {
                    d.writeUTF(c.rock());
                    d.writeUTF(c.biome());
                    d.writeByte(c.rockType());
                    d.writeFloat(c.rain());
                    d.writeFloat(c.temperature());
                    d.writeInt(c.altitude());
                    d.writeInt(c.inland());
                    d.writeInt(c.oceanDistance());
                    d.writeByte(c.flags());
                    d.writeUTF(c.middleRock());
                    d.writeUTF(c.bottomRock());
                    d.writeFloat(c.rainVariance());
                    d.writeFloat(c.baseGroundwater());
                    d.writeUTF(c.climateZone());
                    d.writeByte(c.soil().ordinal());
                }
            }
            try {
                Files.move(
                        temp,
                        path,
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    public static Tile read(Path path, Key expected) throws IOException {
        try (DataInputStream d =
                new DataInputStream(
                        new BufferedInputStream(new GZIPInputStream(Files.newInputStream(path))))) {
            int magic = d.readInt(), format = d.readInt();
            if (magic != 0x54464154 || format != 5 && format != FORMAT) {
                throw new IOException("Unknown cache version");
            }
            if (d.readInt() != expected.x
                    || d.readInt() != expected.z
                    || d.readInt() != expected.step) {
                throw new IOException("Wrong tile");
            }
            Cell[] a = new Cell[SIDE * SIDE];
            for (int i = 0; i < a.length; i++) {
                String rock = d.readUTF(), biome = d.readUTF();
                int type = d.readUnsignedByte();
                float rain = d.readFloat(), temp = d.readFloat();
                int alt = d.readInt(),
                        inland = d.readInt(),
                        ocean = d.readInt(),
                        flags = d.readUnsignedByte();
                if (type > 3 || !Float.isFinite(rain) || !Float.isFinite(temp)) {
                    throw new IOException("Invalid tile");
                }
                a[i] =
                        new Cell(
                                rock,
                                biome,
                                type,
                                rain,
                                temp,
                                alt,
                                inland,
                                ocean,
                                flags,
                                d.readUTF(),
                                d.readUTF(),
                                d.readFloat(),
                                d.readFloat(),
                                d.readUTF());
                if (format >= 6) {
                    int soil = d.readUnsignedByte();
                    if (soil >= Soil.values().length) {
                        throw new IOException("Invalid soil cache");
                    }
                    a[i] = a[i].withSoil(Soil.values()[soil]);
                }
                if (!Float.isFinite(a[i].rainVariance())
                        || Math.abs(a[i].rainVariance()) > 1
                        || !Float.isFinite(a[i].baseGroundwater())
                        || a[i].baseGroundwater() < 0) {
                    throw new IOException("Invalid climate cache");
                }
            }
            if (d.read() != -1) {
                throw new IOException("Trailing cache data");
            }
            return new Tile(expected, a);
        }
    }
}
