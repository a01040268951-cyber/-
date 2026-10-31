package com.ssaem.tabooword.client;

import java.nio.ShortBuffer;
import java.nio.file.Path;

import org.lwjgl.BufferUtils;
import org.lwjgl.openal.AL10;
import org.lwjgl.openal.ALC10;
import org.lwjgl.openal.ALC11;
import org.vosk.LibVosk;
import org.vosk.LogLevel;
import org.vosk.Model;
import org.vosk.Recognizer;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.ssaem.tabooword.HangulLetters;
import com.ssaem.tabooword.TabooNetwork;
import com.ssaem.tabooword.TabooStatus;
import com.ssaem.tabooword.TabooWord;

import net.minecraft.ChatFormatting;
import net.minecraft.client.GuiMessageTag;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * 마이크(기본 녹음 장치)를 OpenAL로 듣고 Vosk로 한국어를 인식한다.
 * 소리는 이 PC 밖으로 나가지 않고, 금지 자모가 들린 문장만 서버로 보낸다.
 */
final class VoiceListener implements Runnable {
    private static final int[] RATES = {16000, 48000, 44100};
    private static final long COOLDOWN_MS = 2500;

    private static Model model; // 한 번 불러오면 계속 재사용 (로딩에 몇 초 걸림)

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

    /** 서버를 나가면 다시 시도할 수 있게 실패 표시를 지운다. */
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
            Path modelDir = ModelManager.ensureModel(mc.gameDirectory.toPath(), this::isRunning,
                    msg -> tell(msg, ChatFormatting.GRAY));
            if (modelDir == null) {
                return;
            }
            if (model == null) {
                tell("음성 인식 준비 중...", ChatFormatting.GRAY);
                LibVosk.setLogLevel(LogLevel.WARNINGS);
                model = new Model(modelDir.toString());
            }

            int rate = 0;
            for (int r : RATES) {
                device = ALC11.alcCaptureOpenDevice((CharSequence) null, r, AL10.AL_FORMAT_MONO16, r);
                if (device != 0L) {
                    rate = r;
                    break;
                }
            }
            if (device == 0L) {
                fail("마이크를 열 수 없어요. 윈도우 설정에서 기본 녹음 장치를 확인해 주세요.");
                return;
            }
            ALC11.alcCaptureStart(device);
            tell("♪ 마이크로 듣는 중 — 금지: " + String.join(", ", TabooStatus.letters()), ChatFormatting.RED);

            ShortBuffer nativeBuf = BufferUtils.createShortBuffer(rate);
            short[] samples = new short[rate];
            int minChunk = rate / 20; // 50ms
            long cooldownUntil = 0;
            String lastShown = "";

            try (Recognizer recognizer = new Recognizer(model, rate)) {
                while (running) {
                    int available = ALC10.alcGetInteger(device, ALC11.ALC_CAPTURE_SAMPLES);
                    if (available < minChunk) {
                        Thread.sleep(20);
                        continue;
                    }
                    int n = Math.min(available, samples.length);
                    nativeBuf.clear();
                    ALC11.alcCaptureSamples(device, nativeBuf, n);
                    nativeBuf.get(samples, 0, n);

                    if (!localAlive) {
                        recognizer.reset(); // 죽어 있는 동안 한 말은 버린다
                        continue;
                    }
                    boolean isFinal = recognizer.acceptWaveForm(samples, n);
                    String text = isFinal ? field(recognizer.getResult(), "text")
                            : field(recognizer.getPartialResult(), "partial");
                    if (text.isEmpty()) {
                        continue;
                    }
                    if (!text.equals(lastShown)) {
                        lastShown = text;
                        actionBar("♪ " + text);
                    }

                    long now = System.currentTimeMillis();
                    if (now < cooldownUntil || !TabooStatus.enabled()) {
                        if (isFinal) {
                            lastShown = "";
                        }
                        continue;
                    }
                    if (HangulLetters.findViolation(text, TabooStatus.letters()) >= 0) {
                        String said = text;
                        mc.execute(() -> ClientPacketDistributor.sendToServer(new TabooNetwork.SpokePayload(said)));
                        recognizer.reset();
                        cooldownUntil = now + COOLDOWN_MS;
                        lastShown = "";
                    }
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

    private static String field(String json, String key) {
        try {
            JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
            JsonElement el = obj.get(key);
            return el == null ? "" : el.getAsString().trim();
        } catch (RuntimeException e) {
            return "";
        }
    }

    private static void tell(String message, ChatFormatting color) {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            if (mc.player != null) {
                mc.gui.hud.getChat().addPlayerMessage(Component.literal("[금기어] " + message).withStyle(color), null, GuiMessageTag.system());
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
