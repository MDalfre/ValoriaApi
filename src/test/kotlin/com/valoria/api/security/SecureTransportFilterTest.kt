package com.valoria.api.security

import com.valoria.api.config.AppProperties
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockFilterChain
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse

class SecureTransportFilterTest {
    private val properties = AppProperties(
        frontendOrigin = "https://valoria.example",
        requireHttps = true,
        jwt = AppProperties.Jwt(
            secretBase64 = "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
        ),
    )

    private val filter = SecureTransportFilter(properties)

    @Test
    fun `rejects login without https`() {
        val request = MockHttpServletRequest("POST", "/api/auth/login")
        val response = MockHttpServletResponse()

        filter.doFilter(request, response, MockFilterChain())

        assertThat(response.status).isEqualTo(426)
    }

    @Test
    fun `accepts login forwarded through https proxy`() {
        val request = MockHttpServletRequest("POST", "/api/auth/login")
        request.addHeader("X-Forwarded-Proto", "https")
        val response = MockHttpServletResponse()

        filter.doFilter(request, response, MockFilterChain())

        assertThat(response.status).isEqualTo(200)
    }

    @Test
    fun `does not require https for guest token`() {
        val request = MockHttpServletRequest("POST", "/api/auth/guest")
        val response = MockHttpServletResponse()

        filter.doFilter(request, response, MockFilterChain())

        assertThat(response.status).isEqualTo(200)
    }
}
