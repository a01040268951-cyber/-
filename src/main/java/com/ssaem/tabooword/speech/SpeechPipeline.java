package com.ssaem.tabooword.speech;

import java.util.Arrays;

/**
 * 16kHz 모노 16-bit 오디오를 받아서
 *  1) 음성 구간만 잘라내고 (에너지 기반 VAD, 주변 소음에 자동 적응)
 *  2) 말하는 중에는 약 1초마다 지금까지의 말을 중간 인식하고
 *  3) 말이 끝나면 전체를 최종 인식한다.
 * 마인크래프트와 무관한 순수 자바 클래스 (CI에서 음성 파일로 테스트).
 */
public final class SpeechPipeline {
    public static final int RATE = 16000;

    public interface Transcriber {
        /** samples: -1..1 float, 16kHz. 인식된 텍스트 (없으면 빈 문자열) */
        String transcribe(float[] samples, int count);
    }

    public interface Listener {
        void onText(String text, boolean isFinal);
    }

    private static final int FRAME = 480;                 // 30ms
    private static final int START_FRAMES = 3;            // 90ms 이상 소리 → 말 시작
    private static final int END_FRAMES = 22;             // 660ms 조용 → 말 끝
    private static final int PREROLL = RATE * 3 / 10;     // 말 시작 전 0.3초도 포함
    private static final int MAX_UTTER = RATE * 12;       // 한 번에 최대 12초
    private static final int INTERIM_EVERY = RATE;        // 1초마다 중간 인식
    private static final int MIN_INTERIM = RATE * 7 / 10; // 0.7초 이상 말해야 중간 인식
    private static final int MIN_INPUT = RATE * 11 / 10;  // whisper는 1초 미만 입력을 거부 → 0으로 채움
    private static final float MIN_RMS = 0.012f;          // 절대 최소 음량

    private final Transcriber transcriber;
    private final Listener listener;

    private final float[] frame = new float[FRAME];
    private int frameFill;
    private final float[] preroll = new float[PREROLL];
    private int prerollPos;
    private boolean prerollFull;

    private float[] utter = new float[RATE * 4];
    private int utterLen;
    private boolean speaking;
    private int voicedRun;
    private int silentRun;
    private int lastInterimAt;
    private float noiseFloor = 0.004f;
    private int interimEvery = INTERIM_EVERY;

    public SpeechPipeline(Transcriber transcriber, Listener listener) {
        this.transcriber = transcriber;
        this.listener = listener;
    }

    /** 지금까지 쌓인 말을 버린다 (사망 후, 다시 시작할 때). */
    public void reset() {
        speaking = false;
        utterLen = 0;
        voicedRun = 0;
        silentRun = 0;
        lastInterimAt = 0;
        frameFill = 0;
    }

    public void feed(short[] samples, int count) {
        for (int i = 0; i < count; i++) {
            frame[frameFill++] = samples[i] / 32768f;
            if (frameFill == FRAME) {
                processFrame();
                frameFill = 0;
            }
        }
    }

    /** 오디오 끝 (테스트용): 말하던 중이면 최종 인식. */
    public void flush() {
        if (speaking) {
            finish();
        }
    }

    private void processFrame() {
        double sum = 0;
        for (float v : frame) {
            sum += v * v;
        }
        float rms = (float) Math.sqrt(sum / FRAME);
        float threshold = Math.max(MIN_RMS, noiseFloor * 3.0f);
        boolean voiced = rms > threshold;

        if (!speaking) {
            // 조용할 때 주변 소음 수준을 천천히 따라간다
            noiseFloor = noiseFloor * 0.97f + Math.min(rms, 0.05f) * 0.03f;
            pushPreroll();
            voicedRun = voiced ? voicedRun + 1 : 0;
            if (voicedRun >= START_FRAMES) {
                speaking = true;
                silentRun = 0;
                utterLen = 0;
                lastInterimAt = 0;
                appendPreroll();
            }
            return;
        }

        append(frame, FRAME);
        silentRun = voiced ? 0 : silentRun + 1;
        if (silentRun >= END_FRAMES || utterLen >= MAX_UTTER) {
            finish();
            return;
        }
        if (utterLen >= MIN_INTERIM && utterLen - lastInterimAt >= interimEvery) {
            lastInterimAt = utterLen;
            emit(false);
        }
    }

    private void finish() {
        emit(true);
        speaking = false;
        utterLen = 0;
        voicedRun = 0;
        prerollPos = 0;
        prerollFull = false;
    }

    private void emit(boolean isFinal) {
        int n = Math.max(utterLen, MIN_INPUT);
        float[] input = new float[n]; // 1초 미만이면 뒤를 0(무음)으로 채움
        System.arraycopy(utter, 0, input, 0, utterLen);
        long start = System.nanoTime();
        String text = HallucinationFilter.clean(transcriber.transcribe(input, n));
        // 인식이 느린 PC에서는 중간 인식 간격을 늘려서 밀리지 않게 한다
        int costSamples = (int) ((System.nanoTime() - start) / 1_000_000L * RATE / 1000);
        interimEvery = Math.max(INTERIM_EVERY, costSamples * 6 / 5);
        if (!text.isEmpty() || isFinal) {
            listener.onText(text, isFinal);
        }
    }

    private void pushPreroll() {
        for (float v : frame) {
            preroll[prerollPos++] = v;
            if (prerollPos == PREROLL) {
                prerollPos = 0;
                prerollFull = true;
            }
        }
    }

    private void appendPreroll() {
        if (prerollFull) {
            append(Arrays.copyOfRange(preroll, prerollPos, PREROLL), PREROLL - prerollPos);
        }
        append(Arrays.copyOfRange(preroll, 0, prerollPos), prerollPos);
    }

    private void append(float[] src, int n) {
        if (utterLen + n > utter.length) {
            utter = Arrays.copyOf(utter, Math.max(utter.length * 2, utterLen + n));
        }
        System.arraycopy(src, 0, utter, utterLen, n);
        utterLen += n;
    }
}
