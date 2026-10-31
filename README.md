# 금기어 (Taboo Word)

채팅에 **ㅇ(이응)** 이 들어간 글자를 치면 그 자리에서 죽는 모드.

"안녕" → 사망. "방" → 사망. "ㅇㅇ" → 사망. "감사합니다" → 생존.

## 규칙
- 채팅 메시지의 한글을 자모 단위로 검사합니다. 초성·받침 어디에 있든 걸립니다.
- 띄어쓰기·기호는 그대로 둔 채 검사합니다.
- 금기어를 말하면 채팅은 그대로 보이고, 바로 다음 틱에 즉사합니다. (크리에이티브·불사의 토템도 소용없음)
- 관전 모드 플레이어는 제외.

## 명령어
| 명령어 | 설명 | 권한 |
|---|---|---|
| `/taboo` | 현재 상태와 금지 자모 보기 | 누구나 |
| `/taboo on` / `/taboo off` | 켜기 / 끄기 | OP |
| `/taboo add ㄱ` | 금지 자모 추가 | OP |
| `/taboo remove ㄱ` | 금지 자모 제거 | OP |

설정은 월드별로 `serverconfig/tabooword-server.toml`에 저장됩니다.

## 설치
- Minecraft Java **26.3** + **NeoForge**
- `mods` 폴더에 jar를 넣으세요. 멀티플레이는 서버와 클라이언트 모두 설치를 권장합니다.

---

## English
Say any Hangul syllable containing a banned letter (default **ㅇ**) in chat and you die instantly.
Use `/taboo on|off|add|remove` to configure. Requires Minecraft 26.3 + NeoForge.
