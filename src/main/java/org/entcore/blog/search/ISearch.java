package org.entcore.blog.search;

import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;

import java.util.List;

public interface ISearch {
    Future<JsonArray> get(String userId, List<String> groupIds, List<String> searchWords, Integer page, Integer limit);

    JsonArray formatSearchResult(JsonArray blogArray, JsonArray columnsHeader);

}
