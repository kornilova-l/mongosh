"use strict";
var __assign = (this && this.__assign) || function () {
    __assign = Object.assign || function(t) {
        for (var s, i = 1, n = arguments.length; i < n; i++) {
            s = arguments[i];
            for (var p in s) if (Object.prototype.hasOwnProperty.call(s, p))
                t[p] = s[p];
        }
        return t;
    };
    return __assign.apply(this, arguments);
};
var __awaiter = (this && this.__awaiter) || function (thisArg, _arguments, P, generator) {
    function adopt(value) { return value instanceof P ? value : new P(function (resolve) { resolve(value); }); }
    return new (P || (P = Promise))(function (resolve, reject) {
        function fulfilled(value) { try { step(generator.next(value)); } catch (e) { reject(e); } }
        function rejected(value) { try { step(generator["throw"](value)); } catch (e) { reject(e); } }
        function step(result) { result.done ? resolve(result.value) : adopt(result.value).then(fulfilled, rejected); }
        step((generator = generator.apply(thisArg, _arguments || [])).next());
    });
};
var __generator = (this && this.__generator) || function (thisArg, body) {
    var _ = { label: 0, sent: function() { if (t[0] & 1) throw t[1]; return t[1]; }, trys: [], ops: [] }, f, y, t, g;
    return g = { next: verb(0), "throw": verb(1), "return": verb(2) }, typeof Symbol === "function" && (g[Symbol.iterator] = function() { return this; }), g;
    function verb(n) { return function (v) { return step([n, v]); }; }
    function step(op) {
        if (f) throw new TypeError("Generator is already executing.");
        while (_) try {
            if (f = 1, y && (t = op[0] & 2 ? y["return"] : op[0] ? y["throw"] || ((t = y["return"]) && t.call(y), 0) : y.next) && !(t = t.call(y, op[1])).done) return t;
            if (y = 0, t) op = [op[0] & 2, t.value];
            switch (op[0]) {
                case 0: case 1: t = op; break;
                case 4: _.label++; return { value: op[1], done: false };
                case 5: _.label++; y = op[1]; op = [0]; continue;
                case 7: op = _.ops.pop(); _.trys.pop(); continue;
                default:
                    if (!(t = _.trys, t = t.length > 0 && t[t.length - 1]) && (op[0] === 6 || op[0] === 2)) { _ = 0; continue; }
                    if (op[0] === 3 && (!t || (op[1] > t[0] && op[1] < t[3]))) { _.label = op[1]; break; }
                    if (op[0] === 6 && _.label < t[1]) { _.label = t[1]; t = op; break; }
                    if (t && _.label < t[2]) { _.label = t[2]; _.ops.push(op); break; }
                    if (t[2]) _.ops.pop();
                    _.trys.pop(); continue;
            }
            op = body.call(thisArg, _);
        } catch (e) { op = [6, e]; y = 0; } finally { f = t = 0; }
        if (op[0] & 5) throw op[1]; return { value: op[0] ? op[1] : void 0, done: true };
    }
};
var __importDefault = (this && this.__importDefault) || function (mod) {
    return (mod && mod.__esModule) ? mod : { "default": mod };
};
Object.defineProperty(exports, "__esModule", { value: true });
var mongodb_1 = require("mongodb");
var node_cursor_1 = __importDefault(require("./node-cursor"));
/**
 * Default driver options we always use.
 */
var DEFAULT_OPTIONS = Object.freeze({
    useNewUrlParser: true,
    useUnifiedTopology: true
});
/**
 * Encapsulates logic for communicating with a MongoDB instance via
 * the Node Driver.
 */
var NodeTransport = /** @class */ (function () {
    /**
     * Instantiate a new Node transport with the Node driver's connected
     * MongoClient instance.
     *
     * @param {MongoClient} mongoClient - The Node drivers' MongoClient instance.
     */
    function NodeTransport(mongoClient) {
        this.mongoClient = mongoClient;
    }
    /**
     * Create a NodeTransport from a URI.
     *
     * @param {String} uri - The URI.
     *
     * @returns {NodeTransport} The Node transport.
     */
    NodeTransport.fromURI = function (uri) {
        return __awaiter(this, void 0, void 0, function () {
            var mongoClient;
            return __generator(this, function (_a) {
                switch (_a.label) {
                    case 0:
                        mongoClient = new mongodb_1.MongoClient(uri, DEFAULT_OPTIONS);
                        return [4 /*yield*/, mongoClient.connect()];
                    case 1:
                        _a.sent();
                        return [2 /*return*/, new NodeTransport(mongoClient)];
                }
            });
        });
    };
    /**
     * Run an aggregation pipeline.
     *
     * @note: Passing a null collection will cause the
     *   aggregation to run on the DB.
     *
     * @param {String} database - The database name.
     * @param {String} collection - The collection name.
     * @param {Array} pipeline - The aggregation pipeline.
     * @param {Object} options - The pipeline options.
     * @param {Object} dbOptions - The database options (i.e. readConcern, writeConcern. etc).
     *
     * @returns {Cursor} The aggregation cursor.
     */
    NodeTransport.prototype.aggregate = function (database, collection, pipeline, options, dbOptions) {
        if (pipeline === void 0) { pipeline = []; }
        if (options === void 0) { options = {}; }
        if (dbOptions === void 0) { dbOptions = {}; }
        if (collection === null) {
            return this.db(database).aggregate(pipeline, options);
        }
        return new node_cursor_1.default(this.db(database).collection(collection).aggregate(pipeline, options));
    };
    /**
     * Run an aggregation pipeline only on a DB.
     *
     * @note: Passing a null collection will cause the
     *   aggregation to run on the DB.
     *
     * @param {String} database - The database name.
     * @param {Array} pipeline - The aggregation pipeline.
     * @param {Object} options - The pipeline options.
     * @param {Object} dbOptions - The database options (i.e. readConcern, writeConcern. etc).
     *
     * @returns {Cursor} The aggregation cursor.
     */
    NodeTransport.prototype.aggregateDb = function (database, pipeline, options, dbOptions) {
        if (pipeline === void 0) { pipeline = []; }
        if (options === void 0) { options = {}; }
        if (dbOptions === void 0) { dbOptions = {}; }
        return new node_cursor_1.default(this.db(database, dbOptions).aggregate(pipeline, options));
    };
    /**
     * Execute a mix of write operations.
     *
     * @param {String} database - The database name.
     * @param {String} collection - The collection name.
     * @param {Object} requests - The bulk write requests.
     * @param {Object} options - The bulk write options.
  
     * @param {Object} dbOptions - The database options (i.e. readConcern, writeConcern. etc).*
     * @returns {Promise} The promise of the result.
     */
    NodeTransport.prototype.bulkWrite = function (database, collection, requests, options, dbOptions) {
        if (requests === void 0) { requests = {}; }
        if (options === void 0) { options = {}; }
        if (dbOptions === void 0) { dbOptions = {}; }
        return this.db(database, dbOptions).collection(collection).
            bulkWrite(requests, options);
    };
    /**
     * Deprecated count command.
     *
     * @param {String} database - The db name.
     * @param {String} collection - The collection name.
     * @param {Object} query - The filter.
     * @param {Object} options - The count options.
     * @param {Object} dbOptions - The database option i.e. read/writeConcern
     *
     * @returns {Promise} The promise of the count.
     */
    NodeTransport.prototype.count = function (database, collection, query, options, dbOptions) {
        if (query === void 0) { query = {}; }
        if (options === void 0) { options = {}; }
        if (dbOptions === void 0) { dbOptions = {}; }
        return this.db(database, dbOptions).collection(collection).count(query);
    };
    /**
     * Get an exact document count from the collection.
     *
     * @param {String} database - The database name.
     * @param {String} collection - The collection name.
     * @param {Object} filter - The filter.
     * @param {Object} options - The count options.
     * @param {Object} dbOptions - The database options (i.e. readConcern, writeConcern. etc).*
     * @returns {Promise} The promise of the count.
     */
    NodeTransport.prototype.countDocuments = function (database, collection, filter, options, dbOptions) {
        if (filter === void 0) { filter = {}; }
        if (options === void 0) { options = {}; }
        if (dbOptions === void 0) { dbOptions = {}; }
        return this.db(database, dbOptions).collection(collection).
            countDocuments(filter, options);
    };
    /**
     * Delete multiple documents from the collection.
     *
     * @param {String} database - The database name.
     * @param {String} collection - The collection name.
     * @param {Object} filter - The filter.
     * @param {Object} options - The delete many options.
     * @param {Object} dbOptions - The database options (i.e. readConcern, writeConcern. etc).
  *
     * @returns {Promise} The promise of the result.
     */
    NodeTransport.prototype.deleteMany = function (database, collection, filter, options, dbOptions) {
        if (filter === void 0) { filter = {}; }
        if (options === void 0) { options = {}; }
        if (dbOptions === void 0) { dbOptions = {}; }
        return this.db(database, dbOptions).collection(collection).
            deleteMany(filter, options);
    };
    /**
     * Delete one document from the collection.
     *
     * @param {String} database - The database name.
     * @param {String} collection - The collection name.
     * @param {Object} filter - The filter.
     * @param {Object} options - The delete one options.
     * @param {Object} dbOptions - The database options (i.e. readConcern, writeConcern. etc).
    *
     * @returns {Promise} The promise of the result.
     */
    NodeTransport.prototype.deleteOne = function (database, collection, filter, options, dbOptions) {
        if (filter === void 0) { filter = {}; }
        if (options === void 0) { options = {}; }
        if (dbOptions === void 0) { dbOptions = {}; }
        return this.db(database, dbOptions).collection(collection).
            deleteOne(filter, options);
    };
    /**
     * Get distinct values for the field.
     *
     * @param {String} database - The database name.
     * @param {String} collection - The collection name.
     * @param {String} fieldName - The field name.
     * @param {Object} filter - The filter.
     * @param {Object} options - The distinct options.
     * @param {Object} dbOptions - The database options (i.e. readConcern, writeConcern. etc).
    *
     * @returns {Cursor} The cursor.
     */
    NodeTransport.prototype.distinct = function (database, collection, fieldName, filter, options, dbOptions) {
        if (filter === void 0) { filter = {}; }
        if (options === void 0) { options = {}; }
        if (dbOptions === void 0) { dbOptions = {}; }
        return this.db(database, dbOptions).collection(collection).
            distinct(fieldName, filter, options);
    };
    /**
     * Get an estimated document count from the collection.
     *
     * @param {String} database - The database name.
     * @param {String} collection - The collection name.
     * @param {Object} options - The count options.
     * @param {Object} dbOptions - The database options (i.e. readConcern, writeConcern. etc).
    *
     * @returns {Promise} The promise of the result.
     */
    NodeTransport.prototype.estimatedDocumentCount = function (database, collection, options, dbOptions) {
        if (options === void 0) { options = {}; }
        if (dbOptions === void 0) { dbOptions = {}; }
        return this.db(database, dbOptions).collection(collection).
            estimatedDocumentCount(options);
    };
    /**
     * Find documents in the collection.
     *
     * @param {String} database - The database name.
     * @param {String} collection - The collection name.
     * @param {Object} filter - The filter.
     * @param {Object} options - The find options.
    *
     * @returns {Cursor} The cursor.
     */
    NodeTransport.prototype.find = function (database, collection, filter, options) {
        if (filter === void 0) { filter = {}; }
        if (options === void 0) { options = {}; }
        var findOptions = __assign({}, options);
        if ('allowPartialResults' in findOptions) {
            // @ts-ignore
            findOptions.partial = findOptions.allowPartialResults;
        }
        if ('noCursorTimeout' in findOptions) {
            // @ts-ignore
            findOptions.timeout = findOptions.noCursorTimeout;
        }
        if ('tailable' in findOptions) {
            // @ts-ignore
            findOptions.cursorType = findOptions.tailable ? 'TAILABLE' : 'NON_TAILABLE'; // TODO
        }
        return new node_cursor_1.default(this.db(database).collection(collection).find(filter, options));
    };
    /**
     * Find one document and delete it.
     *
     * @param {String} database - The database name.
     * @param {String} collection - The collection name.
     * @param {Object} filter - The filter.
     * @param {Object} options - The find options.
     * @param {Object} dbOptions - The database options (i.e. readConcern, writeConcern. etc).
    *
     * @returns {Promise} The promise of the result.
     */
    NodeTransport.prototype.findOneAndDelete = function (database, collection, filter, options, dbOptions) {
        if (filter === void 0) { filter = {}; }
        if (options === void 0) { options = {}; }
        if (dbOptions === void 0) { dbOptions = {}; }
        return this.db(database, dbOptions).collection(collection).
            findOneAndDelete(filter, options);
    };
    /**
     * Find one document and replace it.
     *
     * @param {String} database - The database name.
     * @param {String} collection - The collection name.
     * @param {Object} filter - The filter.
     * @param {Object} replacement - The replacement.
     * @param {Object} options - The find options.
    *
     * @returns {Promise} The promise of the result.
     */
    NodeTransport.prototype.findOneAndReplace = function (database, collection, filter, replacement, options) {
        if (filter === void 0) { filter = {}; }
        if (replacement === void 0) { replacement = {}; }
        if (options === void 0) { options = {}; }
        var findOneAndReplaceOptions = __assign({}, options);
        if ('returnDocument' in options) {
            // @ts-ignore
            findOneAndReplaceOptions.returnOriginal = options.returnDocument;
            // @ts-ignore
            delete findOneAndReplaceOptions.returnDocument;
        }
        return this.db(database).collection(collection).
            findOneAndReplace(filter, replacement, findOneAndReplaceOptions);
    };
    /**
     * Find one document and update it.
     *
     * @param {String} database - The database name.
     * @param {String} collection - The collection name.
     * @param {Object} filter - The filter.
     * @param {(Object|Array)} update - The update.
     * @param {Object} options - The find options.
    *
     * @returns {Promise} The promise of the result.
     */
    NodeTransport.prototype.findOneAndUpdate = function (database, collection, filter, update, options) {
        if (filter === void 0) { filter = {}; }
        if (update === void 0) { update = {}; }
        if (options === void 0) { options = {}; }
        var findOneAndUpdateOptions = __assign({}, options);
        if ('returnDocument' in options) {
            // @ts-ignore
            findOneAndReplaceOptions.returnOriginal = options.returnDocument;
            // @ts-ignore
            delete findOneAndReplaceOptions.returnDocument;
        }
        return this.db(database).collection(collection).
            findOneAndUpdate(filter, update, findOneAndUpdateOptions);
    };
    /**
     * Insert many documents into the colleciton.
     *
     * @param {String} database - The database name.
     * @param {String} collection - The collection name.
     * @param {Array} docs - The documents.
     * @param {Object} options - The insert many options.
     * @param {Object} dbOptions - The database options (i.e. readConcern, writeConcern. etc).
    *
     * @returns {Promise} The promise of the result.
     */
    NodeTransport.prototype.insertMany = function (database, collection, docs, options, dbOptions) {
        if (docs === void 0) { docs = []; }
        if (options === void 0) { options = {}; }
        if (dbOptions === void 0) { dbOptions = {}; }
        return this.db(database, dbOptions).collection(collection).
            insertMany(docs, options);
    };
    /**
     * Insert one document into the collection.
     *
     * @param {String} database - The database name.
     * @param {String} collection - The collection name.
     * @param {Object} doc - The document.
     * @param {Object} options - The insert one options.
     * @param {Object} dbOptions - The database options (i.e. readConcern, writeConcern. etc).
    *
     * @returns {Promise} The promise of the result.
     */
    NodeTransport.prototype.insertOne = function (database, collection, doc, options, dbOptions) {
        if (doc === void 0) { doc = {}; }
        if (options === void 0) { options = {}; }
        if (dbOptions === void 0) { dbOptions = {}; }
        return this.db(database, dbOptions).collection(collection).
            insertOne(doc, options);
    };
    /**
     * Is the collection capped?
     *
     * @param {String} database - The database name.
     * @param {String} collection - The collection name.
     * @returns {Promise} The promise of the result.
     */
    NodeTransport.prototype.isCapped = function (database, collection) {
        return this.db(database).collection(collection).isCapped();
    };
    /**
     * Deprecated remove command.
     *
     * @param {String} database - The db name.
     * @param {String} collection - The collection name.
     * @param {Object} query - The query.
     * @param {Object} options - The options.
     * @return {Promise}
     */
    NodeTransport.prototype.remove = function (database, collection, query, options) {
        if (query === void 0) { query = {}; }
        if (options === void 0) { options = {}; }
        return this.db(database).collection(collection).remove(query, options);
    };
    /**
     * Deprecated save command.
     *
     * @note: Shell API sets writeConcern via options in Document,
     * node driver flat.
     *
     * @param {String} database - The db name.
     * @param {String} collection - The collection name.
     * @param {Object} doc - The doc.
     * @param {Object} options - The options.
     * @param {Object} dbOptions - The DB options
     * @return {Promise}
     */
    NodeTransport.prototype.save = function (database, collection, doc, options, dbOptions) {
        if (doc === void 0) { doc = {}; }
        if (options === void 0) { options = {}; }
        if (dbOptions === void 0) { dbOptions = {}; }
        return this.db(database, dbOptions).collection(collection).save(doc, options);
    };
    /**
     * Replace a document with another.
     *
     * @param {String} database - The database name.
     * @param {String} collection - The collection name.
     * @param {Object} filter - The filter.
     * @param {Object} replacement - The replacement document for matches.
     * @param {Object} options - The replace options.
     * @param {Object} dbOptions - The database options (i.e. readConcern, writeConcern. etc).
    *
     * @returns {Promise} The promise of the result.
     */
    NodeTransport.prototype.replaceOne = function (database, collection, filter, replacement, options, dbOptions) {
        if (filter === void 0) { filter = {}; }
        if (replacement === void 0) { replacement = {}; }
        if (options === void 0) { options = {}; }
        if (dbOptions === void 0) { dbOptions = {}; }
        return this.db(database, dbOptions).collection(collection).
            replaceOne(filter, replacement, options);
    };
    /**
     * Run a command against the database.
     *
     * @param {String} database - The database name.
     * @param {Object} spec - The command specification.
     * @param {Object} options - The database options.
    *
     * @returns {Promise} The promise of command results.
     */
    NodeTransport.prototype.runCommand = function (database, spec, options) {
        if (spec === void 0) { spec = {}; }
        if (options === void 0) { options = {}; }
        return this.db(database).command(spec, options);
    };
    /**
     * Update many document.
     *
     * @param {String} database - The database name.
     * @param {String} collection - The collection name.
     * @param {Object} filter - The filter.
     * @param {(Object|Array)} update - The updates.
     * @param {Object} options - The update options.
     * @param {Object} dbOptions - The database options (i.e. readConcern, writeConcern. etc).
    *
     * @returns {Promise} The promise of the result.
     */
    NodeTransport.prototype.updateMany = function (database, collection, filter, update, options, dbOptions) {
        if (filter === void 0) { filter = {}; }
        if (update === void 0) { update = {}; }
        if (options === void 0) { options = {}; }
        if (dbOptions === void 0) { dbOptions = {}; }
        return this.db(database, dbOptions).collection(collection).
            updateMany(filter, update, options);
    };
    /**
     * Update a document.
     *
     * @param {String} database - The database name.
     * @param {String} collection - The collection name.
     * @param {Object} filter - The filter.
     * @param {(Object|Array)} update - The updates.
     * @param {Object} options - The update options.
     * @param {Object} dbOptions - The database options (i.e. readConcern, writeConcern. etc).
    *
     * @returns {Promise} The promise of the result.
     */
    NodeTransport.prototype.updateOne = function (database, collection, filter, update, options, dbOptions) {
        if (filter === void 0) { filter = {}; }
        if (update === void 0) { update = {}; }
        if (options === void 0) { options = {}; }
        if (dbOptions === void 0) { dbOptions = {}; }
        return this.db(database, dbOptions).collection(collection).
            updateOne(filter, update, options);
    };
    /**
     * Get the DB Document from the client.
     *
     * @param {String} name - The database name.
     * @param {Object} options - The DB options.
     *
     * @returns {Db} The database.
     */
    NodeTransport.prototype.db = function (name, options) {
        if (options === void 0) { options = {}; }
        if (Object.keys(options).length !== 0) {
            return this.mongoClient.db(name, options);
        }
        return this.mongoClient.db(name);
    };
    return NodeTransport;
}());
exports.default = NodeTransport;