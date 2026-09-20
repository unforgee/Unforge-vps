package net.runelite.client.plugins.unforgestudio;

import com.google.gson.JsonObject;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import javax.swing.BorderFactory;
import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
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
 * Browses the server's content modules and hands a file to the AI for editing.
 *
 * <p>Server content - interfaces included - is Kotlin source under
 * {@code content/<area>/<module>/src/main/kotlin/...}, one Gradle module per feature.
 * There is no data file to edit, so this tool shows the real source and passes it to
 * the studio as context for a question.</p>
 */
class InterfaceDockPanel extends DockTool
{
	private static final int MAX_PREVIEW_CHARS = 20000;
	private static final int MAX_MODULES_SCANNED = 400;

	private final JComboBox<Path> moduleBox = new JComboBox<>();
	private final DefaultListModel<Path> fileModel = new DefaultListModel<>();
	private final JList<Path> fileList = new JList<>(fileModel);
	private final JTextArea source = new JTextArea();
	private final JTextField question = new JTextField();

	InterfaceDockPanel(Client client, ClientThread clientThread, SymbolIndex symbols,
		StudioApiClient api, UnforgeStudioDock dock)
	{
		super(client, clientThread, symbols, api, dock);
		buildUi();
	}

	private void buildUi()
	{
		JPanel top = new JPanel(new BorderLayout(0, 4));

		JPanel header = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
		header.add(new JLabel("Module:"));
		moduleBox.setPreferredSize(new Dimension(330, moduleBox.getPreferredSize().height));
		moduleBox.addActionListener(e -> loadFiles());
		header.add(moduleBox);
		header.add(button("Rescan", this::scanModules));
		top.add(header, BorderLayout.NORTH);

		JPanel ask = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
		ask.add(new JLabel("Question:"));
		question.setPreferredSize(new Dimension(420, question.getPreferredSize().height));
		question.addActionListener(e -> askAboutFile());
		ask.add(question);
		ask.add(button("Ask AI about this file", this::askAboutFile));
		top.add(ask, BorderLayout.SOUTH);

		fileList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		fileList.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
		fileList.addListSelectionListener(e ->
		{
			if (!e.getValueIsAdjusting())
			{
				showFile(fileList.getSelectedValue());
			}
		});

		source.setEditable(false);
		source.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));

		JScrollPane files = new JScrollPane(fileList);
		files.setBorder(BorderFactory.createTitledBorder("Files"));
		JScrollPane sourceScroll = new JScrollPane(source);
		sourceScroll.setBorder(BorderFactory.createTitledBorder("Source"));

		JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, files, sourceScroll);
		split.setResizeWeight(0.3);
		split.setBorder(null);

		add(top, BorderLayout.NORTH);
		add(split, BorderLayout.CENTER);
	}

	// ------------------------------------------------------------------ resolve

	/** The lab root, derived from where the symbol tables were found. */
	private Path labRoot()
	{
		Path symbolsDir = symbols.symbolsDir();
		if (symbolsDir == null || symbolsDir.getParent() == null || symbolsDir.getParent().getParent() == null)
		{
			return null;
		}
		return symbolsDir.getParent().getParent();
	}

	private void scanModules()
	{
		Path root = labRoot();
		if (root == null)
		{
			setStatus("Lab root unknown - set Lab root in the plugin settings", true);
			return;
		}

		Path content = root.resolve("content");
		if (!Files.isDirectory(content))
		{
			setStatus("No content directory under " + root, true);
			return;
		}

		background("Scanning content modules...", () ->
		{
			List<Path> modules = new ArrayList<>();
			try (Stream<Path> walk = Files.walk(content, 4))
			{
				walk.filter(Files::isDirectory)
					.filter(path -> path.endsWith("kotlin"))
					.filter(path -> path.getParent() != null && "main".equals(path.getParent().getFileName().toString()))
					.limit(MAX_MODULES_SCANNED)
					.forEach(path -> modules.add(path.getParent().getParent().getParent()));
			}
			catch (IOException ex)
			{
				throw new IllegalStateException("Scan failed: " + ex.getMessage());
			}

			modules.sort(Comparator.comparing(path -> content.relativize(path).toString()));

			SwingUtilities.invokeLater(() ->
				moduleBox.setModel(new DefaultComboBoxModel<>(modules.toArray(new Path[0]))));

			return modules.size() + " content module(s) found";
		});
	}

	private void loadFiles()
	{
		Path module = (Path) moduleBox.getSelectedItem();
		fileModel.clear();
		source.setText("");

		if (module == null)
		{
			return;
		}

		background("Listing files in " + module.getFileName() + "...", () ->
		{
			List<Path> files = new ArrayList<>();
			try (Stream<Path> walk = Files.walk(module))
			{
				walk.filter(Files::isRegularFile)
					.filter(path -> path.getFileName().toString().endsWith(".kt"))
					.filter(path -> !path.toString().contains("build"))
					.forEach(files::add);
			}
			catch (IOException ex)
			{
				throw new IllegalStateException("Listing failed: " + ex.getMessage());
			}

			// Interface and config definitions first: they are the usual targets.
			files.sort(Comparator
				.comparing((Path path) -> !path.getFileName().toString().contains("Interfaces"))
				.thenComparing(path -> path.toString()));

			SwingUtilities.invokeLater(() ->
			{
				for (Path file : files)
				{
					fileModel.addElement(file);
				}
			});

			return files.size() + " Kotlin file(s)";
		});
	}

	private void showFile(Path file)
	{
		if (file == null)
		{
			return;
		}

		background("Reading " + file.getFileName() + "...", () ->
		{
			String content;
			try
			{
				content = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
			}
			catch (IOException ex)
			{
				throw new IllegalStateException("Read failed: " + ex.getMessage());
			}

			boolean clipped = content.length() > MAX_PREVIEW_CHARS;
			String shown = clipped ? content.substring(0, MAX_PREVIEW_CHARS) + "\n\n... (truncated)" : content;
			SwingUtilities.invokeLater(() ->
			{
				source.setText(shown);
				source.setCaretPosition(0);
			});

			return clipped ? "Showing first " + MAX_PREVIEW_CHARS + " characters" : shown.length() + " characters";
		});
	}

	// ---------------------------------------------------------------------- AI

	private void askAboutFile()
	{
		Path file = fileList.getSelectedValue();
		if (file == null)
		{
			setStatus("Pick a file first", true);
			return;
		}

		String text = question.getText().trim();
		if (text.isEmpty())
		{
			setStatus("Type a question first", true);
			return;
		}

		background("Asking the studio about " + file.getFileName() + "...", () ->
		{
			String content;
			try
			{
				content = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
			}
			catch (IOException ex)
			{
				throw new IllegalStateException("Read failed: " + ex.getMessage());
			}

			if (content.length() > MAX_PREVIEW_CHARS)
			{
				content = content.substring(0, MAX_PREVIEW_CHARS);
			}

			StringBuilder prompt = new StringBuilder();
			prompt.append("File: ").append(file).append('\n');
			prompt.append("This is UnForge (rsmod, revision 239) content source.\n\n");
			prompt.append("```kotlin\n").append(content).append("\n```\n\n");
			prompt.append("Question: ").append(text);

			JsonObject body = new JsonObject();
			body.addProperty("text", prompt.toString());

			StudioApiClient.ApiResult result = api.studioPostLong("/api/unforge/ask", body);
			if (!result.ok())
			{
				throw new IllegalStateException(result.error());
			}

			JsonObject json = result.json();
			String reply = null;
			if (json != null && json.has("result") && json.get("result").isJsonObject())
			{
				JsonObject inner = json.getAsJsonObject("result");
				if (inner.has("text") && !inner.get("text").isJsonNull())
				{
					reply = inner.get("text").getAsString();
				}
			}

			final String finalReply = reply == null ? (json == null ? "(empty response)" : json.toString()) : reply;
			SwingUtilities.invokeLater(() ->
			{
				source.setText(finalReply);
				source.setCaretPosition(0);
			});

			return "Answer shown in the source pane";
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
