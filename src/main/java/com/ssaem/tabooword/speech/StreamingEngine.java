package com.ssaem.tabooword.speech;

import java.nio.file.Path;

import com.k2fsa.sherpa.onnx.EndpointConfig;
import com.k2fsa.sherpa.onnx.EndpointRule;
import com.k2fsa.sherpa.onnx.OnlineModelConfig;
import com.k2fsa.sherpa.onnx.OnlineRecognizer;
import com.k2fsa.sherpa.onnx.OnlineRecognizerConfig;
import com.k2fsa.sherpa.onnx.OnlineStream;
import com.k2fsa.sherpa.onnx.OnlineTransducerModelConfig;

/**
 * 스트리밍 한국어 인식 (sherpa-onnx zipformer, KsponSpeech 학습).
 * 0.1초 단위로 소리를 넣으면 그때까지 인식된 글자를 바로 돌려준다.
 * 한 번 나온 글자는 다시 바뀌지 않으므로 바로 판정해도 된다.
 */
public final class StreamingEngine implements AutoCloseable {
    public static final String[] MODEL_FILES = {
            "encoder-epoch-99-avg-1.int8.onnx",
            "decoder-epoch-99-avg-1.int8.onnx",
            "joiner-epoch-99-avg-1.int8.onnx",
            "tokens.txt",
    };

    public interface Listener {
        /** 인식된 글자가 바뀔 때마다 (확정된 글자) */
        void onText(String text);
    }

    private final OnlineRecognizer recognizer;
    private OnlineStream stream;
    private final Listener listener;
    private String last = "";
    private float[] buf = new float[0];

    public StreamingEngine(Path modelDir, int threads, Listener listener) {
        this.listener = listener;
        OnlineTransducerModelConfig transducer = OnlineTransducerModelConfig.builder()
                .setEncoder(modelDir.resolve(MODEL_FILES[0]).toString())
                .setDecoder(modelDir.resolve(MODEL_FILES[1]).toString())
                .setJoiner(modelDir.resolve(MODEL_FILES[2]).toString())
                .build();
        OnlineModelConfig model = OnlineModelConfig.builder()
                .setTransducer(transducer)
                .setTokens(modelDir.resolve(MODEL_FILES[3]).toString())
                .setNumThreads(threads)
                .setDebug(false)
                .build();
        // 말이 0.8초 끊기면 문장 끝으로 보고 새로 시작 (화면 글자도 새로)
        EndpointConfig endpoint = EndpointConfig.builder()
                .setRule1(EndpointRule.builder().setMustContainNonSilence(false).setMinTrailingSilence(2.4f).setMinUtteranceLength(0f).build())
                .setRule2(EndpointRule.builder().setMustContainNonSilence(true).setMinTrailingSilence(0.8f).setMinUtteranceLength(0f).build())
                .setRule3(EndpointRule.builder().setMustContainNonSilence(false).setMinTrailingSilence(0f).setMinUtteranceLength(15f).build())
                .build();
        OnlineRecognizerConfig config = OnlineRecognizerConfig.builder()
                .setOnlineModelConfig(model)
                .setEndpointConfig(endpoint)
                .setEnableEndpoint(true)
                .setDecodingMethod("greedy_search")
                .build();
        recognizer = new OnlineRecognizer(config);
        stream = recognizer.createStream();
    }

    /** 16kHz 16-bit 소리를 넣는다. */
    public void feed(short[] samples, int count) {
        if (buf.length < count) {
            buf = new float[count];
        }
        for (int i = 0; i < count; i++) {
            buf[i] = samples[i] / 32768f;
        }
        float[] chunk = buf.length == count ? buf : java.util.Arrays.copyOf(buf, count);
        stream.acceptWaveform(chunk, SpeechPipeline.RATE);
        while (recognizer.isReady(stream)) {
            recognizer.decode(stream);
        }
        String text = recognizer.getResult(stream).getText().trim();
        if (!text.equals(last)) {
            last = text;
            if (!text.isEmpty()) {
                listener.onText(text);
            }
        }
        if (recognizer.isEndpoint(stream)) {
            reset();
        }
    }

    /** 지금까지 들은 말을 버리고 새로 시작 */
    public void reset() {
        recognizer.reset(stream);
        last = "";
    }

    @Override
    public void close() {
        stream.release();
        recognizer.release();
    }
}
