package net.runelite.client.plugins.unforgeai;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class UnforgeAiClientTest
{
	@Test
	public void onlyLocalPlainWebSocketEndpointsAreAccepted()
	{
		assertTrue(UnforgeAiClient.isLoopbackWebSocketUrl("ws://127.0.0.1:18901/ws"));
		assertTrue(UnforgeAiClient.isLoopbackWebSocketUrl("ws://localhost:18901/ws"));
		assertFalse(UnforgeAiClient.isLoopbackWebSocketUrl("wss://127.0.0.1:18901/ws"));
		assertFalse(UnforgeAiClient.isLoopbackWebSocketUrl("ws://192.0.2.10:18901/ws"));
		assertFalse(UnforgeAiClient.isLoopbackWebSocketUrl("ws://127.0.0.1:18901/ws?remote=true"));
	}

	@Test
	public void onlyLocalPlainHttpGatewaysAreAccepted()
	{
		assertTrue(UnforgeAiClient.isLoopbackHttpUrl("http://127.0.0.1:18900"));
		assertTrue(UnforgeAiClient.isLoopbackHttpUrl("http://localhost:18900"));
		assertFalse(UnforgeAiClient.isLoopbackHttpUrl("https://127.0.0.1:18900"));
		assertFalse(UnforgeAiClient.isLoopbackHttpUrl("http://192.0.2.10:18900"));
		assertFalse(UnforgeAiClient.isLoopbackHttpUrl("http://127.0.0.1:18900?remote=true"));
	}
}
