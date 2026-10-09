package me.leleawa.rotatedworld.api;

/**
 * 不能被静态引用的层（Flix、Fantom）在这里交出它们的实现。
 * Flix 的 {@code new} 表达式只能实现一个 Java 类型，所以每个服务一个方法。
 */
public final class Services {
    private static volatile MaterialRules materialRules;
    private static volatile RetentionPolicy retentionPolicy;
    private static volatile SpotFinder spotFinder;

    private Services() {}

    public static void provideMaterialRules(MaterialRules rules) {
        materialRules = rules;
    }

    public static void provideRetentionPolicy(RetentionPolicy policy) {
        retentionPolicy = policy;
    }

    public static void provideSpotFinder(SpotFinder finder) {
        spotFinder = finder;
    }

    public static MaterialRules materialRules() {
        return require(materialRules, "MaterialRules (Flix)");
    }

    public static RetentionPolicy retentionPolicy() {
        return require(retentionPolicy, "RetentionPolicy (Flix)");
    }

    public static SpotFinder spotFinder() {
        return require(spotFinder, "SpotFinder (Fantom)");
    }

    private static <T> T require(T service, String name) {
        if (service == null) throw new IllegalStateException(name + " 没有启动");
        return service;
    }
}
