package com.takibo.adp.core.engine;

import com.takibo.adp.api.EvaluatorStatus;
import com.takibo.adp.core.evaluator.ContextEvaluator;
import com.takibo.adp.core.evaluator.EvaluatorResult;
import com.takibo.adp.core.evaluator.Recommendation;
import com.takibo.adp.core.model.AccessContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AdpExecutorTest {

    private static final AccessContext CONTEXT = AccessContext.builder().subjectId("alice").build();

    private ExecutorService executorService;

    @BeforeEach
    void setUp() {
        executorService = Executors.newFixedThreadPool(4);
    }

    @AfterEach
    void tearDown() {
        executorService.shutdownNow();
    }

    @Test
    void returnsTheResultsOfEnabledEvaluators_inDeclarationOrder() {
        List<EvaluatorResult> results = new AdpExecutor(executorService).executeWithTimeout(List.of(
                StubEvaluator.ok("first", 0.5, 10.0, 0.9, Recommendation.ALLOW),
                StubEvaluator.disabled("skipped"),
                StubEvaluator.ok("second", 0.5, 20.0, 0.9, Recommendation.ALLOW)), CONTEXT, 1000);

        assertThat(results).extracting(EvaluatorResult::getEvaluatorName).containsExactly("first", "second");
        assertThat(results).allMatch(r -> r.getStatus() == EvaluatorStatus.OK);
    }

    @Test
    void noEnabledEvaluator_returnsAnEmptyList() {
        assertThat(new AdpExecutor(executorService)
                .executeWithTimeout(List.of(StubEvaluator.disabled("off")), CONTEXT, 1000)).isEmpty();
    }

    @Test
    void failingEvaluator_becomesAnErrorResult_withoutAffectingTheOthers() {
        ContextEvaluator failing = evaluator("failing", context -> {
            throw new IllegalStateException("profile store down");
        });

        List<EvaluatorResult> results = new AdpExecutor(executorService).executeWithTimeout(List.of(
                failing, StubEvaluator.ok("healthy", 0.5, 10.0, 0.9, Recommendation.ALLOW)), CONTEXT, 1000);

        EvaluatorResult error = results.get(0);
        assertThat(error.getEvaluatorName()).isEqualTo("failing");
        assertThat(error.getStatus()).isEqualTo(EvaluatorStatus.ERROR);
        assertThat(error.getRiskScore()).isEqualTo(50.0);
        assertThat(error.getConfidence()).isZero();
        assertThat(error.getRecommendation()).isEqualTo(Recommendation.CHALLENGE);
        assertThat(error.getReason()).isEqualTo("Evaluation error: profile store down");
        assertThat(results.get(1).getStatus()).isEqualTo(EvaluatorStatus.OK);
    }

    @Test
    void evaluatorExceedingTheTimeout_becomesATimeoutResult_andTheOthersArePartialResults() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        ContextEvaluator slow = evaluator("slow", context -> {
            try {
                release.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return StubEvaluator.result("slow", 0.0, 1.0, Recommendation.ALLOW, EvaluatorStatus.OK);
        });

        try {
            List<EvaluatorResult> results = new AdpExecutor(executorService).executeWithTimeout(List.of(
                    StubEvaluator.ok("fast", 0.5, 10.0, 0.9, Recommendation.ALLOW), slow), CONTEXT, 100);

            assertThat(results.get(0).getStatus()).isEqualTo(EvaluatorStatus.OK);
            EvaluatorResult timeout = results.get(1);
            assertThat(timeout.getEvaluatorName()).isEqualTo("slow");
            assertThat(timeout.getStatus()).isEqualTo(EvaluatorStatus.TIMEOUT);
            assertThat(timeout.getRiskScore()).isEqualTo(50.0);
            assertThat(timeout.getConfidence()).isZero();
            assertThat(timeout.getReason()).isEqualTo("Evaluation timeout");
            assertThat(timeout.getRecommendation()).isEqualTo(Recommendation.CHALLENGE);
        } finally {
            release.countDown();
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void interruptedWhileWaiting_everyEvaluatorBecomesAnError_andTheInterruptIsRestored() throws Exception {
        ExecutorService interrupted = mock(ExecutorService.class);
        when(interrupted.invokeAll(anyList(), anyLong(), eq(TimeUnit.MILLISECONDS)))
                .thenThrow(new InterruptedException("stop"));

        try {
            List<EvaluatorResult> results = new AdpExecutor(interrupted).executeWithTimeout(List.of(
                    StubEvaluator.ok("a", 0.5, 10.0, 0.9, Recommendation.ALLOW),
                    StubEvaluator.ok("b", 0.5, 10.0, 0.9, Recommendation.ALLOW)), CONTEXT, 100);

            assertThat(results).hasSize(2).allMatch(r -> r.getStatus() == EvaluatorStatus.ERROR
                    && r.getReason().equals("Evaluation error: stop"));
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void unexpectedFailureWhileReadingAResult_becomesAnErrorResult() throws Exception {
        ExecutorService broken = mock(ExecutorService.class);
        Future<EvaluatorResult> future = mock(Future.class);
        when(future.isCancelled()).thenReturn(false);
        when(future.get()).thenThrow(new IllegalStateException("broken future"));
        when(broken.invokeAll(anyList(), anyLong(), eq(TimeUnit.MILLISECONDS)))
                .thenReturn((List) List.of(future));

        List<EvaluatorResult> results = new AdpExecutor(broken).executeWithTimeout(List.of(
                StubEvaluator.ok("a", 0.5, 10.0, 0.9, Recommendation.ALLOW)), CONTEXT, 100);

        assertThat(results).singleElement().satisfies(r -> {
            assertThat(r.getStatus()).isEqualTo(EvaluatorStatus.ERROR);
            assertThat(r.getReason()).isEqualTo("Evaluation error: broken future");
        });
    }

    private static ContextEvaluator evaluator(String name, java.util.function.Function<AccessContext, EvaluatorResult> body) {
        return new ContextEvaluator() {
            @Override public String getName() { return name; }
            @Override public double getWeight() { return 0.5; }
            @Override public boolean isEnabled() { return true; }
            @Override public EvaluatorResult evaluate(AccessContext context) { return body.apply(context); }
        };
    }
}
