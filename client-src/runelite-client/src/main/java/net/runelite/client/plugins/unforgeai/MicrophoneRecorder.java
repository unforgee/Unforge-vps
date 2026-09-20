package net.runelite.client.plugins.unforgeai;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.TargetDataLine;

final class MicrophoneRecorder implements AutoCloseable
{
	private static final float SAMPLE_RATE = 16_000f;
	private static final int SAMPLE_SIZE_BITS = 16;
	private static final int CHANNELS = 1;
	private static final int FRAME_SIZE = 2;
	private static final int MAX_SECONDS = 30;
	private static final int MAX_PCM_BYTES = (int) SAMPLE_RATE * FRAME_SIZE * MAX_SECONDS;
	private static final AudioFormat FORMAT = new AudioFormat(
		AudioFormat.Encoding.PCM_SIGNED,
		SAMPLE_RATE,
		SAMPLE_SIZE_BITS,
		CHANNELS,
		FRAME_SIZE,
		SAMPLE_RATE,
		false
	);

	private final Object lock = new Object();
	private final ByteArrayOutputStream pcm = new ByteArrayOutputStream(MAX_PCM_BYTES);
	private volatile boolean recording;
	private boolean sessionOpen;
	private TargetDataLine line;
	private Thread captureThread;

	void start() throws LineUnavailableException
	{
		synchronized (lock)
		{
			if (sessionOpen)
			{
				return;
			}
			DataLine.Info info = new DataLine.Info(TargetDataLine.class, FORMAT);
			if (!AudioSystem.isLineSupported(info))
			{
				throw new LineUnavailableException("No microphone supports 16 kHz mono PCM input");
			}
			TargetDataLine openedLine = (TargetDataLine) AudioSystem.getLine(info);
			openedLine.open(FORMAT);
			pcm.reset();
			line = openedLine;
			sessionOpen = true;
			recording = true;
			openedLine.start();
			captureThread = new Thread(() -> capture(openedLine), "unforge-ai-microphone");
			captureThread.setDaemon(true);
			captureThread.start();
		}
	}

	byte[] stop()
	{
		TargetDataLine currentLine;
		Thread currentThread;
		synchronized (lock)
		{
			currentLine = line;
			currentThread = captureThread;
			recording = false;
			sessionOpen = false;
			line = null;
			captureThread = null;
		}
		if (currentLine != null)
		{
			currentLine.stop();
			currentLine.close();
		}
		if (currentThread != null && currentThread != Thread.currentThread())
		{
			try
			{
				currentThread.join(1000L);
			}
			catch (InterruptedException ex)
			{
				Thread.currentThread().interrupt();
			}
		}
		synchronized (lock)
		{
			return toWav(pcm.toByteArray());
		}
	}

	boolean isRecording()
	{
		synchronized (lock)
		{
			return sessionOpen;
		}
	}

	@Override
	public void close()
	{
		stop();
	}

	private void capture(TargetDataLine currentLine)
	{
		byte[] buffer = new byte[4096];
		while (recording)
		{
			int count = currentLine.read(buffer, 0, buffer.length);
			if (count <= 0)
			{
				continue;
			}
			synchronized (lock)
			{
				int remaining = MAX_PCM_BYTES - pcm.size();
				pcm.write(buffer, 0, Math.min(count, remaining));
				if (pcm.size() >= MAX_PCM_BYTES)
				{
					recording = false;
				}
			}
		}
	}

	private static byte[] toWav(byte[] pcmBytes)
	{
		ByteArrayOutputStream output = new ByteArrayOutputStream(44 + pcmBytes.length);
		try
		{
			writeAscii(output, "RIFF");
			writeLittleEndianInt(output, 36 + pcmBytes.length);
			writeAscii(output, "WAVE");
			writeAscii(output, "fmt ");
			writeLittleEndianInt(output, 16);
			writeLittleEndianShort(output, (short) 1);
			writeLittleEndianShort(output, (short) CHANNELS);
			writeLittleEndianInt(output, (int) SAMPLE_RATE);
			writeLittleEndianInt(output, (int) SAMPLE_RATE * FRAME_SIZE);
			writeLittleEndianShort(output, (short) FRAME_SIZE);
			writeLittleEndianShort(output, (short) SAMPLE_SIZE_BITS);
			writeAscii(output, "data");
			writeLittleEndianInt(output, pcmBytes.length);
			output.write(pcmBytes);
			return output.toByteArray();
		}
		catch (IOException ex)
		{
			throw new IllegalStateException("Could not create WAV dictation payload", ex);
		}
	}

	private static void writeAscii(ByteArrayOutputStream output, String value) throws IOException
	{
		output.write(value.getBytes(java.nio.charset.StandardCharsets.US_ASCII));
	}

	private static void writeLittleEndianInt(ByteArrayOutputStream output, int value)
	{
		output.write(value & 0xff);
		output.write((value >>> 8) & 0xff);
		output.write((value >>> 16) & 0xff);
		output.write((value >>> 24) & 0xff);
	}

	private static void writeLittleEndianShort(ByteArrayOutputStream output, short value)
	{
		output.write(value & 0xff);
		output.write((value >>> 8) & 0xff);
	}
}
