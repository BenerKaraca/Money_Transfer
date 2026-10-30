package com.bener.corebank.shared;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class RequestIdInterceptor implements HandlerInterceptor {
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String incoming = request.getHeader("X-Request-Id");
        String requestId = incoming != null && incoming.matches("[A-Za-z0-9-]{1,64}")
                ? incoming : UUID.randomUUID().toString();
        response.setHeader("X-Request-Id", requestId);
        return true;
    }
}
