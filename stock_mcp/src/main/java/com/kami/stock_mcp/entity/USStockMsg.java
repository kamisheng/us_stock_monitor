package com.kami.stock_mcp.entity;

import lombok.Data;

/**
 *
 * @Author kamiSheng
 * @Date 2026/9/19 21:06
 * @Description us_stock_monitor_dev
 * @Package com.kami.stock_web.entity
 */
@Data
public class USStockMsg {
    private String stockCode;
    private String title;
    private String link;
    private String pubDateNy;
    private String tags;
    private Long counts24Hour;
    private Long counts3Day;
    private Long counts1Week;
}
