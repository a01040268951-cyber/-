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

import com.ssaem.tabooword.TabooWord;

/**
 * Whisper 음성 인식 모델(MIT, whisper.cpp 형식)을 처음 한 번만 내려받아 .minecraft/tabooword 에 둔다.
 * 직접 받은 ggml-*.bin 파일을 같은 위치에 넣어도 된다.
 */
final class ModelManager {
    private static final String BASE_URL = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/";

    private ModelManager() {
    }

    static String fileName(String size) {
        return "ggml-" + size + "-q5_1.bin";
    }

    static String sizeLabel(String size) {
        return "small".equals(size) ? "약 190MB" : "약 60MB";
    }

    /** 모델 파일 경로. 없으면 내려받는다. 중간에 취소되면 null. */
    static Path ensureModel(Path gameDir, String size, BooleanSupplier keepGoing, Consumer<String> notify) throws IOException {
        Path base = gameDir.resolve("tabooword");
        Path model = base.resolve(fileName(size));
        if (Files.isRegularFile(model) && Files.size(model) > 10_000_000L) {
            return model;
        }
        Files.createDirectories(base);
        Path part = base.resolve(fileName(size) + ".part");
        notify.accept("음성 인식 모델을 처음 한 번 내려받는 중... (" + sizeLabel(size) + ")");
        try {
            if (!download(BASE_URL + fileName(size), part, keepGoing, notify)) {
                Files.deleteIfExists(part);
                return null;
            }
            Files.move(part, model, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            Files.deleteIfExists(part);
            TabooWord.LOGGER.warn("[tabooword] model download failed", e);
            throw e;
        }
        notify.accept("모델 준비 완료!");
        return model;
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
        if (total > 0 && done != total) {
            throw new IOException("incomplete download: " + done + "/" + total);
        }
        return true;
    }
}
