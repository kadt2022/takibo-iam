package com.takibo.outbox.spring.scheduling;

import com.takibo.outbox.spring.processor.OutboxProcessor;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class OutboxSchedulerTest {

    @Test
    void eachTickRunsOneProcessingPass() {
        OutboxProcessor processor = mock(OutboxProcessor.class);
        OutboxScheduler scheduler = new OutboxScheduler(processor);

        scheduler.tick();
        scheduler.tick();

        verify(processor, times(2)).processOnce();
    }
}
