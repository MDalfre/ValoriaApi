package com.valoria.api.controller

import com.valoria.api.repository.AccountAlreadyExistsException
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class ApiExceptionHandler {
    @ExceptionHandler(AccountAlreadyExistsException::class)
    fun duplicate(): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "Login name is already in use.")

    @ExceptionHandler(BadCredentialsException::class)
    fun credentials(): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, "Invalid credentials.")

    @ExceptionHandler(MethodArgumentNotValidException::class, IllegalArgumentException::class)
    fun validation(exception: Exception): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.message ?: "Invalid request.")
}

