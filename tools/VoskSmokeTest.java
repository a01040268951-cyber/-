import java.io.File;
import java.util.List;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;

import org.vosk.LibVosk;
import org.vosk.LogLevel;
import org.vosk.Model;
import org.vosk.Recognizer;

import com.ssaem.tabooword.HangulLetters;

/**
 * CI 전용: 한국어 음성 파일을 Vosk로 인식하고, 모드와 같은 방식(16-bit short 배열)으로
 * 넣었을 때 ㅇ 판정이 어떻게 나오는지 출력한다.
 * usage: java -cp ... VoskSmokeTest.java <modelDir> <wav16kMono>...
 */
public class VoskSmokeTest {
    public static void main(String[] args) throws Exception {
        LibVosk.setLogLevel(LogLevel.WARNINGS);
        try (Model model = new Model(args[0])) {
            for (int i = 1; i < args.length; i++) {
                try (AudioInputStream ais = AudioSystem.getAudioInputStream(new File(args[i]));
                     Recognizer rec = new Recognizer(model, ais.getFormat().getSampleRate())) {
                    byte[] bytes = new byte[1600 * 2];
                    short[] samples = new short[1600];
                    int n;
                    String firstHit = null;
                    while ((n = ais.read(bytes)) > 0) {
                        int count = n / 2;
                        for (int k = 0; k < count; k++) {
                            samples[k] = (short) ((bytes[2 * k] & 0xff) | (bytes[2 * k + 1] << 8));
                        }
                        boolean fin = rec.acceptWaveForm(samples, count);
                        String json = fin ? rec.getResult() : rec.getPartialResult();
                        if (firstHit == null && HangulLetters.findViolation(json.replaceAll("[a-z\"{}:]", ""), List.of("ㅇ")) >= 0) {
                            firstHit = json.replace('\n', ' ');
                        }
                    }
                    String result = rec.getFinalResult().replace('\n', ' ');
                    System.out.println("FILE " + args[i]);
                    System.out.println("  final   = " + result);
                    System.out.println("  ㅇ hit  = " + (firstHit == null ? "(none)" : firstHit));
                }
            }
        }
    }
}
