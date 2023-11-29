package org.entcore.blog.search;

import com.mongodb.DBObject;
import com.mongodb.QueryBuilder;
import fr.wseduc.mongodb.MongoDb;
import fr.wseduc.mongodb.MongoQueryBuilder;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.entcore.blog.core.constants.Field;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.entcore.common.mongodb.MongoDbResult.validResultsHandler;

public class BlogSearch implements ISearch {
    private static final Logger log = LoggerFactory.getLogger(BlogSearch.class);
    private final MongoDb mongo;

    public BlogSearch() {
        this.mongo = MongoDb.getInstance();
    }

    @Override
    public Future<JsonArray> get(String userId, List<String> groupIds, List<String> searchWords, Integer page, Integer limit) {
        Promise<JsonArray> promise = Promise.promise();
        mongo.find(Field.BLOG_COLLECTION, MongoQueryBuilder.build(getMongoBuilder(userId, groupIds, searchWords)), validResultsHandler(result -> {
            if (result.isRight()) {
                promise.complete(result.right().getValue());
            } else {
                promise.fail(result.left().getValue());
            }
        }));

        return promise.future();
    }

    private QueryBuilder getMongoBuilder(String userId, List<String> groupIds, List<String> searchWordsArray) {
        final List<DBObject> groups = new ArrayList<>();
        groups.add(QueryBuilder.start(Field.USERID).is(userId).get());
        for (String gpId : groupIds) {
            groups.add(QueryBuilder.start(Field.GROUPID).is(gpId).get());
        }
        final QueryBuilder worldsQuery = QueryBuilder.start();
        worldsQuery.and(searchWordsArray.stream().map(s -> QueryBuilder.start(Field.TITLE).regex(Pattern.compile("(^|$|\\W)" + s + "(^|$|\\W)", (Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE))).get())
                .toArray(DBObject[]::new));
        return new QueryBuilder().and(worldsQuery.get(),
                new QueryBuilder().or(
                        QueryBuilder.start(Field.VISIBILITY).is(Field.PUBLIC).get(),
                        QueryBuilder.start("author.userId").is(userId).get(),
                        QueryBuilder.start(Field.SHARED).elemMatch(
                                new QueryBuilder().or(groups.toArray(new DBObject[0])).get()
                        ).get()).get());
    }

    @Override
    public JsonArray formatSearchResult(final JsonArray blogArray, final JsonArray columnsHeader) {
        try {
            final List<String> aHeader = columnsHeader.stream()
                    .filter(String.class::isInstance)
                    .map(String.class::cast)
                    .collect(Collectors.toList());
            final JsonArray result = new JsonArray();

            for (int i = 0; i < blogArray.size(); i++) {
                final JsonObject blog = blogArray.getJsonObject(i);
                final JsonObject blogFormatted = new JsonObject();
                if (blog != null) {
                    final String blogId = blog.getString(Field._ID);
                    blogFormatted.put(aHeader.get(0), blog.getString(Field.TITLE));
                    blogFormatted.put(aHeader.get(1), blog.getString(Field.DESCRIPTION, ""));
                    blogFormatted.put(aHeader.get(2), blog.getJsonObject(Field.MODIFIED));
                    blogFormatted.put(aHeader.get(3), blog.getJsonObject(Field.AUTHOR).getString(Field.USERNAME));
                    blogFormatted.put(aHeader.get(4), blog.getJsonObject(Field.AUTHOR).getString(Field.USERID));
                    blogFormatted.put(aHeader.get(5), "/blog#/view/" + blogId);
                    result.add(blogFormatted);
                }
            }
            return result;
        } catch (Exception e) {
            log.error(String.format("[Blog@%s::formatSearchResult] Failed to format search result %s:%s", this.getClass().getSimpleName(), e.getClass().getSimpleName(), e.getMessage()));
            return new JsonArray();
        }
    }
}
