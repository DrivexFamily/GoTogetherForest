# 폰 앱 화면 캡처 (결정 #37)

가족 폰 앱(`phone/`)을 시험용 가족(`FakeFamilySource`, 가상 이름)으로 그린 화면이다. 화면마다 폰(412×915dp) 한 장과 태블릿(1280×800dp) 한 장.

| 파일 | 화면 |
| --- | --- |
| `phone-connect-*` | 가족 폰 연결(QR 찍기·12자리 코드·이 폰 이름) |
| `phone-car-*` | 우리 차(진행 길·%·남은 시간·"N초 전 갱신"·대략 위치) |
| `phone-car-notrip-*` | 여행 없음 → 아이 숲 보러 가기 |
| `phone-forest-*` | 아이 숲(아이 고르기·숲 맵·보관함·끌어 옮기기) |
| `phone-shop-*` | 꾸미기 상점(가격은 시험용) |
| `phone-link-*` | 연결 관리(연결된 폰·어른 마일리지·이 폰 끊기) |

다시 찍기(저장소 루트에서): `./gradlew -Pphone=true :phone:ui:testDebugUnitTest --tests '*PhoneShots*'`
