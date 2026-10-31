import java.io.File;
import java.util.List;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;

import org.vosk.LibVosk;
import org.vosk.LogLevel;
import org.vosk.Model;
import org.vosk.Recognizer;

import com.ssaem.tabooword.TriggerGate;

/**
 * CI 전용: 한국어 음성 파일을 모드와 같은 방식(100ms 단위 short 배열 + TriggerGate)으로 넣어
 * 실제로 "사망" 판정이 나는지 출력한다. 시간은 오디오 길이로 흉내 낸다.
 * usage: java -cp ... VoskSmokeTest.java <modelDir> <wav16kMono>...
 */
public class VoskSmokeTest {
    public static void main(String[] args) throws Exception {
        LibVosk.setLogLevel(LogLevel.WARNINGS);
        List<String> banned = List.of("ㅇ");
        try (Model model = new Model(args[0])) {
            for (int i = 1; i < args.length; i++) {
                try (AudioInputStream ais = AudioSystem.getAudioInputStream(new File(args[i]));
                     Recognizer rec = new Recognizer(model, ais.getFormat().getSampleRate())) {
                    int chunk = (int) (ais.getFormat().getSampleRate() / 10);
                    byte[] bytes = new byte[chunk * 2];
                    short[] samples = new short[chunk];
                    TriggerGate gate = new TriggerGate();
                    long t = 0;
                    String killed = null;
                    long killedAt = -1;
                    int n;
                    boolean silence = false;
                    while (killed == null) {
                        n = ais.read(bytes);
                        if (n <= 0) {
                            if (silence) break;
                            // 문장 끝 뒤 1초 침묵을 넣어 최종 결과가 나오게 함
                            java.util.Arrays.fill(bytes, (byte) 0);
                            n = bytes.length;
                            silence = true;
                        }
                        int count = n / 2;
                        for (int k = 0; k < count; k++) {
                            samples[k] = (short) ((bytes[2 * k] & 0xff) | (bytes[2 * k + 1] << 8));
                        }
                        boolean fin = rec.acceptWaveForm(samples, count);
                        String text = field(fin ? rec.getResult() : rec.getPartialResult(), fin ? "text" : "partial");
                        t += 100;
                        if (text.isEmpty()) continue;
                        String said = gate.offer(text, fin, t, banned);
                        if (said != null) {
                            killed = said;
                            killedAt = t;
                        }
                    }
                    String fin = field(rec.getFinalResult(), "text");
                    System.out.println("FILE " + args[i]);
                    System.out.println("  인식(최종) = " + fin);
                    System.out.println("  판정       = " + (killed == null ? "생존" : "사망 \"" + killed + "\" @" + killedAt + "ms"));
                }
            }
        }
    }

    private static String field(String json, String key) {
        int k = json.indexOf("\"" + key + "\"");
        if (k < 0) return "";
        int a = json.indexOf('"', json.indexOf(':', k) + 1);
        int b = json.indexOf('"', a + 1);
        return a < 0 || b < 0 ? "" : json.substring(a + 1, b).trim();
    }
}
