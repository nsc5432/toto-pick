package com.toto.baseballApi.baseballresult.infrastructure.persistence;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import com.toto.baseballApi.baseballresult.domain.BaseballResult;
import com.toto.baseballApi.baseballresult.domain.BaseballResultRepository;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
class BaseballResultRepositoryImpl implements BaseballResultRepository {

    private final BaseballResultJpaRepository baseballResultJpaRepository;

    @Override
    public Page<BaseballResult> findAll(Pageable pageable) {
        return baseballResultJpaRepository.findAll(pageable).map(this::toDomain);
    }

    private BaseballResult toDomain(BaseballResultJpaEntity entity) {
        return new BaseballResult(
                entity.getId(),
                entity.getYear(),
                entity.getRound(),
                entity.getTournament(),
                entity.getYmd(),
                entity.getTm(),
                entity.getHome(),
                entity.getAway(),
                entity.getGameType(),
                entity.getCond(),
                entity.getRes1(),
                entity.getRes2(),
                entity.getTotalResult(),
                entity.getTotalDiv());
    }
}
