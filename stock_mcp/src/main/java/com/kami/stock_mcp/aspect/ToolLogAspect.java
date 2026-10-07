package com.kami.stock_mcp.aspect;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.stereotype.Component;
import org.aspectj.lang.annotation.Aspect;

import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.util.Arrays;

/**
 *
 * @Author kamiSheng
 * @Date 2026/10/5 17:37
 * @Description us_stock_monitor_dev
 * @Package com.kami.stock_mcp.aspect
 */
@Slf4j
@Component
@Aspect
public class ToolLogAspect {

    @Around("execution(* com.kami.stock_mcp.tool..*(..))")
    public Object recordLogAspect(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        log.info("=============================== 调用MCP工具：{}({}) ================================",
                method.getName(), Arrays.toString(joinPoint.getArgs()));
        Object proceed = joinPoint.proceed();
        log.info("=============================== 返回：{} ===============================", proceed);
        return proceed;
    }
}
