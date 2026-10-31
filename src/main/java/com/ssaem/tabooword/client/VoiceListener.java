package com.ssaem.tabooword.client;

import java.nio.ShortBuffer;
import java.nio.file.Path;

import org.lwjgl.BufferUtils;
import org.lwjgl.openal.AL10;
import org.lwjgl.openal.ALC10;
import org.lwjgl.openal.ALC11;

import com.ssaem.tabooword.TabooNetwork;
import com.ssaem.tabooword.TabooStatus;
import com.ssaem.tabooword.TabooWord;
import com.ssaem.tabooword.speech.SpeechPipeline;
import com.ssaem.tabooword.speech.TabooJudge;
import com.ssaem.tabooword.speech.WhisperTranscriber;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * 마이크(기본 녹음 장치)를 OpenAL로 듣고 Whisper로 한국어를 인식한다.
 * 소리는 이 PC 밖으로 나가지 않고, 금지 자모가 들린 문장만 서버로 보낸다.
 */
final class VoiceListener implements Runnable {
    private static final int RATE = SpeechPipeline.RATE; // 16000
    private static final long COOLDOWN_MS = 2500;

    private static WhisperTranscriber transcriber; // 한 번 불러오면 계속 재사용
    private static String loadedModel;

    private volatile boolean running;
    private volatile boolean failed;
    private volatile boolean localAlive = true;
    private Thread thread;

    synchronized void start() {
        if (running || failed || (thread != null && thread.isAlive())) {
            return; // 이전 스레드가 마이크를 닫는 중이면 다음 틱에 다시 시도
        }
        running = true;
        thread = new Thread(this, "tabooword-mic");
        thread.setDaemon(true);
        thread.start();
    }

    synchronized void stop() {
        running = false;
    }

    void clearFailure() {
        failed = false;
    }

    boolean isRunning() {
        return running;
    }

    void setLocalAlive(boolean alive) {
        localAlive = alive;
    }

    @Override
    public void run() {
        Minecraft mc = Minecraft.getInstance();
        long device = 0L;
        try {
            String size = ClientConfig.MODEL.get();
            Path modelFile = ModelManager.ensureModel(mc.gameDirectory.toPath(), size, this::isRunning,
                    msg -> tell(msg, ChatFormatting.GRAY));
            if (modelFile == null) {
                return;
            }
            if (transcriber == null || !size.equals(loadedModel)) {
                tell("음성 인식 준비 중...", ChatFormatting.GRAY);
                if (transcriber != null) {
                    transcriber.close();
                }
                int threads = ClientConfig.THREADS.get();
                transcriber = new WhisperTranscriber(modelFile, threads > 0 ? threads : WhisperTranscriber.defaultThreads());
                loadedModel = size;
            }

            // 16kHz로 열고, 안 되면 48kHz로 열어서 1/3로 줄인다
            int captureRate = RATE;
            device = ALC11.alcCaptureOpenDevice((CharSequence) null, RATE, AL10.AL_FORMAT_MONO16, RATE * 5);
            if (device == 0L) {
                captureRate = RATE * 3;
                device = ALC11.alcCaptureOpenDevice((CharSequence) null, captureRate, AL10.AL_FORMAT_MONO16, captureRate * 5);
            }
            if (device == 0L) {
                fail("마이크를 열 수 없어요. 윈도우 설정에서 기본 녹음 장치를 확인해 주세요.");
                return;
            }
            ALC11.alcCaptureStart(device);
            tell("♪ 마이크로 듣는 중 — 금지: " + String.join(", ", TabooStatus.letters()), ChatFormatting.RED);

            TabooJudge judge = new TabooJudge();
            long[] cooldownUntil = {0};
            SpeechPipeline pipeline = new SpeechPipeline(transcriber, (text, isFinal) -> {
                if (!text.isEmpty()) {
                    actionBar("♪ " + text);
                }
                long now = System.currentTimeMillis();
                if (now < cooldownUntil[0] || !localAlive || !TabooStatus.enabled()) {
                    return;
                }
                String said = judge.judge(text, isFinal, TabooStatus.letters());
                if (said != null) {
                    mc.execute(() -> ClientPacketDistributor.sendToServer(new TabooNetwork.SpokePayload(said)));
                    judge.reset();
                    cooldownUntil[0] = now + COOLDOWN_MS;
                }
            });

            int bufLen = captureRate; // 1초 분량씩
            ShortBuffer nativeBuf = BufferUtils.createShortBuffer(bufLen);
            short[] samples = new short[bufLen];
            short[] down = new short[bufLen / 3 + 1];
            int minChunk = captureRate / 20; // 50ms
            boolean wasAlive = true;

            while (running) {
                int available = ALC10.alcGetInteger(device, ALC11.ALC_CAPTURE_SAMPLES);
                if (available < minChunk) {
                    Thread.sleep(20);
                    continue;
                }
                int n = Math.min(available, bufLen);
                nativeBuf.clear();
                ALC11.alcCaptureSamples(device, nativeBuf, n);
                nativeBuf.get(samples, 0, n);

                if (!localAlive) {
                    wasAlive = false; // 죽어 있는 동안 한 말은 버린다
                    continue;
                }
                if (!wasAlive) {
                    pipeline.reset();
                    judge.reset();
                    wasAlive = true;
                }
                if (captureRate == RATE) {
                    pipeline.feed(samples, n);
                } else {
                    int m = n / 3;
                    for (int i = 0; i < m; i++) {
                        down[i] = (short) ((samples[3 * i] + samples[3 * i + 1] + samples[3 * i + 2]) / 3);
                    }
                    pipeline.feed(down, m);
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Throwable t) {
            TabooWord.LOGGER.error("[tabooword] voice listener failed", t);
            fail("음성 인식을 시작하지 못했어요: " + t.getClass().getSimpleName() + " — 로그(latest.log)를 확인해 주세요.");
        } finally {
            if (device != 0L) {
                ALC11.alcCaptureStop(device);
                ALC11.alcCaptureCloseDevice(device);
            }
            running = false;
        }
    }

    private void fail(String message) {
        failed = true;
        running = false;
        tell(message, ChatFormatting.RED);
    }

    private static void tell(String message, ChatFormatting color) {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            if (mc.player != null) {
                mc.gui.hud.getChat().addPlayerMessage(Component.literal("[금기어] " + message).withStyle(color), null, null);
            }
        });
    }

    private static void actionBar(String message) {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            if (mc.player != null) {
                mc.gui.hud.setOverlayMessage(Component.literal(message).withStyle(ChatFormatting.GRAY), false);
            }
        });
    }
}
