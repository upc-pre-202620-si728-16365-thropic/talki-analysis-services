package com.thropic.talki.scoring.infrastructure.messaging.consumer;

import com.thropic.talki.scoring.domain.event.FillerAnalyzedEvent;
import com.thropic.talki.scoring.domain.event.ScoringCompletedEvent;
import com.thropic.talki.scoring.domain.model.ScoreCalculator;
import com.thropic.talki.scoring.domain.model.ScoreResult;
import com.thropic.talki.scoring.infrastructure.messaging.config.RabbitMQConfig;
import com.thropic.talki.scoring.infrastructure.messaging.producer.ScoringCompletedPublisher;
import com.thropic.talki.scoring.infrastructure.persistence.ScoreResultRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import java.util.Optional;

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

        // Idempotencia at-least-once: solo una práctica aún pendiente (o desconocida) se evalúa.
        // La unique constraint sobre session_id actúa como red de seguridad contra consumers
        // concurrentes o redeliveries simultáneas.
        Optional<ScoreResult> existing = scoreResultRepository.findBySessionId(event.getSessionId());
        if (existing.isPresent() && !existing.get().isPending()) {
            log.warn("[scoring-service] Duplicate fillers.analyzed ignored — sessionId={}",
                    event.getSessionId());
            return;
        }

        ScoreResult result = existing.orElseGet(
                () -> ScoreResult.pending(event.getSessionId(), event.getUserId()));
        result.recordEvidence(event.getTotalFillers(), event.getFillersByType(), event.getWordCount(),
                event.getWordsPerMinute(), event.getDurationSeconds(),
                event.getSilenceRatio(), event.getVolumeRmsAvg());
        ScoringCompletedEvent scoring = evaluate(event, result);

        try {
            scoreResultRepository.save(result);
        } catch (DataIntegrityViolationException race) {
            // Si lo que ganó la carrera es el registro PENDING, este análisis sigue sin
            // aplicarse: se reintenta. Si ya hay un resultado, es un duplicado.
            if (scoreResultRepository.findBySessionId(event.getSessionId())
                    .filter(ScoreResult::isPending).isPresent()) {
                throw race;
            }
            log.warn("[scoring-service] Race on session_id unique — treating as duplicate, sessionId={}",
                    event.getSessionId());
            return;
        }

        if (scoring == null) {
            log.warn("[scoring-service] Sin puntuación — sessionId={} state={}",
                    event.getSessionId(), result.getAnalysisState());
            return;
        }
        log.info("[scoring-service] Score persisted and published — sessionId={} overall={}",
                event.getSessionId(), scoring.getOverallScore());
        publisher.publish(scoring);
    }

    /** Devuelve el evento a publicar, o null si esta práctica no debe tener puntuación. */
    private ScoringCompletedEvent evaluate(FillerAnalyzedEvent event, ScoreResult result) {
        if (event.getWordCount() <= 0) {
            result.markInsufficientEvidence();
            return null;
        }
        try {
            ScoringCompletedEvent scoring = scoreCalculator.calculate(event);
            result.complete(scoring.getScores(), scoring.getWordsPerMinute(), scoring.getSilenceRatio());
            return scoring;
        } catch (IllegalArgumentException invalidMetrics) {
            // Reintentar no cambiaría el resultado: se registra el fallo, sin puntuación.
            log.error("[scoring-service] Métricas inválidas — sessionId={}", event.getSessionId(), invalidMetrics);
            result.markFailed();
            return null;
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
