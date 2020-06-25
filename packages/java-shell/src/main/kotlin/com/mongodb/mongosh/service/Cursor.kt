package com.mongodb.mongosh.service

import com.mongodb.Tag
import com.mongodb.TagSet
import com.mongodb.client.MongoCursor
import com.mongodb.mongosh.MongoShellContext
import com.mongodb.mongosh.result.ArrayResult
import com.mongodb.mongosh.result.DocumentResult
import org.bson.Document
import org.graalvm.polyglot.HostAccess
import org.graalvm.polyglot.Value

internal class Cursor(private var helper: MongoIterableHelper<*>, private val context: MongoShellContext) : ServiceProviderCursor {
    private var iterator: MongoCursor<out Any?>? = null
    private var closed = false

    private fun getOrCreateIterator(): MongoCursor<out Any?> {
        var it = iterator
        if (it == null) {
            it = helper.iterable.iterator()
            iterator = it
        }
        return it
    }

    @HostAccess.Export
    override fun addOption(option: Int): Cursor {
        throw NotImplementedError("addOption is not supported") // not supported in driver
    }

    @HostAccess.Export
    override fun allowPartialResults(): Cursor {
        checkQueryNotExecuted()
        helper.allowPartialResults()
        return this
    }

    @HostAccess.Export
    override fun batchSize(v: Int): Cursor {
        checkQueryNotExecuted()
        helper.batchSize(v)
        return this
    }

    @HostAccess.Export
    override fun close(options: Value) {
        closed = true
        iterator?.close()
    }

    @HostAccess.Export
    override fun clone(): Cursor {
        checkQueryNotExecuted()
        throw NotImplementedError("clone is not supported") // not supported in driver
    }

    @HostAccess.Export
    override fun isClosed(): Boolean = closed

    @HostAccess.Export
    override fun collation(v: Value): Cursor {
        checkQueryNotExecuted()
        if (!v.hasMembers()) {
            throw IllegalArgumentException("Expected one argument of type object. Got: $v")
        }
        val collation = toDocument(context, v)
        helper.collation(collation)
        return this
    }

    @HostAccess.Export
    override fun comment(v: String): Cursor {
        checkQueryNotExecuted()
        helper.comment(v)
        return this
    }

    @HostAccess.Export
    override fun count(): Int {
        checkQueryNotExecuted()
        return helper.count()
    }

    @HostAccess.Export
    override fun forEach(func: Value) {
        if (!func.canExecute()) {
            throw IllegalArgumentException("Expected one argument of type function. Got: $func")
        }
        getOrCreateIterator().forEach { v ->
            func.execute(context.toJs(v))
        }
    }

    @HostAccess.Export
    override fun hasNext(): Boolean = getOrCreateIterator().hasNext()

    @HostAccess.Export
    override fun hint(v: Value): Cursor {
        checkQueryNotExecuted()
        if (!(v.hasMembers() || v.isString)) {
            throw IllegalArgumentException("Expected one argument of type string or object. Got: $v")
        }
        if (v.isString) {
            helper.hint(v.asString())
        } else if (v.hasMembers()) {
            helper.hint(toDocument(context, v))
        }
        return this
    }

    @HostAccess.Export
    override fun isExhausted(): Boolean {
        return isClosed() && !hasNext()
    }

    @HostAccess.Export
    override fun itcount(): Int {
        checkQueryNotExecuted()
        return helper.itcount()
    }

    @HostAccess.Export
    override fun limit(v: Int): Cursor {
        checkQueryNotExecuted()
        helper.limit(v)
        return this
    }

    @HostAccess.Export
    override fun map(func: Value): Cursor {
        checkQueryNotExecuted()
        if (!func.canExecute()) {
            throw IllegalArgumentException("Expected one argument of type function. Got: $func")
        }
        helper = helper.map(func)
        return this
    }

    @HostAccess.Export
    override fun max(v: Value): Cursor {
        checkQueryNotExecuted()
        if (!v.hasMembers()) {
            throw IllegalArgumentException("Expected one argument of type object. Got: $v")
        }
        helper.max(toDocument(context, v))
        return this
    }

    @HostAccess.Export
    override fun maxTimeMS(v: Long): Cursor {
        checkQueryNotExecuted()
        helper.maxTimeMS(v)
        return this
    }

    @HostAccess.Export
    override fun min(v: Value): ServiceProviderCursor {
        checkQueryNotExecuted()
        if (!v.hasMembers()) {
            throw IllegalArgumentException("Expected one argument of type object. Got: $v")
        }
        helper.min(toDocument(context, v))
        return this
    }

    @HostAccess.Export
    override fun next(): Any? = getOrCreateIterator().next()

    @HostAccess.Export
    override fun noCursorTimeout(): Cursor {
        checkQueryNotExecuted()
        helper.noCursorTimeout()
        return this
    }

    @HostAccess.Export
    override fun oplogReplay(): Cursor {
        checkQueryNotExecuted()
        helper.oplogReplay()
        return this
    }

    @HostAccess.Export
    override fun projection(v: Value): ServiceProviderCursor {
        checkQueryNotExecuted()
        helper.projection(toDocument(context, v))
        return this
    }

    @HostAccess.Export
    override fun readConcern(v: String): ServiceProviderCursor {
        checkQueryNotExecuted()
        helper = helper.readConcern(v)
        return this
    }

    @HostAccess.Export
    override fun readPref(v: String, tagSet: Value?): Cursor {
        checkQueryNotExecuted()
        val tagSetList = toList(tagSet, "tagSet")?.filterIsInstance<Document>()
        val tags = tagSetList
                ?.map { tagSet ->
                    val listOfTags = tagSet.keys.mapTo(mutableListOf()) { key -> Tag(key, tagSet[key] as String) }
                    TagSet(listOfTags)
                }
        helper = helper.readPrev(v, tags)
        return this
    }

    private fun toList(value: Value?, fieldName: String): List<Any?>? {
        if (value == null || value.isNull) return null
        if (!value.hasArrayElements()) {
            throw IllegalArgumentException("$fieldName should be a list: $value")
        }
        return (context.extract(value) as ArrayResult).value
    }

    @HostAccess.Export
    override fun returnKey(v: Value): Cursor {
        checkQueryNotExecuted()
        helper.returnKey(if (v.isBoolean) v.asBoolean() else true)
        return this
    }

    @HostAccess.Export
    override fun size(): Value {
        throw NotImplementedError("size is not supported")
    }

    @HostAccess.Export
    override fun skip(v: Int): Cursor {
        checkQueryNotExecuted()
        helper.skip(v)
        return this
    }

    @HostAccess.Export
    override fun sort(spec: Value): Cursor {
        checkQueryNotExecuted()
        helper.sort(toDocument(context, spec))
        return this
    }

    @HostAccess.Export
    override fun tailable(): Cursor {
        checkQueryNotExecuted()
        helper.tailable()
        return this
    }

    @HostAccess.Export
    override fun toArray(): Any? {
        checkQueryNotExecuted()
        return context.toJs(helper.toArray())
    }

    @HostAccess.Export
    override fun explain(verbosity: String?): Any? {
        checkQueryNotExecuted()
        return helper.explain(verbosity)
    }

    private fun checkQueryNotExecuted() {
        check(iterator == null) { "query already executed" }
    }

    private fun toDocument(context: MongoShellContext, map: Value?): Document {
        return if (map == null || map.isNull) Document()
        else (context.extract(map) as DocumentResult).value
    }
}
