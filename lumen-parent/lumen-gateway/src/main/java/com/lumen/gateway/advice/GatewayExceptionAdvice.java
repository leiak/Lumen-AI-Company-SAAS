package com.lumen.gateway.advice;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumen.common.core.domain.R;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Reactive exception handler for the gateway. lumen-common-web's
 * GlobalExceptionAdvice uses servlet HttpServletRequest and is excluded from
 * the gateway's component scan; this advice provides equivalent behavior in
 * WebFlux without requiring servlet APIs.
 */
@Slf4j
@RestControllerAdvice
@RequiredArgsConstructor
public class GatewayExceptionAdvice {

    private final ObjectMapper objectMapper;

    @ExceptionHandler(Exception.class)
    public ResponseEntity<String> handleAll(Exception e) {
        log.error("Gateway error: {}", e.getMessage(), e);
        String body;
        try {
            body = objectMapper.writeValueAsString(R.fail(500, "Gateway error: " + e.getClass().getSimpleName()));
        } catch (JsonProcessingException inner) {
            body = "{\"code\":500,\"msg\":\"Gateway error\"}";
        }
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .contentType(MediaType.APPLICATION_JSON)
            .body(body);
    }
}
