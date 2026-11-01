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

    // ---------- 빠른 엔진 (sherpa-onnx 스트리밍) ----------
    static final String SHERPA_VERSION = "v1.13.3";
    static final String STREAM_MODEL = "sherpa-onnx-streaming-zipformer-korean-2024-06-16";
    private static final String[] STREAM_MODEL_URLS = {
            "https://huggingface.co/k2-fsa/" + STREAM_MODEL + "/resolve/main/",
            "https://huggingface.co/csukuangfj/" + STREAM_MODEL + "/resolve/main/",
    };

    static String platform() {
        String os = System.getProperty("os.name", "").toLowerCase();
        String arch = System.getProperty("os.arch", "").toLowerCase();
        String o = os.contains("win") ? "win" : (os.contains("mac") || os.contains("darwin")) ? "osx" : "linux";
        String a = (arch.contains("aarch64") || arch.contains("arm64")) ? "aarch64" : "x64";
        return o + "-" + a;
    }

    /** 스트리밍 인식 실행 파일(onnxruntime + sherpa-onnx-jni)을 받아 풀고, 그 폴더를 돌려준다. */
    static Path ensureSherpaNatives(Path gameDir, BooleanSupplier keepGoing, Consumer<String> notify) throws IOException {
        String plat = platform();
        Path dir = gameDir.resolve("tabooword").resolve("native-" + SHERPA_VERSION).resolve(plat);
        Path ready = dir.resolve(".ready");
        if (Files.exists(ready)) {
            return dir;
        }
        Files.createDirectories(dir);
        String url = "https://github.com/k2-fsa/sherpa-onnx/releases/download/" + SHERPA_VERSION
                + "/sherpa-onnx-native-lib-" + plat + "-" + SHERPA_VERSION + ".jar";
        Path part = dir.resolve("native.jar.part");
        notify.accept("음성 인식 실행 파일 내려받는 중... (약 10MB)");
        if (!download(url, part, keepGoing, notify)) {
            Files.deleteIfExists(part);
            return null;
        }
        String prefix = "sherpa-onnx/native/" + plat + "/";
        try (java.util.zip.ZipInputStream zin = new java.util.zip.ZipInputStream(Files.newInputStream(part))) {
            java.util.zip.ZipEntry e;
            while ((e = zin.getNextEntry()) != null) {
                String name = e.getName();
                if (!e.isDirectory() && name.startsWith(prefix) && name.indexOf('/', prefix.length()) < 0) {
                    Files.copy(zin, dir.resolve(name.substring(prefix.length())), StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
        Files.deleteIfExists(part);
        Files.writeString(ready, "ok");
        return dir;
    }

    /** 스트리밍 한국어 모델(약 130MB)을 받아 둔 폴더. */
    static Path ensureStreamModel(Path gameDir, String[] files, BooleanSupplier keepGoing, Consumer<String> notify) throws IOException {
        Path dir = gameDir.resolve("tabooword").resolve(STREAM_MODEL);
        Path ready = dir.resolve(".ready");
        if (Files.exists(ready)) {
            return dir;
        }
        Files.createDirectories(dir);
        notify.accept("빠른 음성 인식 모델을 처음 한 번 내려받는 중... (약 130MB)");
        for (String f : files) {
            Path target = dir.resolve(f);
            if (Files.isRegularFile(target) && Files.size(target) > 0) {
                continue;
            }
            Path part = dir.resolve(f + ".part");
            IOException last = null;
            boolean ok = false;
            for (String base : STREAM_MODEL_URLS) {
                try {
                    if (!download(base + f, part, keepGoing, notify)) {
                        Files.deleteIfExists(part);
                        return null;
                    }
                    ok = true;
                    break;
                } catch (IOException e) {
                    last = e;
                }
            }
            if (!ok) {
                Files.deleteIfExists(part);
                throw last != null ? last : new IOException("download failed: " + f);
            }
            Files.move(part, target, StandardCopyOption.REPLACE_EXISTING);
        }
        Files.writeString(ready, "ok");
        notify.accept("모델 준비 완료!");
        return dir;
    }

    // ---------- 정확한 엔진 (Whisper) ----------

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
