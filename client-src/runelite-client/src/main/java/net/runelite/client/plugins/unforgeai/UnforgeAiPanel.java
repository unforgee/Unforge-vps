package net.runelite.client.plugins.unforgeai;

import com.google.gson.JsonObject;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;

final class UnforgeAiPanel extends PluginPanel implements UnforgeAiClient.Listener
{
	private final JLabel connectionLabel = new JLabel("Codex: offline");
	private final JLabel taskLabel = new JLabel("Ready");
	private final JTextArea transcript = new JTextArea();
	private final JTextArea input = new JTextArea();
	private final JButton sendButton = new JButton("Send");
	private final JButton cancelButton = new JButton("Cancel");
	private final JButton reconnectButton = new JButton("Reconnect");
	private final JButton dictateButton = new JButton("Sanele");
	private final MicrophoneRecorder recorder = new MicrophoneRecorder();
	private final ExecutorService dictationExecutor = Executors.newSingleThreadExecutor(r ->
	{
		Thread thread = new Thread(r, "unforge-ai-dictation");
		thread.setDaemon(true);
		return thread;
	});

	private UnforgeAiClient client;
	private boolean initialized;
	private volatile boolean dictationBusy;

	void init(UnforgeAiClient client)
	{
		if (initialized)
		{
			return;
		}
		initialized = true;
		this.client = client;
		setLayout(new BorderLayout(0, 8));
		setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
		setBackground(ColorScheme.DARK_GRAY_COLOR);

		JPanel header = new JPanel(new BorderLayout(0, 4));
		header.setOpaque(false);
		JLabel title = new JLabel("Unforge Codex");
		title.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		title.setFont(title.getFont().deriveFont(Font.BOLD, 14f));
		header.add(title, BorderLayout.NORTH);
		connectionLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		taskLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		JPanel state = new JPanel(new GridLayout(2, 1));
		state.setOpaque(false);
		state.add(connectionLabel);
		state.add(taskLabel);
		header.add(state, BorderLayout.CENTER);
		add(header, BorderLayout.NORTH);

		transcript.setEditable(false);
		transcript.setLineWrap(true);
		transcript.setWrapStyleWord(true);
		transcript.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		transcript.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		transcript.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
		JScrollPane transcriptScroll = new JScrollPane(transcript);
		transcriptScroll.setBorder(BorderFactory.createLineBorder(ColorScheme.MEDIUM_GRAY_COLOR));
		add(transcriptScroll, BorderLayout.CENTER);

		input.setRows(3);
		input.setLineWrap(true);
		input.setWrapStyleWord(true);
		input.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		input.setCaretColor(ColorScheme.LIGHT_GRAY_COLOR);
		input.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		input.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
		input.getInputMap().put(KeyStroke.getKeyStroke("control ENTER"), "send-task");
		input.getActionMap().put("send-task", new AbstractAction()
		{
			@Override
			public void actionPerformed(java.awt.event.ActionEvent event)
			{
				submitInput();
			}
		});
		JScrollPane inputScroll = new JScrollPane(input);
		inputScroll.setBorder(BorderFactory.createLineBorder(ColorScheme.MEDIUM_GRAY_COLOR));
		inputScroll.setPreferredSize(new Dimension(PluginPanel.PANEL_WIDTH - 20, 74));

		sendButton.addActionListener(event -> submitInput());
		cancelButton.addActionListener(event ->
		{
			if (client != null)
			{
				client.cancelActiveTask();
			}
		});
		reconnectButton.addActionListener(event ->
		{
			if (client != null)
			{
				client.reconnect();
			}
		});
		dictateButton.addActionListener(event -> toggleDictation());
		JPanel actions = new JPanel(new GridLayout(2, 2, 6, 6));
		actions.setOpaque(false);
		actions.add(sendButton);
		actions.add(dictateButton);
		actions.add(cancelButton);
		actions.add(reconnectButton);

		JPanel composer = new JPanel(new BorderLayout(0, 6));
		composer.setOpaque(false);
		composer.add(inputScroll, BorderLayout.CENTER);
		composer.add(actions, BorderLayout.SOUTH);
		add(composer, BorderLayout.SOUTH);

		append("System", "Local Codex connection. Ctrl+Enter sends; Sanele records and sends.");
	}

	@Override
	public void onConnectionState(String state)
	{
		onEdt(() ->
		{
			connectionLabel.setText("Codex: " + state);
			if ("connected".equals(state))
			{
				taskLabel.setText("Ready");
			}
		});
	}

	@Override
	public void onMessage(JsonObject message)
	{
		String type = value(message, "type");
		String status = value(message, "status");
		String phase = value(message, "phase");
		String text = value(message, "text");
		String progress = value(message, "message");
		onEdt(() ->
		{
			switch (type)
			{
				case "connection.ready":
					connectionLabel.setText("Codex: ready");
					break;
				case "task.accepted":
					taskLabel.setText("Task accepted");
					break;
				case "task.progress":
					taskLabel.setText(phase == null ? "Working" : "Working: " + phase);
					if (progress != null)
					{
						append("Progress", progress);
					}
					break;
				case "task.result":
					taskLabel.setText(status == null ? "Completed" : status);
					if (text != null)
					{
						append("Codex", text);
					}
					break;
				case "task.error":
					taskLabel.setText("Failed");
					append("Error", text == null ? progress : text);
					break;
				case "task.cancelled":
					taskLabel.setText("Cancelled");
					append("System", "Task cancelled.");
					break;
				default:
					break;
			}
		});
	}

	@Override
	public void onClientError(String message)
	{
		onEdt(() ->
		{
			taskLabel.setText("Error");
			append("Client", message);
		});
	}

	@Override
	public void onDictationState(String state)
	{
		onEdt(() ->
		{
			if ("transcribing".equals(state))
			{
				taskLabel.setText("Transcribing");
			}
		});
	}

	@Override
	public void onDictationText(String text)
	{
		onEdt(() ->
		{
			dictationBusy = false;
			dictateButton.setEnabled(true);
			dictateButton.setText("Sanele");
			append("You (dictation)", text);
			if (client != null)
			{
				client.submit(text);
			}
		});
	}

	@Override
	public void onDictationError(String message)
	{
		onEdt(() ->
		{
			dictationBusy = false;
			dictateButton.setEnabled(true);
			dictateButton.setText("Sanele");
			taskLabel.setText("Dictation error");
			append("Dictation", message);
		});
	}

	void close()
	{
		dictationExecutor.shutdownNow();
		recorder.close();
	}

	private void toggleDictation()
	{
		if (client == null || dictationBusy)
		{
			return;
		}
		if (recorder.isRecording())
		{
			stopDictation();
		}
		else
		{
			startDictation();
		}
	}

	private void startDictation()
	{
		dictationBusy = true;
		dictateButton.setEnabled(false);
		dictateButton.setText("Starting...");
		taskLabel.setText("Opening microphone");
		dictationExecutor.execute(() ->
		{
			try
			{
				recorder.start();
				onEdt(() ->
				{
					dictationBusy = false;
					dictateButton.setEnabled(true);
					dictateButton.setText("Stop dictation");
					taskLabel.setText("Listening");
					append("System", "Listening. Press Stop dictation when finished.");
				});
			}
			catch (Exception ex)
			{
				onDictationError("Microphone could not be opened: " + ex.getMessage());
			}
		});
	}

	private void stopDictation()
	{
		dictationBusy = true;
		dictateButton.setEnabled(false);
		dictateButton.setText("Transcribing...");
		taskLabel.setText("Stopping microphone");
		dictationExecutor.execute(() ->
		{
			try
			{
				byte[] wav = recorder.stop();
				if (wav.length <= 44)
				{
					onDictationError("No speech was recorded.");
					return;
				}
				client.transcribeAudio(wav);
			}
			catch (Exception ex)
			{
				onDictationError("Could not finish dictation: " + ex.getMessage());
			}
		});
	}

	private void submitInput()
	{
		String text = input.getText();
		if (text == null || text.trim().isEmpty() || client == null)
		{
			return;
		}
		append("You", text.trim());
		input.setText("");
		client.submit(text);
	}

	private void append(String speaker, String text)
	{
		if (text == null || text.trim().isEmpty())
		{
			return;
		}
		if (transcript.getDocument().getLength() > 0)
		{
			transcript.append("\n\n");
		}
		transcript.append(speaker + ":\n" + text.trim());
		transcript.setCaretPosition(transcript.getDocument().getLength());
	}

	private void onEdt(Runnable runnable)
	{
		if (SwingUtilities.isEventDispatchThread())
		{
			runnable.run();
		}
		else
		{
			SwingUtilities.invokeLater(runnable);
		}
	}

	private static String value(JsonObject object, String name)
	{
		return object.has(name) && !object.get(name).isJsonNull() ? object.get(name).getAsString() : null;
	}
}
