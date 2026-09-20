package net.runelite.client.plugins.unforgestudio;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import net.runelite.api.Client;
import net.runelite.client.callback.ClientThread;

/**
 * Creates an interface teleport at the player's current tile.
 *
 * <p>Teleport entries live in a nested {@code category -> group -> teleports} table, so
 * the form mirrors that shape. A brand new category has to be created in the same change
 * set as its first entry, which is what the "new zone" checkbox requests.</p>
 */
class TeleportDockPanel extends DockTool
{
	private static final String SOURCE = "crownwield";

	private final JComboBox<TargetFile> targetBox = new JComboBox<>();
	private final JTextField nameField = new JTextField(18);
	private final JTextField categoryField = new JTextField(14);
	private final JTextField groupField = new JTextField(14);
	private final JTextField priceField = new JTextField(6);
	private final JCheckBox createZoneBox = new JCheckBox("Create the zone if it is new");
	private final JTextArea output = new JTextArea();

	TeleportDockPanel(Client client, ClientThread clientThread, SymbolIndex symbols,
		StudioApiClient api, UnforgeStudioDock dock)
	{
		super(client, clientThread, symbols, api, dock);
		buildUi();
	}

	private void buildUi()
	{
		JPanel form = new JPanel(new BorderLayout(0, 4));

		JPanel target = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
		target.add(new JLabel("Table:"));
		targetBox.setPreferredSize(new Dimension(340, targetBox.getPreferredSize().height));
		target.add(targetBox);
		target.add(button("Refresh", this::refreshTargets));
		form.add(target, BorderLayout.NORTH);

		JPanel fields = new JPanel(new GridLayout(0, 4, 6, 2));
		fields.add(new JLabel("Name"));
		fields.add(nameField);
		fields.add(new JLabel("Category"));
		fields.add(categoryField);

		fields.add(new JLabel("Group"));
		fields.add(groupField);
		fields.add(new JLabel("Price"));
		fields.add(priceField);

		fields.add(createZoneBox);
		fields.add(new JLabel(""));
		fields.add(button("Preview", () -> submit(false)));
		fields.add(button("Apply", () -> submit(true)));

		form.add(fields, BorderLayout.CENTER);

		output.setEditable(false);
		output.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
		JScrollPane scroll = new JScrollPane(output);
		scroll.setBorder(BorderFactory.createTitledBorder("Preview / result"));

		add(form, BorderLayout.NORTH);
		add(scroll, BorderLayout.CENTER);
	}

	private void refreshTargets()
	{
		background("Loading teleport tables...", () ->
		{
			StudioApiClient.ApiResult result = api.studioGet(
				"/api/content/catalog?source=" + SOURCE + "&kind=teleport-eco&limit=50");

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
					if (record.has("sourcePath"))
					{
						files.add(new TargetFile(
							text(record, "title"),
							text(record, "sourcePath"),
							text(record, "sourceHash")));
					}
				}
			}

			SwingUtilities.invokeLater(() -> targetBox.setModel(new DefaultComboBoxModel<>(files.toArray(new TargetFile[0]))));

			return files.isEmpty() ? "No teleport tables in the catalogue" : files.size() + " table(s)";
		});
	}

	private void submit(boolean apply)
	{
		String name = nameField.getText().trim();
		if (name.isEmpty())
		{
			setStatus("Give the teleport a name", true);
			return;
		}

		TargetFile target = (TargetFile) targetBox.getSelectedItem();
		if (target == null)
		{
			setStatus("Pick a table first (Refresh)", true);
			return;
		}

		String category = categoryField.getText().trim();
		String group = groupField.getText().trim();
		String price = priceField.getText().trim();

		background(apply ? "Applying..." : "Building preview...", () ->
		{
			JsonObject body = new JsonObject();
			body.addProperty("source", SOURCE);
			body.addProperty("sourcePath", target.sourcePath);
			body.addProperty("expectedHash", target.sourceHash);
			body.addProperty("name", name);
			body.addProperty("category", category.isEmpty() ? "Custom" : category);
			body.addProperty("group", group.isEmpty() ? "Custom" : group);
			body.addProperty("createZone", createZoneBox.isSelected());
			body.addProperty("apply", apply);
			body.addProperty("reason", "Dock: interface teleport \"" + name + "\" at the player's tile");

			if (!price.isEmpty())
			{
				try
				{
					body.addProperty("price", Integer.parseInt(price));
				}
				catch (NumberFormatException ignored)
				{
					// Leave the price out rather than sending a bad value.
				}
			}

			StudioApiClient.ApiResult result = api.studioPost("/api/content/teleport-from-live", body);
			if (!result.ok())
			{
				SwingUtilities.invokeLater(() -> output.setText(result.error()));
				throw new IllegalStateException(result.error());
			}

			String rendered = render(result.json());
			SwingUtilities.invokeLater(() -> output.setText(rendered));

			JsonObject json = result.json();
			boolean valid = json != null && json.has("preview")
				&& !json.getAsJsonObject("preview").get("valid").isJsonNull()
				&& json.getAsJsonObject("preview").get("valid").getAsBoolean();

			if (!valid)
			{
				return "Preview has validation errors - see the panel";
			}

			return apply ? "Applied teleport \"" + name + "\"" : "Preview is clean - press Apply to write it";
		});
	}

	private static String render(JsonObject json)
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

			if (preview.has("issues"))
			{
				for (JsonElement element : preview.getAsJsonArray("issues"))
				{
					JsonObject issue = element.getAsJsonObject();
					builder.append("  [").append(text(issue, "severity")).append("] ")
						.append(text(issue, "code")).append(": ").append(text(issue, "message")).append('\n');
				}
			}

			if (preview.has("fileDiffs"))
			{
				for (JsonElement element : preview.getAsJsonArray("fileDiffs"))
				{
					JsonObject diff = element.getAsJsonObject();
					builder.append('\n').append(text(diff, "sourcePath")).append('\n')
						.append(textUnclipped(diff, "diff")).append('\n');
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

	private static JButton button(String text, Runnable onClick)
	{
		JButton button = new JButton(text);
		button.setFocusable(false);
		button.addActionListener(e -> onClick.run());
		return button;
	}

	private static String text(JsonObject object, String name)
	{
		if (object == null || !object.has(name) || object.get(name).isJsonNull())
		{
			return "";
		}
		return object.get(name).getAsString();
	}

	private static String textUnclipped(JsonObject object, String name)
	{
		return text(object, name);
	}

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
			return label + "  (" + sourcePath + ")";
		}
	}
}
