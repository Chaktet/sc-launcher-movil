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
import com.tungsten.fclcore.util.io.FileUtils;
import com.tungsten.fclcore.util.io.Unzipper;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.*;
import java.util.function.Predicate;

public class ModpackInstallTask<T> extends Task<Void> {

    private final File modpackFile;
    private final File dest;
    private final Charset charset;
    private final List<String> subDirectories;
    private final List<ModpackConfiguration.FileInformation> overrides;
    private final Predicate<String> callback;
    // files handled so far / files in the overrides, read by the launcher UI while this long local step runs
    private volatile long filesDone = 0, filesTotal = 0;

    /**
     * Constructor
     * @param modpackFile a zip file
     * @param dest destination to store unpacked files
     * @param charset charset of the zip file
     * @param subDirectories the subdirectory of zip file to unpack
     * @param callback test whether the file (given full path) in zip file should be unpacked or not
     * @param oldConfiguration old modpack information if upgrade
     */
    public ModpackInstallTask(File modpackFile, File dest, Charset charset, List<String> subDirectories, Predicate<String> callback, ModpackConfiguration<T> oldConfiguration) {
        this.modpackFile = modpackFile;
        this.dest = dest;
        this.charset = charset;
        this.subDirectories = subDirectories;
        this.callback = callback;

        if (oldConfiguration == null)
            overrides = Collections.emptyList();
        else
            overrides = oldConfiguration.getOverrides();
    }

    public long getFilesDone() {
        return filesDone;
    }

    public long getFilesTotal() {
        return filesTotal;
    }

    @Override
    public void execute() throws Exception {
        Set<String> entries = new HashSet<>();
        if (!FileUtils.makeDirectory(dest))
            throw new IOException("Unable to make directory " + dest);

        HashMap<String, ModpackConfiguration.FileInformation> files = new HashMap<>();
        for (ModpackConfiguration.FileInformation file : overrides)
            files.put(file.getPath(), file);


        int subDirectoryCount = subDirectories.size();
        int index = 0;
        long[] finishedFiles = {0};
        for (String subDirectory : subDirectories) {
            final int finishedDirectories = index++;
            final long[] subTotal = {0};
            new Unzipper(modpackFile, dest)
                    .setSubDirectory(subDirectory)
                    .setTerminateIfSubDirectoryNotExists()
                    .setReplaceExistentFile(true)
                    .setEncoding(charset)
                    .setProgressCallback((done, total, entryPath) -> {
                        subTotal[0] = total;
                        filesTotal = finishedFiles[0] + total;
                        filesDone = finishedFiles[0] + done;
                        updateMessage(entryPath);
                        updateProgress((finishedDirectories + 1.0 * done / total) / subDirectoryCount);
                    })
                    .setFilter((destPath, isDirectory, zipEntry, entryPath) -> {
                        if (isDirectory) return true;
                        if (!callback.test(entryPath)) return false;
                        entries.add(entryPath);

                        if (!files.containsKey(entryPath)) {
                            // If old modpack does not have this entry, add this entry or override the file that user added.
                            // A file already holding the same bytes is left alone: rewriting it changes nothing and is
                            // slow on Android shared storage (~10,000 override files in the SC pack, 27-10).
                            return !sameContent(zipEntry, destPath);
                        } else if (!Files.exists(destPath)) {
                            // If both old and new modpacks have this entry, but the file is deleted by user, leave it missing.
                            return false;
                        } else if (Objects.equals(files.get(entryPath).getHash(), DigestUtils.digestToString("SHA-1", zipEntry))) {
                            // Unchanged between the old and the new modpack: the file on disk is either that same
                            // content or the user's own edit, and both stay as they are, so it is neither read nor rewritten.
                            return false;
                        } else {
                            // If both old and new modpacks have this entry, and user has modified this file,
                            // we will not replace it since this modified file is what user expects.
                            String fileHash = DigestUtils.digestToString("SHA-1", destPath);
                            String oldHash = files.get(entryPath).getHash();
                            return Objects.equals(oldHash, fileHash);
                        }
                    }).unzip();
            finishedFiles[0] += subTotal[0];
        }

        // If old modpack have this entry, and new modpack deleted it. Delete this file.
        for (ModpackConfiguration.FileInformation file : overrides) {
            File original = new File(dest, file.getPath());
            if (original.exists() && !entries.contains(file.getPath()))
                original.delete();
        }
    }

    private static boolean sameContent(java.nio.file.Path zipEntry, java.nio.file.Path destPath) {
        // Only a shortcut: when the check cannot be made the file is written, as before 0.1.15. An IOException here
        // aborted the whole install (0.1.15: NoSuchFileException on a mods/ jar that vanished between isRegularFile
        // and size on Android shared storage, emulator and players' SC-40, 28-10).
        try {
            if (!Files.isRegularFile(destPath) || Files.size(destPath) != Files.size(zipEntry))
                return false;
            return DigestUtils.digestToString("SHA-1", destPath).equals(DigestUtils.digestToString("SHA-1", zipEntry));
        } catch (IOException e) {
            return false;
        }
    }
}
