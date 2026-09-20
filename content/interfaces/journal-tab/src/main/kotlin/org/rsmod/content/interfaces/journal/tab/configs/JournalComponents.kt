package org.rsmod.content.interfaces.journal.tab.configs

import org.rsmod.api.type.refs.comp.ComponentReferences

typealias journal_components = JournalComponents

object JournalComponents : ComponentReferences() {
    val tab_container = find("side_journal:tab_container", 524739677799913345)
    val summary_list = find("side_journal:summary_list", 2973327531214900433)
    val quest_list = find("side_journal:quest_list", 8263924698808498727)
    val task_list = find("side_journal:task_list", 2317701014815971944)

    // The visible Achievement Diaries slot is repurposed as the Perks tab button. The old
    // adventure-path slot is not present on every client revision, so binding Perks to it made
    // the page unreachable even though the server-side interface existed.
    val perk_list = task_list

    // Fifth tab slot in the side journal - repurposed as the Points & Progress tab button.
    val points_list = find("side_journal:league_list")

    val summary_contents = find("account_summary_sidepanel:summary_contents", 2485176660594907617)
    val summary_click_layer =
        find("account_summary_sidepanel:summary_click_layer", 2485176660594907618)
}
