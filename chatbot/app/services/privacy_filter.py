from ko_pii import Anonymizer, ProcessingMode

detector = Anonymizer(mode=ProcessingMode.AUDIT)

BLOCKED_TYPES = {
    "RRN",  # 주민등록번호
    "PHONE",  # 전화번호
    "EMAIL",  # 이메일
    "CARD",  # 카드번호
    "ACCOUNT",  # 계좌번호
    "PERSON",  # 사람 이름
    "POSTAL_CODE",  # 우편번호
}


def check_privacy(text: str) -> bool:
    result = detector.process(text)

    for detection in result.detections:
        if detection.detection.label in BLOCKED_TYPES:
            return True

    return False
