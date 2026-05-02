package com.takibo.audit.domain;

import io.micrometer.common.util.StringUtils;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.spel.SpelEvaluationException;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10) // 2e
@Slf4j
public class SpelExpressionIdResolutionStrategy implements IdResolutionStrategy {

    private final SpelExpressionParser parser = new SpelExpressionParser();

    @Override
    public Optional<String> resolve(JoinPoint joinPoint, String expression, Object result) {
        if (StringUtils.isBlank(expression) || expression.startsWith("#path.")) {
            return Optional.empty();
        }

        EvaluationContext context = new StandardEvaluationContext();
        context.setVariable("args", joinPoint.getArgs());
        context.setVariable("result", result);
        if (joinPoint.getArgs() != null && joinPoint.getArgs().length > 0) {
            context.setVariable("request", joinPoint.getArgs()[0]);
        }

        try {
            Object value = parser.parseExpression(expression).getValue(context);
            return value != null ? Optional.of(value.toString()) : Optional.empty();
        } catch (SpelEvaluationException e) {
            log.debug("Évaluation SpEL échouée pour '{}'", expression, e);
            return Optional.empty();
        }
    }
}
