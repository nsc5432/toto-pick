package com.toto.baseballApi.pick.domain;

import java.util.List;

/** One betting slip — a combination of {@code combinedN} selections wagered together. */
public record PickSlip(List<PickSelection> selections) {
}
