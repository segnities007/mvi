package com.segnities007.mvi

/**
 * StateとIntentを受け取り、新しい状態を生成する
 *
 * @param State 管理する状態の型
 * @param Intent ユーザーの意図を表す型
 */
interface Reducer<State : UiState, Intent : UiIntent> {
    /**
     * Intentを処理して新しい状態を生成する
     */
    fun reduce(state: State, intent: Intent): State
}
