package com.documind.processing;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Durable topology with a dead letter queue so failed deliveries are never lost.
 */
@Configuration
@ConditionalOnProperty(name = "documind.processing.mode", havingValue = "rabbit")
public class RabbitProcessingConfig {

    public static final String EXCHANGE = "documind.processing.exchange";
    public static final String ROUTING_KEY = "document.process";
    public static final String QUEUE = "documind.document.processing";
    public static final String DEAD_LETTER_EXCHANGE = "documind.processing.dlx";
    public static final String DEAD_LETTER_QUEUE = "documind.document.processing.dlq";
    public static final String DEAD_LETTER_ROUTING_KEY = "document.dead";

    @Bean
    Queue documentProcessingQueue() {
        return QueueBuilder.durable(QUEUE)
                .deadLetterExchange(DEAD_LETTER_EXCHANGE)
                .deadLetterRoutingKey(DEAD_LETTER_ROUTING_KEY)
                .build();
    }

    @Bean
    Queue documentProcessingDeadLetterQueue() {
        return QueueBuilder.durable(DEAD_LETTER_QUEUE).build();
    }

    @Bean
    DirectExchange documentProcessingExchange() {
        return new DirectExchange(EXCHANGE, true, false);
    }

    @Bean
    DirectExchange documentProcessingDeadLetterExchange() {
        return new DirectExchange(DEAD_LETTER_EXCHANGE, true, false);
    }

    @Bean
    Binding documentProcessingBinding() {
        return BindingBuilder.bind(documentProcessingQueue())
                .to(documentProcessingExchange())
                .with(ROUTING_KEY);
    }

    @Bean
    Binding documentProcessingDeadLetterBinding() {
        return BindingBuilder.bind(documentProcessingDeadLetterQueue())
                .to(documentProcessingDeadLetterExchange())
                .with(DEAD_LETTER_ROUTING_KEY);
    }

    @Bean
    Jackson2JsonMessageConverter processingMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
