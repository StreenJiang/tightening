package com.tightening.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tightening.dto.BaseDTO;
import com.tightening.entity.BaseEntity;
import org.springframework.beans.BeanUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public final class Converter {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public static <E extends BaseEntity, T extends BaseDTO> T entity2Dto(E entity, Supplier<T> supplier) {
        T dto = supplier.get();
        BeanUtils.copyProperties(entity, dto);
        return dto;
    }

    public static <E extends BaseEntity, T extends BaseDTO> List<T> entity2Dto(List<E> entities, Supplier<T> supplier) {
        List<T> result = new ArrayList<>();
        entities.forEach(entity -> {
            T dto = supplier.get();
            BeanUtils.copyProperties(entity, dto);
            result.add(dto);
        });
        return result;
    }

    public static <E extends BaseEntity, T extends BaseDTO> E dto2Entity(T dto, Supplier<E> supplier) {
        E entity = supplier.get();
        BeanUtils.copyProperties(dto, entity);
        return entity;
    }

    public static <E extends BaseEntity, T extends BaseDTO> List<E> dto2Entity(List<T> dtos, Supplier<E> supplier) {
        List<E> result = new ArrayList<>();
        dtos.forEach(dto -> {
            E entity = supplier.get();
            BeanUtils.copyProperties(dto, entity);
            result.add(entity);
        });
        return result;
    }

    public static <T> String fromList(List<T> list) {
        try {
            return OBJECT_MAPPER.writeValueAsString(list);
        } catch (Exception e) {
            throw new RuntimeException("Convert from list to json failed...", e);
        }
    }

    public static <T> List<T> fromJsonToList(String json, Class<T> elementType) {
        if (json == null || json.isBlank()) {
            return new ArrayList<>();
        }
        try {
            return OBJECT_MAPPER.readValue(json, OBJECT_MAPPER.getTypeFactory().constructCollectionType(List.class, elementType));
        } catch (Exception e) {
            throw new RuntimeException("Convert from json to list failed...", e);
        }
    }
}
