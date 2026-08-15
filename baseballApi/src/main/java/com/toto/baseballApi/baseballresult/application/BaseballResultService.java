package com.toto.baseballApi.baseballresult.application;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import com.toto.baseballApi.baseballresult.domain.BaseballResult;
import com.toto.baseballApi.baseballresult.domain.BaseballResultRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class BaseballResultService {

    private final BaseballResultRepository baseballResultRepository;

    public Page<BaseballResult> getBaseballResults(Pageable pageable) {
        return baseballResultRepository.findAll(pageable);
    }
}
