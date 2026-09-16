package pl.lejdi.plannerkmp.feature.tasks.ui

// Shared between DashboardScreen's FAB and TaskEditScreen's root so the FAB visually morphs
// into the add-task form (Nav3's sharedBounds transition). Only used for adding a new task -
// editing an existing one navigates there from its TaskCard, not from the FAB.
internal const val AddTaskSharedKey = "addTaskFabExplode"
