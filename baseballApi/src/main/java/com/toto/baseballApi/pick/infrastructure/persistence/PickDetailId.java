package com.toto.baseballApi.pick.infrastructure.persistence;

import java.io.Serializable;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/** {@code @IdClass} companion for {@link PickDetailJpaEntity}'s composite primary key. */
@EqualsAndHashCode
@NoArgsConstructor
@AllArgsConstructor
class PickDetailId implements Serializable {

    private Integer pickMasterId;
    private Integer resultId;
}
