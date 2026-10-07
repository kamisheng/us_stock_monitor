package com.kami.stock_web.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.time.LocalDateTime;

@Data
@ToString
@NoArgsConstructor
@AllArgsConstructor
@TableName("us_stock_rss")
public class USStockRss {

    /** 主键：库里是 varchar(64)，插入时由 MyBatis-Plus 生成雪花串 */
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private String id;
    @TableField("stock_code")
    private String stockCode;
    @TableField("title")
    private String title;
    @TableField("title_zh")
    private String titleZh;
    @TableField("link")
    private String link;
    @TableField("pub_date_gmt")
    private LocalDateTime pubDateGmt;
    @TableField("pub_date_bj")
    private LocalDateTime pubDateBj;
    @TableField("tags")
    private String tags;

    /** 英文原始标签（如 low float, penny stock）；tags 列存对应的中文（如 ⚖️ 低浮动） */
    @TableField("tags_en")
    private String tagsEn;
}
