package com.scadicat.launcher;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public class Downloader {

    private static final String USER_AGENT = "ScadicatLauncher/1.0";
    private static final int BUFFER_SIZE = 256 * 1024; // 256 KB буфер
    private static final int THREADS = 16;             // 16 потоков для скачивания
    private static final long MIN_CHUNK_SIZE = 2 * 1024 * 1024; // Качаем в потоках, если файл > 2 МБ

    public static void download(String url, Path target) throws IOException {
        if (Files.exists(target) && Files.size(target) > 0) return;
        downloadForce(url, target);
    }

    public static void downloadForce(String url, Path target) throws IOException {
        downloadForceFast(url, target, null);
    }

    /** Скачивание файлов модов (без чанков, так как они мелкие) */
    public static void downloadRawGithub(String owner, String repo, String branch,
                                         String path, Path target) throws IOException {
        String decoded = URLDecoder.decode(path, StandardCharsets.UTF_8);
        String encoded = decoded.replace(" ", "%20").replace("#", "%23");
        String url = "https://raw.githubusercontent.com/" + owner + "/" + repo + "/" + branch + "/" + encoded;
        downloadForceFast(url, target, null);
    }

    /**
     * ⭐ МАКСИМАЛЬНАЯ СКОРОСТЬ: Многопоточное скачивание больших файлов (ZIP с модпаком)
     */
    public static void downloadForceFast(String url, Path target, java.util.function.Consumer<Long> progressCallback) throws IOException {
        Files.createDirectories(target.getParent());

        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setRequestProperty("User-Agent", USER_AGENT);
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(60000);
        conn.setInstanceFollowRedirects(true);

        int code = conn.getResponseCode();
        if (code == 404) throw new IOException("HTTP 404 (Файл не найден)");
        if (code != 200) throw new IOException("HTTP " + code);

        long fileSize = conn.getContentLengthLong();
        String acceptRanges = conn.getHeaderField("Accept-Ranges");

        // Если файл маленький или сервер не поддерживает Range — качаем в 1 поток
        if (fileSize < MIN_CHUNK_SIZE || acceptRanges == null || !acceptRanges.contains("bytes")) {
            downloadSingleThread(conn, target, progressCallback);
            return;
        }

        // ⭐ МНОГОПОТОЧНОЕ СКАЧИВАНИЕ
        try (RandomAccessFile raf = new RandomAccessFile(target.toFile(), "rw")) {
            raf.setLength(fileSize);
        }

        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        List<Callable<Void>> tasks = new ArrayList<>();
        long chunkSize = fileSize / THREADS;
        AtomicLong totalDownloaded = new AtomicLong(0);

        for (int i = 0; i < THREADS; i++) {
            long start = i * chunkSize;
            long end = (i == THREADS - 1) ? fileSize - 1 : (start + chunkSize - 1);

            tasks.add(() -> {
                HttpURLConnection chunkConn = (HttpURLConnection) new URL(url).openConnection();
                chunkConn.setRequestProperty("User-Agent", USER_AGENT);
                chunkConn.setRequestProperty("Range", "bytes=" + start + "-" + end);

                int chunkCode = chunkConn.getResponseCode();
                if (chunkCode != 206 && chunkCode != 200) {
                    throw new IOException("Ошибка чанка: HTTP " + chunkCode);
                }

                try (InputStream in = new BufferedInputStream(chunkConn.getInputStream(), BUFFER_SIZE);
                     RandomAccessFile chunkRaf = new RandomAccessFile(target.toFile(), "rw")) {
                    chunkRaf.seek(start);
                    byte[] buf = new byte[BUFFER_SIZE];
                    int n;
                    long written = 0;
                    long toWrite = end - start + 1;
                    while (written < toWrite && (n = in.read(buf, 0, (int) Math.min(buf.length, toWrite - written))) != -1) {
                        chunkRaf.write(buf, 0, n);
                        written += n;
                        if (progressCallback != null) {
                            progressCallback.accept(totalDownloaded.addAndGet(n));
                        }
                    }
                }
                return null;
            });
        }

        try {
            List<Future<Void>> results = pool.invokeAll(tasks);
            for (Future<Void> f : results) f.get();
        } catch (Exception ex) {
            throw new IOException("Ошибка многопоточной загрузки: " + ex.getMessage());
        } finally {
            pool.shutdown();
            try { pool.awaitTermination(1, TimeUnit.MINUTES); } catch (Exception ignored) {}
        }
    }

    private static void downloadSingleThread(HttpURLConnection conn, Path target, java.util.function.Consumer<Long> progressCallback) throws IOException {
        try (InputStream in = new BufferedInputStream(conn.getInputStream(), BUFFER_SIZE);
             OutputStream out = new BufferedOutputStream(Files.newOutputStream(target), BUFFER_SIZE)) {
            byte[] buf = new byte[BUFFER_SIZE];
            int n;
            long total = 0;
            while ((n = in.read(buf)) != -1) {
                out.write(buf, 0, n);
                total += n;
                if (progressCallback != null) progressCallback.accept(total);
            }
        }
    }
}