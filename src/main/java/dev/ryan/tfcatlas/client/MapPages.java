package dev.ryan.tfcatlas.client;

import com.mojang.blaze3d.platform.NativeImage;
import dev.ryan.tfcatlas.core.ColourTransition;
import dev.ryan.tfcatlas.core.DetailCoverage;
import dev.ryan.tfcatlas.core.Layer;
import dev.ryan.tfcatlas.core.RockLayer;
import dev.ryan.tfcatlas.core.Sampling;
import dev.ryan.tfcatlas.core.Tile;
import dev.ryan.tfcatlas.core.TileWindow;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

/**
 * Packs 64 data tiles per draw. Display colours survive eviction of the much larger Cell arrays.
 */
final class MapPages {
    private record Key(int x, int z, int step, Layer layer) {}

    final class Page {
        final Key key;
        final BitSet painted = new BitSet(64), ready = new BitSet(64), fallback = new BitSet(64);
        final long[] inheritedRevision = new long[64];
        final int[] inheritedStep = new int[64];
        final long[] childRevisions = new long[256];

        private record Fade(int[] from, int[] to, long started) {}

        final Map<Integer, Fade> fades = new HashMap<>();
        final DetailCoverage fine;
        DynamicTexture texture;
        ResourceLocation id;
        int pixels;
        boolean dirty;
        long revision, nativeRevision;

        Page(Key key, int pixels) {
            this.key = key;
            fine = new DetailCoverage(Math.max(32, key.step * Tile.GRID));
            resize(pixels);
        }

        int tileSpan() {
            return Tile.SIDE * Tile.GRID * key.step;
        }

        int blockX() {
            return key.x * 8 * tileSpan();
        }

        int blockZ() {
            return key.z * 8 * tileSpan();
        }

        int span() {
            return 8 * tileSpan();
        }

        int size() {
            return pixels * 8;
        }

        int index(Tile.Key tile) {
            return Math.floorMod(tile.x(), 8) + 8 * Math.floorMod(tile.z(), 8);
        }

        void resize(int next) {
            finishFades();
            Arrays.fill(childRevisions, 0);
            if (fine != null) {
                next = Math.max(next, key.step / (32 / Tile.GRID));
                fine.invalidateRevisions();
            }
            Arrays.fill(inheritedRevision, 0);
            nativeRevision = revision = ++serial;
            NativeImage image = new NativeImage(next * 8, next * 8, true);
            if (texture != null) {
                NativeImage old = texture.getPixels();
                for (int z = 0; z < next * 8; z++) {
                    for (int x = 0; x < next * 8; x++) {
                        image.setPixelRGBA(
                                x, z, old.getPixelRGBA(x * pixels / next, z * pixels / next));
                    }
                }
                close();
                ready.clear();
            }
            pixels = next;
            texture = new DynamicTexture(image);
            texture.setFilter(false, false);
            id = Minecraft.getInstance().getTextureManager().register("tfcatlas_page", texture);
            dirty = false;
        }

        void add(Tile tile, Profile profile) {
            RockLayer rockLayer = profile.selectedRockLayer();
            int index = index(tile.key()),
                    ox = (index % 8) * pixels,
                    oz = (index / 8) * pixels,
                    group = 32 / pixels;
            NativeImage image = texture.getPixels();
            finishFade(index);
            int[] from = new int[pixels * pixels], to = new int[pixels * pixels];
            boolean changed = false;
            for (int z = 0; z < pixels; z++) {
                for (int x = 0; x < pixels; x++) {
                    int at = x + z * pixels;
                    from[at] = to[at] = image.getPixelRGBA(ox + x, oz + z);
                    if (fine != null
                            && key.step > 32 / Tile.GRID
                            && !fine.coarsePixelAllowed(ox + x, oz + z, pixels)) {
                        continue;
                    }
                    int red = 0, green = 0, blue = 0;
                    // Every source sample contributes when reducing a tile for the screen.
                    for (int dz = 0; dz < group; dz++) {
                        for (int dx = 0; dx < group; dx++) {
                            int rgb =
                                    key.layer.mapColor(
                                            tile.cells()[x * group + dx + (z * group + dz) * 32],
                                            profile.accessible,
                                            profile.colors,
                                            profile.climateContinents,
                                            rockLayer);
                            red += (rgb >>> 16) & 255;
                            green += (rgb >>> 8) & 255;
                            blue += rgb & 255;
                        }
                    }
                    int n = group * group;
                    to[at] = 0xff000000 | ((blue / n) << 16) | ((green / n) << 8) | (red / n);
                    if ((from[at] >>> 24) == 0) {
                        image.setPixelRGBA(ox + x, oz + z, to[at]);
                    } else {
                        changed |= from[at] != to[at];
                    }
                }
            }
            if (changed) {
                fades.put(index, new Fade(from, to, System.nanoTime() / 1_000_000));
            }
            painted.set(index);
            ready.set(index);
            dirty = true;
            nativeRevision = revision = ++serial;
        }

        boolean shown(Tile.Key tile) {
            int i = index(tile);
            return painted.get(i) || fallback.get(i);
        }

        void copyFallback(Page source, Tile.Key tile) {
            int index = index(tile);
            if (painted.get(index)) {
                return;
            }
            source.finishFades();
            boolean lowerPriority =
                    inheritedStep[index] != 0 && source.key.step > inheritedStep[index];
            if (inheritedStep[index] == source.key.step
                    && inheritedRevision[index] == source.revision) {
                return;
            }
            int ox = (index % 8) * pixels, oz = (index / 8) * pixels;
            NativeImage src = source.texture.getPixels(), dst = texture.getPixels();
            boolean any = false;
            for (int z = 0; z < pixels; z++) {
                for (int x = 0; x < pixels; x++) {
                    if (fine != null
                            && key.step > 32 / Tile.GRID
                            && !fine.coarsePixelAllowed(ox + x, oz + z, pixels)) {
                        continue;
                    }
                    if (lowerPriority && (dst.getPixelRGBA(ox + x, oz + z) >>> 24) != 0) {
                        continue;
                    }
                    double wx = tile.blockX() + (x + .5) * tile.span() / pixels,
                            wz = tile.blockZ() + (z + .5) * tile.span() / pixels;
                    int sx = (int) ((wx - source.blockX()) * source.size() / source.span()),
                            sz = (int) ((wz - source.blockZ()) * source.size() / source.span());
                    int rgba = src.getPixelRGBA(sx, sz);
                    if ((rgba >>> 24) != 0) {
                        dst.setPixelRGBA(ox + x, oz + z, rgba);
                        any = true;
                    }
                }
            }
            if (!lowerPriority) {
                inheritedRevision[index] = source.revision;
                inheritedStep[index] = source.key.step;
            }
            if (any) {
                fallback.set(index);
                dirty = true;
                revision = ++serial;
            }
        }

        void copyFine(Page source, Tile.Key tile) {
            source.finishFades();
            int ratio = key.step / (32 / Tile.GRID),
                    x = tile.x() - key.x * 8 * ratio,
                    z = tile.z() - key.z * 8 * ratio;
            if (!fine.needs(x, z, source.nativeRevision)) {
                return;
            }
            finishFade(index(Tile.Key.at(tile.blockX(), tile.blockZ(), key.step)));
            int fromIndex = source.index(tile),
                    sx = fromIndex % 8 * source.pixels,
                    sz = fromIndex / 8 * source.pixels;
            int unit = pixels / ratio, ox = x * unit, oz = z * unit;
            NativeImage src = source.texture.getPixels(), dst = texture.getPixels();
            for (int iz = 0; iz < unit; iz++) {
                for (int ix = 0; ix < unit; ix++) {
                    int x0 = ix * source.pixels / unit,
                            x1 = Math.max(x0 + 1, (ix + 1) * source.pixels / unit);
                    int z0 = iz * source.pixels / unit,
                            z1 = Math.max(z0 + 1, (iz + 1) * source.pixels / unit);
                    int red = 0, green = 0, blue = 0, n = (x1 - x0) * (z1 - z0);
                    for (int dz = z0; dz < z1; dz++) {
                        for (int dx = x0; dx < x1; dx++) {
                            int c = src.getPixelRGBA(sx + dx, sz + dz);
                            red += c & 255;
                            green += (c >>> 8) & 255;
                            blue += (c >>> 16) & 255;
                        }
                    }
                    dst.setPixelRGBA(
                            ox + ix,
                            oz + iz,
                            0xff000000 | ((blue / n) << 16) | ((green / n) << 8) | (red / n));
                }
            }
            fine.mark(x, z, source.nativeRevision);
            dirty = true;
            revision = ++serial;
        }

        boolean copyChild(Page source, Tile.Key child) {
            Tile.Key parent = Tile.Key.at(child.blockX(), child.blockZ(), key.step);
            int index = index(parent);
            if (painted.get(index)) {
                return false;
            }
            source.finishFades();
            int x = child.x() - key.x * 16, z = child.z() - key.z * 16, at = x + 16 * z;
            if (childRevisions[at] == source.nativeRevision) {
                return false;
            }
            int unit = pixels / 2,
                    ox = x * unit,
                    oz = z * unit,
                    si = source.index(child),
                    sx = (si % 8) * source.pixels,
                    sz = (si / 8) * source.pixels;
            NativeImage src = source.texture.getPixels(), dst = texture.getPixels();
            boolean any = false;
            for (int iz = 0; iz < unit; iz++) {
                for (int ix = 0; ix < unit; ix++) {
                    if (!fine.coarsePixelAllowed(ox + ix, oz + iz, pixels)) {
                        continue;
                    }
                    int x0 = ix * source.pixels / unit,
                            x1 = Math.max(x0 + 1, (ix + 1) * source.pixels / unit),
                            z0 = iz * source.pixels / unit,
                            z1 = Math.max(z0 + 1, (iz + 1) * source.pixels / unit);
                    int r = 0, g = 0, b = 0, n = 0;
                    for (int dz = z0; dz < z1; dz++) {
                        for (int dx = x0; dx < x1; dx++) {
                            int c = src.getPixelRGBA(sx + dx, sz + dz);
                            if ((c >>> 24) == 0) {
                                continue;
                            }
                            r += c & 255;
                            g += (c >>> 8) & 255;
                            b += (c >>> 16) & 255;
                            n++;
                        }
                    }
                    if (n > 0) {
                        dst.setPixelRGBA(
                                ox + ix,
                                oz + iz,
                                0xff000000 | ((b / n) << 16) | ((g / n) << 8) | (r / n));
                        any = true;
                    }
                }
            }
            childRevisions[at] = source.nativeRevision;
            if (any) {
                fallback.set(index);
                dirty = true;
                revision = ++serial;
            }
            return any;
        }

        private void finishFade(int index) {
            Fade f = fades.remove(index);
            if (f != null) {
                writeFade(index, f, ColourTransition.DURATION_MS);
                nativeRevision = revision = ++serial;
            }
        }

        private void finishFades() {
            for (int index : new ArrayList<>(fades.keySet())) {
                finishFade(index);
            }
        }

        private void writeFade(int index, Fade f, long elapsed) {
            NativeImage image = texture.getPixels();
            int ox = index % 8 * pixels, oz = index / 8 * pixels;
            for (int z = 0; z < pixels; z++) {
                for (int x = 0; x < pixels; x++) {
                    int i = x + z * pixels;
                    image.setPixelRGBA(
                            ox + x, oz + z, ColourTransition.blend(f.from[i], f.to[i], elapsed));
                }
            }
            dirty = true;
        }

        void upload() {
            long now = System.nanoTime() / 1_000_000;
            for (var it = fades.entrySet().iterator(); it.hasNext(); ) {
                var entry = it.next();
                long elapsed = now - entry.getValue().started;
                writeFade(entry.getKey(), entry.getValue(), elapsed);
                revision = ++serial;
                if (elapsed >= ColourTransition.DURATION_MS) {
                    it.remove();
                    nativeRevision = revision;
                }
            }
            if (dirty) {
                texture.upload();
                dirty = false;
            }
        }

        void close() {
            Minecraft.getInstance().getTextureManager().release(id);
        }
    }

    private long serial;
    private final Map<Key, Page> pages = new LinkedHashMap<>(128, .75f, true);

    static int pixels(TileWindow window, double scale) {
        return Sampling.texturePixels(window.step(), scale);
    }

    private Key key(Tile.Key tile, Layer layer) {
        return new Key(Math.floorDiv(tile.x(), 8), Math.floorDiv(tile.z(), 8), tile.step(), layer);
    }

    Page get(Tile.Key tile, Layer layer) {
        return pages.get(key(tile, layer));
    }

    boolean ready(Tile.Key tile, Layer layer, int pixels) {
        Page p = get(tile, layer);
        return p != null
                && p.pixels >= pixels
                && (p.ready.get(p.index(tile))
                        || p.fine != null
                                && tile.step() > 32 / Tile.GRID
                                && p.fine.complete(
                                        Math.floorMod(tile.x(), 8), Math.floorMod(tile.z(), 8)));
    }

    void add(Tile tile, Profile profile, int pixels) {
        Key key = key(tile.key(), profile.selected());
        Page page = pages.get(key);
        if (page == null) {
            page = new Page(key, pixels);
            pages.put(key, page);
        } else if (page.pixels < pixels) {
            page.resize(pixels);
        }
        page.add(tile, profile);
    }

    void inherit(Tile.Key tile, Layer layer, int pixels) {
        Page target = get(tile, layer);
        if (target != null && target.painted.get(target.index(tile))) {
            return;
        }
        for (int step = tile.step() * 2; step <= Sampling.MAX_MAP_STEP; step *= 2) {
            Tile.Key parent = Tile.Key.at(tile.blockX(), tile.blockZ(), step);
            Page source = get(parent, layer);
            if (source == null) {
                continue;
            }
            // Partial composite pages are useful too: transparent pixels stay transparent.
            if (target == null) {
                Key key = key(tile, layer);
                target = new Page(key, pixels);
                pages.put(key, target);
            } else if (target.pixels < pixels) {
                target.resize(pixels);
            }
            target.copyFallback(source, tile);
            if (source.painted.get(source.index(parent))
                    || source.fine != null
                            && source.fine.complete(
                                    Math.floorMod(parent.x(), 8), Math.floorMod(parent.z(), 8))) {
                break;
            }
        }
    }

    void preferFine(TileWindow overview, TileWindow detail, Layer layer, int pixels) {
        if (overview.step() <= 32 / Tile.GRID || detail.step() != 32 / Tile.GRID) {
            return;
        }
        // Copy cached 32-block colours into the overview itself. No double-opacity draw pass.
        for (Page source : visible(detail, layer)) {
            for (int i = source.painted.nextSetBit(0);
                    i >= 0;
                    i = source.painted.nextSetBit(i + 1)) {
                Tile.Key tile =
                        new Tile.Key(
                                source.key.x * 8 + i % 8, source.key.z * 8 + i / 8, 32 / Tile.GRID);
                if (!detail.contains(tile)) {
                    continue;
                }
                Tile.Key parent = Tile.Key.at(tile.blockX(), tile.blockZ(), overview.step());
                Key key = key(parent, layer);
                Page target = pages.get(key);
                if (target == null) {
                    target = new Page(key, pixels);
                    pages.put(key, target);
                } else if (target.pixels < pixels) {
                    target.resize(pixels);
                }
                target.copyFine(source, tile);
            }
        }
    }

    void inheritChildren(TileWindow window, Layer layer, int pixels) {
        if (window.step() <= 4) {
            return;
        }
        int step = window.step() / 2, copied = 0;
        var detail =
                new TileWindow(
                        window.minX() * 2,
                        window.minZ() * 2,
                        window.maxX() * 2 + 1,
                        window.maxZ() * 2 + 1,
                        window.centerX() * 2,
                        window.centerZ() * 2,
                        step);
        record Source(Page page, Tile.Key tile) {}
        List<Source> available = new ArrayList<>();
        for (Page source : visible(detail, layer)) {
            for (int i = source.painted.nextSetBit(0);
                    i >= 0;
                    i = source.painted.nextSetBit(i + 1)) {
                Tile.Key child =
                        new Tile.Key(source.key.x * 8 + i % 8, source.key.z * 8 + i / 8, step);
                if (detail.contains(child)) {
                    available.add(new Source(source, child));
                }
            }
        }
        available.sort(Comparator.comparingDouble(source -> window.distanceSquared(source.tile())));
        for (Source from : available) {
            Tile.Key child = from.tile(),
                    parent = Tile.Key.at(child.blockX(), child.blockZ(), window.step());
            Key key = key(parent, layer);
            Page target = pages.get(key);
            if (target == null) {
                target = new Page(key, pixels);
                pages.put(key, target);
            } else if (target.pixels < pixels) {
                target.resize(pixels);
            }
            if (target.copyChild(from.page(), child) && ++copied >= 128) {
                return;
            }
        }
    }

    List<Page> visible(TileWindow window, Layer layer) {
        List<Page> found = new ArrayList<>();
        for (Page p : pages.values()) {
            if (p.key.layer == layer
                    && p.key.step == window.step()
                    && p.key.x * 8 <= window.maxX()
                    && p.key.x * 8 + 7 >= window.minX()
                    && p.key.z * 8 <= window.maxZ()
                    && p.key.z * 8 + 7 >= window.minZ()) {
                found.add(p);
            }
        }
        return found;
    }

    void trim(TileWindow window, Layer layer) {
        long pixels = pages.values().stream().mapToLong(p -> (long) p.size() * p.size()).sum();
        // About 64 MiB of colour pixels in each of CPU/GPU memory; prefer retaining this view.
        if (pixels <= 16 * 1024 * 1024 && pages.size() <= 32768) {
            return;
        }
        Set<Page> visible = new HashSet<>(visible(window, layer));
        for (int pass = 0; pass < 2; pass++) {
            for (var it = pages.values().iterator();
                    it.hasNext() && (pixels > 16 * 1024 * 1024 || pages.size() > 32768); ) {
                Page p = it.next();
                if (pass == 0 && visible.contains(p)) {
                    continue;
                }
                pixels -= (long) p.size() * p.size();
                p.close();
                it.remove();
            }
        }
    }

    void clear() {
        pages.values().forEach(Page::close);
        pages.clear();
    }
}
