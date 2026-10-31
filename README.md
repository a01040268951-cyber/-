# 금기어 (Taboo Word)

마이크로 **ㅇ(이응)** 이 들어간 말을 하면 그 자리에서 죽는 모드.

"안녕" → 사망. "오케이" → 사망. "감사합니다" → 생존.

## 이렇게 동작해요
- 각자 PC에서 마이크 소리를 한국어 음성 인식(Whisper)으로 글자로 바꿉니다. 인터넷 없이 PC 안에서 처리돼요.
- 말소리가 날 때만 인식하고, 말하는 중에는 약 1초마다, 말이 끝나면 한 번 더 인식합니다.
- 인식된 말에 금지 자모가 들어 있으면 그 문장만 서버로 보내고, 서버가 다시 확인한 뒤 즉사시킵니다. (크리에이티브·불사의 토템도 소용없음)
- 전체 채팅에 `[금기어] Steve 사망 — "안녕하세요"` 처럼 걸린 글자가 표시됩니다.
- 내가 말한 게 어떻게 인식되는지 화면 아래(액션바)에 실시간으로 보여요.
- 관전 모드와 죽어 있는 동안은 제외.

## 처음 실행할 때
- 게임에 들어가면 음성 인식 모델(약 190MB)을 한 번 자동으로 내려받습니다. `.minecraft/tabooword/` 폴더에 저장돼요.
- 컴퓨터가 느려서 인식이 밀리면 `config/tabooword-client.toml`에서 `model = "base"`(약 60MB, 더 빠름)로 바꾸세요.
- 윈도우의 **기본 녹음 장치**를 씁니다. 다른 마이크를 쓰려면 윈도우 소리 설정에서 기본 장치를 바꾸세요.
- 스피커 소리가 마이크로 들어가면 오인식될 수 있으니 **헤드폰**을 권장합니다.
- 메모리를 약 500MB 더 씁니다. 런처에서 4GB 이상 할당을 권장해요.

## 명령어
| 명령어 | 설명 | 권한 |
|---|---|---|
| `/taboo` | 현재 상태와 금지 자모 보기 | 누구나 |
| `/taboo on` / `/taboo off` | 켜기 / 끄기 (끄면 마이크도 꺼짐) | OP |
| `/taboo add ㄱ` | 금지 자모 추가 | OP |
| `/taboo remove ㄱ` | 금지 자모 제거 | OP |

설정은 월드별로 `serverconfig/tabooword-server.toml`에 저장됩니다.

## 설치
- Minecraft Java **26.3** + **NeoForge**
- 서버와 모든 플레이어 모두 설치해야 합니다. (싱글플레이는 그냥 넣으면 끝)
- 서로 목소리를 들으려면 Simple Voice Chat이나 디스코드를 같이 쓰세요. 이 모드는 듣고 판정만 합니다.

## 개인정보
마이크 소리는 내 PC 밖으로 나가지 않습니다. 금지 자모가 들린 문장(글자)만, 접속한 서버로 전송됩니다.

## 크레딧
음성 인식: [whisper.cpp](https://github.com/ggerganov/whisper.cpp) / OpenAI Whisper 모델 (MIT), [whisper-jni](https://github.com/GiviMAD/whisper-jni) (Apache 2.0)

---

## English
Say anything containing a banned Hangul letter (default **ㅇ**) into your microphone and you die instantly.
Offline speech recognition (Whisper small, ~190 MB, downloaded on first run). Audio never leaves your PC;
only the recognized sentence that triggered the rule is sent to the server you joined.
Requires Minecraft 26.3 + NeoForge on both client and server. `/taboo on|off|add|remove` to configure.
