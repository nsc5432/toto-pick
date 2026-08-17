package com.toto.baseballApi.baseballresult.presentation;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.toto.baseballApi.baseballresult.application.BaseballResultService;
import com.toto.baseballApi.baseballresult.presentation.dto.BaseballResultResponse;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/baseball-results")
@RequiredArgsConstructor
public class BaseballResultController {

    private final BaseballResultService baseballResultService;

    @GetMapping
    public Page<BaseballResultResponse> getBaseballResults(
            @PageableDefault(size = 20, sort = { "year", "round", "ymd", "tm" }, direction = Sort.Direction.DESC) Pageable pageable) {
        return baseballResultService.getBaseballResults(pageable).map(BaseballResultResponse::from);
    }
}
