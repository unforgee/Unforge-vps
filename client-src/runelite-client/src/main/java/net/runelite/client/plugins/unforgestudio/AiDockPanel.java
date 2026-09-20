package net.runelite.client.plugins.unforgestudio;

import com.google.gson.JsonObject;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Font;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import net.runelite.api.Client;
import net.runelite.client.callback.ClientThread;

/**
 * Asks the studio's AI router a question without leaving the game window.
 *
 * <p>Goes through {@code POST /api/unforge/ask}, which runs the request through the
 * studio's existing provider routing and returns the answer together with which
 * provider produced it. Answers are read-only: this panel never writes content.</p>
 */
class AiDockPanel extends DockTool
{
	private final JTextArea prompt = new JTextArea(3, 40);
	private final JTextArea answer = new JTextArea();

	AiDockPanel(Client client, ClientThread clientThread, SymbolIndex symbols,
		StudioApiClient api, UnforgeStudioDock dock)
	{
		super(client, clientThread, symbols, api, dock);
		buildUi();
	}

	private void buildUi()
	{
		JPanel top = new JPanel(new BorderLayout(0, 4));

		prompt.setLineWrap(true);
		prompt.setWrapStyleWord(true);
		prompt.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
		JScrollPane promptScroll = new JScrollPane(prompt);
		promptScroll.setBorder(BorderFactory.createTitledBorder("Ask the studio"));

		JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
		actions.add(button("Ask", this::ask));
		actions.add(new JLabel("Answers are read-only - the AI never writes content from here."));
		top.add(promptScroll, BorderLayout.CENTER);
		top.add(actions, BorderLayout.SOUTH);

		answer.setEditable(false);
		answer.setLineWrap(true);
		answer.setWrapStyleWord(true);
		answer.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
		JScrollPane answerScroll = new JScrollPane(answer);
		answerScroll.setBorder(BorderFactory.createTitledBorder("Answer"));

		add(top, BorderLayout.NORTH);
		add(answerScroll, BorderLayout.CENTER);
	}

	private void ask()
	{
		String text = prompt.getText().trim();
		if (text.isEmpty())
		{
			setStatus("Type a question first", true);
			return;
		}

		background("Asking the studio...", () ->
		{
			JsonObject body = new JsonObject();
			body.addProperty("text", text);

			StudioApiClient.ApiResult result = api.studioPostLong("/api/unforge/ask", body);
			if (!result.ok())
			{
				SwingUtilities.invokeLater(() -> answer.setText(result.error()));
				throw new IllegalStateException(result.error());
			}

			JsonObject json = result.json();
			String reply = null;
			String provider = null;

			if (json != null && json.has("result") && json.get("result").isJsonObject())
			{
				JsonObject inner = json.getAsJsonObject("result");
				if (inner.has("text") && !inner.get("text").isJsonNull())
				{
					reply = inner.get("text").getAsString();
				}
				if (inner.has("providerId") && !inner.get("providerId").isJsonNull())
				{
					provider = inner.get("providerId").getAsString();
				}
			}

			if (reply == null)
			{
				reply = json == null ? "(empty response)" : json.toString();
			}

			final String finalReply = reply;
			final String finalProvider = provider;
			SwingUtilities.invokeLater(() -> answer.setText(finalReply));
			SwingUtilities.invokeLater(() -> answer.setCaretPosition(0));

			return finalProvider == null ? "Answer received" : "Answer received from " + finalProvider;
		});
	}

	private static JButton button(String text, Runnable onClick)
	{
		JButton button = new JButton(text);
		button.setFocusable(false);
		button.addActionListener(e -> onClick.run());
		return button;
	}
}
