package com.fishing.logbook.auth

import com.fishing.logbook.domain.User
import com.fishing.logbook.repository.UserRepository
import com.fishing.logbook.security.JwtTokenProvider
import jakarta.validation.Valid
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.web.bind.annotation.*

data class SignUpRequest(
    @field:Email val email: String,
    @field:Size(min = 8, max = 64) val password: String,
    @field:NotBlank val nickname: String,
)

data class LoginRequest(
    @field:Email val email: String,
    @field:NotBlank val password: String,
)

data class TokenResponse(val token: String, val userId: Long, val nickname: String)

@Service
class AuthService(
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder,
    private val tokenProvider: JwtTokenProvider,
) {
    fun signUp(req: SignUpRequest): TokenResponse {
        require(!userRepository.existsByEmail(req.email)) { "이미 가입된 이메일입니다" }
        val user = userRepository.save(
            User(
                email = req.email,
                password = passwordEncoder.encode(req.password),
                nickname = req.nickname,
            )
        )
        return TokenResponse(tokenProvider.createToken(user.id!!), user.id!!, user.nickname)
    }

    fun login(req: LoginRequest): TokenResponse {
        val user = userRepository.findByEmail(req.email)
            ?: throw IllegalArgumentException("이메일 또는 비밀번호가 올바르지 않습니다")
        require(passwordEncoder.matches(req.password, user.password)) {
            "이메일 또는 비밀번호가 올바르지 않습니다"
        }
        return TokenResponse(tokenProvider.createToken(user.id!!), user.id!!, user.nickname)
    }
}

@RestController
@RequestMapping("/api/auth")
class AuthController(private val authService: AuthService) {

    @PostMapping("/signup")
    fun signUp(@Valid @RequestBody req: SignUpRequest): TokenResponse = authService.signUp(req)

    @PostMapping("/login")
    fun login(@Valid @RequestBody req: LoginRequest): TokenResponse = authService.login(req)
}
