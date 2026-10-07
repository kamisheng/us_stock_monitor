package com.kami.stock_mcp.entity.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 *
 * @Author kamiSheng
 * @Date 2026/10/5 21:31
 * @Description us_stock_monitor_dev
 * @Package com.kami.stock_mcp.entity.dto
 */
@Data
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class QueryCountsDTO {
    private String counts;
    private String startTime;
    private String endTime;

}
