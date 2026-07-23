package com.valoria.api.config

import com.nimbusds.jose.jwk.source.ImmutableSecret
import com.valoria.api.security.JwtService
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.Customizer.withDefaults
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.oauth2.jose.jws.MacAlgorithm
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.JwtValidators
import org.springframework.security.oauth2.jwt.JwtEncoder
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter
import org.springframework.security.web.SecurityFilterChain
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource
import java.util.Base64
import javax.crypto.spec.SecretKeySpec

@Configuration
@EnableMethodSecurity
class SecurityConfig {
    @Bean
    fun passwordEncoder(): PasswordEncoder = BCryptPasswordEncoder(12)

    @Bean
    fun jwtEncoder(properties: AppProperties): JwtEncoder {
        val key = secretKey(properties)
        return NimbusJwtEncoder(ImmutableSecret(key))
    }

    @Bean
    fun jwtDecoder(properties: AppProperties): JwtDecoder {
        val key = secretKey(properties)
        return NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build().also {
            it.setJwtValidator(JwtValidators.createDefaultWithIssuer(properties.jwt.issuer))
        }
    }

    @Bean
    fun securityFilterChain(
        http: HttpSecurity,
        jwtAuthenticationConverter: JwtAuthenticationConverter,
    ): SecurityFilterChain {
        http
            .csrf { it.disable() }
            .cors(withDefaults())
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests {
                it.requestMatchers("/actuator/health/**", "/api/auth/guest").permitAll()
                it.anyRequest().authenticated()
            }
            .oauth2ResourceServer {
                it.jwt { jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter) }
            }
        return http.build()
    }

    @Bean
    fun jwtAuthenticationConverter(): JwtAuthenticationConverter {
        val authorities = JwtGrantedAuthoritiesConverter()
        authorities.setAuthoritiesClaimName(JwtService.ROLES_CLAIM)
        authorities.setAuthorityPrefix("ROLE_")
        return JwtAuthenticationConverter().also {
            it.setJwtGrantedAuthoritiesConverter(authorities)
            it.setPrincipalClaimName("sub")
        }
    }

    @Bean
    fun corsConfigurationSource(properties: AppProperties): CorsConfigurationSource {
        val config = CorsConfiguration()
        config.allowedOrigins = listOf(properties.frontendOrigin)
        config.allowedMethods = listOf("GET", "POST", "PUT", "DELETE", "OPTIONS")
        config.allowedHeaders = listOf("Authorization", "Content-Type")
        config.exposedHeaders = listOf("Content-Disposition")
        config.allowCredentials = false
        config.maxAge = 3600
        return UrlBasedCorsConfigurationSource().also {
            it.registerCorsConfiguration("/api/**", config)
        }
    }

    private fun secretKey(properties: AppProperties): SecretKeySpec {
        val bytes = Base64.getDecoder().decode(properties.jwt.secretBase64)
        require(bytes.size >= 32) { "JWT_SECRET_BASE64 must contain at least 32 random bytes." }
        return SecretKeySpec(bytes, "HmacSHA256")
    }
}
