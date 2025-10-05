# MVI モジュール

MVIアーキテクチャパターンの基盤を提供するモジュールです。

---

## 概要

### MVIとは

**MVI (Model-View-Intent)** は単方向データフローを実現するアーキテクチャパターンです。

```
User Action → Intent → ViewModel → UseCase → State → View
                          ↓
                       Effect
```

### 提供されるコンポーネント

- **UiState**: UIの状態（Loading / Success / Error）
- **UiIntent**: ユーザーのアクション
- **UiEffect**: 一度きりのイベント（ナビゲーション、Snackbarなど）
- **Reducer**: 状態変換の純粋関数
- **BaseViewModel**: MVI実装の基底クラス

### 開発フロー

1. UiState/Intent/Effectを定義
2. Reducerを実装
3. ViewModelを実装
4. Compose UIで使用

---

## 基本的な使い方

### 1. 状態・Intent・Effectの定義

```kotlin
// 状態定義（Loading/Success/Errorパターンを推奨）
sealed interface TimelineUiState : UiState {
    data object Loading : TimelineUiState
    
    data class Success(
        val posts: List<Post>,
        val isRefreshing: Boolean = false
    ) : TimelineUiState
    
    data class Error(
        val message: String,
        val cause: Throwable? = null
    ) : TimelineUiState
}

// Intent定義（sealed interfaceで定義）
sealed interface TimelineIntent : UiIntent {
    data object LoadTimeline : TimelineIntent
    data object RefreshTimeline : TimelineIntent
    data class LikePost(val postId: String) : TimelineIntent
}

// Effect定義（sealed interfaceで定義）
sealed interface TimelineEffect : UiEffect {
    data class ShowSnackbar(val message: String) : TimelineEffect
    data class NavigateToPost(val postId: String) : TimelineEffect
}
```

### 2. Reducerの実装

**Reducerは純粋関数です。現在の状態とIntentから新しい状態を生成します。**

```kotlin
class TimelineReducer : Reducer<TimelineUiState, TimelineIntent> {
    override fun reduce(
        state: TimelineUiState,
        intent: TimelineIntent
    ): TimelineUiState {
        return when (intent) {
            // ローディング開始
            is TimelineIntent.LoadTimeline -> {
                TimelineUiState.Loading
            }
            
            // リフレッシュ開始
            is TimelineIntent.RefreshTimeline -> {
                if (state !is TimelineUiState.Success) return state
                state.copy(isRefreshing = true)
            }
            
            // いいね（楽観的UI更新）
            is TimelineIntent.LikePost -> {
                if (state !is TimelineUiState.Success) return state
                
                val updatedPosts = state.posts.map { post ->
                    if (post.id == intent.postId) {
                        post.copy(isLikedByMe = true, likeCount = post.likeCount + 1)
                    } else {
                        post
                    }
                }
                state.copy(posts = updatedPosts)
            }
        }
    }
}
```

### 3. ViewModelの実装

**ViewModelはIntentを受け取り、UseCaseを実行し、Reducerを使って状態を更新します。**

```kotlin
class TimelineViewModel(
    private val getTimelineUseCase: GetTimelineUseCase,
    private val likePostUseCase: LikePostUseCase,
    reducer: TimelineReducer
) : BaseViewModel<TimelineUiState, TimelineIntent, TimelineEffect>(
    initialState = TimelineUiState.Loading,
    reducer = reducer
) {
    
    init {
        onIntent(TimelineIntent.LoadTimeline)
    }
    
    override fun onIntent(intent: TimelineIntent) {
        when (intent) {
            is TimelineIntent.LoadTimeline -> loadTimeline()
            is TimelineIntent.RefreshTimeline -> refreshTimeline()
            is TimelineIntent.LikePost -> likePost(intent.postId)
        }
    }
    
    private fun loadTimeline() {
        viewModelScope.launch {
            // Reducerで状態をLoadingに更新
            updateState(TimelineIntent.LoadTimeline)
            
            // UseCaseを実行
            val result = getTimelineUseCase()
            
            // 早期リターン: 失敗時
            if (result.isFailure) {
                setState(TimelineUiState.Error(
                    message = result.exceptionOrNull()?.message ?: "エラー"
                ))
                return@launch
            }
            
            // 成功時
            val posts = result.getOrNull() ?: return@launch
            setState(TimelineUiState.Success(posts = posts))
        }
    }
    
    private fun refreshTimeline() {
        viewModelScope.launch {
            updateState(TimelineIntent.RefreshTimeline)
            
            val result = getTimelineUseCase()
            
            if (result.isFailure) {
                // リフレッシュ失敗時は現在の状態を保持
                val current = uiState.value
                if (current is TimelineUiState.Success) {
                    setState(current.copy(isRefreshing = false))
                }
                sendEffect(TimelineEffect.ShowSnackbar("更新に失敗しました"))
                return@launch
            }
            
            val posts = result.getOrNull() ?: return@launch
            setState(TimelineUiState.Success(posts = posts, isRefreshing = false))
        }
    }
    
    private fun likePost(postId: String) {
        viewModelScope.launch {
            // 楽観的UI更新（Reducerを使用）
            updateState(TimelineIntent.LikePost(postId))
            
            // API呼び出し
            val result = likePostUseCase(postId)
            
            // 失敗時はロールバック
            if (result.isFailure) {
                val current = uiState.value
                if (current is TimelineUiState.Success) {
                    val revertedPosts = current.posts.map { post ->
                        if (post.id == postId) {
                            post.copy(isLikedByMe = false, likeCount = post.likeCount - 1)
                        } else {
                            post
                        }
                    }
                    setState(current.copy(posts = revertedPosts))
                }
                sendEffect(TimelineEffect.ShowSnackbar("いいねに失敗しました"))
            }
        }
    }
    
    // ヘルパーメソッド
    private fun updateState(intent: TimelineIntent) {
        setState(reducer.reduce(uiState.value, intent))
    }
    
    private fun setState(newState: TimelineUiState) {
        _uiState.value = newState
    }
}
```

**注意**: `BaseViewModel`には`_uiState`へのアクセスがないため、ViewModelで独自に実装する必要があります。

### 4. Compose UIでの使用

```kotlin
@Composable
fun TimelineScreen(
    viewModel: TimelineViewModel = koinViewModel(),
    navController: NavHostController
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    
    // Effect処理
    LaunchedEffect(Unit) {
        viewModel.effect.collect { effect ->
            when (effect) {
                is TimelineEffect.ShowSnackbar -> {
                    snackbarHostState.showSnackbar(effect.message)
                }
                is TimelineEffect.NavigateToPost -> {
                    navController.navigate("post/${effect.postId}")
                }
            }
        }
    }
    
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        when (val state = uiState) {
            is TimelineUiState.Loading -> {
                LoadingView()
            }
            
            is TimelineUiState.Success -> {
                TimelineContent(
                    posts = state.posts,
                    isRefreshing = state.isRefreshing,
                    onIntent = viewModel::onIntent
                )
            }
            
            is TimelineUiState.Error -> {
                ErrorView(
                    message = state.message,
                    onRetry = { viewModel.onIntent(TimelineIntent.LoadTimeline) }
                )
            }
        }
    }
}
```

---

## コンポーネント詳細

### UiState

**シンプルなインターフェース**: UIの状態を表現します。

```kotlin
interface UiState
```

**推奨パターン**: Loading/Success/Errorの3状態パターンを使うことを推奨します。

```kotlin
sealed interface MyUiState : UiState {
    data object Loading : MyUiState
    data class Success(val data: MyData) : MyUiState
    data class Error(val message: String) : MyUiState
}
```

### UiIntent

**ユーザーのアクション**: sealed interfaceで定義します。

```kotlin
interface UiIntent
```

実装例：
```kotlin
sealed interface MyIntent : UiIntent {
    data object Load : MyIntent
    data class Update(val id: String) : MyIntent
}
```

### UiEffect

**一度きりのイベント**: sealed interfaceで定義します。

```kotlin
sealed interface UiEffect
```

実装例：
```kotlin
sealed interface MyEffect : UiEffect {
    data class ShowSnackbar(val message: String) : MyEffect
    data class Navigate(val route: String) : MyEffect
}
```

### Reducer

**状態変換の純粋関数**: StateとIntentから新しいStateを生成します。

```kotlin
interface Reducer<State : UiState, Intent : UiIntent> {
    fun reduce(state: State, intent: Intent): State
}
```

### BaseViewModel

**MVI実装の基底クラス**: Reducerを必須とします。

- `uiState: StateFlow<State>` - 現在の状態
- `effect: Flow<Effect>` - 一度きりのイベント
- `onIntent(intent: Intent)` - Intentを処理する（実装必須）
- `sendEffect(effect: Effect)` - Effectを送信する

---

## テスト

### Reducerのテスト

```kotlin
class TimelineReducerTest {
    private val reducer = TimelineReducer()
    
    @Test
    fun `LoadTimelineでLoading状態に遷移する`() {
        val currentState = TimelineUiState.Success(emptyList())
        val intent = TimelineIntent.LoadTimeline
        
        val newState = reducer.reduce(currentState, intent)
        
        assertTrue(newState is TimelineUiState.Loading)
    }
    
    @Test
    fun `LikePostで投稿のいいね状態が更新される`() {
        val post = Post(id = "1", isLikedByMe = false, likeCount = 10)
        val currentState = TimelineUiState.Success(listOf(post))
        val intent = TimelineIntent.LikePost("1")
        
        val newState = reducer.reduce(currentState, intent)
        
        assertTrue(newState is TimelineUiState.Success)
        val updatedPost = (newState as TimelineUiState.Success).posts[0]
        assertTrue(updatedPost.isLikedByMe)
        assertEquals(11, updatedPost.likeCount)
    }
}
```

### ViewModelのテスト

```kotlin
class TimelineViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()
    
    private lateinit var getTimelineUseCase: GetTimelineUseCase
    private lateinit var likePostUseCase: LikePostUseCase
    private lateinit var reducer: TimelineReducer
    private lateinit var viewModel: TimelineViewModel
    
    @Before
    fun setup() {
        getTimelineUseCase = mockk()
        likePostUseCase = mockk()
        reducer = TimelineReducer()
        viewModel = TimelineViewModel(getTimelineUseCase, likePostUseCase, reducer)
    }
    
    @Test
    fun `初期状態はLoading`() {
        assertTrue(viewModel.uiState.value is TimelineUiState.Loading)
    }
    
    @Test
    fun `タイムライン取得成功時にSuccess状態に遷移`() = runTest {
        val posts = listOf(createTestPost())
        coEvery { getTimelineUseCase() } returns Result.success(posts)
        
        viewModel.onIntent(TimelineIntent.LoadTimeline)
        advanceUntilIdle()
        
        val state = viewModel.uiState.value
        assertTrue(state is TimelineUiState.Success)
        assertEquals(1, (state as TimelineUiState.Success).posts.size)
    }
    
    @Test
    fun `タイムライン取得失敗時にError状態に遷移`() = runTest {
        coEvery { getTimelineUseCase() } returns Result.failure(Exception("Network error"))
        
        viewModel.onIntent(TimelineIntent.LoadTimeline)
        advanceUntilIdle()
        
        val state = viewModel.uiState.value
        assertTrue(state is TimelineUiState.Error)
        assertEquals("Network error", (state as TimelineUiState.Error).message)
    }
}
```