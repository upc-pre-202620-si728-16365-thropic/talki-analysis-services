package com.thropic.talki.scoring.infrastructure.messaging.consumer;

import com.thropic.talki.scoring.domain.event.FillerAnalyzedEvent;
import com.thropic.talki.scoring.domain.event.ScoringCompletedEvent;
import com.thropic.talki.scoring.domain.model.ScoreCalculator;
import com.thropic.talki.scoring.domain.model.ScoreResult;
import com.thropic.talki.scoring.domain.model.VoiceScore;
import com.thropic.talki.scoring.infrastructure.messaging.producer.ScoringCompletedPublisher;
import com.thropic.talki.scoring.infrastructure.persistence.ScoreResultRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FillerAnalyzedConsumerTest {

    @Mock
    private ScoreCalculator scoreCalculator;

    @Mock
    private ScoringCompletedPublisher publisher;

    @Mock
    private ScoreResultRepository scoreResultRepository;

    @InjectMocks
    private FillerAnalyzedConsumer consumer;

    private FillerAnalyzedEvent inputEvent;
    private ScoringCompletedEvent calculatedEvent;

    @BeforeEach
    void setUp() {
        inputEvent = new FillerAnalyzedEvent();
        inputEvent.setSessionId("sess-1");
        inputEvent.setUserId("user-1");
        inputEvent.setTotalFillers(5);
        inputEvent.setFillersByType(Map.of("este", 5));
        inputEvent.setWordCount(300);
        inputEvent.setDurationSeconds(120);
        inputEvent.setTraceId("trace-1");
        VoiceScore scores = new VoiceScore(80, 75, 70, 85, 90);
        calculatedEvent = new ScoringCompletedEvent(
                "sess-1", "user-1", scores, 5, 0.08, 150, 120, "trace-1"
        );
    }

    @Test
    void handle_whenNewSession_shouldPersistAndPublish() {
        when(scoreResultRepository.findBySessionId("sess-1")).thenReturn(Optional.empty());
        when(scoreCalculator.calculate(inputEvent)).thenReturn(calculatedEvent);

        consumer.handle(inputEvent);

        verify(scoreResultRepository).save(any(ScoreResult.class));
        verify(publisher).publish(calculatedEvent);
    }

    @Test
    void handle_whenDuplicateEvent_shouldNotPersistOrPublishAgain() {
        ScoreResult existing = new ScoreResult("sess-1", "user-1",
                new VoiceScore(80, 75, 70, 85, 90), 5, 150, 0.08);
        when(scoreResultRepository.findBySessionId("sess-1")).thenReturn(Optional.of(existing));

        consumer.handle(inputEvent);

        verify(scoreCalculator, never()).calculate(any());
        verify(scoreResultRepository, never()).save(any());
        verify(publisher, never()).publish(any());
    }

    @Test
    void handle_whenRaceConditionOnSave_shouldTreatAsDuplicate() {
        // El check inicial pasa (otra réplica del consumer pasó por el mismo
        // gate al mismo tiempo). Cuando llega el save(), la unique constraint
        // sobre session_id explota: el consumer debe tratarlo como duplicado
        // y NO devolver el mensaje al DLX.
        when(scoreResultRepository.findBySessionId("sess-1")).thenReturn(Optional.empty());
        when(scoreCalculator.calculate(inputEvent)).thenReturn(calculatedEvent);
        when(scoreResultRepository.save(any(ScoreResult.class)))
                .thenThrow(new DataIntegrityViolationException("Unique violation on session_id"));

        consumer.handle(inputEvent);

        verify(scoreResultRepository).save(any(ScoreResult.class));
        verify(publisher, never()).publish(any());
    }

    @Test
    void handle_shouldPersistScoreResultWithCorrectDimensions() {
        when(scoreResultRepository.findBySessionId("sess-1")).thenReturn(Optional.empty());
        when(scoreCalculator.calculate(inputEvent)).thenReturn(calculatedEvent);

        consumer.handle(inputEvent);

        ArgumentCaptor<ScoreResult> captor = ArgumentCaptor.forClass(ScoreResult.class);
        verify(scoreResultRepository).save(captor.capture());
        ScoreResult saved = captor.getValue();

        assertThat(saved.getSessionId()).isEqualTo("sess-1");
        assertThat(saved.getUserId()).isEqualTo("user-1");
        assertThat(saved.getFluency()).isEqualTo(80);
        assertThat(saved.getClarity()).isEqualTo(75);
        assertThat(saved.getVolume()).isEqualTo(70);
        assertThat(saved.getVocabulary()).isEqualTo(85);
        assertThat(saved.getConfidence()).isEqualTo(90);
        assertThat(saved.getTotalFillers()).isEqualTo(5);
        assertThat(saved.getWordsPerMinute()).isEqualTo(150);
        assertThat(saved.getSilenceRatio()).isEqualTo(0.08);
    }
}
