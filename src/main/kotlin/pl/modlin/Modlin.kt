package pl.modlin

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import pl.modlin.ModuleState.*
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.reflect.KClass
import kotlin.reflect.KProperty

/**
 * Module is an essential component of modlin framework.
 * - It's a fragment of the program with own lifecycle and state management.
 * - Modules live in a hierarchy, where child module cannot be in higher state than its parent module.
 * -
 */
interface Module {
    /**
     * Name of the module, used for debugging. Can be overridden for custom name.
     */
    val name: String
        get() = this::class.simpleName ?: "Module"

    /**
     * Scope used for executing module's logic.
     */
    val controller: ModuleController
}

sealed interface ModuleState {

    val level: Int
    val inProgress: Boolean

    operator fun compareTo(other: ModuleState): Int {
        return level.compareTo(other.level)
    }

    data class Active(override val inProgress: Boolean = false) : ModuleState {
        override val level = 2
    }

    data class Paused(override val inProgress: Boolean = false) : ModuleState {
        override val level = 1
    }

    data class Closed(
        override val inProgress: Boolean = false,
        val error: Exception? = null
    ) : ModuleState {
        override val level = 0
    }

}

fun <T : ModuleState> T.setInProgress(inProgress: Boolean) : T {
    if(this.inProgress == inProgress) {
        return this
    }
    @Suppress("UNCHECKED_CAST")
    return when (this) {
        is Active -> this.copy(inProgress = inProgress)
        is Paused -> this.copy(inProgress = inProgress)
        is Closed -> this.copy(inProgress = inProgress)
    } as T
}

interface ModlinLifecycle : SuspendCloseable {

    val moduleState: StateFlow<ModuleState>

    val parent: ModlinLifecycle?

    suspend fun setState(state: ModuleState, isDirect: Boolean = true)

    override suspend fun close() {
        setState(Closed())
    }

}

/**
 * Module controller manages state of the module form its creation (exclusively),
 * to its closing. Closed module and controller of closed module, becomes useless.
 */
class ModuleController : SuspendCloseable, ModlinLifecycle {

    private val setStateMutex: Mutex = Mutex()

    private val _moduleState = MutableStateFlow<ModuleState>(Active())
    @Volatile private var directState: ModuleState = _moduleState.value

    /**
     * State flow emitting all module's state changes and holding current module state.
     */
    override val moduleState = _moduleState.asStateFlow()

    internal var _parent: ModlinLifecycle? = null
    override val parent: ModlinLifecycle? get() = _parent

    private val lifecycleHandleable: LinkedHashMap<Any, Any> = LinkedHashMap()

    /**
     * Similar to [addLifecycleHandleable], but without key,
     * so it cannot be later queried by [getLifecycleHandleable] method.
     */
    fun addLifecycleHandleable(any: Any) {
        if (any is AutoCloseable || any is SuspendCloseable || any is CoroutineScope) {
            synchronized(lifecycleHandleable) {
                lifecycleHandleable[any] = any
            }
        }
    }

    /**
     * Adds object which can handle [Module]'s lifecycle events.
     *
     * Currently following types are supported:
     * - [AutoCloseable]
     * - [SuspendCloseable]
     * - [Pausable]
     * - [CoroutineScope]
     *
     * Passing object of any other type, will be ignored and not handled.
     *
     * @param key identifier of the object to query it later, using [getLifecycleHandleable]
     * @param any object handling the lifecycle events.
     */
    fun addLifecycleHandleable(key: Any, any: Any) {
        if (any is AutoCloseable || any is SuspendCloseable) { // also includes ModlinLifecycle, since it implements SuspendClosable
            synchronized(lifecycleHandleable) {
                lifecycleHandleable[key] = any
            }
        }
    }

    /**
     * Queries objects previously added by [addLifecycleHandleable].
     * @return object or null if key does not point to any object. Please note,
     * that after closing module, all handleables are removed.
     */
    fun getLifecycleHandleable(key: Any): Any? = synchronized(lifecycleHandleable) {
        lifecycleHandleable[key]
    }

    /**
     * Sets state of the module. Closed module cannot be moved
     * again to state [ModuleState.Active]. Doing so, will result in
     * [IllegalStateException]. If module is already in desired state,
     * nothing happens.
     *
     * @param broker the state change broker. See [BrokerData] for more details.
     */
    override suspend fun setState(state: ModuleState, isDirect: Boolean) {

        setStateMutex.withLock {
            val currentState = _moduleState.value
            if (currentState::class == state::class) {
                return // Nothing to do
            }
            if (currentState is Closed) {
                throw IllegalStateException() // cannot modify state of closed module
            }
            val ps = parent?.moduleState?.value ?: Active()
            if(ps < state) {
                throw IllegalStateException("Parent has lower state ($ps) than target child state ($state).")
            }

            // Save latest direct state, to properly reconstruct paused
            // state, after resuming parent module
            if(isDirect || state is Closed) {
                directState = state
            } else if(state is Active) {
                // Handle resume of the parent module, by
                // restoring state from before
                setState(directState, true)
                return@withLock
            }

            // Begin state transition
            _moduleState.value = state.setInProgress(true)
            val handleables = getCopyOfLifecycleHandleable()
            if (state is Closed) {
                handleables.forEach {
                    try {
                        when (it) {
                            is AutoCloseable -> it.close()
                            is SuspendCloseable -> it.close()
                        }
                    } finally {
                        // Complete state transition
                        _moduleState.value = state.setInProgress(false)
                    }
                }
            } else {
                handleables.forEach {
                    when (it) {
                        is ModlinLifecycle -> it.setState(state, false)
                    }
                }
                // Complete state transition
                _moduleState.value = state.setInProgress(false)
            }
        }
    }

    private fun getCopyOfLifecycleHandleable() = synchronized(lifecycleHandleable) {
        lifecycleHandleable.values.toList()
    }

}

/**
 * Delegate to use for creating module like so:
 * ```kotlin
 * class ExampleModule : Module by modlin {}
 * ```
 */
val modlin: Module
    get() = object : Module {
        override val controller: ModuleController by lazy { ModuleController() }
    }

/**
 * Coroutine scope associated with the module.
 * Canceled automatically when the module is closed.
 */
context(module: Module)
val moduleScope: CoroutineScope
    get() {
        val scopeKeyName = "##module_scope"
        val mc = module.controller
        val current = mc.getLifecycleHandleable(scopeKeyName) as? CoroutineScope
        return current
            ?: CoroutineScope(EmptyCoroutineContext).also {
                mc.addLifecycleHandleable(scopeKeyName, it)
            }
    }

/**
 * Launches logic on [moduleScope].
 */
context(module: Module)
fun launch(block: CoroutineScope.() -> Unit): Job = moduleScope.launch(block = block)

/**
 * Launches logic on [moduleScope] when the module is [ModuleState.Active] and cancels it when module is [ModuleState.Paused] or [ModuleState.Closed].
 *
 * Logic will be executed again, when module is resumed.
 *
 * @return [Job] returned by `moduleScope.launch {}` method.
 */
context(module: Module)
fun launchPausable(block: CoroutineScope.() -> Unit): Job {
    val parentScope = moduleScope
    val childScope = CoroutineScope(
        parentScope.coroutineContext + Job(parentScope.coroutineContext[Job])
    )
    return moduleScope.launch {
        var currentJob: Job? = null
        module.state.collectLatest {
            if(it is ModuleState.Active) {
                // Launch pausable logic
                currentJob = childScope.launch(block = block)
            } else {
                // Cancel pausable logic
                currentJob?.cancelAndJoin()
            }
        }
    }
}

/**
 * Suspends if module is paused, release when module resumes.
 */
context(module: Module)
suspend fun pausePoint() {
    module.controller.moduleState.first { it !is ModuleState.Paused }
}

/**
 * Flow to observe current module state.
 */
val Module.state get() = controller.moduleState

/**
 * Parent controller of the module or null if the module has no parent.
 */
val Module.parentController get() = controller.parent

/**
 * Declares child module which will inherit lifecycle of the parent.
 */
context(module: Module)
inline fun <reified T : Module> child(noinline init: () -> T): ModuleRoute<T> {
    return ModuleRoute(
        parentModule = module,
        moduleClass = T::class,
        init = init,
    )
}


class ModuleRoute<T : Module>(
    val parentModule: Module,
    val moduleClass: KClass<T>,
    private val init: () -> T
) {
    private var currentInstance: T? = null
    private val onInstanceClose = AutoCloseable {
        currentInstance = null
    }

    val instance: T
        get() = currentInstance ?: init().also {
            parentModule.controller.addLifecycleHandleable(it)
            it.controller._parent = parentModule.controller
            it.controller.addLifecycleHandleable("##onInstanceClose", onInstanceClose)
            currentInstance = it
        }

    operator fun getValue(thisRef: Any?, property: KProperty<*>): T {
        return instance
    }

}