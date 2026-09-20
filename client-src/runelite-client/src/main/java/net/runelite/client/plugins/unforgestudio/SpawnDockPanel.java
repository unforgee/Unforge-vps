package net.runelite.client.plugins.unforgestudio;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.swing.BorderFactory;
import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import net.runelite.api.Client;
import net.runelite.client.callback.ClientThread;

/**
 * Puts an NPC or object spawn at the player's current tile.
 *
 * <p>This is the "create content where I am standing" tool. The entity is chosen by
 * name from the server's own symbol table, the target file is chosen from the content
 * editor's catalogue, and nothing is written until the preview has been reviewed and
 * applied. The editor owns validation, the SHA-256 lock and the snapshot.</p>
 */
class SpawnDockPanel extends DockTool
{
	private static final String SOURCE = "crownwield";
	private static final int SEARCH_LIMIT = 40;

	private final JComboBox<String> kindBox = new JComboBox<>(new String[]{"NPC", "Object"});
	private final JTextField searchField = new JTextField();
	private final DefaultListModel<String> matchModel = new DefaultListModel<>();
	private final JList<String> matchList = new JList<>(matchModel);
	private final JComboBox<TargetFile> targetBox = new JComboBox<>();
	private final JTextArea output = new JTextArea();
	private final JLabel positionLabel = new JLabel("position: -");

	private Position currentPosition;

	SpawnDockPanel(Client client, ClientThread clientThread, SymbolIndex symbols,
		StudioApiClient api, UnforgeStudioDock dock)
	{
		super(client, clientThread, symbols, api, dock);

		buildUi();
		refreshPosition();
	}

	private void buildUi()
	{
		JPanel controls = new JPanel(new BorderLayout(0, 4));

		JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
		top.add(new JLabel("Kind:"));
		kindBox.addActionListener(e -> clearMatches());
		top.add(kindBox);
		top.add(new JLabel("Target:"));
		targetBox.setPreferredSize(new Dimension(320, targetBox.getPreferredSize().height));
		top.add(targetBox);
		top.add(button("Refresh files", this::refreshTargets));
		controls.add(top, BorderLayout.NORTH);

		JPanel search = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
		search.add(new JLabel("Find by name:"));
		searchField.setPreferredSize(new Dimension(220, searchField.getPreferredSize().height));
		searchField.addActionListener(e -> runSearch());
		search.add(searchField);
		search.add(button("Search", this::runSearch));
		search.add(positionLabel);
		search.add(button("At my tile", this::refreshPosition));
		controls.add(search, BorderLayout.CENTER);

		JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
		actions.add(button("Preview", () -> submit(false)));
		actions.add(button("Apply", () -> submit(true)));
		controls.add(actions, BorderLayout.SOUTH);

		matchList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		matchList.setFont(monoFont());
		matchList.addListSelectionListener(e ->
		{
			if (!e.getValueIsAdjusting() && matchList.getSelectedValue() != null)
			{
				showResolvedId(matchList.getSelectedValue());
			}
		});

		output.setEditable(false);
		output.setFont(monoFont());
		output.setLineWrap(false);

		JScrollPane matches = new JScrollPane(matchList);
		matches.setBorder(BorderFactory.createTitledBorder("Matches"));

		JScrollPane result = new JScrollPane(output);
		result.setBorder(BorderFactory.createTitledBorder("Preview / result"));

		JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, matches, result);
		split.setResizeWeight(0.35);
		split.setBorder(null);

		add(controls, BorderLayout.NORTH);
		add(split, BorderLayout.CENTER);
	}

	// -------------------------------------------------------------- behaviour

	private SymbolIndex.Kind selectedKind()
	{
		return kindBox.getSelectedIndex() == 0 ? SymbolIndex.Kind.NPC : SymbolIndex.Kind.LOC;
	}

	private String selectedKindId()
	{
		return kindBox.getSelectedIndex() == 0 ? "npc-spawn" : "object-spawn";
	}

	private void clearMatches()
	{
		matchModel.clear();
		output.setText("");
	}

	private void refreshPosition()
	{
		withPosition(position ->
		{
			currentPosition = position;
			SwingUtilities.invokeLater(() ->
				positionLabel.setText(position == null ? "position: not logged in" : "position: " + position));
		});
	}

	private void runSearch()
	{
		SymbolIndex.Kind kind = selectedKind();
		String query = searchField.getText().trim();
		List<String> hits = symbols.search(kind, query, SEARCH_LIMIT);

		matchModel.clear();
		for (String hit : hits)
		{
			matchModel.addElement(hit);
		}

		if (hits.isEmpty())
		{
			setStatus(symbols.isLoaded()
				? "No " + kind.label() + " matched \"" + query + "\""
				: symbols.status(), !symbols.isLoaded());
		}
		else
		{
			setStatus(hits.size() + " " + kind.label() + " match" + (hits.size() == 1 ? "" : "es"));
		}
	}

	private void showResolvedId(String name)
	{
		symbols.idFor(selectedKind(), name).ifPresent(id ->
			setStatus("resolved \"" + name + "\" to id " + id));
	}

	private void refreshTargets()
	{
		String kind = selectedKindId();
		background("Loading target files for " + kind + "...", () ->
		{
			StudioApiClient.ApiResult result = api.studioGet(
				"/api/content/catalog?source=" + SOURCE + "&kind=" + kind + "&limit=200");

			if (!result.ok())
			{
				throw new IllegalStateException(result.error());
			}

			List<TargetFile> files = new ArrayList<>();
			JsonObject json = result.json();
			if (json != null && json.has("records"))
			{
				JsonArray records = json.getAsJsonArray("records");
				for (JsonElement element : records)
				{
					JsonObject record = element.getAsJsonObject();
					if (!record.has("sourcePath"))
					{
						continue;
					}

					files.add(new TargetFile(
						text(record, "title"),
						text(record, "sourcePath"),
						text(record, "sourceHash")));
				}
			}

			SwingUtilities.invokeLater(() -> targetBox.setModel(new DefaultComboBoxModel<>(files.toArray(new TargetFile[0]))));

			return files.isEmpty()
				? "No writable " + kind + " files found in the catalogue"
				: files.size() + " target file(s) available";
		});
	}

	private void submit(boolean apply)
	{
		final String name = matchList.getSelectedValue();
		if (name == null)
		{
			setStatus("Pick a name from the matches list first", true);
			return;
		}

		final TargetFile target = (TargetFile) targetBox.getSelectedItem();
		if (target == null)
		{
			setStatus("Pick a target file first (Refresh files)", true);
			return;
		}

		final SymbolIndex.Kind kind = selectedKind();
		final int id = symbols.idFor(kind, name).orElse(-1);
		if (id < 0)
		{
			setStatus("Could not resolve \"" + name + "\" to an id", true);
			return;
		}

		background(apply ? "Applying..." : "Building preview...", () ->
		{
			JsonObject body = new JsonObject();
			body.addProperty("source", SOURCE);
			body.addProperty("kind", selectedKindId());
			body.addProperty("sourcePath", target.sourcePath);
			body.addProperty("expectedHash", target.sourceHash);
			body.addProperty("name", name);
			body.addProperty("apply", apply);
			body.addProperty("reason", "Dock: add " + name + " at the player's tile");

			if (kind == SymbolIndex.Kind.NPC)
			{
				body.addProperty("npcId", id);
			}
			else
			{
				body.addProperty("objectId", id);
			}

			StudioApiClient.ApiResult result = api.studioPost("/api/content/spawn-from-live", body);
			if (!result.ok())
			{
				SwingUtilities.invokeLater(() -> output.setText(result.error()));
				throw new IllegalStateException(result.error());
			}

			String rendered = renderResult(result.json());
			SwingUtilities.invokeLater(() -> output.setText(rendered));

			JsonObject json = result.json();
			boolean valid = json != null && json.has("preview")
				&& !json.getAsJsonObject("preview").get("valid").isJsonNull()
				&& json.getAsJsonObject("preview").get("valid").getAsBoolean();

			if (!valid)
			{
				return "Preview has validation errors - see the panel";
			}

			if (!apply)
			{
				return "Preview is clean - press Apply to write it";
			}

			return "Applied: " + name + " at " + text(json, "position");
		});

		refreshPosition();
	}

	private String renderResult(JsonObject json)
	{
		if (json == null)
		{
			return "(empty response)";
		}

		StringBuilder builder = new StringBuilder();
		if (json.has("position"))
		{
			builder.append("position: ").append(json.get("position")).append('\n');
		}

		if (json.has("preview"))
		{
			JsonObject preview = json.getAsJsonObject("preview");
			builder.append("valid: ").append(preview.has("valid") && preview.get("valid").getAsBoolean()).append('\n');

			if (preview.has("issues") && preview.getAsJsonArray("issues").size() > 0)
			{
				builder.append("\nIssues:\n");
				for (JsonElement element : preview.getAsJsonArray("issues"))
				{
					JsonObject issue = element.getAsJsonObject();
					builder.append("  [").append(text(issue, "severity")).append("] ")
						.append(text(issue, "code")).append(": ")
						.append(text(issue, "message")).append('\n');
				}
			}

			if (preview.has("fileDiffs"))
			{
				for (JsonElement element : preview.getAsJsonArray("fileDiffs"))
				{
					JsonObject diff = element.getAsJsonObject();
					builder.append('\n').append(text(diff, "sourcePath")).append('\n');
					builder.append(text(diff, "diff")).append('\n');
				}
			}
		}

		if (json.has("applied"))
		{
			JsonObject applied = json.getAsJsonObject("applied");
			builder.append("\napplied: ").append(applied.has("applied") && applied.get("applied").getAsBoolean());
			if (applied.has("snapshotId") && !applied.get("snapshotId").isJsonNull())
			{
				builder.append("  snapshot: ").append(applied.get("snapshotId").getAsString());
			}
			builder.append('\n');
		}

		return builder.toString();
	}

	// ------------------------------------------------------------------ helpers

	private static JButton button(String text, Runnable onClick)
	{
		JButton button = new JButton(text);
		button.setFocusable(false);
		button.addActionListener(e -> onClick.run());
		return button;
	}

	private static Font monoFont()
	{
		return new Font(Font.MONOSPACED, Font.PLAIN, 11);
	}

	private static String text(JsonObject object, String name)
	{
		if (object == null || !object.has(name) || object.get(name).isJsonNull())
		{
			return "";
		}
		String value = object.get(name).getAsString();
		return value.length() > 200 ? value.substring(0, 200) + "..." : value;
	}

	/** One selectable catalogue file, remembering its optimistic-lock hash. */
	private static final class TargetFile
	{
		private final String title;
		private final String sourcePath;
		private final String sourceHash;

		TargetFile(String title, String sourcePath, String sourceHash)
		{
			this.title = title;
			this.sourcePath = sourcePath;
			this.sourceHash = sourceHash;
		}

		@Override
		public String toString()
		{
			String label = title == null || title.isEmpty() ? sourcePath : title;
			return String.format(Locale.ROOT, "%s  (%s)", label, sourcePath);
		}
	}
}
