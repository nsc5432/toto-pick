package com.toto.baseballApi.baseballresult.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface BaseballResultRepository {

    Page<BaseballResult> findAll(Pageable pageable);
}
