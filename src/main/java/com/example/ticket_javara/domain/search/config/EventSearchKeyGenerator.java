package com.example.ticket_javara.domain.search.config;

import com.example.ticket_javara.domain.search.dto.request.SearchRequestDto;
import org.springframework.cache.interceptor.KeyGenerator;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import java.lang.reflect.Method;

@Component("eventSearchKeyGenerator")
public class EventSearchKeyGenerator implements KeyGenerator {
    @Override
    public Object generate(Object target, Method method, Object... params) {
        SearchRequestDto requestDto = (SearchRequestDto) params[0];
        Pageable pageable = (Pageable) params[1];
        
        return String.format("%s:%s:%s:%s:%s:%s:%d:%d:%s",
                requestDto.getKeyword(),
                requestDto.getCategory(),
                requestDto.getStartDate(),
                requestDto.getEndDate(),
                requestDto.getMinPrice(),
                requestDto.getMaxPrice(),
                pageable.getPageNumber(),
                pageable.getPageSize(),
                pageable.getSort().toString());
    }
}
