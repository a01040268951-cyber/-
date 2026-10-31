package com.ssaem.tabooword.speech;

import java.io.IOException;
import java.nio.file.Path;

import io.github.givimad.whisperjni.WhisperContext;
import io.github.givimad.whisperjni.WhisperFullParams;
import io.github.givimad.whisperjni.WhisperJNI;
import io.github.givimad.whisperjni.WhisperSamplingStrategy;

/** whisper.cpp(whisper-jni)로 한국어를 인식한다. 한 스레드에서만 사용. */
public final class WhisperTranscriber implements SpeechPipeline.Transcriber, AutoCloseable {
    private static boolean libraryLoaded;

    private final WhisperJNI whisper;
    private final WhisperContext context;
    private final WhisperFullParams params;

    public WhisperTranscriber(Path modelFile, int threads) throws IOException {
        synchronized (WhisperTranscriber.class) {
            if (!libraryLoaded) {
                WhisperJNI.loadLibrary();
                WhisperJNI.setLibraryLogger(null);
                libraryLoaded = true;
            }
        }
        whisper = new WhisperJNI();
        context = whisper.init(modelFile);
        if (context == null) {
            throw new IOException("whisper model load failed: " + modelFile);
        }
        params = new WhisperFullParams(WhisperSamplingStrategy.GREEDY);
        params.language = "ko";
        params.translate = false;
        params.nThreads = threads;
        params.noTimestamps = true;
        params.printProgress = false;
        params.printRealtime = false;
        params.printTimestamps = false;
        params.printSpecial = false;
        params.suppressBlank = true;
        params.suppressNonSpeechTokens = true;
        params.noContext = true;
        params.singleSegment = true;
        params.temperatureInc = 0f; // 다시 시도하지 않음 (속도)
    }

    @Override
    public String transcribe(float[] samples, int count) {
        int rc = whisper.full(context, params, samples, count);
        if (rc != 0) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        int n = whisper.fullNSegments(context);
        for (int i = 0; i < n; i++) {
            sb.append(whisper.fullGetSegmentText(context, i));
        }
        return sb.toString().trim();
    }

    @Override
    public void close() {
        whisper.free(context);
    }

    public static int defaultThreads() {
        int cores = Runtime.getRuntime().availableProcessors();
        return Math.max(1, Math.min(4, cores / 2)); // 마인크래프트가 쓸 코어를 남겨 둠
    }
}
