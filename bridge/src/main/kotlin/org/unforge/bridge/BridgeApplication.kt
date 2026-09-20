package org.unforge.bridge

import org.slf4j.LoggerFactory

fun main() {
    val logger = LoggerFactory.getLogger("UnforgeBridgeApplication")
    val config = BridgeConfig.fromEnvironment()
    val bridge = UnforgeBridge(config)
    Runtime.getRuntime().addShutdownHook(Thread({ bridge.close() }, "unforge-bridge-shutdown"))
    bridge.start()
    logger.info("Unforge text bridge is running; press Ctrl+C to stop")
    try {
        Thread.currentThread().join()
    } catch (_: InterruptedException) {
        Thread.currentThread().interrupt()
        bridge.close()
    }
}
