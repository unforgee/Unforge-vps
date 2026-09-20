package org.rsmod.server.app.modules

import org.rsmod.module.ExtendedModule
import org.rsmod.server.app.GameService
import org.rsmod.server.services.ConstructionService
import org.rsmod.server.services.DonorService
import org.rsmod.server.services.PrestigeService
import org.rsmod.server.services.QuestService
import org.rsmod.server.services.Service

object ServiceModule : ExtendedModule() {
    override fun bind() {
        addSetBinding<Service>(GameService::class.java)
        addSetBinding<Service>(DonorService::class.java)
        addSetBinding<Service>(PrestigeService::class.java)
        addSetBinding<Service>(ConstructionService::class.java)
        addSetBinding<Service>(QuestService::class.java)
    }
}
