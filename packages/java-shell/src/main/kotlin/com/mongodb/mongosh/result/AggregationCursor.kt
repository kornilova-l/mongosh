package com.mongodb.mongosh.result

import com.mongodb.mongosh.MongoShellContext
import org.graalvm.polyglot.Value

class AggregationCursor<out T> internal constructor(private var cursor: Value?, private var context: MongoShellContext?) : Cursor<T> {
    override fun hasNext(): Boolean {
        val (cursor, _) = checkClosed()
        return cursor.invokeMember("hasNext").asBoolean()
    }

    override fun next(): T {
        val (cursor, context) = checkClosed()
        if (!hasNext()) throw NoSuchElementException()
        return context.extract(cursor.invokeMember("next")).value as T
    }

    override fun toReplString(): String {
        val (cursor, context) = checkClosed()
        return context.extract(cursor.invokeMember("toReplString")).toReplString()
    }

    override fun close() {
        val (c, _) = checkClosed()
        c.invokeMember("close")
        cursor = null
        context = null
    }

    private fun checkClosed(): Pair<Value, MongoShellContext> {
        val cursor = this.cursor
        val context = this.context
        if (cursor == null || context == null) throw IllegalStateException("Cursor has already been closed")
        return cursor to context
    }
}