package com.hoperefund.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/** 비밀번호 암호화기 등록. 회원가입 · 로그인 · 비밀번호 변경이 모두 같은 BCrypt 를 쓴다. */
@Configuration
public class PasswordConfig {

    // BCrypt: 같은 비밀번호도 매번 다른 암호문이 나오고(솔트), 일부러 느리게 만들어 무작위 대입을 어렵게 한다
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
