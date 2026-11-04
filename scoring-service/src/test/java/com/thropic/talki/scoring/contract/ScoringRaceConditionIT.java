package com.thropic.talki.scoring.contract;

import com.thropic.talki.scoring.domain.event.FillerAnalyzedEvent;
import com.thropic.talki.scoring.domain.model.ScoreResult;
import com.thropic.talki.scoring.domain.model.VoiceScore;
import com.thropic.talki.scoring.infrastructure.messaging.consumer.FillerAnalyzedConsumer;
import com.thropic.talki.scoring.infrastructure.persistence.ScoreResultRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doReturn;

/**
 * Dos réplicas pasan a la vez la comprobación de duplicado: la segunda choca con
 * la restricción única de session_id al guardar y debe tratarse como duplicado,
 * sin error y sin devolver el mensaje.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class ScoringRaceConditionIT extends AbstractRabbitIT {

    @SpyBean
    private ScoreResultRepository scoreResultRepository;

    @Autowired
    private FillerAnalyzedConsumer consumer;

    @Test
    void uniqueViolationOnSave_shouldBeTreatedAsDuplicateWithoutFailingTheMessage() {
        String sessionId = "session-" + UUID.randomUUID();
        scoreResultRepository.save(new ScoreResult(sessionId, "user-race",
                new VoiceScore(80, 80, 80, 80, 80), 1, 130, 0.08));
        doReturn(Optional.empty()).when(scoreResultRepository).findBySessionId(sessionId);

        FillerAnalyzedEvent event = new FillerAnalyzedEvent();
        event.setSessionId(sessionId);
        event.setUserId("user-race");
        event.setTotalFillers(1);
        event.setFillersByType(Map.of("este", 1));
        event.setWordCount(100);
        event.setWordsPerMinute(130);
        event.setSilenceRatio(0.08);
        event.setVolumeRmsAvg(0.6);
        event.setDurationSeconds(60);

        assertThatCode(() -> consumer.handle(event)).doesNotThrowAnyException();

        assertThat(scoreResultRepository.findAll().stream()
                .filter(r -> r.getSessionId().equals(sessionId)))
                .hasSize(1);
    }
}
