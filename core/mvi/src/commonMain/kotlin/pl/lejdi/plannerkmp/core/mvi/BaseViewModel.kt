package pl.lejdi.plannerkmp.core.mvi

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

abstract class BaseViewModel<S : MviState, E : MviEvent, F : MviEffect> : ViewModel() {

    protected abstract fun createInitialState(): S

    // Lazy so createInitialState() runs after the subclass's own constructor
    // has finished, not while calling up through BaseViewModel's own init.
    private val _state: MutableStateFlow<S> by lazy { MutableStateFlow(createInitialState()) }
    val state: StateFlow<S> get() = _state.asStateFlow()

    private val _effect = Channel<F>(Channel.BUFFERED)
    val effect: Flow<F> = _effect.receiveAsFlow()

    abstract fun onEvent(event: E)

    protected fun setState(reduce: S.() -> S) {
        _state.update(reduce)
    }

    protected fun sendEffect(effect: F) {
        viewModelScope.launch { _effect.send(effect) }
    }
}
