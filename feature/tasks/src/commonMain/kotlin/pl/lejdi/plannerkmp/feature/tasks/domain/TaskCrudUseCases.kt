package pl.lejdi.plannerkmp.feature.tasks.domain

import pl.lejdi.plannerkmp.core.common.AppResult
import pl.lejdi.plannerkmp.core.mvi.UseCase
import pl.lejdi.plannerkmp.feature.tasks.data.TasksDatasource

class AddTask(
    private val datasource: TasksDatasource,
) : UseCase<Task, AppResult<Unit>> {
    override suspend fun invoke(params: Task): AppResult<Unit> = datasource.addTask(params)
}

class EditTask(
    private val datasource: TasksDatasource,
) : UseCase<Task, AppResult<Unit>> {
    override suspend fun invoke(params: Task): AppResult<Unit> = datasource.editTask(params)
}

class DeleteTask(
    private val datasource: TasksDatasource,
) : UseCase<Long, AppResult<Unit>> {
    override suspend fun invoke(params: Long): AppResult<Unit> = datasource.deleteTask(params)
}
