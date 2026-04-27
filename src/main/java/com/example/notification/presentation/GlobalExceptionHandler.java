package com.example.notification.presentation;

import com.example.notification.application.service.DuplicateNotificationException;
import com.example.notification.application.service.NotificationNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    // void 반환: 응답 body 없이 상태 코드만 반환. Spring이 @ResponseStatus를 읽어 처리
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public void handleValidation() {}

    @ExceptionHandler(DuplicateNotificationException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public void handleDuplicate() {}

    @ExceptionHandler(NotificationNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public void handleNotFound() {}

    // retryDead에서 DEAD가 아닌 상태 알림에 재시도 요청 시 발생 → 409로 매핑
    @ExceptionHandler(IllegalStateException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public void handleIllegalState() {}

    // IN_APP 외 채널에 read 필터 적용 시 발생 → 400으로 매핑
    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public void handleIllegalArgument() {}
}
