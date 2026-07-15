package com.brainos.dashboard;

import java.time.LocalDate;

public record DailyCount(LocalDate date, long count) {}
