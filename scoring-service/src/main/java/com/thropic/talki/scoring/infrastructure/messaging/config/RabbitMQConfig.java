package com.thropic.talki.scoring.infrastructure.messaging.config;

import org.springframework.amqp.rabbit.config.RetryInterceptorBuilder;
import org.springframework.amqp.rabbit.retry.RejectAndDontRequeueRecoverer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    public static final String EXCHANGE         = "talki.events";
    public static final String DLX              = "talki.dlx";
    public static final String QUEUE_DLQ        = "scoring.fillers-ready.dlq";
    public static final String QUEUE            = "scoring.fillers-ready";
    public static final String ROUTING_KEY      = "fillers.analyzed";
    public static final String OUT_ROUTING      = "scoring.completed";
    public static final String QUEUE_SESSIONS   = "scoring.session-finalized";
    public static final String ROUTING_SESSIONS = "session.live.finalized";

    @Bean
    public TopicExchange talkiEventsExchange() {
        return ExchangeBuilder.topicExchange(EXCHANGE).durable(true).build();
    }

    @Bean
    public DirectExchange deadLetterExchange() {
        return ExchangeBuilder.directExchange(DLX).durable(true).build();
    }

    @Bean
    public Queue scoringQueue() {
        return QueueBuilder.durable(QUEUE)
                .withArgument("x-dead-letter-exchange", DLX)
                .build();
    }

    @Bean
    public Binding scoringBinding(Queue scoringQueue, TopicExchange talkiEventsExchange) {
        return BindingBuilder.bind(scoringQueue).to(talkiEventsExchange).with(ROUTING_KEY);
    }

    /** Sus fallos van a la misma cola de fallos, no a la de filler-detection-service. */
    @Bean
    public Queue sessionFinalizedQueue() {
        return QueueBuilder.durable(QUEUE_SESSIONS)
                .withArgument("x-dead-letter-exchange", DLX)
                .withArgument("x-dead-letter-routing-key", ROUTING_KEY)
                .build();
    }

    @Bean
    public Binding sessionFinalizedBinding(Queue sessionFinalizedQueue, TopicExchange talkiEventsExchange) {
        return BindingBuilder.bind(sessionFinalizedQueue).to(talkiEventsExchange).with(ROUTING_SESSIONS);
    }

    @Bean
    public Queue deadLetterQueue() {
        return QueueBuilder.durable(QUEUE_DLQ).build();
    }

    /** Un fallo persistente queda aquí, visible, en lugar de perderse en el DLX. */
    @Bean
    public Binding deadLetterBinding(DirectExchange deadLetterExchange) {
        return BindingBuilder.bind(deadLetterQueue()).to(deadLetterExchange).with(ROUTING_KEY);
    }

    @Bean
    public Jackson2JsonMessageConverter messageConverter() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        return new Jackson2JsonMessageConverter(mapper);
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(messageConverter());
        return template;
    }

    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(messageConverter());
        // Fallo transitorio: 3 intentos. Fallo persistente: a la cola de fallos, sin reencolar.
        factory.setDefaultRequeueRejected(false);
        factory.setAdviceChain(RetryInterceptorBuilder.stateless()
                .maxAttempts(3)
                .backOffOptions(200, 2.0, 1000)
                .recoverer(new RejectAndDontRequeueRecoverer())
                .build());
        return factory;
    }
}
