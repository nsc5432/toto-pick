package com.toto.baseballApi.pick.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "pick_dtl")
@IdClass(PickDetailId.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PACKAGE)
class PickDetailJpaEntity {

    @Id
    @Column(name = "pick_mstr_id")
    private Integer pickMasterId;

    @Id
    @Column(name = "result_id")
    private Integer resultId;

    @Column(name = "total_result", nullable = false, length = 5)
    private String totalResult;
}
