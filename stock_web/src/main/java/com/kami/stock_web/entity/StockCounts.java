package com.kami.stock_web.entity;

import lombok.Data;

/**
 *
 * @Author kamiSheng
 * @Date 2026/9/23 22:06
 * @Description us_stock_monitor_dev
 * @Package com.kami.stock_web.entity
 */
@Data
public class StockCounts {

    private Long counts24Hour;
    private Long counts3Day;
    private Long counts1Week;

}
