package com.tungsten.fcl.sc

import com.tungsten.fclcore.task.FetchTask
import com.tungsten.fclcore.task.Task
import com.tungsten.fclcore.task.TaskExecutor
import com.tungsten.fclcore.task.TaskListener
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * SC: our own «preparing the game» screen instead of FCL's grey launch dialog (Felix 27-10: «un mensaje creado por
 * nosotros … con una barra REAL donde ponga lo que se está haciendo, que no cierre el launcher y el tiempo estimado
 * según su rendimiento»). LauncherHelper hands us its task executor; every task that starts or ends updates the page
 * (scApp.carga) with the current stage in plain Spanish, done/total of that stage, overall progress, download speed and
 * an estimate of the time left measured on this phone (tasks per second of the stage, and bytes per second while
 * downloading). Nothing here is made up: if a number is not known it is not sent.
 */
object ScCarga {
    /** The app shows our screen; FCL's TaskDialog is not shown (LauncherHelper checks it). */
    const val ACTIVO = true

    // FCL stage → what the player reads, in the order they run
    private val ETAPAS = linkedMapOf(
        "launch.state.version" to "Comprobando la versión del juego",
        "launch.state.mods" to "Revisando los mods",
        "launch.state.java" to "Preparando Java",
        "launch.state.dependencies" to "Descargando recursos de Minecraft",
        "launch.state.logging_in" to "Iniciando sesión",
        "launch.state.waiting_launching" to "Abriendo el juego",
    )

    @Volatile private var actualExecutor: TaskExecutor? = null

    /** «Cancelar» / «Reintentar» of the screen: stops the launch (cargaFin follows from onStop). */
    fun cancelar() { actualExecutor?.cancel() }

    @JvmStatic
    fun adjuntar(executor: TaskExecutor) {
        actualExecutor = executor
        val inicio = System.currentTimeMillis()
        val hechos = ConcurrentHashMap<String, AtomicInteger>()
        val vistos = ConcurrentHashMap<String, AtomicInteger>()
        val inicioEtapa = ConcurrentHashMap<String, Long>()
        val actual = java.util.concurrent.atomic.AtomicReference("launch.state.version")
        val velocidad = AtomicLong(0)
        val ultimoEnvio = AtomicLong(0)

        fun etapa(t: Task<*>): String? = (t.inheritedStage ?: t.stage)?.takeIf { it in ETAPAS }

        fun enviar(forzar: Boolean = false) {
            val ahora = System.currentTimeMillis()
            if (!forzar && ahora - ultimoEnvio.get() < 250) return
            ultimoEnvio.set(ahora)
            val e = actual.get()
            val hecho = hechos[e]?.get() ?: 0
            val total = vistos[e]?.get() ?: 0
            val j = JSONObject()
                .put("etapa", e)
                .put("titulo", ETAPAS[e])
                .put("hechos", hecho)
                .put("total", total)
                .put("indice", ETAPAS.keys.indexOf(e))
                .put("etapas", JSONArray(ETAPAS.values.toList()))
                .put("bps", velocidad.get())
                .put("segundos", (ahora - inicio) / 1000)
            // time left of this stage, from its own pace on this phone (only once there is something to measure)
            val desde = inicioEtapa[e]
            if (desde != null && hecho >= 5 && total > hecho) {
                val porTarea = (ahora - desde).toDouble() / hecho
                j.put("restante", ((total - hecho) * porTarea / 1000).toLong())
            }
            ScUi.js("scApp.carga(${j})")
        }

        executor.addTaskListener(object : TaskListener() {
            override fun onReady(task: Task<*>) {
                val e = etapa(task) ?: return
                vistos.computeIfAbsent(e) { AtomicInteger() }.incrementAndGet()
                inicioEtapa.putIfAbsent(e, System.currentTimeMillis())
                if (ETAPAS.keys.indexOf(e) >= ETAPAS.keys.indexOf(actual.get())) actual.set(e)
                enviar()
            }

            override fun onFinished(task: Task<*>) {
                val e = etapa(task) ?: return
                hechos.computeIfAbsent(e) { AtomicInteger() }.incrementAndGet()
                enviar()
            }

            override fun onStop(success: Boolean, executor: TaskExecutor) {
                if (success) ScArranque.segundosPreparacion = (System.currentTimeMillis() - inicio) / 1000
                alVelocidad?.let { h -> runCatching { FetchTask.speedEvent.channel(FetchTask.SpeedEvent::class.java).unregister(h) } }
                ScUi.js("scApp.cargaFin(${success})")
            }
        })
        val handler = java.util.function.Consumer<FetchTask.SpeedEvent> { ev -> velocidad.set(ev.speed.toLong()); enviar() }
        alVelocidad = handler
        FetchTask.speedEvent.channel(FetchTask.SpeedEvent::class.java).registerWeak(handler)
        enviar(true)
    }

    // kept here so the weak registration lives as long as the launch
    @Volatile private var alVelocidad: java.util.function.Consumer<FetchTask.SpeedEvent>? = null
}
