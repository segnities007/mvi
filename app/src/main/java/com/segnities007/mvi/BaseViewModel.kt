package com.segnities007.mvi

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/**
 * MVIパターンの基底ViewModel
 *
 * Intent処理、State管理、Effect送信を統合的に管理する
 *
 * @param State UIの状態の型
 * @param Intent ユーザーの意図の型
 * @param Effect 一度きりのイベントの型
 */
abstract class BaseViewModel<State : UiState, Intent : UiIntent, Effect : UiEffect>(
    initialState: State
) : ViewModel() {

    private val _uiState = MutableStateFlow(initialState)
    val uiState: StateFlow<State> = _uiState.asStateFlow()

    private val _effect = Channel<Effect>(Channel.BUFFERED)
    val effect = _effect.receiveAsFlow()

    /**
     * Intentを処理する
     */
    abstract fun onIntent(intent: Intent)

    /**
     * Stateを更新する
     */
    protected  fun setState(newState: State) {
        _uiState.value = newState
    }

    /**
     * 一度きりのEffectを送信する
     */
    protected fun sendEffect(effect: Effect) {
        viewModelScope.launch {
            _effect.send(effect)
        }
    }
}
