/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2020  huangyuhui <huanghongxun2008@126.com> and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package com.tungsten.fclcore.mod;

import com.tungsten.fclcore.task.Task;
import com.tungsten.fclcore.util.DigestUtils;
import com.tungsten.fclcore.util.gson.JsonUtils;
import com.tungsten.fclcore.util.io.CompressingUtils;
import com.tungsten.fclcore.util.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public final class MinecraftInstanceTask<T> extends Task<ModpackConfiguration<T>> {

    private final File zipFile;
    private final Charset encoding;
    private final List<String> subDirectories;
    private final File jsonFile;
    private final T manifest;
    private final String type;
    private final String name;
    private final String version;

    public MinecraftInstanceTask(File zipFile, Charset encoding, List<String> subDirectories, T manifest, ModpackProvider modpackProvider, String name, String version, File jsonFile) {
        this.zipFile = zipFile;
        this.encoding = encoding;
        this.subDirectories = subDirectories.stream().map(FileUtils::normalizePath).collect(Collectors.toList());
        this.manifest = manifest;
        this.jsonFile = jsonFile;
        this.type = modpackProvider.getName();
        this.name = name;
        this.version = version;
    }

    @Override
    public void execute() throws Exception {
        // SC: the SHA-1 of every override (about 10,000 entries in our phone pack) took ~29 s on one core (CI, 28-10).
        // The entries are listed first and hashed by several threads, each with its own read-only view of the zip;
        // the result (paths and hashes, in the same order) is exactly what the single-threaded loop produced.
        List<String[]> entradas = new ArrayList<>(); // {subDirectory, relativePath}
        try (FileSystem fs = CompressingUtils.readonly(zipFile.toPath()).setEncoding(encoding).build()) {
            for (String subDirectory : subDirectories) {
                Path root = fs.getPath(subDirectory);
                if (!Files.exists(root))
                    continue;
                Files.walkFileTree(root, new SimpleFileVisitor<Path>() {
                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                        entradas.add(new String[]{subDirectory, root.relativize(file).normalize().toString().replace(File.separatorChar, '/')});
                        return FileVisitResult.CONTINUE;
                    }
                });
            }
        }
        final int total = entradas.size();
        final String[] hashes = new String[total];
        final java.util.concurrent.atomic.AtomicInteger hechos = new java.util.concurrent.atomic.AtomicInteger();
        int hilos = Math.max(1, Math.min(4, Runtime.getRuntime().availableProcessors()));
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(hilos);
        try {
            List<java.util.concurrent.Future<?>> trabajos = new ArrayList<>();
            for (int h = 0; h < hilos; h++) {
                final int desde = h;
                final int paso = hilos;
                trabajos.add(pool.submit(() -> {
                    try (FileSystem fs = CompressingUtils.readonly(zipFile.toPath()).setEncoding(encoding).build()) {
                        for (int i = desde; i < total; i += paso) {
                            String[] e = entradas.get(i);
                            hashes[i] = DigestUtils.digestToString("SHA-1", fs.getPath(e[0]).resolve(e[1]));
                            int n = hechos.incrementAndGet();
                            if (n % 50 == 0 || n == total) { updateMessage(e[1]); updateProgress(n, total); }
                        }
                    }
                    return null;
                }));
            }
            for (java.util.concurrent.Future<?> t : trabajos) {
                try {
                    t.get();
                } catch (java.util.concurrent.ExecutionException ex) {
                    Throwable c = ex.getCause();
                    if (c instanceof IOException) throw (IOException) c;
                    throw new IOException(c);
                }
            }
        } finally {
            pool.shutdownNow();
        }
        List<ModpackConfiguration.FileInformation> overrides = new ArrayList<>(total);
        for (int i = 0; i < total; i++)
            overrides.add(new ModpackConfiguration.FileInformation(entradas.get(i)[1], hashes[i]));

        ModpackConfiguration<T> configuration = new ModpackConfiguration<>(manifest, type, name, version, overrides);
        FileUtils.writeText(jsonFile, JsonUtils.GSON.toJson(configuration));
        setResult(configuration);
    }

    /**
     * 统计子目录内的文件条目数，只遍历目录结构，不解压数据
     */
    private long countEntries(Path root) throws IOException {
        long[] count = {0};
        Files.walkFileTree(root, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                count[0]++;
                return FileVisitResult.CONTINUE;
            }
        });
        return count[0];
    }
}
