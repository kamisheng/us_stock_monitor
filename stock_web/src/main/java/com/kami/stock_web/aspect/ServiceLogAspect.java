package com.kami.stock_web.aspect;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

/**
 *
 * @Author kamiSheng
 * @Date 2026/9/21 22:03
 * @Description us_stock_monitor_dev
 * @Package com.kami.stock_web.apect
 */
@Slf4j
@Component
@Aspect
public class ServiceLogAspect {

    @Around("execution(* com.kami.stock_web.service.*.*(..)) ")
    public Object recordTimeLog(ProceedingJoinPoint joinPoint) throws Throwable {
        long startTime = System.currentTimeMillis();

        Object proceed = joinPoint.proceed();
        Object pointCut = joinPoint.getTarget().getClass().getName() + "." + joinPoint.getSignature().getName();

        long endTime = System.currentTimeMillis();
        long takeTime = endTime - startTime;
        if(takeTime > 3000) {
            log.warn("[{}], [{}], [{}], [{}]", pointCut, "slow",takeTime, joinPoint.getArgs());
        } else if (takeTime > 2000) {
            log.info("[{}], [{}], [{}], [{}]", pointCut, "normal", takeTime, joinPoint.getArgs());
        }else {
            log.info("[{}], [{}], [{}], [{}]", pointCut, "OK", takeTime, joinPoint.getArgs());
        }
        return proceed;
    }
}
