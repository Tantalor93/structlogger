package cz.muni.fi;

import cz.muni.fi.annotation.VarContext;

public class Example {

    @VarContext(context = DefaultContext.class)
    private static StructLogger<DefaultContext> defaultLog = StructLogger.instance();

    @VarContext(context = BlockCacheContext.class)
    private static StructLogger<BlockCacheContext> structLog = StructLogger.instance();

    @VarContext(context = AnotherContext.class)
    private static StructLogger<AnotherContext> anotherContextStructLog = StructLogger.instance();

    public static void main(String[] args) throws Exception {

        int numCached = 0;
        int neededCached = 0;
        long blockId = 0;
        long datanodeUuid = 0;
        String reason = "reason";

        defaultLog.info("test {} string literal {}")
                .varDouble(1.2)
                .varBoolean(false)
                .log();

        defaultLog.info("cau3 {} {} {} mnau {} {} {}")
                .varDouble(1.2)
                .varBoolean(true)
                .varDouble(5.6)
                .varDouble(1.0)
                .varBoolean(false)
                .varBoolean(false)
                .log();

        structLog.warn("Block removal {} for dataNode from {} PENDING_UNCACHED - it was uncached by the dataNode.")
                .blockId(blockId)
                .dataNodeUuid(datanodeUuid)
                .log();

        structLog.warn("Block removal {} for dataNode from {} PENDING_UNCACHED - it was uncached by the dataNode.")
                .blockId(blockId)
                .dataNodeUuid(datanodeUuid)
                .log();

        structLog.warn("Block removal {} for dataNode from {} PENDING_UNCACHED - it was uncached by the dataNode.")
                .blockId(blockId)
                .dataNodeUuid(datanodeUuid)
                .log();

        structLog.info("Cannot cache block {} because {}")
                .blockId(blockId)
                .reason(reason)
                .log();

        structLog.info("Block {} removal for dataNode {} from PENDING_CACHED - we already have enough cached replicas {} {}")
                .blockId(blockId)
                .dataNodeUuid(datanodeUuid)
                .numCached(numCached)
                .neededCached(neededCached)
                .log();

        structLog.info("Block {} removal for dataNode {} from PENDING_UNCACHED - we do not have enough cached replicas {} {}")
                .blockId(blockId)
                .dataNodeUuid(datanodeUuid)
                .numCached(numCached)
                .neededCached(neededCached)
                .log();

        structLog.info("Block {} removal for dataNode from cachedBlocks - neededCached == 0, and pendingUncached and pendingCached are empty.")
                .blockId(blockId)
                .log();

        structLog.info("Block {} removal for dataNode {} from PENDING_UNCACHED - it was uncached by the dataNode.")
                .blockId(new Object().hashCode())
                .dataNodeUuid(someMethod())
                .log();

        structLog.error("errorek {}")
                .blockId(blockId)
                .log();

        anotherContextStructLog.info("ahoj {} ")
                .context("ahoj")
                .log();

        structLog.info("Block {} removal for dataNode {} from PENDING_UNCACHED - it was uncached by the dataNode.")
                .blockId(new Object().hashCode())
                .dataNodeUuid(someMethod())
                .log();

        structLog.info("ahojkya {}")
                .object(new Test("ahoj"))
                .log();
    }

    private static int someMethod() {
        return 0;
    }
}