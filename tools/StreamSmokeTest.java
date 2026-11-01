import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;

import com.ssaem.tabooword.speech.StreamingEngine;
import com.ssaem.tabooword.speech.TabooJudge;

/**
 * CI 전용: 빠른 엔진(스트리밍)에 음성 파일을 0.1초씩 넣어 판정과 처리 시간을 잰다.
 * 출력 형식은 WhisperSmokeTest와 같다 (summarize.py로 요약).
 */
public class StreamSmokeTest {
    public static void main(String[] args) throws Exception {
        long t0 = System.nanoTime();
        List<String> events = new ArrayList<>();
        long[] audioMs = {0};
        String[] killed = {null};
        TabooJudge[] judge = {new TabooJudge()};
        try (StreamingEngine engine = new StreamingEngine(Path.of(args[0]), Integer.parseInt(args[1]), text -> {
            events.add("중간 @" + audioMs[0] + "ms: " + text);
            if (killed[0] == null) {
                String k = judge[0].judge(text, true, List.of("ㅇ"));
                if (k != null) {
                    killed[0] = k + "  (오디오 " + audioMs[0] + "ms 시점)";
                }
            }
        })) {
            System.out.printf("MODEL streaming load=%dms%n", (System.nanoTime() - t0) / 1_000_000);
            for (int i = 2; i < args.length; i++) {
                short[] audio = load(args[i]);
                events.clear();
                killed[0] = null;
                judge[0] = new TabooJudge();
                engine.reset();
                long spent = 0;
                int calls = 0;
                short[] buf = new short[1600];
                for (int p = 0; p < audio.length; p += 1600) {
                    int n = Math.min(1600, audio.length - p);
                    System.arraycopy(audio, p, buf, 0, n);
                    audioMs[0] = (p + n) * 1000L / 16000;
                    long a = System.nanoTime();
                    engine.feed(buf, n);
                    spent += System.nanoTime() - a;
                    calls++;
                }
                if (!events.isEmpty()) {
                    String lastEvent = events.get(events.size() - 1);
                    events.add("최종 " + lastEvent.substring(lastEvent.indexOf('@')));
                }
                System.out.println("FILE " + args[i]);
                for (String e : events) System.out.println("   " + e);
                System.out.println("   판정: " + (killed[0] == null ? "생존" : "사망 \"" + killed[0] + "\""));
                System.out.printf("   인식 %d회, 평균 %dms%n", calls, Math.max(1, spent / 1_000_000 / calls));
            }
        }
    }

    private static short[] load(String path) throws Exception {
        try (AudioInputStream ais = AudioSystem.getAudioInputStream(new File(path))) {
            byte[] b = ais.readAllBytes();
            int pad = 16000;
            short[] s = new short[b.length / 2 + pad * 2];
            for (int k = 0; k < b.length / 2; k++) {
                s[pad + k] = (short) ((b[2 * k] & 0xff) | (b[2 * k + 1] << 8));
            }
            return s;
        }
    }
}
