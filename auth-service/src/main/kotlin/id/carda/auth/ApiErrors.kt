package id.carda.auth

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

class ApiFailure(val status: HttpStatus, val code: String) : RuntimeException(code)
data class ApiError(val code: String)

@RestControllerAdvice
class ApiErrors {
    @ExceptionHandler(ApiFailure::class)
    fun known(error: ApiFailure): ResponseEntity<ApiError> =
        ResponseEntity.status(error.status).body(ApiError(error.code))

    @ExceptionHandler(MethodArgumentNotValidException::class, IllegalArgumentException::class)
    fun invalid(): ResponseEntity<ApiError> =
        ResponseEntity.badRequest().body(ApiError("invalid_request"))
}
