# 5.5.498.1307: 커버 배경 뮤직비디오

영상 선택 시 정사각형 전경 아트워크를 추가하던 구조를 제거했습니다. 세로 화면에서는 상단 전체 가로폭과 화면 높이의 64%, 가로 태블릿에서는 좌측 커버 영역을 사용합니다. 제목·아티스트·음악/영상 선택기·재생 조작의 기존 배치를 유지하고 가장자리 그라데이션으로 연결합니다. 로컬 영상은 실제 영상 비율로 중앙을 잘라 채웁니다. YouTube 공식 iframe은 일반적인 뮤직비디오의 16:9 비율로 영역을 채웁니다. 원본 영상 자체에 들어간 검은 여백은 제거하지 않습니다.

렌더러는 아트워크의 유효성 검사·교차 전환과 분리해 하나만 유지합니다. 무음과 기존 음악의 재생 위치가 기준입니다. 재생/정지·탐색·영상 시점 조절로 WebView를 다시 만들지 않습니다. 가사 화면과 앱이 보이지 않을 때는 영상을 정지하고, 음악 보기로 돌아가거나 화면을 벗어나면 영상 자원을 해제합니다.

YouTube WebView에서 모든 URL 이동을 차단하던 처리를 HTTPS 하위 프레임 허용으로 수정했습니다. 최상위 페이지의 외부 이동·파일/스크립트/intent 이동은 차단합니다. 앱 패키지 ID를 HTTPS 참조 주소로 사용하고 origin/widget_referrer와 일치시킵니다. JavaScript 콜백을 등록한 후 API를 요청하며 WebChromeClient를 설정합니다. 네트워크 실패·재생 불가·임베드 제한·식별 실패(153)·자동 재생 차단·25초 로딩 지연을 표시하고 다시 시도/영상 관리로 복구할 수 있습니다. 로딩 중이나 실패 시 커버를 유지합니다.

공식 문서: [YouTube embedded player identity](https://developers.google.com/youtube/terms/required-minimum-functionality#embedded-player-api-client-identity), [Android loadDataWithBaseURL](https://developer.android.com/reference/android/webkit/WebView#loadDataWithBaseURL(java.lang.String,java.lang.String,java.lang.String,java.lang.String,java.lang.String)). YouTube 콘텐츠를 다운로드하거나 비공식 미디어 URL을 추출하지 않습니다.

검증에는 실제 Android WebView와 배포용 HTML을 사용하되 원격 API 응답만 테스트 응답으로 대체하는 iframe 통합 검증을 포함합니다. 참조 헤더·하위 프레임 로딩·비율과 영역 채움·무음·탐색·재생 상태·153 오류 전달을 확인합니다. 실제 커버/로컬 WebM을 사용하는 전체 플레이어에서 배경 경계·단일 렌더러·조작 위치 유지·영상을 끌 때 해제를 확인합니다. 기존 미디어·영구 기록·고정 추가 작업·알림 회귀도 포함합니다.

이 실행 환경에서는 YouTube 접속이 프록시의 CONNECT 403으로 거부됩니다. 따라서 실제 YouTube 서비스의 온라인 재생이나 Samsung Android 17 실기기 동작을 검증했다고 주장하지 않습니다. 위 WebView 검증은 앱 측 통합을 검증하며, 서비스의 연령/지역/게시자 제한을 해제하지 않습니다.
