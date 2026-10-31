package com.ssaem.tabooword.client;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import com.ssaem.tabooword.TabooWord;

/**
 * Vosk 한국어 음성 인식 모델(Apache 2.0)을 처음 한 번만 내려받아 .minecraft/tabooword 에 풀어 둔다.
 * 직접 받은 모델 폴더를 같은 위치에 넣어도 된다.
 */
final class ModelManager {
    static final String MODEL_NAME = "vosk-model-small-ko-0.22";
    private static final String[] URLS = {
            "https://alphacephei.com/vosk/models/" + MODEL_NAME + ".zip",
            "https://huggingface.co/rhasspy/vosk-models/resolve/main/ko/" + MODEL_NAME + ".zip",
    };
    private static final String READY_MARKER = ".tabooword-ready";

    private ModelManager() {
    }

    /** 모델 폴더 경로. 없으면 내려받는다. 중간에 취소되면 null. */
    static Path ensureModel(Path gameDir, BooleanSupplier keepGoing, Consumer<String> notify) throws IOException {
        Path base = gameDir.resolve("tabooword");
        Path modelDir = base.resolve(MODEL_NAME);
        if (Files.exists(modelDir.resolve(READY_MARKER)) || Files.isDirectory(modelDir.resolve("am"))) {
            return modelDir;
        }
        Files.createDirectories(base);
        Path zip = base.resolve(MODEL_NAME + ".zip.part");

        IOException last = null;
        for (String url : URLS) {
            try {
                notify.accept("음성 인식 모델을 처음 한 번 내려받는 중... (약 87MB)");
                if (!download(url, zip, keepGoing, notify)) {
                    return null;
                }
                notify.accept("압축 푸는 중...");
                unzip(zip, base);
                Files.deleteIfExists(zip);
                Files.writeString(modelDir.resolve(READY_MARKER), "ok");
                notify.accept("모델 준비 완료!");
                return modelDir;
            } catch (IOException e) {
                last = e;
                TabooWord.LOGGER.warn("[tabooword] model download failed from {}", url, e);
            }
        }
        Files.deleteIfExists(zip);
        throw last != null ? last : new IOException("model download failed");
    }

    private static boolean download(String url, Path target, BooleanSupplier keepGoing, Consumer<String> notify) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) URI.create(url).toURL().openConnection();
        conn.setConnectTimeout(15_000);
        conn.setReadTimeout(30_000);
        conn.setInstanceFollowRedirects(true);
        conn.setRequestProperty("User-Agent", "tabooword-minecraft-mod");
        int code = conn.getResponseCode();
        if (code / 100 != 2) {
            throw new IOException("HTTP " + code + " from " + url);
        }
        long total = conn.getContentLengthLong();
        long done = 0;
        int lastPercent = 0;
        try (InputStream in = conn.getInputStream();
             OutputStream out = Files.newOutputStream(target)) {
            byte[] buf = new byte[64 * 1024];
            int n;
            while ((n = in.read(buf)) >= 0) {
                if (!keepGoing.getAsBoolean()) {
                    return false;
                }
                out.write(buf, 0, n);
                done += n;
                if (total > 0) {
                    int percent = (int) (done * 100 / total);
                    if (percent >= lastPercent + 20) {
                        lastPercent = percent - percent % 20;
                        notify.accept("다운로드 " + lastPercent + "%");
                    }
                }
            }
        } finally {
            conn.disconnect();
        }
        return true;
    }

    private static void unzip(Path zip, Path dest) throws IOException {
        Path root = dest.toAbsolutePath().normalize();
        try (ZipInputStream zin = new ZipInputStream(Files.newInputStream(zip))) {
            ZipEntry entry;
            while ((entry = zin.getNextEntry()) != null) {
                Path out = root.resolve(entry.getName()).normalize();
                if (!out.startsWith(root)) {
                    throw new IOException("bad zip entry: " + entry.getName());
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(out);
                } else {
                    Files.createDirectories(out.getParent());
                    Files.copy(zin, out, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }
}
