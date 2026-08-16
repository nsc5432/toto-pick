package com.toto.baseballApi.pick.infrastructure.persistence;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

interface PickMasterJpaRepository extends JpaRepository<PickMasterJpaEntity, Integer> {

    List<PickMasterJpaEntity> findByOutputMoneyIsNull();

    List<PickMasterJpaEntity> findByUserNameAndYmdBetween(String userName, String ymdFrom, String ymdTo);
}
