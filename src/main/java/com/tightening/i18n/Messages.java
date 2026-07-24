package com.tightening.i18n;

import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Locale;

public final class Messages {

    private static volatile MessageSource messageSource;

    private Messages() {}

    public static void setMessageSource(MessageSource ms) {
        messageSource = ms;
    }

    public static String get(String key, Object... args) {
        return get(resolveLocale(), key, args);
    }

    public static String get(Locale locale, String key, Object... args) {
        if (messageSource == null) {
            return "[[" + key + "]]";
        }
        return messageSource.getMessage(key, args, "[[" + key + "]]", locale);
    }

    private static Locale resolveLocale() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes) {
            return LocaleContextHolder.getLocale();
        }
        return Locale.SIMPLIFIED_CHINESE;
    }
}
