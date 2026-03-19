package com.tightening.util;

import com.tightening.dto.BaseDTO;
import com.tightening.entity.BaseEntity;
import org.springframework.beans.BeanUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class Converter {
    public static <E extends BaseEntity, T extends BaseDTO> T entity2Dto(E entity,
                                                                         Supplier<T> supplier) {
        T dto = supplier.get();
        BeanUtils.copyProperties(entity, dto);
        return dto;
    }

    public static <E extends BaseEntity, T extends BaseDTO> List<T> entity2Dto(List<E> entities,
                                                                               Supplier<T> supplier) {
        List<T> result = new ArrayList<>();
        entities.forEach(entity -> {
            T dto = supplier.get();
            BeanUtils.copyProperties(entity, dto);
            result.add(dto);
        });
        return result;
    }

    public static <E extends BaseEntity, T extends BaseDTO> E dto2Entity(T dto,
                                                                         Supplier<E> supplier) {
        E entity = supplier.get();
        BeanUtils.copyProperties(dto, entity);
        return entity;
    }

    public static <E extends BaseEntity, T extends BaseDTO> List<E> dto2Entity(List<T> dtos,
                                                                               Supplier<E> supplier) {
        List<E> result = new ArrayList<>();
        dtos.forEach(dto -> {
            E entity = supplier.get();
            BeanUtils.copyProperties(dto, entity);
            result.add(entity);
        });
        return result;
    }
}
