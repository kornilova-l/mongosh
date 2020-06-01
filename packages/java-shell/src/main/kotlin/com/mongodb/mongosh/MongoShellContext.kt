package com.mongodb.mongosh

import com.mongodb.DBRef
import com.mongodb.client.MongoClient
import com.mongodb.client.result.UpdateResult
import com.mongodb.mongosh.result.*
import com.mongodb.mongosh.service.Either
import com.mongodb.mongosh.service.JavaServiceProvider
import com.mongodb.mongosh.service.Left
import com.mongodb.mongosh.service.Right
import org.bson.Document
import org.bson.json.JsonReader
import org.bson.types.*
import org.graalvm.polyglot.Context
import org.graalvm.polyglot.Source
import org.graalvm.polyglot.Value
import org.graalvm.polyglot.proxy.ProxyExecutable
import org.graalvm.polyglot.proxy.ProxyObject
import org.graalvm.polyglot.proxy.ProxyObject.fromMap
import org.intellij.lang.annotations.Language
import java.io.Closeable
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.temporal.ChronoField
import java.time.temporal.TemporalAccessor
import java.time.temporal.TemporalField
import java.time.temporal.UnsupportedTemporalTypeException
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

internal class MongoShellContext(client: MongoClient) : Closeable {
    private val ctx: Context = Context.create()
    private val serviceProvider = JavaServiceProvider(client, this)
    private val shellEvaluator: Value
    private val bsonTypes: BsonTypes

    /** Java functions don't have js methods such as apply, bind, call etc.
     * So we need to create a real js function that wraps Java code */
    private val functionProducer = evalInner("(fun) => function inner() { return fun(...arguments); }")

    init {
        val setupScript = MongoShell::class.java.getResource("/js/all-standalone.js").readText()
        evalInner(setupScript, "all-standalone.js")
        val context = ctx.getBindings("js")
        val global = context["_global"]!!
        context.removeMember("_global")
        val shellInternalState = global.getMember("ShellInternalState").newInstance(serviceProvider)
        shellEvaluator = global.getMember("ShellEvaluator").newInstance(shellInternalState)
        val jsSymbol = context["Symbol"]!!
        shellInternalState.invokeMember("setCtx", context)
        initContext(context, jsSymbol)
        bsonTypes = BsonTypes(
                evalInner("new MaxKey().constructor"),
                evalInner("new MinKey().constructor"),
                evalInner("new ObjectId().constructor"),
                evalInner("new NumberDecimal().constructor"),
                evalInner("new Timestamp().constructor"),
                evalInner("new Code().constructor"),
                evalInner("new DBRef('').constructor"),
                evalInner("new BSONSymbol('').constructor"),
                evalInner("new NumberLong().constructor"),
                evalInner("new BinData(0, '').constructor"),
                evalInner("new HexData(0, '').constructor"))
    }

    private fun initContext(context: Value, jsSymbol: Value) {
        context.putMember("BSONSymbol", context["Symbol"])
        context.putMember("Symbol", jsSymbol)
        val date = evalInner("(dateHelper) => function inner() { return dateHelper(new.target !== undefined, ...arguments) }", "dateHelper_script")
                .execute(ProxyExecutable { args -> dateHelper(args[0].asBoolean(), args.drop(1)) })
        date.putMember("now", ProxyExecutable { System.currentTimeMillis() })
        context.putMember("Date", date)
        val isoDate = functionProducer.execute(ProxyExecutable { args -> dateHelper(true, args.toList()) })
        context.putMember("ISODate", isoDate)
        context.putMember("UUID", functionProducer.execute(ProxyExecutable { args -> if (args.isEmpty()) UUID.randomUUID() else UUID.fromString(args[0].asString()) }))
    }

    private fun dateHelper(createObject: Boolean, args: List<Value>): Any {
        val date = when {
            args.isEmpty() -> Date(System.currentTimeMillis())
            args.size == 1 -> {
                when (val v = extract(args[0]).value) {
                    is String -> parseDate(v)
                    is Number -> Date(v.toLong())
                    else -> throw IllegalArgumentException("Expected number of string. Got: ${args[0]} ($v)")
                }
            }
            else -> {
                if (args.any { !it.fitsInInt() }) throw IllegalArgumentException("Expected list of numbers. Got: ${args}")
                else {
                    val localDateTime = LocalDateTime.of(args[0].asInt(), args.getOrNull(1)?.asInt() ?: 1,
                            args.getOrNull(2)?.asInt() ?: 1, args.getOrNull(3)?.asInt() ?: 0,
                            args.getOrNull(4)?.asInt() ?: 0, args.getOrNull(5)?.asInt() ?: 0,
                            args.getOrNull(6)?.asInt() ?: 0)
                    Date(localDateTime.atZone(ZoneOffset.UTC).toInstant().toEpochMilli())
                }
            }
        }
        return if (createObject) date else DATE_FORMATTER.format(date.toInstant().atZone(ZoneOffset.UTC).toLocalDateTime())
    }

    private fun parseDate(str: String): Date {
        val accessor = DATE_FORMATTER.parse(str)
        val localDateTime = LocalDateTime.of(
                accessor.safeGet(ChronoField.YEAR) ?: 0,
                accessor.safeGet(ChronoField.MONTH_OF_YEAR) ?: 1,
                accessor.safeGet(ChronoField.DAY_OF_MONTH) ?: 1,
                accessor.safeGet(ChronoField.HOUR_OF_DAY) ?: 0,
                accessor.safeGet(ChronoField.MINUTE_OF_HOUR) ?: 0,
                accessor.safeGet(ChronoField.SECOND_OF_MINUTE) ?: 0,
                accessor.safeGet(ChronoField.NANO_OF_SECOND) ?: 0)
        return Date(localDateTime.atZone(ZoneOffset.UTC).toInstant().toEpochMilli())
    }

    private fun TemporalAccessor.safeGet(field: TemporalField): Int? {
        try {
            return this[field]
        } catch (ignored: UnsupportedTemporalTypeException) {
        }
        return null
    }

    operator fun get(value: String): Value? {
        return ctx.getBindings("js")[value]
    }

    internal operator fun Value.get(identifier: String): Value? {
        return getMember(identifier)
    }

    internal fun unwrapPromise(v: Value): Value {
        try {
            return if (v.instanceOf("Promise"))
                CompletableFuture<Value>().also { future ->
                    v.invokeMember("then", ProxyExecutable { args ->
                        future.complete(args[0])
                    }).invokeMember("catch", ProxyExecutable { args ->
                        val error = args[0]
                        if (error.isHostObject && error.asHostObject<Any>() is Throwable) {
                            future.completeExceptionally(error.asHostObject<Any>() as Throwable)
                        } else {
                            val message = error.toString() + (if (error.instanceOf("Error")) "\n${error.getMember("stack").asString()}" else "")
                            future.completeExceptionally(Exception(message))
                        }
                    })
                }.get(1, TimeUnit.SECONDS)
            else v
        } catch (e: ExecutionException) {
            throw e.cause ?: e
        }
    }

    fun extract(v: Value, type: String? = null): MongoShellResult<*> {
        return when {
            v.instanceOf("Promise") -> extract(unwrapPromise(v))
            type == "Help" -> extract(v["attr"]!!)
            type == "Cursor" -> FindCursorResult(FindCursor<Any?>(v, this))
            type == "AggregationCursor" -> AggregationCursorResult(AggregationCursor<Any?>(v, this))
            type == "InsertOneResult" -> InsertOneResult(v["acknowledged"]!!.asBoolean(), v["insertedId"]!!.asString())
            type == "DeleteResult" -> DeleteResult(v["acknowledged"]!!.asBoolean(), v["deletedCount"]!!.asLong())
            type == "UpdateResult" -> {
                val res = if (v["acknowledged"]!!.asBoolean()) {
                    UpdateResult.acknowledged(
                            v["matchedCount"]!!.asLong(),
                            v["modifiedCount"]!!.asLong(),
                            null
                    )
                } else UpdateResult.unacknowledged()
                MongoShellUpdateResult(res)
            }
            type == "BulkWriteResult" -> BulkWriteResult(
                    v["acknowledged"]!!.asBoolean(),
                    v["insertedCount"]!!.asLong(),
                    v["matchedCount"]!!.asLong(),
                    v["modifiedCount"]!!.asLong(),
                    v["deletedCount"]!!.asLong(),
                    v["upsertedCount"]!!.asLong(),
                    (extract(v["upsertedIds"]!!) as ArrayResult).value)
            type == "InsertManyResult" -> InsertManyResult(v["acknowledged"]!!.asBoolean(), extract(v["insertedIds"]!!).value as List<String>)
            v.instanceOf("RegExp") -> {
                val pattern = v["source"]!!.asString()
                val flags1 = v["flags"]!!.asString()
                var f = 0
                if (flags1.contains('m')) f = f.or(Pattern.MULTILINE)
                if (flags1.contains('i')) f = f.or(Pattern.CASE_INSENSITIVE)
                PatternResult(Pattern.compile(pattern, f))
            }
            v.instanceOf(bsonTypes.maxKey) -> MaxKeyResult()
            v.instanceOf(bsonTypes.minKey) -> MinKeyResult()
            v.instanceOf(bsonTypes.objectId) -> ObjectIdResult(JsonReader(v.invokeMember("toExtendedJSON").toString()).readObjectId())
            v.instanceOf(bsonTypes.numberDecimal) -> Decimal128Result(JsonReader(v.invokeMember("toExtendedJSON").toString()).readDecimal128())
            v.instanceOf(bsonTypes.bsonSymbol) -> SymbolResult(Symbol(JsonReader(v.invokeMember("toExtendedJSON").toString()).readSymbol()))
            v.instanceOf(bsonTypes.timestamp) -> {
                val timestamp = JsonReader(extract(v.invokeMember("toExtendedJSON")).value.toLiteral()).readTimestamp()
                BSONTimestampResult(BSONTimestamp(timestamp.time, timestamp.inc))
            }
            v.instanceOf(bsonTypes.code) -> {
                val scope = extract(v["scope"]!!).value as? Document
                val code = v["code"]!!.asString()
                if (scope == null) CodeResult(Code(code))
                else CodeWithScopeResult(CodeWithScope(code, scope))
            }
            v.instanceOf(bsonTypes.dbRef) -> {
                val databaseName = v["db"]?.let { if (it.isNull) null else it }?.asString()
                val collectionName = v["collection"]!!.asString()
                val value = extract(v["oid"]!!).value
                DBRefResult(DBRef(databaseName, collectionName, value))
            }
            v.instanceOf(bsonTypes.numberLong) -> LongResult(JsonReader(v.invokeMember("toExtendedJSON").toString()).readInt64())
            v.instanceOf(bsonTypes.binData) -> {
                val binary = JsonReader(v.invokeMember("toExtendedJSON").toString()).readBinaryData()
                BinaryResult(Binary(binary.type, binary.data))
            }
            v.instanceOf(bsonTypes.hexData) -> {
                val binary = JsonReader(v.invokeMember("toExtendedJSON").toString()).readBinaryData()
                BinaryResult(Binary(binary.type, binary.data))
            }
            v.isString -> StringResult(v.asString())
            v.isBoolean -> BooleanResult(v.asBoolean())
            v.fitsInInt() -> IntResult(v.asInt())
            v.fitsInLong() -> LongResult(v.asLong())
            v.fitsInFloat() -> FloatResult(v.asFloat())
            v.fitsInDouble() -> DoubleResult(v.asDouble())
            v.isNull -> NullResult
            v.isHostObject && v.asHostObject<Any?>() is Unit -> VoidResult
            v.isHostObject && v.asHostObject<Any?>() is Document -> DocumentResult(v.asHostObject())
            v.isHostObject && v.asHostObject<Any?>() is ObjectId -> ObjectIdResult(v.asHostObject())
            v.isHostObject && v.asHostObject<Any?>() is UUID -> UUIDResult(v.asHostObject())
            v.isHostObject && v.asHostObject<Any?>() is Date -> DateResult(v.asHostObject())
            v.hasArrayElements() -> ArrayResult((0 until v.arraySize).map { extract(v.getArrayElement(it)).value })
            v.canExecute() -> FunctionResult
            v.hasMembers() -> DocumentResult(Document(v.memberKeys.associateWith { key -> extract(v[key]!!).value })) // todo: handle recursion
            else -> throw IllegalArgumentException("unknown result: $v")
        }
    }

    private fun evalInner(@Language("js") script: String, name: String = "Unnamed"): Value {
        return ctx.eval(Source.newBuilder("js", script, name).buildLiteral())
    }

    fun eval(@Language("js") script: String, name: String): Value {
        val originalEval = ProxyExecutable { args ->
            evalInner(args[0].asString(), name)
        }
        return shellEvaluator.invokeMember("customEval", originalEval, script)
    }

    fun <T> toJsPromise(promise: Either<T>): Value {
        return when (promise) {
            is Right -> evalInner("(v) => new Promise(((resolve) => resolve(v)))", "resolved_promise_script").execute(promise.value)
            is Left -> evalInner("(v) => new Promise(((_, reject) => reject(v)))", "rejected_promise_script").execute(promise.value)
        }
    }

    override fun close() = serviceProvider.close()

    private fun Value.instanceOf(clazz: Value?): Boolean {
        return clazz != null && evalInner("(o, clazz) => o instanceof clazz", "instance_of_class_script").execute(this, clazz).asBoolean()
    }

    private fun Value.instanceOf(@Language("js") clazz: String): Boolean = evalInner("(x) => x instanceof $clazz", "instance_of_script").execute(this).asBoolean()

    fun toJs(o: Any?): Any? {
        return when (o) {
            is Iterable<*> -> toJs(o)
            is Map<*, *> -> toJs(o)
            else -> o
        }
    }

    private fun toJs(map: Map<*, *>): ProxyObject {
        val convertedMap: Map<String, Any?> = map.entries.asSequence()
                .filter { (key, _) -> key is String }
                .associate { e -> e.key as String to toJs(e.value) }
        return fromMap(convertedMap)
    }

    private fun toJs(list: Iterable<Any?>): Value {
        val array = evalInner("[]")
        list.forEachIndexed { index, v ->
            array.setArrayElement(index.toLong(), toJs(v))
        }
        return array
    }
}

private data class BsonTypes(
        val maxKey: Value,
        val minKey: Value,
        val objectId: Value,
        val numberDecimal: Value,
        val timestamp: Value,
        val code: Value,
        val dbRef: Value,
        val bsonSymbol: Value,
        val numberLong: Value,
        val binData: Value,
        val hexData: Value)

/**
 * yyyy-MM-dd['T'HH:mm:ss.SSS['Z']]
 */
private val DATE_FORMATTER = DateTimeFormatterBuilder()
        .parseCaseInsensitive()
        .append(DateTimeFormatter.ISO_LOCAL_DATE)
        .optionalStart()
        .appendLiteral('T')
        .append(DateTimeFormatter.ISO_LOCAL_TIME)
        .optionalStart()
        .appendLiteral('Z')
        .optionalEnd()
        .optionalEnd()
        .toFormatter()