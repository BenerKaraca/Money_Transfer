package com.bener.corebank.shared;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class ServiceTimingAspect {
    private static final Logger log = LoggerFactory.getLogger(ServiceTimingAspect.class);

    @Around("execution(* com.bener.corebank..*Service.*(..))")
    public Object measure(ProceedingJoinPoint call) throws Throwable {
        long start = System.nanoTime();
        try {
            return call.proceed();
        } finally {
            long elapsedMillis = (System.nanoTime() - start) / 1_000_000;
            log.debug("service={} elapsedMs={}", call.getSignature().toShortString(), elapsedMillis);
        }
    }
}
