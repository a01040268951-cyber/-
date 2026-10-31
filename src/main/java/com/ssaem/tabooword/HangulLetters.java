package com.ssaem.tabooword;

import java.util.Collection;
import java.util.Map;

/**
 * 한글 글자를 자모(ㄱ, ㅏ, ㅇ ...)로 분해해서 금지 자모가 들어 있는지 검사한다.
 * 마인크래프트 코드에 의존하지 않는 순수 자바 클래스.
 */
public final class HangulLetters {
    private static final String CHO = "ㄱㄲㄴㄷㄸㄹㅁㅂㅃㅅㅆㅇㅈㅉㅊㅋㅌㅍㅎ";
    private static final String JUNG = "ㅏㅐㅑㅒㅓㅔㅕㅖㅗㅘㅙㅚㅛㅜㅝㅞㅟㅠㅡㅢㅣ";
    // 0번 = 받침 없음
    private static final String JONG = " ㄱㄲㄳㄴㄵㄶㄷㄹㄺㄻㄼㄽㄾㄿㅀㅁㅂㅄㅅㅆㅇㅈㅊㅋㅌㅍㅎ";

    // 겹받침·이중모음은 구성 자모도 포함한다 (예: 닭 → ㄺ, ㄹ, ㄱ)
    private static final Map<Character, String> PARTS = Map.ofEntries(
            Map.entry('ㄳ', "ㄱㅅ"), Map.entry('ㄵ', "ㄴㅈ"), Map.entry('ㄶ', "ㄴㅎ"),
            Map.entry('ㄺ', "ㄹㄱ"), Map.entry('ㄻ', "ㄹㅁ"), Map.entry('ㄼ', "ㄹㅂ"),
            Map.entry('ㄽ', "ㄹㅅ"), Map.entry('ㄾ', "ㄹㅌ"), Map.entry('ㄿ', "ㄹㅍ"),
            Map.entry('ㅀ', "ㄹㅎ"), Map.entry('ㅄ', "ㅂㅅ"),
            Map.entry('ㅘ', "ㅗㅏ"), Map.entry('ㅙ', "ㅗㅐ"), Map.entry('ㅚ', "ㅗㅣ"),
            Map.entry('ㅝ', "ㅜㅓ"), Map.entry('ㅞ', "ㅜㅔ"), Map.entry('ㅟ', "ㅜㅣ"),
            Map.entry('ㅢ', "ㅡㅣ"));

    private HangulLetters() {
    }

    /** 글자 c를 이루는 자모들. 한글이 아니면 빈 문자열. */
    public static String lettersOf(char c) {
        StringBuilder sb = new StringBuilder(6);
        if (c >= 0xAC00 && c <= 0xD7A3) {           // 완성형 음절 (가~힣)
            int i = c - 0xAC00;
            add(sb, CHO.charAt(i / 588));
            add(sb, JUNG.charAt((i % 588) / 28));
            int jong = i % 28;
            if (jong > 0) {
                add(sb, JONG.charAt(jong));
            }
        } else if (c >= 0x1100 && c < 0x1100 + 19) { // 조합형 초성
            add(sb, CHO.charAt(c - 0x1100));
        } else if (c >= 0x1161 && c < 0x1161 + 21) { // 조합형 중성
            add(sb, JUNG.charAt(c - 0x1161));
        } else if (c >= 0x11A8 && c < 0x11A8 + 27) { // 조합형 종성
            add(sb, JONG.charAt(c - 0x11A8 + 1));
        } else if (c >= 0x3131 && c <= 0x3163) {     // 낱자 (ㅇ, ㅋㅋ 등)
            add(sb, c);
        }
        return sb.toString();
    }

    private static void add(StringBuilder sb, char letter) {
        sb.append(letter);
        String parts = PARTS.get(letter);
        if (parts != null) {
            sb.append(parts);
        }
    }

    /** text에서 금지 자모가 들어간 첫 글자의 위치. 없으면 -1. */
    public static int findViolation(String text, Collection<? extends String> banned) {
        if (text == null || banned.isEmpty()) {
            return -1;
        }
        for (int i = 0; i < text.length(); i++) {
            String letters = lettersOf(text.charAt(i));
            if (letters.isEmpty()) {
                continue;
            }
            for (String b : banned) {
                if (b.length() == 1 && letters.indexOf(b.charAt(0)) >= 0) {
                    return i;
                }
            }
        }
        return -1;
    }

    /** 금지 목록에 넣을 수 있는 한글 낱자(ㄱ~ㅎ, ㅏ~ㅣ)인지. */
    public static boolean isLetter(Object o) {
        return o instanceof String s && s.length() == 1 && s.charAt(0) >= 0x3131 && s.charAt(0) <= 0x3163;
    }
}
