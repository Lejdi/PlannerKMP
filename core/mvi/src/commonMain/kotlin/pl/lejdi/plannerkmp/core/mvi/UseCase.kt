package pl.lejdi.plannerkmp.core.mvi

fun interface UseCase<in P, out R> {
    suspend operator fun invoke(params: P): R
}
