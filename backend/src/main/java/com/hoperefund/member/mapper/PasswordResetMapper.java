package com.hoperefund.member.mapper;

import com.hoperefund.member.dto.PasswordResetDTO;
import org.apache.ibatis.annotations.Mapper;

/** PASSWORD_RESET 테이블 조회 · 저장 (F-01-03). SQL 은 resources/mapper/member/PasswordResetMapper.xml 에 있다. */
@Mapper
public interface PasswordResetMapper {

    // 실행 후 reset.resetId 에 새 번호가 채워진다 (XML 의 selectKey · SEQ_PASSWORD_RESET)
    int insertReset(PasswordResetDTO reset);

    // 회원의 가장 최근 재설정 번호. 없으면 null (예전 코드는 쓰지 않는다)
    Long selectLatestResetId(long memberId);

    // 재설정 1행을 잠그고 조회 → 같은 코드로 동시에 확인해도 한 번에 하나씩 처리한다 (트랜잭션 안에서 부른다)
    PasswordResetDTO selectResetForUpdate(long resetId);

    // 코드가 틀림: 틀린 횟수 + 1
    int increaseFailCount(long resetId);

    // 재설정 완료: 사용 처리 (USED_YN = 'Y')
    int markUsed(long resetId);
}
