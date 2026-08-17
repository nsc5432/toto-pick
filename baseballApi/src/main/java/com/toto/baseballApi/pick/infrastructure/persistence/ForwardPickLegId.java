package com.toto.baseballApi.pick.infrastructure.persistence;

import java.io.Serializable;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
class ForwardPickLegId implements Serializable {

    private Integer forwardPickId;
    private Integer legNo;
}
