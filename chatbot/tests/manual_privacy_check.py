from app.services.privacy_filter import check_privacy, detector

test_questions = [
    "소득공제란 무엇인가요?",
    "제 전화번호는 010-1234-5678입니다.",
    "제 이메일은 test@example.com입니다.",
    "제 주민등록번호는 990101-1234567입니다.",
    "제 카드번호는 4111-1111-1111-1111입니다.",
    "제 계좌번호는 123-456-789012입니다.",
    "제 계좌번호는 110-123-456789입니다.",
    "제 계좌번호는 12345678901234입니다.",
    "계좌번호란 무엇인가요?",
    "연봉이 3500만원이면 소득공제는 어떻게 되나요?",
    "주민등록번호란 무엇인가요?",
]

for question in test_questions:
    result = detector.process(question)

    labels = [record.detection.label for record in result.detections]

    blocked = check_privacy(question)

    print(f"질문: {question}")
    print(f"탐지 유형: {labels}")
    print(f"차단 여부: {blocked}")
    print("-" * 50)
