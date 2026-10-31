package com.ssaem.tabooword;

import java.util.Collections;

import org.vosk.LibVosk;
import org.vosk.Recognizer;

import com.sun.jna.Function;
import com.sun.jna.Library;
import com.sun.jna.NativeLibrary;
import com.sun.jna.Platform;
import com.sun.jna.Pointer;

/**
 * Vosk는 결과를 UTF-8로 돌려주는데, Vosk 자바 바인딩은 운영체제 기본 인코딩으로 읽는다.
 * 한국어 윈도우(CP949)에서는 한글이 깨지므로, 이미 불러온 같은 네이티브 라이브러리에서
 * 함수를 직접 호출해 UTF-8로 읽는다.
 */
public final class VoskText {
    private static Function resultFn;
    private static Function partialFn;
    private static Function finalFn;
    private static boolean tried;

    private VoskText() {
    }

    private static synchronized void init() {
        if (tried) {
            return;
        }
        tried = true;
        try {
            LibVosk.class.getName(); // LibVosk 초기화 (네이티브 라이브러리 등록)
            // LibVosk가 Native.register 할 때와 같은 이름·옵션 → JNA가 캐시해 둔 같은 라이브러리를 돌려준다
            NativeLibrary lib = NativeLibrary.getInstance(Platform.isWindows() ? "libvosk" : "vosk",
                    Collections.singletonMap(Library.OPTION_CLASSLOADER, LibVosk.class.getClassLoader()));
            resultFn = lib.getFunction("vosk_recognizer_result");
            partialFn = lib.getFunction("vosk_recognizer_partial_result");
            finalFn = lib.getFunction("vosk_recognizer_final_result");
        } catch (Throwable t) {
            resultFn = partialFn = finalFn = null;
            TabooWord.LOGGER.warn("[tabooword] UTF-8 reader unavailable, falling back", t);
        }
    }

    private static String call(Function fn, Recognizer r) {
        Pointer p = fn.invokePointer(new Object[] {r.getPointer()});
        return p == null ? "" : p.getString(0, "UTF-8");
    }

    public static String result(Recognizer r) {
        init();
        return resultFn != null ? call(resultFn, r) : r.getResult();
    }

    public static String partial(Recognizer r) {
        init();
        return partialFn != null ? call(partialFn, r) : r.getPartialResult();
    }

    public static String finalResult(Recognizer r) {
        init();
        return finalFn != null ? call(finalFn, r) : r.getFinalResult();
    }
}
