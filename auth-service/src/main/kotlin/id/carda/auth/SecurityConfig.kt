package id.carda.auth

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import java.time.Clock
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

@Configuration
class SecurityConfig {
    @Bean fun clock(): Clock = Clock.systemUTC()
    @Bean fun passwordEncoder(): PasswordEncoder = Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8()

    @Bean
    fun chain(http: HttpSecurity, accessFilter: AccessFilter): SecurityFilterChain = http
        .csrf { it.disable() }
        .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
        .headers { headers ->
            headers.contentSecurityPolicy { it.policyDirectives(
                "default-src 'none'; script-src 'self'; style-src 'self'; connect-src 'self'; " +
                    "img-src 'self'; base-uri 'none'; frame-ancestors 'none'; form-action 'self'",
            ) }
            headers.referrerPolicy { it.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER) }
        }
        .authorizeHttpRequests {
            it.requestMatchers(HttpMethod.POST,
                "/api/v1/auth/register", "/api/v1/auth/verify-email", "/api/v1/auth/login",
                "/api/v1/auth/refresh", "/api/v1/auth/password-reset/request", "/api/v1/auth/password-reset/confirm",
            ).permitAll()
            it.requestMatchers("/actuator/health").permitAll()
            it.requestMatchers(HttpMethod.GET, "/account-delete/index.html", "/account-delete/portal.js",
                "/account-delete/portal.css").permitAll()
            it.anyRequest().authenticated()
        }
        .exceptionHandling { handlers -> handlers.authenticationEntryPoint { _, response, _ ->
            response.status = HttpServletResponse.SC_UNAUTHORIZED
            response.contentType = "application/json"
            response.writer.write("{\"code\":\"unauthorized\"}")
        } }
        .addFilterBefore(accessFilter, UsernamePasswordAuthenticationFilter::class.java)
        .build()
}

@Component
class AccessFilter(
    private val repository: IdentityRepository,
    private val tokens: TokenFactory,
    private val clock: Clock,
) : OncePerRequestFilter() {
    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, chain: FilterChain) {
        val headers = request.getHeaders("Authorization")
        val header = if (headers.hasMoreElements()) headers.nextElement() else null
        if (header != null && !headers.hasMoreElements() && header.startsWith("Bearer ") && header.length <= 512) {
            val accountId = repository.accountForAccess(tokens.hash(header.substring(7)), clock.instant())
            if (accountId != null) {
                SecurityContextHolder.getContext().authentication =
                    UsernamePasswordAuthenticationToken(accountId.toString(), null, emptyList())
            }
        }
        chain.doFilter(request, response)
    }
}
