package com.fenix.mobilemcp.memory

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import dagger.hilt.android.qualifiers.ApplicationContext

/**
 * F3: réplica fiel del esquema Fénix PC (server.py) en SQLite nativo Android.
 * Tablas: sesiones, entradas, kv (+extendidos), kv_historial, temps,
 * tareas_pasos, provider_health, misiones. Sin dependencias nuevas.
 */
@Singleton
class FenixMemoryDb @Inject constructor(@ApplicationContext context: Context) :
    SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS sesiones(
            id TEXT PRIMARY KEY, agente TEXT, titulo TEXT, tarea TEXT DEFAULT '',
            estado TEXT DEFAULT 'abierta', resumen TEXT DEFAULT '',
            creado TEXT, actualizado TEXT)""",
        )
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS entradas(
            id INTEGER PRIMARY KEY AUTOINCREMENT, session_id TEXT, agente TEXT,
            tipo TEXT, evento TEXT, detalle TEXT DEFAULT '', ts TEXT,
            destilado INTEGER DEFAULT 0)""",
        )
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS kv(
            clave TEXT, ambito TEXT, valor TEXT, actualizado TEXT,
            categoria TEXT DEFAULT 'general', tags TEXT DEFAULT '',
            expira_en TEXT DEFAULT NULL, creado TEXT DEFAULT '',
            ultima_lectura TEXT DEFAULT '', autor TEXT DEFAULT '',
            PRIMARY KEY(clave, ambito))""",
        )
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS kv_historial(
            id INTEGER PRIMARY KEY AUTOINCREMENT, clave TEXT, ambito TEXT,
            autor TEXT, accion TEXT, ts TEXT)""",
        )
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS temps(
            id INTEGER PRIMARY KEY AUTOINCREMENT, ruta TEXT UNIQUE,
            creado TEXT, expira TEXT)""",
        )
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS tareas_pasos(
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            task_id TEXT NOT NULL, agente TEXT NOT NULL,
            paso_num INTEGER NOT NULL, accion TEXT NOT NULL,
            resultado TEXT DEFAULT '', estado TEXT DEFAULT 'hecho',
            ts TEXT NOT NULL)""",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_tareas_pasos_task ON tareas_pasos(task_id)")
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS provider_health(
            nombre TEXT PRIMARY KEY,
            cooldown_hasta TEXT DEFAULT NULL,
            auth_failures INTEGER DEFAULT 0,
            last_error TEXT DEFAULT '',
            total_calls INTEGER DEFAULT 0,
            total_fallos INTEGER DEFAULT 0,
            round_robin_idx INTEGER DEFAULT 0,
            actualizado TEXT DEFAULT '')""",
        )
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS misiones(
            id TEXT PRIMARY KEY, ia TEXT NOT NULL, tipo TEXT DEFAULT 'general',
            instrucciones TEXT NOT NULL, schema_output TEXT DEFAULT '',
            estado TEXT DEFAULT 'encolada', resultado TEXT DEFAULT '',
            timeout_seg INTEGER DEFAULT 30, prioridad TEXT DEFAULT 'NORMAL',
            creado TEXT, completado TEXT)""",
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, old: Int, new: Int) {
        // F3: sin migraciones destructivas; las columnas ya vienen en onCreate.
        onCreate(db)
    }

    /** Guarda o actualiza una entrada kv + historial. Respeta TTL en expira_en (ISO-8601). */
    fun kvGuardar(
        clave: String,
        ambito: String,
        valor: String,
        categoria: String = "general",
        tags: String = "",
        expiraEn: String? = null,
        autor: String = "fenix-mobile",
    ) {
        val db = writableDatabase
        val now = Instant.now().toString()
        val cv = ContentValues().apply {
            put("clave", clave)
            put("ambito", ambito)
            put("valor", valor)
            put("actualizado", now)
            put("categoria", categoria)
            put("tags", tags)
            put("expira_en", expiraEn)
            put("autor", autor)
        }
        val rows = db.update("kv", cv, "clave=? AND ambito=?", arrayOf(clave, ambito))
        if (rows == 0) {
            cv.put("creado", now)
            db.insert("kv", null, cv)
        }
        val h = ContentValues().apply {
            put("clave", clave)
            put("ambito", ambito)
            put("autor", autor)
            put("accion", "guardar")
            put("ts", now)
        }
        db.insert("kv_historial", null, h)
    }

    /** Lee una entrada; null si no existe o expiró (y la purga). */
    fun kvLeer(clave: String, ambito: String): String? {
        val db = writableDatabase
        db.rawQuery(
            "SELECT valor, expira_en FROM kv WHERE clave=? AND ambito=?",
            arrayOf(clave, ambito),
        ).use { c ->
            if (!c.moveToFirst()) return null
            val exp = c.getString(1)
            if (exp != null && exp < Instant.now().toString()) {
                db.delete("kv", "clave=? AND ambito=?", arrayOf(clave, ambito))
                return null
            }
            db.execSQL(
                "UPDATE kv SET ultima_lectura=? WHERE clave=? AND ambito=?",
                arrayOf(Instant.now().toString(), clave, ambito),
            )
            return c.getString(0)
        }
    }

    /** Búsqueda simple por texto en clave/valor/tags (TF-IDF completo llega en F4). */
    fun kvBuscar(consulta: String, limite: Int = 5): List<Triple<String, String, String>> {
        val out = mutableListOf<Triple<String, String, String>>()
        readableDatabase.rawQuery(
            "SELECT clave, ambito, valor FROM kv WHERE clave LIKE ? OR valor LIKE ? OR tags LIKE ? LIMIT ?",
            arrayOf("%$consulta%", "%$consulta%", "%$consulta%", limite.toString()),
        ).use { c ->
            while (c.moveToNext()) out.add(Triple(c.getString(0), c.getString(1), c.getString(2)))
        }
        return out
    }

    /** Paso de bitácora (numeración secuencial por task_id). */
    fun bitacoraPaso(taskId: String, agente: String, accion: String, resultado: String, estado: String = "hecho") {
        val db = writableDatabase
        val next =
            db.rawQuery(
                "SELECT COALESCE(MAX(paso_num),0)+1 FROM tareas_pasos WHERE task_id=?",
                arrayOf(taskId),
            ).use { c -> c.moveToFirst(); c.getInt(0) }
        val cv = ContentValues().apply {
            put("task_id", taskId)
            put("agente", agente)
            put("paso_num", next)
            put("accion", accion)
            put("resultado", resultado)
            put("estado", estado)
            put("ts", Instant.now().toString())
        }
        db.insert("tareas_pasos", null, cv)
    }

    /** Registra enfriamiento (cooldown) por error 429/rate-limit. */
    fun setProviderCooldown(nombre: String, cooldownSeconds: Long, lastError: String = "") {
        val db = writableDatabase
        val now = Instant.now()
        val cooldownHasta = now.plusSeconds(cooldownSeconds).toString()
        val cv = ContentValues().apply {
            put("nombre", nombre)
            put("cooldown_hasta", cooldownHasta)
            put("last_error", lastError)
            put("actualizado", now.toString())
        }
        val rows = db.update("provider_health", cv, "nombre=?", arrayOf(nombre))
        if (rows == 0) {
            cv.put("total_fallos", 1)
            cv.put("total_calls", 1)
            db.insert("provider_health", null, cv)
        } else {
            db.execSQL(
                "UPDATE provider_health SET total_fallos = total_fallos + 1, total_calls = total_calls + 1 WHERE nombre=?",
                arrayOf(nombre),
            )
        }
    }

    /** Verifica si un proveedor está actualmente en periodo de enfriamiento (cooldown). */
    fun isProviderInCooldown(nombre: String): Boolean {
        val db = readableDatabase
        val now = Instant.now().toString()
        db.rawQuery(
            "SELECT cooldown_hasta FROM provider_health WHERE nombre=? AND cooldown_hasta > ?",
            arrayOf(nombre, now),
        ).use { c ->
            return c.moveToFirst()
        }
    }

    /** Registra éxito de llamada a proveedor. */
    fun reportProviderSuccess(nombre: String) {
        val db = writableDatabase
        val now = Instant.now().toString()
        val cv = ContentValues().apply {
            put("nombre", nombre)
            put("cooldown_hasta", null as String?)
            put("last_error", "")
            put("actualizado", now)
        }
        val rows = db.update("provider_health", cv, "nombre=?", arrayOf(nombre))
        if (rows == 0) {
            cv.put("total_calls", 1)
            cv.put("total_fallos", 0)
            db.insert("provider_health", null, cv)
        } else {
            db.execSQL(
                "UPDATE provider_health SET total_calls = total_calls + 1 WHERE nombre=?",
                arrayOf(nombre),
            )
        }
    }

    /** Registra fallo general de proveedor. */
    fun reportProviderFailure(nombre: String, error: String) {
        val db = writableDatabase
        val now = Instant.now().toString()
        val cv = ContentValues().apply {
            put("nombre", nombre)
            put("last_error", error)
            put("actualizado", now)
        }
        val rows = db.update("provider_health", cv, "nombre=?", arrayOf(nombre))
        if (rows == 0) {
            cv.put("total_calls", 1)
            cv.put("total_fallos", 1)
            db.insert("provider_health", null, cv)
        } else {
            db.execSQL(
                "UPDATE provider_health SET total_fallos = total_fallos + 1, total_calls = total_calls + 1 WHERE nombre=?",
                arrayOf(nombre),
            )
        }
    }

    companion object {
        const val DB_NAME = "fenix_memory.db"
        const val DB_VERSION = 1
    }
}
