package com.toto.baseballApi.pick.infrastructure.persistence;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

interface PickMasterJpaRepository extends JpaRepository<PickMasterJpaEntity, Integer> {

    List<PickMasterJpaEntity> findByOutputMoneyIsNull();

    List<PickMasterJpaEntity> findByUserNameAndAlgorithmCodeInAndYmdBetween(
            String userName, List<String> algorithmCodes, String ymdFrom, String ymdTo);

    List<PickMasterJpaEntity> findByUserNameAndAlgorithmCodeIsNotNullAndYmdBetween(
            String userName, String ymdFrom, String ymdTo);
}
