"""smoke.log 요약: 설정별 판정 정확도, 평균 인식 시간, 걸릴 때까지 걸린 시간."""
import re
import sys

log = open(sys.argv[1], encoding="utf-8", errors="replace").read()
sentences = dict(re.findall(r"^(\S+)\.wav = (.*)$", log, re.M))


def has_ieung(text):
    for ch in text:
        c = ord(ch)
        if 0xAC00 <= c <= 0xD7A3:
            i = c - 0xAC00
            if i // 588 == 11 or i % 28 == 21:
                return True
        elif ch == "ㅇ":
            return True
    return False


out = []
for block in log.split("=== ")[1:]:
    head = block.splitlines()[0]
    ok = total = 0
    times, delays, wrong = [], [], []
    for name, body in re.findall(r"FILE (\S+)\.wav\n(.*?)(?=FILE |\Z)", block, re.S):
        base = name.replace("_n", "")
        truth = has_ieung(sentences.get(base, ""))
        verdict = re.search(r"판정: (.*)", body)
        if not verdict:
            continue
        died = "사망" in verdict.group(1)
        total += 1
        if died == truth:
            ok += 1
        else:
            fin = re.search(r"최종 @\d+ms: (.*)", body)
            wrong.append(f"{name}({sentences.get(base, '')} → {fin.group(1)[:30] if fin else '-'})")
        t = re.search(r"평균 (\d+)ms", body)
        if t and int(t.group(1)) > 0:
            times.append(int(t.group(1)))
        k = re.search(r"오디오 (\d+)ms 시점", body)
        if died and truth and k:
            delays.append(int(k.group(1)) - 1000)  # 앞 1초 무음 제외 = 말 시작부터
    avg = sum(times) // len(times) if times else 0
    dly = sum(delays) // len(delays) if delays else 0
    out.append(f"## {head}: 판정 {ok}/{total}, 인식 1회 평균 {avg}ms, 말 시작→판정 평균 {dly}ms(오디오 기준)")
    for w in wrong:
        out.append(f"   틀림: {w}")
print("\n".join(out))
