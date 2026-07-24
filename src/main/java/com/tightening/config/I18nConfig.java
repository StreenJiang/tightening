package com.tightening.config;

import com.tightening.i18n.Messages;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;

@Configuration
public class I18nConfig {

    private final MessageSource messageSource;

    public I18nConfig(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    @EventListener(ContextRefreshedEvent.class)
    public void init() {
        Messages.setMessageSource(messageSource);
    }
}
