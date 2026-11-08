package com.thropic.talki.scoring.infrastructure.messaging.consumer;

import com.thropic.talki.scoring.domain.event.FillerAnalyzedEvent;
import com.thropic.talki.scoring.domain.event.ScoringCompletedEvent;
import com.thropic.talki.scoring.domain.model.ScoreCalculator;
import com.thropic.talki.scoring.domain.model.ScoreResult;
import com.thropic.talki.scoring.domain.model.VoiceScore;
import com.thropic.talki.scoring.infrastructure.messaging.config.RabbitMQConfig;
import com.thropic.talki.scoring.infrastructure.messaging.producer.ScoringCompletedPublisher;
import com.thropic.talki.scoring.infrastructure.persistence.ScoreResultRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

@Component
public class FillerAnalyzedConsumer {

    private static final Logger log = LoggerFactory.getLogger(FillerAnalyzedConsumer.class);

    private final ScoreCalculator scoreCalculator;
    private final ScoringCompletedPublisher publisher;
    private final ScoreResultRepository scoreResultRepository;

    public FillerAnalyzedConsumer(ScoreCalculator scoreCalculator,
                                   ScoringCompletedPublisher publisher,
                                   ScoreResultRepository scoreResultRepository) {
        this.scoreCalculator = scoreCalculator;
        this.publisher = publisher;
        this.scoreResultRepository = scoreResultRepository;
    }

    // Sin @Transactional: save() confirma por sí mismo y el evento se publica después del
    // commit. Dentro de una transacción, capturar la violación de unicidad no evitaría el rollback.
    @RabbitListener(queues = RabbitMQConfig.QUEUE)
    public void handle(FillerAnalyzedEvent event) {
        if (isBlank(event.getSessionId()) || isBlank(event.getUserId())) {
            // Sin correlación no hay reintento útil: directo a la cola de fallos.
            throw new AmqpRejectAndDontRequeueException(
                    "fillers.analyzed sin sessionId/userId, eventId=" + event.getEventId());
        }
        log.info("[scoring-service] Received fillers.analyzed — sessionId={} totalFillers={}",
                event.getSessionId(), event.getTotalFillers());

        // Idempotencia at-least-once: chequeo barato antes del cálculo. La
        // unique constraint sobre session_id en BD actúa como red de seguridad
        // contra race conditions entre consumers concurrentes o redeliveries
        // simultáneas — se captura DataIntegrityViolationException y se trata
        // como duplicado en lugar de devolver el mensaje al DLX.
        if (scoreResultRepository.findBySessionId(event.getSessionId()).isPresent()) {
            log.warn("[scoring-service] Duplicate fillers.analyzed ignored — sessionId={}",
                    event.getSessionId());
            return;
        }

        ScoringCompletedEvent scoring = scoreCalculator.calculate(event);
        VoiceScore scores = scoring.getScores();

        ScoreResult persisted = new ScoreResult(
                event.getSessionId(),
                event.getUserId(),
                scores,
                event.getTotalFillers(),
                scoring.getWordsPerMinute(),
                scoring.getSilenceRatio()
        );
        try {
            scoreResultRepository.save(persisted);
        } catch (DataIntegrityViolationException race) {
            log.warn("[scoring-service] Race on session_id unique — treating as duplicate, sessionId={}",
                    event.getSessionId());
            return;
        }

        log.info("[scoring-service] Score persisted and published — sessionId={} overall={}",
                event.getSessionId(), scoring.getOverallScore());

        publisher.publish(scoring);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
