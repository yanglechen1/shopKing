package com.bidking.enums;

/**
 * 道具备忘录
 * 玩家可在准备阶段购买，出价阶段使用（每次出价限用一个）
 */
public enum ItemType {

    DOUBLE_BID(2000, "翻倍卡", "本轮出价翻倍计算"),
    BID_INSURANCE(1500, "保险卡", "未中标时返还出价金币"),
    PEEK_TOTAL(1000, "透视卡", "查看仓库总价值"),
    EXTRA_3000(3000, "加价券", "出价额外增加3000"),
    HALF_DISCOUNT(2500, "截胡卡", "中标价减半");

    public final int price;
    public final String displayName;
    public final String description;

    ItemType(int price, String displayName, String description) {
        this.price = price;
        this.displayName = displayName;
        this.description = description;
    }
}
