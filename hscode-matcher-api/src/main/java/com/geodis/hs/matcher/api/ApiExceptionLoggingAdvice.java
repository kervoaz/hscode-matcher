package com.geodis.hs.matcher.api;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.server.ResponseStatusException;

/**
 * Logs client errors (4xx) and returns a small {@code text/plain} body so tools (REST Client, curl)
 * show the reason; server logs retain the same detail for the console.
 */
@RestControllerAdvice
public class ApiExceptionLoggingAdvice {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionLoggingAdvice.class);

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<String> handleResponseStatus(ResponseStatusException ex, HttpServletRequest req) {
        String reason = Optional.ofNullable(ex.getReason()).orElse("");
        if (ex.getStatusCode().is4xxClientError()) {
            log.warn(
                    "HTTP {} {} {} — {}",
                    ex.getStatusCode().value(),
                    req.getMethod(),
                    req.getRequestURI(),
                    reason);
        } else {
            log.error(
                    "HTTP {} {} {} — {}",
                    ex.getStatusCode().value(),
                    req.getMethod(),
                    req.getRequestURI(),
                    reason,
                    ex);
        }
        return textResponse(ex.getStatusCode().value(), reason);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<String> handleUnreadable(HttpMessageNotReadableException ex, HttpServletRequest req) {
        String msg = Optional.ofNullable(ex.getMessage()).orElse("Malformed request body");
        log.warn("HTTP 400 {} {} — JSON body not readable: {}", req.getMethod(), req.getRequestURI(), msg);
        return textResponse(400, "Malformed JSON: " + truncate(msg, 500));
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<String> handleMissingPart(MissingServletRequestPartException ex, HttpServletRequest req) {
        log.warn(
                "HTTP 400 {} {} — missing multipart part '{}'",
                req.getMethod(),
                req.getRequestURI(),
                ex.getRequestPartName());
        return textResponse(400, "Missing multipart part: " + ex.getRequestPartName());
    }

    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<String> handleMultipart(MultipartException ex, HttpServletRequest req) {
        String msg = Optional.ofNullable(ex.getMessage()).orElse(ex.getClass().getSimpleName());
        log.warn("HTTP 400 {} {} — multipart error: {}", req.getMethod(), req.getRequestURI(), msg, ex);
        return textResponse(400, "Multipart error: " + truncate(msg, 500));
    }

    private static ResponseEntity<String> textResponse(int status, String body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.TEXT_PLAIN);
        return ResponseEntity.status(status).headers(headers).body(body == null || body.isEmpty() ? "(no details)" : body);
    }

    private static String truncate(String s, int max) {
        if (s == null || s.length() <= max) {
            return s;
        }
        return s.substring(0, max) + "…";
    }
}
