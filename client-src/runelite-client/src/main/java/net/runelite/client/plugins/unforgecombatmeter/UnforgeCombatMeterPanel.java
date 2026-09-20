package net.runelite.client.plugins.unforgecombatmeter;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Font;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;

final class UnforgeCombatMeterPanel extends PluginPanel
{
	private final CombatMeterModel model;
	private final UnforgeCombatMeterConfig config;
	private final JTabbedPane tabs = new JTabbedPane();
	private final JTextArea[] views = {new JTextArea(), new JTextArea(), new JTextArea(), new JTextArea()};

	UnforgeCombatMeterPanel(CombatMeterModel model, UnforgeCombatMeterConfig config)
	{
		this.model = model;
		this.config = config;
		setLayout(new BorderLayout(0, 6));
		setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
		setBackground(ColorScheme.DARK_GRAY_COLOR);
		JPanel header = new JPanel(new BorderLayout()); header.setOpaque(false);
		JLabel title = new JLabel("Unforge Combat Meter"); title.setForeground(Color.WHITE); title.setFont(title.getFont().deriveFont(Font.BOLD, 13f));
		header.add(title, BorderLayout.WEST);
		JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 2, 0)); buttons.setOpaque(false);
		JButton reset = new JButton("Reset"); reset.addActionListener(e -> { model.reset(); refresh(); }); buttons.add(reset); header.add(buttons, BorderLayout.EAST);
		add(header, BorderLayout.NORTH);
		String[] names = {"Damage", "Healing", "Taken", "Tanking"};
		for (int i = 0; i < views.length; i++) { views[i].setEditable(false); views[i].setLineWrap(true); views[i].setWrapStyleWord(true); views[i].setForeground(ColorScheme.LIGHT_GRAY_COLOR); views[i].setBackground(ColorScheme.DARKER_GRAY_COLOR); tabs.addTab(names[i], new JScrollPane(views[i])); }
		add(tabs, BorderLayout.CENTER);
	}

	void refresh()
	{
		CombatMeterModel.Kind[] kinds = {CombatMeterModel.Kind.DAMAGE, CombatMeterModel.Kind.HEALING, CombatMeterModel.Kind.TAKEN};
		for (int i = 0; i < 3; i++) render(views[i], kinds[i]);
		views[3].setText("Tanking\n\nDamage taken is the authoritative aggro proxy until the server exposes target-switch telemetry.\n\n" + format(CombatMeterModel.Kind.TAKEN));
	}

	private void render(JTextArea area, CombatMeterModel.Kind kind)
	{
		long total = model.total(kind); StringBuilder out = new StringBuilder();
		out.append(kind).append("\nEncounter: ").append(model.elapsedSeconds()).append("s\n\n");
		for (CombatMeterModel.ActorStats a : model.sorted(kind)) { long value = kind == CombatMeterModel.Kind.DAMAGE ? a.damage : kind == CombatMeterModel.Kind.HEALING ? a.healing : a.taken; int pct = total == 0 ? 0 : (int) (value * 100 / total); out.append(String.format("%-18s %,8d  %3d%%  %s\n", a.name, value, pct, bar(pct))); out.append("  hits=").append(a.hits).append(" max=").append(a.maxHit).append("  ").append(a.breakdown).append("\n"); }
		if (model.sorted(kind).isEmpty()) out.append("No server/client combat events observed yet.\n"); area.setText(out.toString()); area.setCaretPosition(0);
	}
	private String format(CombatMeterModel.Kind kind) { return "Total: " + model.total(kind); }
	private String bar(int pct) { int n = Math.min(20, pct / 5); return "[" + "#".repeat(n) + "-".repeat(20 - n) + "]"; }
}
