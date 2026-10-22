package com.takibo.adp.core.engine;

import com.takibo.adp.api.EvaluatorStatus;
import com.takibo.adp.core.evaluator.*;
import com.takibo.adp.core.model.AccessContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.concurrent.*;

@Slf4j
@RequiredArgsConstructor
public class AdpExecutor {
    
    private final ExecutorService executorService;
    
    public List<EvaluatorResult> executeWithTimeout(
        List<ContextEvaluator> evaluators,
        AccessContext context,
        int timeoutMs
    ) {
        List<EvaluatorTask> tasks = new ArrayList<>();
        
        for (ContextEvaluator evaluator : evaluators) {
            if (evaluator.isEnabled()) {
                tasks.add(new EvaluatorTask(evaluator, context));
            }
        }
        
        if (tasks.isEmpty()) {
            log.warn("No enabled evaluators found");
            return List.of();
        }
        
        List<EvaluatorResult> results = new ArrayList<>();
        
        try {
            List<Future<EvaluatorResult>> futures = executorService.invokeAll(
                tasks, 
                timeoutMs, 
                TimeUnit.MILLISECONDS
            );
            
            for (int i = 0; i < futures.size(); i++) {
                Future<EvaluatorResult> future = futures.get(i);
                EvaluatorTask task = tasks.get(i);
                
                try {
                    if (future.isCancelled()) {
                        log.warn("Evaluator {} timeout", task.evaluator.getName());
                        results.add(createTimeoutResult(task.evaluator));
                    } else {
                        results.add(future.get());
                    }
                } catch (ExecutionException e) {
                    log.error("Evaluator {} error", task.evaluator.getName(), e.getCause());
                    results.add(createErrorResult(task.evaluator, e.getCause()));
                } catch (Exception e) {
                    log.error("Evaluator {} unexpected error", task.evaluator.getName(), e);
                    results.add(createErrorResult(task.evaluator, e));
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Executor interrupted", e);
            
            for (EvaluatorTask task : tasks) {
                results.add(createErrorResult(task.evaluator, e));
            }
        }
        
        return results;
    }
    
    private EvaluatorResult createTimeoutResult(ContextEvaluator evaluator) {
        return EvaluatorResult.builder()
            .evaluatorName(evaluator.getName())
            .riskScore(50.0)
            .confidence(0.0)
            .reason("Evaluation timeout")
            .recommendation(Recommendation.CHALLENGE)
            .status(EvaluatorStatus.TIMEOUT)
            .build();
    }
    
    private EvaluatorResult createErrorResult(ContextEvaluator evaluator, Throwable error) {
        return EvaluatorResult.builder()
            .evaluatorName(evaluator.getName())
            .riskScore(50.0)
            .confidence(0.0)
            .reason("Evaluation error: " + error.getMessage())
            .recommendation(Recommendation.CHALLENGE)
            .status(EvaluatorStatus.ERROR)
            .build();
    }
    
    private static class EvaluatorTask implements Callable<EvaluatorResult> {
        private final ContextEvaluator evaluator;
        private final AccessContext context;
        
        EvaluatorTask(ContextEvaluator evaluator, AccessContext context) {
            this.evaluator = evaluator;
            this.context = context;
        }
        
        @Override
        public EvaluatorResult call() {
            return evaluator.evaluate(context);
        }
    }
}
