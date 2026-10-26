package dev.javaquest;

import org.springframework.http.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import java.util.Map;

@RestControllerAdvice
public class Errors {
    @ExceptionHandler(ApiError.class) ResponseEntity<?> api(ApiError e) {
        return ResponseEntity.status(e.status).body(Map.of("message",e.getMessage()));
    }
    @ExceptionHandler({MethodArgumentNotValidException.class,HttpMessageNotReadableException.class})
    ResponseEntity<?> invalid(Exception e) { return ResponseEntity.badRequest().body(Map.of("message","输入格式不正确，请检查字段与长度")); }
    @ExceptionHandler(Exception.class) ResponseEntity<?> unexpected(Exception e) {
        org.slf4j.LoggerFactory.getLogger(Errors.class).error("Request failed",e);
        return ResponseEntity.internalServerError().body(Map.of("message","服务暂时无法处理请求，请稍后重试"));
    }
}
