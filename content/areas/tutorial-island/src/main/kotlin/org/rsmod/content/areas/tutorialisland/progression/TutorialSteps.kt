package org.rsmod.content.areas.tutorialisland.progression

import org.rsmod.api.utils.vars.VarEnumDelegate

/**
 * Packed into `tutorial_2` varp. Values are sequential for progress-bar mapping. Quest completes at
 * [MagicCast]; leave finishes at [HomeTele].
 */
enum class TutorialStep(override val varValue: Int) : VarEnumDelegate {
    CharCreate(0),
    ExpSelect(1),
    GielinorTalk(2),
    GielinorSettings(3),
    GielinorDoor(4),
    SurvivalTalk(5),
    SurvivalInv(6),
    SurvivalFish(7),
    SurvivalSkills(8),
    SurvivalTools(9),
    SurvivalWc(10),
    SurvivalFm(11),
    SurvivalCook(12),
    SurvivalGate(13),
    ChefDoor(14),
    ChefTalk(15),
    ChefDough(16),
    ChefBread(17),
    ChefExit(18),
    QuestDoor(19),
    QuestTalk(20),
    QuestList(21),
    QuestLadder(22),
    MineTalk(23),
    MineOres(24),
    MineSmelt(25),
    MineHammer(26),
    MineDagger(27),
    MineGate(28),
    CombatTalk(29),
    CombatWorn(30),
    CombatStats(31),
    CombatDagger(32),
    CombatGear(33),
    CombatStyles(34),
    CombatMelee(35),
    CombatRange(36),
    CombatLadder(37),
    PrayerTalk(38),
    PrayerTab(39),
    PrayerExit(40),
    BankOpen(41),
    PollView(42),
    AccountTalk(43),
    AccountMgmt(44),
    AccountExit(45),
    Ironman(46),
    MagicTalk(47),
    MagicTab(48),
    MagicRunes(49),
    MagicCast(50),
    LeaveTalk(51),
    HomeTele(52),
    Completed(53);

    val guideTitle: String
        get() =
            when (this) {
                CharCreate -> "Setting your appearance"
                ExpSelect -> "Past Experience"
                GielinorTalk -> "Getting started"
                GielinorSettings -> "Settings menu"
                GielinorDoor -> "Moving on"
                SurvivalTalk -> "Survival Expert"
                SurvivalInv -> "You've been given an item"
                SurvivalFish -> "Inventory"
                SurvivalSkills -> "You've gained some experience"
                SurvivalTools,
                SurvivalWc -> "Woodcutting"
                SurvivalFm -> "Firemaking"
                SurvivalCook -> "Cooking"
                SurvivalGate -> "Moving on"
                ChefDoor,
                ChefTalk -> "Cooking"
                ChefDough -> "Making dough"
                ChefBread -> "Cooking dough"
                ChefExit -> "Fancy a run?"
                QuestDoor,
                QuestTalk,
                QuestList -> "Quest journal"
                QuestLadder -> "Moving on"
                MineTalk,
                MineOres -> "Mining"
                MineSmelt -> "Smelting"
                MineHammer,
                MineDagger -> "Smithing a dagger"
                MineGate -> "Combat"
                CombatTalk,
                CombatWorn -> "Equipping items"
                CombatStats,
                CombatDagger -> "Equipment stats"
                CombatGear -> "Unequipping items"
                CombatStyles -> "Combat interface"
                CombatMelee -> "Attacking"
                CombatRange -> "Rat ranging"
                CombatLadder -> "Moving on"
                PrayerTalk,
                PrayerTab -> "Prayer menu"
                PrayerExit -> "Moving on"
                BankOpen -> "Banking"
                PollView -> "Moving on"
                AccountTalk,
                AccountMgmt -> "Account Management"
                AccountExit,
                Ironman -> "Your final instructor!"
                MagicTalk,
                MagicTab -> "Open up your final menu"
                MagicRunes,
                MagicCast -> "Magic casting"
                LeaveTalk,
                HomeTele -> "To the mainland!"
                Completed -> "Welcome to Lumbridge!"
            }

    val guideBody: String
        get() =
            when (this) {
                CharCreate ->
                    "Before you get started, you'll need to set the appearance of your character. Please use the open interface to set your appearance."
                ExpSelect ->
                    "Before you get started, please use the open interface to select your experience with Old School RuneScape."
                GielinorTalk ->
                    "When you're ready to get started, click on the Gielinor Guide. He is indicated by a flashing yellow arrow."
                GielinorSettings ->
                    "Please click on the flashing spanner icon found on the bottom right of your screen. This will display your settings menu."
                GielinorDoor ->
                    "It's time to meet your first instructor. To continue, all you need to do is click on the door. It's indicated by a flashing yellow arrow."
                SurvivalTalk ->
                    "Talk to the Survival Expert. She is indicated by a flashing yellow arrow."
                SurvivalInv ->
                    "To view the item you've been given, open your inventory. Click the flashing backpack icon on the right of your screen."
                SurvivalFish ->
                    "This is your inventory. You can view all of your items here, including the net you've just been given. Let's use it to catch some shrimp — click a fishing spot indicated by the yellow arrow."
                SurvivalSkills ->
                    "Click on the flashing bar graph icon near the inventory button to see your skills menu."
                SurvivalTools ->
                    "On this menu you can view your skills. Speak to the Survival Expert to continue."
                SurvivalWc ->
                    "It's time to cook your shrimp. However, you require a fire to do that which means you need some logs. Give it a go by clicking on one of the trees in the area."
                SurvivalFm ->
                    "Now that you have some logs, it's time to light a fire. First, click on the tinderbox in your inventory. Then, with the tinderbox highlighted, click on the logs."
                SurvivalCook ->
                    "Now it's time to get cooking. To do so, click on the shrimp in your inventory. Then, with the shrimp highlighted, click on a fire to cook them."
                SurvivalGate ->
                    "Well done! Speak to the survival expert if you want a recap, otherwise you can move on. Click on the gate shown and follow the path."
                ChefDoor ->
                    "Talk to learn the more advanced aspects of Cooking such as combining ingredients!"
                ChefTalk,
                ChefDough ->
                    "To make dough you must mix flour with water. Click on the flour in your inventory, then click on the water to combine them into dough."
                ChefBread ->
                    "Now you have made the dough, you can bake it into some bread. To do so, just click on the indicated range."
                ChefExit ->
                    "When navigating the world, you can either run or walk. You can use the flashing orb next to the minimap to toggle running."
                QuestDoor ->
                    "It's time to learn about quests! Just talk to the Quest Guide to get started."
                QuestTalk,
                QuestList -> "Click on the flashing icon to the left of your inventory."
                QuestLadder ->
                    "It's time to enter some caves. Click on the ladder to go down to the next area."
                MineTalk,
                MineOres ->
                    "To mine a rock, all you need to do is click on it. First up, try mining some tin."
                MineSmelt ->
                    "You now have some tin ore and some copper ore. You can smelt these into a bronze bar. Click on the indicated furnace."
                MineHammer,
                MineDagger ->
                    "To smith you'll need a hammer and enough metal bars. Click on the anvil, or alternatively use the bar on it."
                MineGate ->
                    "In this area you will find out about melee and ranged combat. Speak to the guide and he will tell you all about it."
                CombatTalk,
                CombatWorn ->
                    "You now have access to a new interface. Click on the flashing icon of a man, the one to the right of your backpack icon."
                CombatStats ->
                    "This is your worn inventory. In the bottom left corner, you will notice a flashing button. Click on it now."
                CombatDagger ->
                    "You can see what items you are wearing. Let's add something. Click your dagger to equip it."
                CombatGear ->
                    "Try swapping your dagger for the sword and shield that the combat instructor gave you."
                CombatStyles ->
                    "Click on the flashing crossed swords icon to open the combat interface."
                CombatMelee ->
                    "It's time to slay some rats! To attack a rat, all you have to do is click on it."
                CombatRange ->
                    "Equip the shortbow and arrows, then try killing another rat. You don't need to enter the pen this time."
                CombatLadder ->
                    "You have completed the tasks here. To move on, click on the indicated ladder."
                PrayerTalk,
                PrayerTab -> "Click on the flashing icon to open the Prayer menu."
                PrayerExit ->
                    "You're done here. Head through the door and follow the path round to the bank."
                BankOpen ->
                    "This is the Bank of Gielinor. To open your bank, just click on the indicated booth."
                PollView -> "To continue, close the bank and click on the indicated poll booth."
                AccountTalk,
                AccountMgmt -> "Click on the flashing icon to open your Account Management menu."
                AccountExit,
                Ironman ->
                    "You're almost finished on tutorial island. Pass through the door to find the path leading to your final instructor."
                MagicTalk,
                MagicTab -> "Open up the magic interface by clicking on the flashing icon."
                MagicRunes,
                MagicCast ->
                    "Look for the Wind Strike spell in your magic interface. Click on this spell to select it and then click on a chicken to cast it."
                LeaveTalk,
                HomeTele ->
                    "You're nearly finished with the tutorial. Speak with the magic instructor, then use your home teleport spell to teleport to Lumbridge!"
                Completed ->
                    "Welcome to Lumbridge! If you need some help, simply talk to the Lumbridge Guide."
            }

    companion object {
        val MAX_PROGRESS = HomeTele.varValue
    }
}
