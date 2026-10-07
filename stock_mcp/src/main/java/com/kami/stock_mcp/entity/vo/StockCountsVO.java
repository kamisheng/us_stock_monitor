package com.kami.stock_mcp.entity.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 *
 * @Author kamiSheng
 * @Date 2026/10/5 21:28
 * @Description us_stock_monitor_dev
 * @Package com.kami.stock_mcp.entity.vo
 */
@Data
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class StockCountsVO {
    private String stockCode;
    private String cnt;
}
