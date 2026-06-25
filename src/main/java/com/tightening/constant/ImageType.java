package com.tightening.constant;

import lombok.Getter;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Getter
public enum ImageType {
    ORIGINAL("original"),
    RENDERED("rendered"),
    THUMBNAIL("thumbnail");

    private final String value;
    private static final Map<String, ImageType> BY_VALUE =
        Arrays.stream(values()).collect(Collectors.toMap(ImageType::getValue, Function.identity()));

    ImageType(String value) { this.value = value; }

    public static Optional<ImageType> fromValue(String value) {
        return Optional.ofNullable(BY_VALUE.get(value));
    }
}
