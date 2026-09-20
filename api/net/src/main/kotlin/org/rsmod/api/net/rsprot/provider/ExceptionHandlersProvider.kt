package org.rsmod.api.net.rsprot.provider

import io.netty.channel.ChannelHandlerContext
import net.rsprot.protocol.api.ChannelExceptionHandler
import net.rsprot.protocol.api.IncomingGameMessageConsumerExceptionHandler
import net.rsprot.protocol.api.Session
import net.rsprot.protocol.api.handlers.ExceptionHandlers
import net.rsprot.protocol.message.IncomingGameMessage
import org.rsmod.game.entity.Player

object ExceptionHandlersProvider {
    fun provide(): ExceptionHandlers<Player> {
        val channelHandler = ChannelExceptionHandler { _: ChannelHandlerContext, cause: Throwable ->
            System.err.println(
                "DISCONNECT DIAGNOSTIC channel exception: ${cause.stackTraceToString()}"
            )
            throw cause
        }
        val messageHandler =
            IncomingGameMessageConsumerExceptionHandler {
                _: Session<Player>,
                message: IncomingGameMessage,
                throwable: Throwable ->
                System.err.println(
                    "DISCONNECT DIAGNOSTIC packet decode/handler exception: " +
                        "packet=${message::class.qualifiedName}\n${throwable.stackTraceToString()}"
                )
                throw throwable
            }
        return ExceptionHandlers(channelHandler, messageHandler)
    }
}
