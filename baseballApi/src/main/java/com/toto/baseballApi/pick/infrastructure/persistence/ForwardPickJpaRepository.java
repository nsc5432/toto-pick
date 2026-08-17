package com.toto.baseballApi.pick.infrastructure.persistence;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

interface ForwardPickJpaRepository extends JpaRepository<ForwardPickJpaEntity, Integer> {

    boolean existsByAlgorithmCodeAndUserNameAndYmdAndBucketAndSlipNo(
            String algorithmCode, String userName, String ymd, String bucket, Integer slipNo);

    List<ForwardPickJpaEntity> findByAlgorithmCodeAndUserNameOrderByYmdAscIdAsc(
            String algorithmCode, String userName);

    List<ForwardPickJpaEntity> findAllByOrderByYmdAscIdAsc();
}
