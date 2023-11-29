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
import org.entcore.blog.Blog;
import org.entcore.blog.core.constants.Field;
import org.entcore.common.service.impl.MongoDbSearchService;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.entcore.common.mongodb.MongoDbResult.validResultsHandler;

public class PostSearch implements ISearch {
    private static final Logger log = LoggerFactory.getLogger(BlogSearch.class);
    private final MongoDb mongo;

    public PostSearch() {
        this.mongo = MongoDb.getInstance();
    }

    @Override
    public Future<JsonArray> get(String userId, List<String> groupIds, List<String> searchWords, Integer page, Integer limit) {
        return this.getBlogId(userId, groupIds)
                .compose(listIds -> this.searchPosts(page, limit, searchWords, listIds));
    }

    private Future<List<String>> getBlogId(String userId, List<String> groupIdsLst) {
        Promise<List<String>> promise = Promise.promise();
        final List<DBObject> groups = new ArrayList<>();
        groups.add(QueryBuilder.start("userId").is(userId).get());
        for (String gpId : groupIdsLst) {
            groups.add(QueryBuilder.start("groupId").is(gpId).get());
        }

        final QueryBuilder rightsQuery = new QueryBuilder().or(
                QueryBuilder.start("author.userId").is(userId).get(),
                QueryBuilder.start("shared").elemMatch(
                        new QueryBuilder().or(groups.toArray(new DBObject[groups.size()])).get()
                ).get());

        final JsonObject projection = new JsonObject();
        projection.put("_id", 1);
        mongo.find(Blog.BLOGS_COLLECTION, MongoQueryBuilder.build(rightsQuery), null, projection, validResultsHandler(result -> {
            if (result.isRight()) {
                List<String> blogIdList = result.right().getValue().stream()
                        .filter(JsonObject.class::isInstance)
                        .map(JsonObject.class::cast)
                        .map(blogInfo -> blogInfo.getString("_id"))
                        .collect(Collectors.toList());
                promise.complete(blogIdList);
            } else {
                promise.fail(result.left().getValue());
            }
        }));

        return promise.future();
    }

    private Future<JsonArray> searchPosts(int page, int limit, List<String> searchWords, final List<String> setIds) {
        Promise<JsonArray> promise = Promise.promise();
        final int skip = (0 == page) ? -1 : page * limit;

        final QueryBuilder worldsQuery = QueryBuilder.start();

        // toArray(DBObject[]::new) allows you to have a DBObject... from a stream
        worldsQuery.and(searchWords.stream().map(s -> new QueryBuilder()
                .or(
                        QueryBuilder.start(Field.TITLE).regex(Pattern.compile("(^|$|\\W)" + s + "(^|$|\\W)", (Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE))).get(),
                        QueryBuilder.start(Field.CONTENT).regex(Pattern.compile("(^|$|\\W)" + s + "(^|$|\\W)", (Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE))).get()
                ).get()).toArray(DBObject[]::new));

        final QueryBuilder blogQuery = QueryBuilder.start("blog.$id").in(setIds);
        final QueryBuilder publishedQuery = QueryBuilder.start("state").is(Field.PUBLISHED);

        final QueryBuilder query = new QueryBuilder().and(worldsQuery.get(), blogQuery.get(), publishedQuery.get());

        JsonObject sort = new JsonObject().put("modified", -1);
        final JsonObject projection = new JsonObject();
        projection.put("title", 1);
        projection.put("content", 1);
        projection.put("blog.$id", 1);
        projection.put("modified", 1);
        projection.put("author.userId", 1);
        projection.put("author.username", 1);

        mongo.find(Blog.POSTS_COLLECTION, MongoQueryBuilder.build(query), sort,
                projection, skip, limit, Integer.MAX_VALUE, validResultsHandler(result -> {
                    if (result.isRight()) {
                        promise.complete(result.right().getValue());
                    } else {
                        promise.fail(result.left().getValue());
                    }
                }));

        return promise.future();
    }

    @Override
    public JsonArray formatSearchResult(final JsonArray results, final JsonArray columnsHeader) {
        try {
            final List<String> aHeader = columnsHeader.getList();
            final JsonArray traity = new JsonArray();

            for (int i = 0; i < results.size(); i++) {
                final JsonObject j = results.getJsonObject(i);
                final JsonObject jr = new JsonObject();
                if (j != null) {
                    final String blogId = j.getJsonObject("blog").getString("$id");
                    jr.put(aHeader.get(0), j.getString("title"));
                    jr.put(aHeader.get(1), j.getString("content", ""));
                    jr.put(aHeader.get(2), j.getJsonObject("modified"));
                    jr.put(aHeader.get(3), j.getJsonObject("author").getString("username"));
                    jr.put(aHeader.get(4), j.getJsonObject("author").getString("userId"));
                    jr.put(aHeader.get(5), "/blog#/view/" + blogId + "/" + j.getString("_id"));
                    traity.add(jr);
                }
            }
            return traity;
        } catch (Exception e) {
            log.error(String.format("[Blog@%s::formatSearchResult] Failed to format search result %s:%s", this.getClass().getSimpleName(), e.getClass().getSimpleName(), e.getMessage()));
            return new JsonArray();
        }
    }
}
