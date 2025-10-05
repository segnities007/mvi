package com.segnities007.mvi

/**
 * 一度きりのイベント（副作用）を表現する
 *
 * ナビゲーション、Snackbar表示など
 * sealed interfaceとして定義すること
 */
sealed interface UiEffect
