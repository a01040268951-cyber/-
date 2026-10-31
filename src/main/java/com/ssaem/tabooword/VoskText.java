package com.ssaem.tabooword;

import java.util.Map;

import org.vosk.LibVosk;
import org.vosk.Recognizer;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Platform;
import com.sun.jna.Pointer;

/**
 * Vosk는 결과를 UTF-8로 돌려주는데, Vosk 자바 바인딩은 운영체제 기본 인코딩으로 읽는다.
 * 한국어 윈도우(CP949)에서는 한글이 깨지므로, 같은 함수를 UTF-8로 다시 연결해서 읽는다.
 */
public final class VoskText {
    private interface Utf8 extends Library {
        String vosk_recognizer_result(Pointer recognizer);

        String vosk_recognizer_partial_result(Pointer recognizer);

        String vosk_recognizer_final_result(Pointer recognizer);
    }

    private static Utf8 lib;
    private static boolean failed;

    private VoskText() {
    }

    private static synchronized Utf8 lib() {
        if (lib == null && !failed) {
            try {
                LibVosk.class.getName(); // 네이티브 라이브러리 준비 (LibVosk 초기화)
                lib = Native.load(Platform.isWindows() ? "libvosk" : "vosk", Utf8.class, Map.of(
                        Library.OPTION_STRING_ENCODING, "UTF-8",
                        Library.OPTION_CLASSLOADER, LibVosk.class.getClassLoader()));
            } catch (Throwable t) {
                failed = true;
                TabooWord.LOGGER.warn("[tabooword] UTF-8 binding failed, falling back", t);
            }
        }
        return lib;
    }

    public static String result(Recognizer r) {
        Utf8 l = lib();
        return l != null ? l.vosk_recognizer_result(r.getPointer()) : r.getResult();
    }

    public static String partial(Recognizer r) {
        Utf8 l = lib();
        return l != null ? l.vosk_recognizer_partial_result(r.getPointer()) : r.getPartialResult();
    }

    public static String finalResult(Recognizer r) {
        Utf8 l = lib();
        return l != null ? l.vosk_recognizer_final_result(r.getPointer()) : r.getFinalResult();
    }
}
