package com.kami.stock_web.utils;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;

public class GMTDateConverter {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    /**
     * 把 GMT 的 LocalDateTime 格式化成北京时间的字符串（yyyy-MM-dd HH:mm）。
     * <p>
     * 注意不要用 {@code localDateTime.toString()} —— 那会输出 ISO 格式（2026-09-19T14:30）带个 T。
     */
    public static String formatBeijing(LocalDateTime gmtDateTime) {
        if (gmtDateTime == null) {
            return "";
        }
        return gmtDateTime.atZone(ZoneId.of("GMT"))
                .withZoneSameInstant(ZoneId.of("Asia/Shanghai"))
                .format(FORMATTER);
    }

    /**
     * 把 GMT 的 LocalDateTime 格式化成纽约时间的字符串（yyyy-MM-dd HH:mm），自动处理夏令时。
     */
    public static String formatNewYork(LocalDateTime gmtDateTime) {
        if (gmtDateTime == null) {
            return "";
        }
        return gmtDateTime.atZone(ZoneId.of("GMT"))
                .withZoneSameInstant(ZoneId.of("America/New_York"))
                .format(FORMATTER);
    }

    public static String getNewYorkTime(Date externalDate) {
        return convertGmtToNewYork(externalDate).format(FORMATTER);
    }

    public static LocalDateTime convertGmtToNewYork(Date externalDate) {

        // 1. 将 Date 转为 Instant（UTC 时间点）
        Instant instant = externalDate.toInstant();

        // 2. 解释为 GMT 时间（其实 instant 就是 UTC/GMT）
        ZonedDateTime gmtTime = instant.atZone(ZoneId.of("GMT"));

        // 3. 转换为纽约时间（自动处理夏令时）
        ZonedDateTime newYorkTime = gmtTime.withZoneSameInstant(ZoneId.of("America/New_York"));

        return newYorkTime.toLocalDateTime();
    }

    public static String getBeijingTime(Date externalDate) {
        return convertGmtToBeijing(externalDate).format(FORMATTER);
    }
    public static LocalDateTime convertGmtToBeijing(Date externalDate) {

        // 1. 将 Date 转为 Instant（UTC 时间点）
        Instant instant = externalDate.toInstant();

        // 2. 解释为 GMT 时间（其实 instant 就是 UTC/GMT）
        ZonedDateTime gmtTime = instant.atZone(ZoneId.of("GMT"));

        // 3. 转换为纽约时间（自动处理夏令时）
        ZonedDateTime beijingTime = gmtTime.withZoneSameInstant(ZoneId.of("Asia/Shanghai"));

        return beijingTime.toLocalDateTime();
    }

    public static String getGMTTime(Date externalDate) {
        return convertGmt(externalDate).format(FORMATTER);
    }

    public static LocalDateTime convertGmt(Date externalDate) {

        // 1. 将 Date 转为 Instant（UTC 时间点）
        Instant instant = externalDate.toInstant();

        // 2. 解释为 GMT 时间（其实 instant 就是 UTC/GMT）
        ZonedDateTime gmtTime = instant.atZone(ZoneId.of("GMT"));

        return gmtTime.toLocalDateTime();
    }

    public static LocalDateTime minus24Hour(LocalDateTime dateTime) {
        if (dateTime == null) {
            throw new IllegalArgumentException("时间不能为空");
        }
        return dateTime.minusHours(24);
    }

    public static LocalDateTime minus3Day(LocalDateTime dateTime) {
        if (dateTime == null) {
            throw new IllegalArgumentException("时间不能为空");
        }
        return dateTime.minusDays(3);
    }

    public static LocalDateTime minus1Week(LocalDateTime dateTime) {
        if (dateTime == null) {
            throw new IllegalArgumentException("时间不能为空");
        }
        return dateTime.minusWeeks(1);
    }

    public static LocalDateTime plus1Minute(LocalDateTime dateTime) {
        if (dateTime == null) {
            throw new IllegalArgumentException("时间不能为空");
        }
        return dateTime.plusMinutes(1);
    }


}