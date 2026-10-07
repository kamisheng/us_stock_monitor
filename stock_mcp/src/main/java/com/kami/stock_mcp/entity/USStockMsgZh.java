package com.kami.stock_mcp.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import lombok.Data;

import java.time.LocalDateTime;

/**
 *
 * @Author kamiSheng
 * @Date 2026/9/19 21:04
 * @Description us_stock_monitor_dev
 * @Package com.kami.stock_web.entity
 */
@Data
public class USStockMsgZh {

    private String stockCode;
    private String titleZh;
    private String link;
    private String pubDateBj;
    private String tags;
    private Long counts24Hour;
    private Long counts3Day;
    private Long counts1Week;

}
