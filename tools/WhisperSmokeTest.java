import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;

import com.ssaem.tabooword.speech.SpeechPipeline;
import com.ssaem.tabooword.speech.TabooJudge;
import com.ssaem.tabooword.speech.WhisperTranscriber;

/**
 * CI 전용: 모드와 똑같은 파이프라인(VAD → whisper 중간/최종 인식 → 판정)에 음성 파일을 넣는다.
 * 각 파일 앞뒤로 1초 무음을 붙이고, 50ms 단위로 넣는다.
 * usage: java -cp ... WhisperSmokeTest.java <model.bin> <threads> <wav>...
 */
public class WhisperSmokeTest {
    public static void main(String[] args) throws Exception {
        long t0 = System.nanoTime();
        try (WhisperTranscriber whisper = new WhisperTranscriber(Path.of(args[0]), Integer.parseInt(args[1]))) {
            System.out.printf("MODEL %s  load=%dms threads=%s%n", args[0], (System.nanoTime() - t0) / 1_000_000, args[1]);
            for (int i = 2; i < args.length; i++) {
                short[] audio = load(args[i]);
                List<String> events = new ArrayList<>();
                long[] spent = new long[1];
                int[] calls = new int[1];
                String[] killed = {null};
                TabooJudge judge = new TabooJudge();
                long[] audioMs = {0};
                SpeechPipeline pipeline = new SpeechPipeline((s, n) -> {
                    long a = System.nanoTime();
                    String r = whisper.transcribe(s, n);
                    spent[0] += System.nanoTime() - a;
                    calls[0]++;
                    return r;
                }, (text, fin) -> {
                    events.add((fin ? "최종 " : "중간 ") + "@" + audioMs[0] + "ms: " + text);
                    if (killed[0] == null) {
                        String k = judge.judge(text, fin, List.of("ㅇ"));
                        if (k != null) {
                            killed[0] = k + "  (오디오 " + audioMs[0] + "ms 시점)";
                        }
                    }
                });
                int chunk = 800;
                short[] buf = new short[chunk];
                for (int p = 0; p < audio.length; p += chunk) {
                    int n = Math.min(chunk, audio.length - p);
                    System.arraycopy(audio, p, buf, 0, n);
                    audioMs[0] = (p + n) * 1000L / 16000;
                    pipeline.feed(buf, n);
                }
                pipeline.flush();
                System.out.println("FILE " + args[i]);
                for (String e : events) System.out.println("   " + e);
                System.out.println("   판정: " + (killed[0] == null ? "생존" : "사망 \"" + killed[0] + "\""));
                System.out.printf("   인식 %d회, 평균 %dms%n", calls[0], calls[0] == 0 ? 0 : spent[0] / 1_000_000 / calls[0]);
            }
        }
    }

    private static short[] load(String path) throws Exception {
        try (AudioInputStream ais = AudioSystem.getAudioInputStream(new File(path))) {
            byte[] b = ais.readAllBytes();
            int pad = 16000; // 앞뒤 1초 무음
            short[] s = new short[b.length / 2 + pad * 2];
            for (int k = 0; k < b.length / 2; k++) {
                s[pad + k] = (short) ((b[2 * k] & 0xff) | (b[2 * k + 1] << 8));
            }
            return s;
        }
    }
}
